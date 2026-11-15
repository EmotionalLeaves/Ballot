package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.emotionalleaves.ballot.core.Scheduling
import com.emotionalleaves.ballot.data.Event
import com.emotionalleaves.ballot.data.EventType
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
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
import com.emotionalleaves.ballot.ui.components.rememberMicGate
import com.emotionalleaves.ballot.ui.components.rememberSpeechCapture
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import com.emotionalleaves.ballot.work.Workers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun BriefingScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val briefItems by g.db.brief().observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())
    val c = LocalBallotColors.current
    Screen("Briefing", kicker = "What is happening near you", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "A two-minute digest of local news, upcoming agendas and decisions that touch your issues.",
        "It is built in the background after work, so it is waiting when you open the app.",
        "Each item ends with one optional action.")) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton("Build today's brief", onClick = { Workers.briefNow(ctx) })
                PosterButton("Weekly long read", primary = false, onClick = {
                    askChief(g, "Write this week's long read: one topic I need to understand for my town (budgets, zoning, or whatever my issues need). Plain language, sourced, under 600 words. Save it with draft_document type OTHER.", "long read")
                    nav.navigate(Routes.CHIEF)
                })
            }
        }
        item { Small("Building runs in the background and needs a connection. Pull this screen again in a minute.") }
        if (briefItems.isEmpty()) item { EmptyNote("Nothing yet. The first brief arrives after your chosen time, or tap Build today's brief.") }
        val grouped = briefItems.groupBy { it.dayEpoch }
        grouped.forEach { (d, list) ->
            item { Title(day(d)) }
            items(list.size) { i ->
                val b = list[i]
                Panel(onClick = if (b.url.isNotBlank()) ({ openUrl(ctx, b.url) }) else null) {
                    Kicker(b.kind)
                    Text(b.title, style = MaterialTheme.typography.titleMedium, color = c.fg)
                    if (b.body.isNotBlank()) Body(b.body)
                    if (b.action.isNotBlank()) Small("Action: ${b.action}", color = c.signal)
                    if (b.url.isNotBlank()) Small(com.emotionalleaves.ballot.core.Verification.host(b.url))
                }
            }
        }
    }
}

@Composable
fun FieldScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val events by g.db.events().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(EventType.MEETING) }
    var place by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var time by remember { mutableStateOf("18:30") }
    var goal by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var debriefing by remember { mutableStateOf<Long?>(null) }
    val speech = rememberSpeechCapture()
    val mic = rememberMicGate()
    val c = LocalBallotColors.current
    val nowMs = now()
    Screen("Field", kicker = "Showing up", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Your calendar of public meetings, community events and volunteer shifts, outside work hours.",
        "Before an event, tap Pre-event card: who will be there, what to say, one goal.",
        "After an event, tap Debrief and just talk for a minute: who you met, what you promised. The Chief files contacts and follow-ups for you to confirm.",
        "First meeting? Go once silently. Listening counts.")) {
        item { BusyBar() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton(if (adding) "Close" else "Add event", onClick = { adding = !adding })
                PosterButton("Find meetings", primary = false, onClick = {
                    askChief(g, "Find upcoming public meetings (council, school board, county, planning) and community events near me from official calendars. " +
                        "Add the ones outside my work hours with add_event, with agenda links when posted.", "finding meetings")
                })
            }
        }
        if (adding) item {
            Panel {
                Field(title, { title = it }, "What", singleLine = true)
                ChipRow(EventType.all, type, { it.replace('_', ' ').lowercase() }) { type = it }
                Field(place, { place = it }, "Where", singleLine = true)
                // Tap a day and a time instead of typing dates on a phone keyboard.
                val days = (0..13).map { LocalDate.now().plusDays(it.toLong()) }
                ChipRow(days, parseDay(date)?.let { LocalDate.ofEpochDay(it) }, { d ->
                    when (d) { LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"
                        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d")) }
                }) { date = it.toString() }
                ChipRow(listOf("09:00", "12:00", "16:30", "17:30", "18:00", "18:30", "19:00"), time, { it }) { time = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(date, { date = it }, "Date (YYYY-MM-DD)", Modifier.weight(1f), singleLine = true)
                    Field(time, { time = it }, "Time (HH:MM)", Modifier.weight(1f), singleLine = true)
                }
                Field(goal, { goal = it }, "One goal for the night", singleLine = true, placeholder = "e.g. introduce yourself to two people")
                if (error.isNotBlank()) Small(error, color = c.signal)
                PosterButton("Save", enabled = title.isNotBlank(), onClick = {
                    scope.launch {
                        val d = parseDay(date); val t = parseHm(time)
                        if (d == null || t == null) { error = "Check the date and time format."; return@launch }
                        val dt = LocalDate.ofEpochDay(d).atStartOfDay().plusMinutes(t.toLong())
                        if (Scheduling.insideWork(g.case.availability(), dt)) { error = "That is inside your work hours."; return@launch }
                        g.db.events().upsert(Event(type = type, title = title, place = place, startAt = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), goal = goal))
                        title = ""; place = ""; goal = ""; error = ""; adding = false
                    }
                })
            }
        }
        item {
            Panel {
                Title("Board and commission finder")
                Small("Many boards are filled by appointment. That builds a record without an election.")
                LinkButton("Find openings I can apply for", onClick = {
                    askChief(g, "Find appointed boards and commissions near me with current openings, from official city and county vacancy pages. " +
                        "For each: what it does, time commitment, eligibility, how to apply and the deadline. Offer to draft an application.", "finding boards")
                    nav.navigate(Routes.CHIEF)
                })
            }
        }
        val upcoming = events.filter { it.startAt >= nowMs - 3 * 3_600_000L }.sortedBy { it.startAt }
        val past = events.filter { it.startAt < nowMs - 3 * 3_600_000L }
        item { Title("Coming up (${upcoming.size})") }
        if (upcoming.isEmpty()) item { EmptyNote("Nothing planned. Tap Find meetings.") }
        items(upcoming.size) { i -> EventPanel(upcoming[i], nav, debriefing, onDebrief = { id -> debriefing = id }, speech = speech, mic = mic) }
        item { Title("Past (${past.size})") }
        items(past.take(30).size) { i -> EventPanel(past[i], nav, debriefing, onDebrief = { id -> debriefing = id }, speech = speech, mic = mic) }
    }
}

@Composable
private fun EventPanel(
    e: Event,
    nav: NavHostController,
    debriefing: Long?,
    onDebrief: (Long?) -> Unit,
    speech: com.emotionalleaves.ballot.ui.components.SpeechCapture,
    mic: (() -> Unit) -> Unit,
) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val c = LocalBallotColors.current
    var typed by remember(e.id) { mutableStateOf("") }
    Panel {
        Kicker("${e.type.replace('_', ' ')} · ${dateTime(e.startAt)}")
        Text(e.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = c.fg)
        if (e.place.isNotBlank()) Small(e.place, color = c.fg)
        if (e.goal.isNotBlank()) Small("Goal: ${e.goal}", color = c.signal)
        if (e.preCard.isNotBlank()) Body(e.preCard)
        if (e.debrief.isNotBlank()) Small("Debrief: ${e.debrief.take(300)}")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(e.attended, onCheckedChange = { v -> scope.launch { g.db.events().upsert(e.copy(attended = v, updatedAt = now())); g.case.advanceIfReady() } })
            Small("Attended", color = c.fg)
            Checkbox(e.gaveComment, onCheckedChange = { v -> scope.launch { g.db.events().upsert(e.copy(gaveComment = v, attended = e.attended || v, updatedAt = now())); g.case.advanceIfReady() } })
            Small("Gave a comment", color = c.fg)
        }
        Row {
            LinkButton("Pre-event card", onClick = {
                askChief(g, "Write a pre-event card for \"${e.title}\" on ${dateTime(e.startAt)} at ${e.place}: who is likely to be there (by role, from sources), what to say in one line, " +
                    "and one goal for the night. Keep it under 120 words. If the agenda is posted, check it: ${e.agendaUrl}", "pre-event card") { r ->
                    scope.launch { g.db.events().get(e.id)?.let { g.db.events().upsert(it.copy(preCard = r.text.take(1200), updatedAt = now())) } }
                }
            })
            if (e.agendaUrl.isNotBlank()) LinkButton("Agenda", onClick = { openUrl(ctx, e.agendaUrl) })
        }
        if (debriefing == e.id) {
            Small(if (speech.listening) speech.partial.ifBlank { "Listening…" } else "Talk for a minute: who you met, what you promised, one thing you learned.", color = c.fg)
            Field(typed, { typed = it }, "Or type it", minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosterButton(if (speech.listening) "Stop" else "Speak", onClick = {
                    if (speech.listening) speech.stop() else mic { speech.start { text, _ -> typed = (typed + " " + text).trim() } }
                })
                PosterButton("File it", primary = false, enabled = typed.isNotBlank(), onClick = {
                    val text = typed
                    scope.launch { g.db.events().upsert(e.copy(debrief = text, attended = true, updatedAt = now())) }
                    askChief(g, "Debrief for the event \"${e.title}\" (${dateTime(e.startAt)}): $text\n\nFile every person I met with add_contact and log_interaction, " +
                        "every promise as a follow-up, and one lesson with record_lesson. Then list what you filed.", "filing the debrief", fromVoice = true)
                    onDebrief(null)
                    nav.navigate(Routes.CHIEF)
                })
            }
        } else LinkButton("Debrief", onClick = { onDebrief(e.id) })
    }
}
