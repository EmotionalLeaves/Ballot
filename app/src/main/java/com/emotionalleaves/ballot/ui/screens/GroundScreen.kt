package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.core.CampaignMath
import com.emotionalleaves.ballot.core.MathInput
import com.emotionalleaves.ballot.core.RaceInput
import com.emotionalleaves.ballot.core.RaceRecommendation
import com.emotionalleaves.ballot.core.RaceScore
import com.emotionalleaves.ballot.core.RaceScorer
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.data.Office
import com.emotionalleaves.ballot.data.RaceCard
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.BigNumber
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ConfidenceBadge
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun GroundScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val offices by g.db.offices().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val cards by g.db.raceCards().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by g.settings.flow.collectAsStateWithLifecycle(initialValue = com.emotionalleaves.ballot.data.AppSettings())
    var open by remember { mutableStateOf<Long?>(null) }
    var rec by remember { mutableStateOf<RaceRecommendation?>(null) }
    var showWorking by remember { mutableStateOf<RaceScore?>(null) }
    val c = LocalBallotColors.current

    fun scoreRaces() {
        scope.launch {
            val p = g.case.profile()
            val contacts = g.db.contacts().all()
            val issues = p.issues.lowercase().split(Regex("[,\\n;]+| and ")).map { it.trim() }.filter { it.length > 2 }
            val stage = Stage.of(p.stage)
            val inputs = offices.map { o ->
                val rc = cards.firstOrNull { it.officeId == o.id } ?: RaceCard(officeId = o.id)
                val win = CampaignMath.winNumber(rc.pastTurnout, rc.seats, g.settings.get().safetyMarginPct)
                val overlap = issues.count { iss -> iss.split(' ').any { w -> w.length > 3 && o.powers.lowercase().contains(w) } }.coerceAtMost(3)
                val months = if (rc.filingDeadlineEpochDay > 0) ChronoUnit.MONTHS.between(LocalDate.now(), LocalDate.ofEpochDay(rc.filingDeadlineEpochDay)).toInt() else 0
                RaceInput(o.id, "${o.body} ${o.district}".trim(), rc.openSeat, rc.incumbentRetiring, rc.uncontestedHistory, win, rc.pastWinnerSpendCents,
                    contacts.count { it.livesInDistrict }, overlap, rc.hoursPerWeek, p.weeklyHours, months,
                    (Stage.RUN.number - stage.number).coerceAtLeast(0), o.partTime)
            }
            val scores = inputs.map { RaceScorer.score(it) }
            scores.forEach { s ->
                val rc = cards.firstOrNull { it.officeId == s.officeId } ?: RaceCard(officeId = s.officeId)
                g.db.raceCards().upsert(rc.copy(score = s.total, scoreWorking = s.lines.joinToString("\n") { "${it.factor} ${it.points}: ${it.note}" }, updatedAt = now()))
            }
            rec = RaceScorer.recommend(scores, inputs)
            g.case.audit("app", "Scored races", outcome = scores.joinToString { "${it.name}=${it.total}" })
        }
    }

    Screen("Offices and races", kicker = "Your political map", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Every office that represents you, who holds it, and when it is next on the ballot.",
        "Each fact is linked to its official source with a label: Verified, Likely or Unverified.",
        "Race cards hold filing rules, signatures, past turnout and past spending, from official pages only.",
        "Score races ranks every office on 0 to 100 and shows the working. The Chief recommends a target, a stretch and a fallback.",
        "The win number calculator turns turnout into doors and hours.")) {
        item { BusyBar() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton("Find my offices with the Chief", onClick = {
                    askChief(g, "Find every office that represents me (city, county, school district, state, federal) from official pages. Save them with find_offices, " +
                        "each with its official link and source. Local and part-time offices first. Define each body in one plain sentence.", "finding offices")
                })
                PosterButton("Score races", primary = false, enabled = offices.isNotEmpty(), onClick = { scoreRaces() })
            }
        }
        // Adding an office by hand needs no AI: a city website, a ballot or a phone call to the clerk is enough.
        item { AddOfficeByHand() }
        item { ReplyPanel { nav.navigate(Routes.CHIEF) } }
        rec?.let { r ->
            item {
                Panel(accent = true) {
                    Text("Recommendation", style = MaterialTheme.typography.labelLarge, color = com.emotionalleaves.ballot.ui.theme.Ink)
                    listOf("Target" to r.target, "Stretch" to r.stretch, "Fallback" to r.fallback).forEach { (label, s) ->
                        if (s != null) Text("$label: ${s.name} · ${s.total}/100", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                    }
                    Text("Scores use only stored, sourced numbers. Missing data scores low on purpose: fill the race card first.",
                        style = MaterialTheme.typography.bodySmall, color = com.emotionalleaves.ballot.ui.theme.Ink)
                }
            }
        }
        item { Title("Offices (${offices.size})") }
        if (offices.isEmpty()) item { EmptyNote("No offices yet. Tap Find my offices, or tell the Chief your ZIP if you skipped it.") }
        items(offices.size) { i ->
            val o = offices[i]
            val rc = cards.firstOrNull { it.officeId == o.id }
            OfficePanel(o, rc, settings.safetyMarginPct, expanded = open == o.id, onToggle = { open = if (open == o.id) null else o.id }, onWorking = {
                showWorking = RaceScore(o.id, o.body, rc?.score ?: 0, rc?.scoreWorking?.split("\n")?.filter { it.isNotBlank() }?.map {
                    com.emotionalleaves.ballot.core.ScoreLine("", 0, it)
                } ?: emptyList())
            }, onRace = {
                askChief(g, "Build the race card for office O${o.id} (${o.body} ${o.district}): filing rules, signatures, fee, filing deadline, seats, last turnout, margin, " +
                    "what the last winner spent and hours per week. Use official election results and campaign finance pages only, save with get_race_card, and add the filing deadline with add_deadline.", "building race card")
            }, onExplain = {
                askChief(g, "Explain in plain language what the ${o.body} ${o.district} really controls, how it makes decisions, and how often it meets. Then ask me one teach-back question.", "explaining")
                nav.navigate(Routes.CHIEF)
            }, onDelete = { scope.launch { g.db.offices().delete(o) } }, onOpen = { openUrl(ctx, it) })
        }
        item { WinNumberCalculator(cards.maxByOrNull { it.score }) }
    }
    showWorking?.let { s ->
        com.emotionalleaves.ballot.ui.components.WalkthroughDialog("Score ${s.total}/100", s.lines.map { it.note }.ifEmpty { listOf("Tap Score races first.") }) { showWorking = null }
    }
}

@Composable
private fun OfficePanel(o: Office, rc: RaceCard?, margin: Int, expanded: Boolean, onToggle: () -> Unit, onWorking: () -> Unit, onRace: () -> Unit,
                        onExplain: () -> Unit, onDelete: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalBallotColors.current
    Panel(onClick = onToggle) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Kicker("${pretty(o.level)} · ${if (o.appointed) "appointed" else "elected"}${if (o.partTime) " · part time" else ""}"); ConfidenceBadge(o.confidence) }
        Text("${o.body} ${o.district}".trim(), style = MaterialTheme.typography.titleLarge, color = c.fg)
        Small("Held by ${o.incumbent.ifBlank { "unknown" }} · next on the ballot ${o.nextElection.ifBlank { "unknown" }}", color = c.fg)
        if (rc != null && rc.score > 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigNumber("${rc.score}", "Race score", size = 34, color = c.signal)
            BigNumber(if (rc.pastTurnout > 0) "${CampaignMath.winNumber(rc.pastTurnout, rc.seats, margin)}" else "—", "Win number", size = 34)
        }
        if (expanded) {
            if (o.powers.isNotBlank()) Body("Controls: ${o.powers}")
            if (o.term.isNotBlank()) Small("Term: ${o.term}")
            if (rc != null) {
                Small("Filing: ${rc.filingRules.ifBlank { "not loaded" }}")
                Small("Signatures ${rc.signaturesNeeded} · fee ${dollars(rc.filingFeeCents)} · filing deadline ${day(rc.filingDeadlineEpochDay)} · seats ${rc.seats}")
                Small("Last turnout ${rc.pastTurnout} · last winner spent ${dollars(rc.pastWinnerSpendCents)} · sources ${rc.sourceIds.ifBlank { "none" }}")
            }
            Small("Last checked ${shortDate(o.lastChecked)}")
            Row {
                LinkButton("Race card", onClick = onRace)
                LinkButton("Explain", onClick = onExplain)
                if (rc?.scoreWorking?.isNotBlank() == true) LinkButton("Working", onClick = onWorking)
            }
            Row {
                if (o.officialUrl.isNotBlank()) LinkButton("Official page", onClick = { onOpen(o.officialUrl) })
                LinkButton("Delete", onClick = onDelete, color = c.muted)
            }
        }
    }
}

@Composable
fun WinNumberCalculator(card: RaceCard?) {
    val g = LocalContext.current.graph
    val c = LocalBallotColors.current
    var turnout by remember(card?.id) { mutableStateOf(card?.pastTurnout?.takeIf { it > 0 }?.toString() ?: "") }
    var seats by remember(card?.id) { mutableStateOf((card?.seats ?: 1).toString()) }
    var margin by remember { mutableStateOf("10") }
    var support by remember { mutableStateOf("50") }
    var cph by remember { mutableStateOf("8") }
    var weeks by remember { mutableStateOf("12") }
    var hours by remember { mutableStateOf("6") }
    var vol by remember { mutableStateOf("0") }
    LaunchedEffect(Unit) {
        val s = g.settings.get()
        margin = s.safetyMarginPct.toString(); support = s.supportRatePct.toString(); cph = s.contactsPerHour.toString(); vol = s.volunteerHoursPerWeek.toString()
        hours = g.case.profile().weeklyHours.toString()
    }
    Panel {
        Title("Win number")
        Small("Use turnout from official past results only. Support rate and contacts per hour should be your measured numbers once you have them.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(turnout, { turnout = it.filter(Char::isDigit) }, "Expected turnout", Modifier.weight(1f), singleLine = true, number = true)
            Field(seats, { seats = it.filter(Char::isDigit) }, "Seats", Modifier.weight(0.6f), singleLine = true, number = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(margin, { margin = it.filter(Char::isDigit) }, "Margin %", Modifier.weight(1f), singleLine = true, number = true)
            Field(support, { support = it.filter(Char::isDigit) }, "Support %", Modifier.weight(1f), singleLine = true, number = true)
            Field(cph, { cph = it.filter(Char::isDigit) }, "Doors/hr", Modifier.weight(1f), singleLine = true, number = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(weeks, { weeks = it.filter(Char::isDigit) }, "Weeks left", Modifier.weight(1f), singleLine = true, number = true)
            Field(hours, { hours = it.filter(Char::isDigit) }, "Your h/wk", Modifier.weight(1f), singleLine = true, number = true)
            Field(vol, { vol = it.filter(Char::isDigit) }, "Volunteer h/wk", Modifier.weight(1f), singleLine = true, number = true)
        }
        val t = turnout.toIntOrNull() ?: 0
        if (t > 0) {
            val r = CampaignMath.run(MathInput(t, seats.toIntOrNull() ?: 1, margin.toIntOrNull() ?: 10, support.toIntOrNull() ?: 50,
                cph.toIntOrNull() ?: 8, weeks.toIntOrNull() ?: 12, hours.toIntOrNull() ?: 0, vol.toIntOrNull() ?: 0))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BigNumber("${r.winNumber}", "Votes to win", color = c.signal)
                BigNumber("${r.conversationsNeeded}", "Conversations")
                BigNumber("${r.hoursNeeded}", "Hours")
            }
            Small("Weekly: ${r.weeklyConversations} conversations, ${r.weeklyHours} hours.", color = c.fg)
            r.explanation.forEach { Small(it) }
            if (!r.closes) Panel(accent = true) {
                Text("The math doesn't add up yet", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                r.shortLevers.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink) }
            }
        }
    }
}

@Composable
private fun AddOfficeByHand() {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val c = LocalBallotColors.current
    var open by remember { mutableStateOf(false) }
    var body by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("LOCAL") }
    var holder by remember { mutableStateOf("") }
    var term by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var powers by remember { mutableStateOf("") }
    var appointed by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    LinkButton(if (open) "Hide" else "Add an office by hand", onClick = { open = !open })
    if (!open) return
    Panel {
        Small("From your city or county website, a sample ballot, or a call to the clerk. Leave out anything you're not sure of.")
        Field(body, { body = it }, "Body, e.g. City Council", singleLine = true)
        Field(district, { district = it }, "Ward or district (if any)", singleLine = true)
        com.emotionalleaves.ballot.ui.components.ChipRow(listOf("LOCAL", "COUNTY", "SCHOOL", "STATE", "FEDERAL"), level, { pretty(it) }) { level = it }
        Field(holder, { holder = it }, "Who holds it now", singleLine = true)
        Field(term, { term = it }, "Term length, e.g. 4 years", singleLine = true)
        Field(next, { next = it }, "Next on the ballot, e.g. November 2027", singleLine = true)
        Field(powers, { powers = it }, "What it really controls", minLines = 2)
        com.emotionalleaves.ballot.ui.components.SwitchRow("Filled by appointment, not election", appointed, { appointed = it })
        Field(url, { url = it.trim() }, "Official page (optional)", singleLine = true)
        PosterButton("Save office", enabled = body.isNotBlank(), onClick = {
            scope.launch {
                g.db.offices().upsert(com.emotionalleaves.ballot.data.Office(body = body.trim(), district = district.trim(), level = level, incumbent = holder.trim(),
                    term = term.trim(), nextElection = next.trim(), powers = powers.trim(), appointed = appointed, officialUrl = url,
                    source = "you", confidence = com.emotionalleaves.ballot.data.Confidence.STATED))
                g.case.audit("you", "Added an office by hand", input = body.trim())
                body = ""; district = ""; holder = ""; term = ""; next = ""; powers = ""; url = ""; appointed = false; open = false
            }
        })
        Small("Saved as 'you said'. Add the official page later and it can be checked.", color = c.muted)
    }
}

