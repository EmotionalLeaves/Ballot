package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.core.CampaignMath
import com.emotionalleaves.ballot.core.MathInput
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.data.ContactRole
import com.emotionalleaves.ballot.data.DoorOutcome
import com.emotionalleaves.ballot.data.Money
import com.emotionalleaves.ballot.data.MoneyDirection
import com.emotionalleaves.ballot.data.Task
import com.emotionalleaves.ballot.data.VoterContact
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.BigNumber
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
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

private val launchItems = listOf(
    "Form your campaign committee (if your rules require one)" to "hq.launch.committee",
    "Name a treasurer" to "hq.launch.treasurer",
    "Open a separate campaign bank account" to "hq.launch.bank",
    "File your candidacy with the election office" to "hq.launch.filing",
    "Announce: launch statement and first event" to "hq.launch.announce",
)

@Composable
fun HqScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val tasks by g.db.tasks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val contacts by g.db.contacts().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val doors by g.db.voterContacts().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val cards by g.db.raceCards().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val money by g.db.money().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by g.settings.flow.collectAsStateWithLifecycle(initialValue = com.emotionalleaves.ballot.data.AppSettings())
    var mode by remember { mutableStateOf("Launch") }
    val c = LocalBallotColors.current
    val p = profile ?: return
    val unlocked = p.stage >= Stage.RUN.number

    LaunchedEffect(unlocked) {
        if (unlocked) launchItems.forEach { (title, key) ->
            if (g.db.tasks().countTemplate(key) == 0) g.db.tasks().upsert(Task(stage = 7, title = title, templateKey = key, legal = key.endsWith("filing"),
                why = "Part of the launch checklist.", doneLooksLike = "Done, with any receipt saved to the Vault.", module = "hq", sizeMinutes = 45, highEffort = true))
        }
    }

    Screen("Campaign HQ", kicker = if (unlocked) "Stage 7 · Run" else "Unlocks at stage 7", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Launch: committee, treasurer, bank account, filing, announcement, in that order. Confirm each rule with the election office.",
        "Call time: one name on screen, a context card, a script, outcome buttons, then a thank-you draft.",
        "Doors: big buttons at each door. Supporters count against your win number.",
        "Voter lists come only from the lawful voter file and are used only as that source permits.",
        "Contact hours are tracked first. Desk work never replaces voter contact.")) {
        if (!unlocked) {
            item {
                Panel {
                    Body("HQ opens when you pass the go or no-go test. Until then the work is getting known and trusted, which is what wins first races.")
                    Small("Inside: launch checklist, fundraising and call time, door knocking, volunteers, turnout plan.")
                    PosterButton("See the path", onClick = { nav.navigate(Routes.PATH) })
                }
            }
            return@Screen
        }
        item {
            val days = if (p.electionDateEpochDay > 0) p.electionDateEpochDay - LocalDate.now().toEpochDay() else -1
            var date by remember { mutableStateOf(if (p.electionDateEpochDay > 0) LocalDate.ofEpochDay(p.electionDateEpochDay).toString() else "") }
            Panel(accent = true) {
                BigNumber(if (days >= 0) "$days" else "—", "Days to election day", color = com.emotionalleaves.ballot.ui.theme.Ink, size = 72)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(date, { date = it }, "Election day (YYYY-MM-DD, from the official calendar)", Modifier.weight(1f), singleLine = true)
                    LinkButton("Set", color = com.emotionalleaves.ballot.ui.theme.Ink, onClick = { parseDay(date)?.let { d -> scope.launch { g.case.updateProfile { it.copy(electionDateEpochDay = d) } } } })
                }
            }
        }
        item { ChipRow(listOf("Launch", "Call time", "Doors", "Volunteers", "Turnout"), mode, { it }) { mode = it } }
        when (mode) {
            "Launch" -> {
                items(launchItems.size) { i ->
                    val (title, key) = launchItems[i]
                    val t = tasks.firstOrNull { it.templateKey == key }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(t?.done == true, { v -> t?.let { scope.launch { if (v) g.case.completeTask(it) else g.db.tasks().upsert(it.copy(done = false, updatedAt = now())) } } })
                        Body(title)
                    }
                }
                item {
                    LinkButton("Walk me through the next item", onClick = {
                        val next = launchItems.firstOrNull { (_, k) -> tasks.firstOrNull { it.templateKey == k }?.done != true }?.first ?: "the announcement"
                        askChief(g, "Walk me through this launch step with official sources for my jurisdiction: $next. Prepare any form as a draft for me to file myself.", "launch step")
                        nav.navigate(Routes.CHIEF)
                    })
                }
            }
            "Call time" -> item { CallTime(contacts.filter { it.role != ContactRole.PRESS }.sortedByDescending { it.closeness }) }
            "Doors" -> item {
                val weeksLeft = if (p.electionDateEpochDay > 0) ((p.electionDateEpochDay - LocalDate.now().toEpochDay()) / 7).toInt().coerceAtLeast(1) else 0
                Doors(doors, cards.maxByOrNull { it.score }?.let { CampaignMath.winNumber(it.pastTurnout, it.seats, settings.safetyMarginPct) } ?: 0, weeksLeft, settings.contactsPerHour)
            }
            "Volunteers" -> {
                val vols = contacts.filter { it.role == ContactRole.VOLUNTEER }
                if (vols.isEmpty()) item { EmptyNote("No volunteers yet. Mark people as Volunteer on their contact page.") }
                items(vols.size) { i ->
                    val v = vols[i]
                    Panel(onClick = { nav.navigate(Routes.contact(v.id)) }, padding = 12.dp) {
                        Text(v.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = c.fg)
                        Small(v.nextStep.ifBlank { "No shift booked" })
                    }
                }
                item {
                    LinkButton("Draft thank-yous", onClick = {
                        askChief(g, "Draft short personal thank-you messages for each of my volunteers with queue_follow_up.", "thank-yous")
                        nav.navigate(Routes.APPROVALS)
                    })
                }
            }
            else -> item {
                Panel {
                    Title("Turnout plan")
                    val supporters = doors.count { it.outcome == DoorOutcome.SUPPORTER }
                    Body("$supporters supporters identified. The plan: make sure each one knows when, where and how to vote early or on election day.")
                    PosterButton("Build the turnout plan", onClick = {
                        askChief(g, "Build my turnout plan for early vote and election day from official election calendars: key dates, what to tell each identified supporter, " +
                            "reminder timing, and volunteer shifts. Create the tasks with create_task. Never schedule inside my work hours.", "turnout plan")
                        nav.navigate(Routes.CHIEF)
                    })
                }
            }
        }
        item {
            val raised = money.filter { it.direction != MoneyDirection.OUT }.sumOf { it.amountCents }
            Small("Raised so far ${dollars(raised)} · doors knocked ${doors.count { it.outcome != DoorOutcome.NOT_HOME }}")
        }
    }
}

@Composable
private fun CallTime(list: List<com.emotionalleaves.ballot.data.Contact>) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    var i by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    if (list.isEmpty()) { EmptyNote("Your first-money list comes from your network. Add people in People first."); return }
    val p = list[i % list.size]
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Kicker("Call ${i % list.size + 1} of ${list.size}")
        Panel {
            Text(p.name.uppercase(), style = MaterialTheme.typography.displaySmall, color = c.fg)
            Small("How you met: ${p.howMet.ifBlank { "—" }} · cares about: ${p.interests.ifBlank { "—" }} · last contact ${shortDate(p.lastContactAt)}", color = c.fg)
            if (p.suggestedAskCents > 0) Small("Suggested ask: ${dollars(p.suggestedAskCents)}", color = c.signal)
            if (p.callOutcome.isNotBlank()) Small("Last outcome: ${p.callOutcome}")
            Body("Script: \"Hi ${p.name.substringBefore(' ')}, it's me. I'm running for local office because [your reason]. Early money decides whether I can reach voters. " +
                "Would you give [amount] to help me get started?\" Then stop talking.")
            if (p.phone.isNotBlank()) PosterButton("Dial", onClick = { dial(ctx, p.phone) })
        }
        Field(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount pledged or given", singleLine = true, number = true)
        listOf("Gave", "Pledged", "Call back", "No", "No answer").chunked(3).forEach { rowItems -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { o ->
                PosterButton(o, primary = o == "Gave" || o == "Pledged", modifier = Modifier.weight(1f).heightIn(min = 56.dp), onClick = {
                    scope.launch {
                        val cents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
                        g.db.contacts().upsert(p.copy(callOutcome = o, pledgeCents = if (o == "Pledged") cents else p.pledgeCents, lastContactAt = now(), updatedAt = now()))
                        if (o == "Gave" && cents > 0) g.db.money().upsert(Money(direction = MoneyDirection.IN, amountCents = cents, person = p.name, contactId = p.id, category = "contribution"))
                        if (o == "Gave" || o == "Pledged") askChief(g, "Draft a short thank-you to ${p.name} for ${if (o == "Gave") "giving" else "pledging"} ${dollars(cents)}. Use queue_follow_up. " +
                            "Remind me to collect any required donor details (address, employer, occupation) if my rules need them.", "thank-you")
                        g.case.audit("you", "Call time", input = "${p.name}: $o ${dollars(cents)}")
                        amount = ""; i++
                    }
                })
            }
        } }
        Row {
            LinkButton("Skip", onClick = { i++ })
            LinkButton("Suggest asks", onClick = {
                askChief(g, "Suggest a contribution ask for each person in my first-money list based only on what I've told you about them and my contribution limits. " +
                    "Never above a loaded limit. List them; I will set them myself.", "suggesting asks")
            })
        }
    }
}

@Composable
private fun Doors(doors: List<VoterContact>, winNumber: Int, weeksLeft: Int, contactsPerHour: Int) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var route by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    val supporters = doors.count { it.outcome == DoorOutcome.SUPPORTER }
    val talked = doors.count { it.outcome != DoorOutcome.NOT_HOME }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BigNumber("$supporters", if (winNumber > 0) "of $winNumber supporters" else "Supporters", color = c.signal)
            BigNumber("$talked", "Conversations")
            BigNumber(if (talked > 0) "${supporters * 100 / talked}%" else "—", "Support rate")
        }
        Small("Scripts under 30 seconds, listening first: \"Hi, I'm running for [office]. What's the biggest thing you'd change around here?\" Then listen.")
        Field(route, { route = it }, "Route or street", singleLine = true)
        Field(issue, { issue = it }, "Issue you heard (optional)", singleLine = true)
        listOf(DoorOutcome.SUPPORTER to "Supporter", DoorOutcome.UNDECIDED to "Undecided", DoorOutcome.OPPOSED to "Opposed", DoorOutcome.NOT_HOME to "Not home").chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (o, label) ->
                    PosterButton(label, primary = o == DoorOutcome.SUPPORTER, modifier = Modifier.weight(1f).heightIn(min = 72.dp), onClick = {
                        scope.launch { g.db.voterContacts().upsert(VoterContact(route = route, outcome = o, issueHeard = issue)); issue = "" }
                    })
                }
            }
        }
        val heard = doors.mapNotNull { it.issueHeard.trim().lowercase().takeIf { s -> s.isNotBlank() } }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
        if (heard.isNotEmpty()) {
            Kicker("What voters are saying")
            heard.forEach { Small("${it.key}: ${it.value}", color = c.fg) }
            LinkButton("Feed this into my stump speech", onClick = {
                askChief(g, "Here is what voters told me at the doors: ${heard.joinToString { "${it.key} (${it.value})" }}. Suggest how my stump speech and door script should change. Do not change my positions.", "updating scripts")
            })
        }
        val measured = if (talked >= 20) supporters * 100 / talked else 0
        if (measured > 0 && winNumber > 0) {
            // The win number already includes the safety margin, so work from it directly.
            val convs = CampaignMath.conversationsNeeded((winNumber - supporters).coerceAtLeast(0), measured)
            val hours = CampaignMath.hoursNeeded(convs, contactsPerHour)
            Small("At your measured ${measured}% support rate you need about $convs more conversations, roughly $hours hours at the doors" +
                if (weeksLeft > 0) ": ${(convs + weeksLeft - 1) / weeksLeft} conversations a week for $weeksLeft weeks." else ". Set election day above for weekly targets.", color = c.fg)
        }
    }
}
