package com.emotionalleaves.ballot.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.agent.Block
import com.emotionalleaves.ballot.agent.LlmMessage
import com.emotionalleaves.ballot.agent.MockRooms
import com.emotionalleaves.ballot.core.SpeechScorer
import com.emotionalleaves.ballot.data.ChatMessage
import com.emotionalleaves.ballot.data.SpeechRep
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.BigNumber
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.DrillRecorder
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Gutter
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.ThickRule
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.components.rememberMicGate
import com.emotionalleaves.ballot.ui.components.rememberSpeechCapture
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch

private data class Drill(val id: String, val title: String, val targetSec: Int, val prompt: String)

private val drills = listOf(
    Drill("INTRO", "Introduce yourself", 30, "Say your name, where you live, and one thing you care about. 30 seconds."),
    Drill("STORY", "60-second story", 60, "Why are you doing this? Tell it as a story, not a list."),
    Drill("COMMENT", "Public comment", 120, "Two minutes, one point, one ask, as if at the microphone."),
    Drill("HOSTILE", "Answer a hard question", 45, "\"Why should anyone trust a first-timer?\" Answer honestly in 45 seconds."),
    Drill("STUMP", "3-minute stump speech", 180, "Your reason, your three issues, your ask."),
)

private val ladder = listOf("INTRO", "STORY", "COMMENT", "HOSTILE", "STUMP", "MOCK_reporter", "MOCK_forum", "MOCK_debate")
private val ladderNames = listOf("Introduce yourself", "60-second story", "Public comment", "Hard question", "Stump speech", "Reporter interview", "Candidate forum", "Win a debate")

@Composable
fun GymScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val reps by g.db.speech().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    var drill by remember { mutableStateOf(drills[1]) }
    var last by remember { mutableStateOf<SpeechRep?>(null) }
    val speech = rememberSpeechCapture()
    val mic = rememberMicGate()
    val recorder = remember { DrillRecorder(ctx) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var flash by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var cards by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    DisposableEffect(Unit) { onDispose { player?.release(); if (recorder.recording) recorder.stop() } }
    LaunchedEffect(Unit) {
        val offices = g.db.offices().all().filter { it.powers.isNotBlank() }.map { "What does the ${it.body} ${it.district} control?".trim() to it.powers }
        val holders = g.db.offices().all().filter { it.incumbent.isNotBlank() }.map { "Who holds ${it.body} ${it.district}?".trim() to it.incumbent }
        val positions = g.db.positions().all().filter { it.stance.isNotBlank() }.map { "Your stance on ${it.issue}?" to it.stance }
        val rules = g.db.rules().all().map { "${it.jurisdiction}: rule?" to "${it.summary} (${it.status})" }
        cards = (offices + holders + positions + rules).shuffled()
    }
    val c = LocalBallotColors.current
    Screen("Gym", kicker = "Skills", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "One five-minute speaking rep a day. Pick a drill, tap Speak, talk, tap Stop.",
        "The score is plain code: clarity (filler words), pace (words per minute) and length against the target.",
        "Mock rooms: the Chief plays a hostile voter, a reporter, a moderator or a donor.",
        "Flashcards are built from your own district's facts.")) {
        item { ChipRow(drills, drill, { it.title }) { drill = it } }
        item {
            Panel {
                Kicker("${drill.title} · target ${drill.targetSec}s")
                Body(if (drill.id == "STORY" && profile?.story60?.isNotBlank() == true) "Your story: ${profile?.story60}" else drill.prompt)
                if (speech.listening) Text(speech.partial.ifBlank { "Listening…" }, style = MaterialTheme.typography.bodyMedium, color = c.signal)
                if (speech.error.isNotBlank()) Small(speech.error, color = c.signal)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterButton(if (speech.listening) "Stop" else "Speak", onClick = {
                        if (speech.listening) speech.stop()
                        else mic {
                            speech.start { text, secs ->
                                val m = SpeechScorer.score(text, secs, drill.targetSec)
                                val rep = SpeechRep(kind = drill.id, prompt = drill.prompt, transcript = text, durationSec = secs, wordsPerMinute = m.wpm,
                                    fillerCount = m.fillers, clarity = m.clarity, pace = m.pace, lengthScore = m.lengthScore, total = m.total,
                                    feedback = SpeechScorer.plainFeedback(m, drill.targetSec).joinToString(" "))
                                last = rep
                                scope.launch {
                                    g.db.speech().upsert(rep)
                                    if (drill.id == "STORY" && m.total >= 70) g.case.updateProfile { it.copy(storyRatedClear = true) }
                                    g.case.advanceIfReady()
                                }
                            }
                        }
                    })
                    PosterButton(if (recorder.recording) "Stop recording" else "Record to listen", primary = false, onClick = {
                        if (recorder.recording) recorder.stop() else mic { recorder.start() }
                    })
                }
                recorder.file?.takeIf { !recorder.recording && it.exists() }?.let { f ->
                    LinkButton("Play last recording", onClick = {
                        player?.release()
                        player = MediaPlayer().apply { setDataSource(f.absolutePath); prepare(); start() }
                    })
                }
            }
        }
        last?.let { r ->
            item {
                Panel(accent = r.total >= 70) {
                    val ink = if (r.total >= 70) com.emotionalleaves.ballot.ui.theme.Ink else c.fg
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BigNumber("${r.total}", "Score", color = ink)
                        BigNumber("${r.wordsPerMinute}", "Words/min", color = ink, size = 34)
                        BigNumber("${r.fillerCount}", "Fillers", color = ink, size = 34)
                        BigNumber("${r.durationSec}s", "Length", color = ink, size = 34)
                    }
                    Text(r.feedback, style = MaterialTheme.typography.bodyMedium, color = ink)
                }
            }
        }
        item {
            val trend = reps.take(10).reversed().map { it.total }
            Small(if (trend.isEmpty()) "No reps yet." else "Last ${trend.size} reps: ${trend.joinToString(" → ")}")
        }
        item { Title("Mock rooms") }
        items(MockRooms.rooms.size) { i ->
            val room = MockRooms.rooms[i]
            Panel(onClick = { nav.navigate(Routes.room(room.id)) }) {
                Text(room.title.uppercase(), style = MaterialTheme.typography.titleMedium, color = c.fg)
                Small(room.setup)
            }
        }
        item { Title("Flashcards") }
        item {
            if (cards.isEmpty()) EmptyNote("Flashcards appear once your offices, positions and rules are in the case file.")
            else {
                val card = cards[flash % cards.size]
                Panel(onClick = { flipped = !flipped }, accent = flipped) {
                    Kicker("Card ${flash % cards.size + 1} of ${cards.size} · tap to flip", color = if (flipped) com.emotionalleaves.ballot.ui.theme.Ink else c.muted)
                    Text(if (flipped) card.second else card.first, style = MaterialTheme.typography.titleLarge,
                        color = if (flipped) com.emotionalleaves.ballot.ui.theme.Ink else c.fg)
                }
                LinkButton("Next card", onClick = { flipped = false; flash++ })
            }
        }
        item { Title("Skill ladder") }
        items(ladder.size) { i ->
            val reached = reps.any { it.kind == ladder[i] && it.total >= 70 }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (reached) "■" else "□", style = MaterialTheme.typography.titleLarge, color = if (reached) c.signal else c.muted)
                Body(ladderNames[i], color = if (reached) c.fg else c.muted)
            }
        }
    }
}

@Composable
fun MockRoomScreen(nav: NavHostController, roomId: String) {
    val g = LocalContext.current.graph
    val room = MockRooms.byId(roomId) ?: MockRooms.rooms.first()
    val thread = "room:${room.id}"
    val messages by g.db.chat().observe(thread).collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val speech = rememberSpeechCapture()
    val mic = rememberMicGate()
    val list = rememberLazyListState()
    val c = LocalBallotColors.current
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.size - 1) }

    val persona = room.persona + "\nThe person you are talking to is a first-time local candidate. " +
        "Their issues: ${profile?.issues ?: "unknown"}. Never break character unless they type 'score me'."

    fun turn(userText: String?) {
        busy = true; error = ""
        scope.launch {
            if (userText != null) g.db.chat().insert(ChatMessage(thread = thread, role = "user", text = userText))
            val history = g.db.chat().recent(thread, 20).map { LlmMessage(it.role, listOf(Block.Text(it.text))) }
            val msgs = mutableListOf(LlmMessage("user", listOf(Block.Text("(The scene starts. Speak first.)"))))
            for (h in history) {
                val lastMsg = msgs.last()
                if (lastMsg.role == h.role) msgs[msgs.lastIndex] = LlmMessage(h.role, lastMsg.content + h.content) else msgs += h
            }
            val reply = runCatching { g.chief.simple(persona, msgs, 400) }.getOrElse { error = it.message ?: "No reply."; busy = false; return@launch }
            g.db.chat().insert(ChatMessage(thread = thread, role = "assistant", text = reply, mode = room.title))
            busy = false
        }
    }

    fun scoreMe() {
        busy = true
        scope.launch {
            val transcript = g.db.chat().recent(thread, 40).joinToString("\n") { (if (it.role == "user") "CANDIDATE: " else "OTHER SIDE: ") + it.text }
            val system = "You are the Chief, a calm senior campaign coach. Score the candidate's performance in this mock ${room.title.lowercase()} plainly. " +
                "Give a score from 0 to 100, then three short lines: what worked, what to fix, one line to practise. Be honest; do not flatter. First line must be 'SCORE: <n>'."
            val out = runCatching { g.chief.simple(system, listOf(LlmMessage("user", listOf(Block.Text(transcript)))), 500) }.getOrElse { error = it.message ?: ""; busy = false; return@launch }
            val n = Regex("SCORE:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            g.db.speech().upsert(SpeechRep(kind = "MOCK_${room.id}", prompt = room.title, transcript = transcript.take(8000), total = n.coerceIn(0, 100), feedback = out))
            g.db.chat().insert(ChatMessage(thread = thread, role = "assistant", text = "COACH'S SCORE\n$out", mode = "Coach"))
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Kicker("Mock room", color = c.signal)
                Text(room.title.uppercase(), style = MaterialTheme.typography.headlineMedium, color = c.fg)
            }
            LinkButton("Done", onClick = { nav.popBackStack() })
        }
        ThickRule(Modifier.padding(horizontal = Gutter))
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Body(room.setup)
                    PosterButton(if (busy) "Starting…" else "Start the scene", enabled = !busy, onClick = { turn(null) })
                }
            }
            items(messages, key = { it.id }) { m ->
                Panel(accent = m.role == "user", padding = 12.dp) {
                    Kicker(if (m.role == "user") "You" else m.mode.ifBlank { room.title }, color = if (m.role == "user") com.emotionalleaves.ballot.ui.theme.Ink else c.muted)
                    Text(m.text, style = MaterialTheme.typography.bodyLarge, color = if (m.role == "user") com.emotionalleaves.ballot.ui.theme.Ink else c.fg)
                }
            }
            if (error.isNotBlank()) item { Small(error, color = c.signal) }
        }
        if (speech.listening) Small(speech.partial.ifBlank { "Listening…" }, Modifier.padding(horizontal = Gutter), color = c.signal)
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(input, { input = it }, "Your answer")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosterButton("Send", enabled = input.isNotBlank() && !busy, onClick = { val t = input; input = ""; turn(t) })
                PosterButton(if (speech.listening) "Stop" else "Speak", primary = false, onClick = {
                    if (speech.listening) speech.stop() else mic { speech.start { text, _ -> if (text.isNotBlank()) turn(text) } }
                })
                PosterButton("Score me", primary = false, enabled = messages.size >= 4 && !busy, onClick = { scoreMe() })
            }
            if (messages.isNotEmpty()) LinkButton("Reset room", onClick = { scope.launch { g.db.chat().clear(thread) } }, color = c.muted)
        }
    }
}
