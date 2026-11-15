package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.emotionalleaves.ballot.agent.BallotJson
import com.emotionalleaves.ballot.agent.Block
import com.emotionalleaves.ballot.agent.LlmMessage
import com.emotionalleaves.ballot.core.CaseStats
import com.emotionalleaves.ballot.core.Scheduling
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.StageRules
import com.emotionalleaves.ballot.core.WeekCounts
import com.emotionalleaves.ballot.core.WeeklyScore
import com.emotionalleaves.ballot.data.Decision
import com.emotionalleaves.ballot.data.Lesson
import com.emotionalleaves.ballot.data.Task
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
import com.emotionalleaves.ballot.ui.components.Progress
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PathScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val decisions by g.db.decisions().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val lessons by g.db.lessons().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var stats by remember { mutableStateOf(CaseStats()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile) { stats = g.case.stats() }
    val p = profile ?: return
    val current = Stage.of(p.stage)
    val c = LocalBallotColors.current
    Screen("The path", kicker = "Eight stages · each unlocks when its exit test is met", walkthrough = listOf(
        "This is the whole road from zero to office. You are on the highlighted stage.",
        "A stage unlocks only when the one before it passes its exit test. Stages 3 to 5 cannot be skipped: they are how people come to know and trust you.",
        "Timings are defaults. The Chief adjusts them to your life.",
        "Fast lanes: an open seat can bring the race decision forward. An appointed board builds a record without an election.",
    )) {
        items(Stage.entries.size) { i ->
            val s = Stage.entries[i]
            val ex = StageRules.exit(s, stats)
            val isCurrent = s == current
            val done = s.number < current.number
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(30.dp).background(if (isCurrent) c.signal else if (done) c.fg else c.bg).border(3.dp, if (isCurrent) c.signal else c.fg),
                        contentAlignment = Alignment.Center) {
                        Text("${s.number}", style = MaterialTheme.typography.titleMedium, color = if (isCurrent || done) c.bg else c.fg)
                    }
                    if (i < Stage.entries.size - 1) Box(Modifier.width(3.dp).height(if (isCurrent) 150.dp else 54.dp).background(c.fg))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = if (s.number > current.number) c.muted else c.fg)
                    Small(s.window)
                    if (isCurrent) {
                        Body("Exit: ${s.exitTest}")
                        Progress(ex.fraction)
                        Small(ex.label, color = c.fg)
                        Small("Usual stall: ${s.stall}. Counter: ${s.counter}")
                        when (s) {
                            Stage.GROUND -> PosterButton("Take the quiz", onClick = { nav.navigate(Routes.QUIZ) })
                            Stage.CHOOSE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PosterButton("Rank races", onClick = { nav.navigate(Routes.GROUND) })
                                PosterButton("Go or no-go", primary = false, onClick = { nav.navigate(Routes.GONOGO) })
                            }
                            Stage.REASON -> PosterButton("Open story bank", onClick = { nav.navigate(Routes.COMPASS) })
                            Stage.SERVE -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PosterButton("Board finder", onClick = { nav.navigate(Routes.FIELD) })
                                PosterButton(if (p.serveDone) "Role logged" else "I have a role", primary = false, onClick = {
                                    scope.launch { g.case.updateProfile { it.copy(serveDone = !it.serveDone) } }
                                })
                            }
                            Stage.RUN -> PosterButton("Campaign HQ", onClick = { nav.navigate(Routes.HQ) })
                            Stage.AFTER -> PosterButton("Write the debrief", onClick = {
                                askChief(g, "Walk me through the stage 8 debrief: what worked, what did not, who to thank, and the next target. Record it with record_decision kind DEBRIEF when we finish.", "debrief")
                                nav.navigate(Routes.CHIEF)
                            })
                            else -> {}
                        }
                    } else if (done) Small("Passed")
                }
            }
        }
        item {
            Panel {
                Kicker("Fast lanes")
                Small("Open seat: an uncontested or vacant local seat lets you make the race decision early. Ask the Chief to check.", color = c.fg)
                Small("Appointment: many boards are filled by appointment. That builds a record without an election.", color = c.fg)
                Small("Apprentice: working on a winning local campaign compresses learning, showing up and base-building into one season.", color = c.fg)
                Small("Party: local party committees often have open volunteer and precinct roles. The Chief explains how each works; the choice is yours.", color = c.fg)
                LinkButton("Check for open seats", onClick = {
                    askChief(g, "Check official sources for any open, vacant or historically uncontested local seat I am eligible for, and any appointed boards with openings. Cite sources.", "checking seats")
                    nav.navigate(Routes.CHIEF)
                })
            }
        }
        item { Title("Decisions") }
        if (decisions.isEmpty()) item { EmptyNote("Big decisions and their reasoning will be kept here.") }
        items(decisions.size) { i ->
            val d = decisions[i]
            Panel {
                Kicker("${d.kind.replace('_', ' ')} · ${shortDate(d.at)}")
                Text(d.question, style = MaterialTheme.typography.titleMedium, color = c.fg)
                if (d.choice.isNotBlank()) Body("Choice: ${d.choice}")
                if (d.reasoning.isNotBlank()) Small(d.reasoning)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = d.agreed, onCheckedChange = { v -> scope.launch { g.db.decisions().upsert(d.copy(agreed = v, updatedAt = now())); g.case.advanceIfReady() } })
                    Small("I agree with this", color = c.fg)
                }
            }
        }
        item { Title("Lessons") }
        if (lessons.isEmpty()) item { EmptyNote("After every event and every week, one lesson goes here.") }
        items(lessons.take(20).size) { i ->
            val l = lessons[i]
            Panel { Kicker("${l.kind} · ${shortDate(l.at)}"); Body(l.what); if (l.change.isNotBlank()) Small("Change: ${l.change}") }
        }
    }
}

@Serializable
data class QuizQ(val q: String, val options: List<String>, val answer: Int, val source: String = "", val explain: String = "")

@Composable
fun QuizScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var questions by remember { mutableStateOf<List<QuizQ>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val answers = remember { mutableStateListOf<Int>() }
    var submitted by remember { mutableStateOf(false) }
    val c = LocalBallotColors.current

    fun generate() {
        loading = true; error = ""; submitted = false; answers.clear()
        scope.launch {
            val offices = g.db.offices().all().joinToString("\n") { "O${it.id}: ${it.body} ${it.district}, held by ${it.incumbent}, powers: ${it.powers}, next election ${it.nextElection}" }
            val sources = g.db.sources().all().filter { it.official }.take(8).joinToString("\n\n") { "S${it.id} ${it.title}: ${it.excerpt.take(1200)}" }
            val explainers = g.db.chat().all().filter { it.role == "assistant" }.takeLast(10).joinToString("\n") { it.text.take(400) }
            if (offices.isBlank() && sources.isBlank()) {
                error = "The quiz is built only from what is in your case file, and it is empty. List your offices in Ground and read a few explainers first."
                loading = false; return@launch
            }
            val system = "You write a 10-question multiple-choice quiz about the user's own town, using ONLY the facts below. Never invent facts. " +
                "Each question cites the record it came from (O#, S#). Reply with JSON only: an array of {\"q\":..., \"options\":[4 strings], \"answer\":index 0-3, \"source\":\"S#\", \"explain\": one sentence}."
            val text = runCatching {
                g.chief.simple(system, listOf(LlmMessage("user", listOf(Block.Text("Offices:\n$offices\n\nOfficial sources:\n$sources\n\nRecent explainers:\n$explainers"))), 3000)
            }.getOrElse { error = it.message ?: "Could not build the quiz."; loading = false; return@launch }
            // Structured output is validated; invalid output is reported, never shown raw.
            val parsed = runCatching {
                val json = text.substring(text.indexOf('['), text.lastIndexOf(']') + 1)
                BallotJson.decodeFromString(ListSerializer(QuizQ.serializer()), json).filter { it.options.size in 2..6 && it.answer in it.options.indices }.take(10)
            }.getOrDefault(emptyList())
            if (parsed.size < 5) error = "The quiz came back malformed. Try again." else { questions = parsed; repeat(parsed.size) { answers += -1 } }
            loading = false
        }
    }

    Screen("Quiz: your town", kicker = "Stage 2 exit test · pass is ${StageRules.QUIZ_PASS} of 10", onBack = { nav.popBackStack() },
        walkthrough = listOf("Ten questions built only from your own case file.", "Pick an answer for each, then check.", "Retake as often as you like. Your best score counts.")) {
        if (questions.isEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Body("The quiz turns reading into knowing.")
                if (error.isNotBlank()) Body(error, color = c.signal)
                PosterButton(if (loading) "Building…" else "Build my quiz", enabled = !loading, onClick = { generate() })
            }
        }
        items(questions.size) { i ->
            val q = questions[i]
            Panel {
                Kicker("Question ${i + 1} · ${q.source}")
                Text(q.q, style = MaterialTheme.typography.titleMedium, color = c.fg)
                q.options.forEachIndexed { oi, o ->
                    val chosen = answers.getOrNull(i) == oi
                    val right = submitted && oi == q.answer
                    val wrong = submitted && chosen && oi != q.answer
                    Panel(accent = chosen && !submitted, onClick = { if (!submitted) answers[i] = oi }, padding = 10.dp) {
                        Text((if (right) "✓ " else if (wrong) "✗ " else "") + o, style = MaterialTheme.typography.bodyLarge,
                            color = if (chosen && !submitted) com.emotionalleaves.ballot.ui.theme.Ink else if (right) com.emotionalleaves.ballot.ui.theme.Go else if (wrong) c.signal else c.fg)
                    }
                }
                if (submitted && q.explain.isNotBlank()) Small(q.explain)
            }
        }
        if (questions.isNotEmpty()) item {
            val score = questions.indices.count { answers.getOrNull(it) == questions[it].answer }
            if (!submitted) PosterButton("Check answers", enabled = answers.none { it < 0 }, onClick = {
                submitted = true
                scope.launch {
                    g.case.updateProfile { it.copy(quizBest = maxOf(it.quizBest, score)) }
                    g.case.audit("you", "Took the town quiz", outcome = "$score/${questions.size}")
                    g.case.advanceIfReady()
                }
            }) else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigNumber("$score/${questions.size}", if (score >= StageRules.QUIZ_PASS) "Passed" else "Not yet", color = c.signal)
                PosterButton("New quiz", onClick = { generate() })
            }
        }
    }
}

private val goNoGoPoints = listOf(
    "Eligibility confirmed with the election office",
    "A win number you can reach",
    "A first-money list that covers the early budget",
    "A treasurer",
    "Weekly hours you can truly give",
    "Employer rules cleared",
    "The people closest to you on board",
)

@Composable
fun GoNoGoScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val answers = remember { mutableStateListOf(*Array(goNoGoPoints.size) { "" }) }
    val notes = remember { mutableStateListOf(*Array(goNoGoPoints.size) { "" }) }
    var target by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val c = LocalBallotColors.current
    Screen("Go or no-go", kicker = "Before stage 7 unlocks", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Seven honest answers. 'Not yet' is fine.", "Any 'no' becomes the next task, not a reason to quit.",
        "When all seven are yes and you agree with the memo, the Run stage unlocks.")) {
        item { Field(target, { target = it }, "The office you are deciding on") }
        items(goNoGoPoints.size) { i ->
            Panel {
                Text(goNoGoPoints[i].uppercase(), style = MaterialTheme.typography.titleMedium, color = c.fg)
                ChipRow(listOf("YES", "NO", "UNSURE"), answers[i].ifBlank { null }, { it.lowercase() }) { answers[i] = it }
                Field(notes[i], { notes[i] = it }, "Evidence or note")
            }
        }
        item {
            val allYes = answers.all { it == "YES" }
            PosterButton(if (saved) "Saved" else "Write the memo", enabled = answers.none { it.isBlank() } && target.isNotBlank(), onClick = {
                scope.launch {
                    val memo = goNoGoPoints.indices.joinToString("\n") { "${goNoGoPoints[it]}: ${answers[it]}${if (notes[it].isNotBlank()) " (${notes[it]})" else ""}" }
                    g.db.decisions().upsert(Decision(kind = "GO_NO_GO", question = "Run for $target?", options = "GO\nNOT YET\nNO",
                        choice = if (allYes) "GO" else "NOT YET", reasoning = memo, agreed = false))
                    goNoGoPoints.indices.filter { answers[it] != "YES" }.forEach { i ->
                        g.db.tasks().upsert(Task(stage = g.case.profile().stage, title = "Get to yes: ${goNoGoPoints[i]}", why = "A go or no-go point is not met yet.",
                            doneLooksLike = "This point answered yes with evidence.", goodEnough = "Evidence, not certainty.", movesExit = true, module = "path",
                            templateKey = "gonogo.$i.${LocalDate.now()}", sizeMinutes = 15))
                    }
                    g.case.audit("you", "Wrote go or no-go memo", input = target, outcome = if (allYes) "GO" else "NOT YET")
                    saved = true
                }
            })
            Small("Then mark the memo 'I agree' on the Path screen.")
        }
    }
}

@Composable
fun WeeklyReviewScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val open by g.db.tasks().observeOpen().collectAsStateWithLifecycle(initialValue = emptyList())
    var counts by remember { mutableStateOf(WeekCounts()) }
    var history by remember { mutableStateOf<List<Int>>(emptyList()) }
    var stop by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("") }
    var keep by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<Long>() }
    var saved by remember { mutableIntStateOf(0) }
    val c = LocalBallotColors.current
    LaunchedEffect(Unit) {
        val ws = Scheduling.weekStart(LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        counts = g.case.weekCounts(ws)
        history = (3 downTo 0).map { w ->
            val from = ws - w * 7 * 86_400_000L
            val cnt = g.case.weekCounts(from)
            WeeklyScore.score(cnt)
        }
    }
    val score = WeeklyScore.score(counts)
    Screen("Weekly review", kicker = "Score · stop · start · keep · three priorities", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "The score counts only real things: conversations, meetings, comments, contacts, follow-ups, reps.", "Time in the app never counts.",
        "Pick one thing to stop, one to start, one to keep.", "Pick next week's three priorities. The plan changes accordingly.")) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BigNumber(score.toString(), "This week", color = c.signal)
                BigNumber(counts.conversations.toString(), "Conversations")
                BigNumber(counts.meetings.toString(), "Meetings")
            }
        }
        item {
            Panel {
                Small("Comments ${counts.comments} · new contacts ${counts.newContacts} · follow-ups on time ${counts.followUpsOnTime} · speaking reps ${counts.speakingReps}", color = c.fg)
                if (counts.doors + counts.dollarsRaised > 0) Small("Doors ${counts.doors} · supporters ${counts.supportersIdentified} · raised ${dollars(counts.dollarsRaised)}", color = c.fg)
                if (history.size >= 2) Small("Last weeks: ${history.joinToString(" → ")}")
            }
        }
        if (WeeklyScore.flat(history)) item {
            Panel(accent = true) {
                Text("FLAT WEEK. THAT'S INFORMATION, NOT FAILURE.", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                PosterButton("Name the blocker", primary = false, onClick = {
                    askChief(g, "My weekly score was flat (${history.joinToString()}). Name the most likely blocker from my case file and offer a smaller plan for next week.", "naming the blocker")
                    nav.navigate(Routes.CHIEF)
                })
            }
        }
        item { Field(stop, { stop = it }, "One thing to stop") }
        item { Field(start, { start = it }, "One thing to start") }
        item { Field(keep, { keep = it }, "One thing to keep") }
        item { Kicker("Next week's three priorities (${picked.size}/3)") }
        items(open.take(15).size) { i ->
            val t = open[i]
            Panel(accent = t.id in picked, onClick = { if (t.id in picked) picked.remove(t.id) else if (picked.size < 3) picked.add(t.id) }, padding = 12.dp) {
                Text(t.title, style = MaterialTheme.typography.bodyLarge, color = if (t.id in picked) com.emotionalleaves.ballot.ui.theme.Ink else c.fg)
            }
        }
        item {
            PosterButton(if (saved > 0) "Saved" else "Save review", onClick = {
                scope.launch {
                    g.db.lessons().upsert(Lesson(kind = "WEEK", what = "Week score $score. Stop: $stop. Keep: $keep.", change = "Start: $start"))
                    val today = LocalDate.now().toEpochDay()
                    picked.forEachIndexed { idx, id -> g.db.tasks().get(id)?.let { g.db.tasks().upsert(it.copy(dueEpochDay = today + 2 + idx * 2, updatedAt = now())) } }
                    g.case.rescore()
                    saved++
                }
            })
            LinkButton("Monthly check: is the target still right?", onClick = {
                askChief(g, "Monthly check: re-test your own assumptions about my target office and timeline against my case file. Is the timeline honest? Be direct.", "monthly check")
                nav.navigate(Routes.CHIEF)
            })
            LinkButton("Quarterly state-of-the-effort memo", onClick = {
                askChief(g, "Write a quarterly state-of-the-effort memo from my case file: trends, stage progress, relationships, what to change. Save it with draft_document type OTHER.", "quarterly memo")
                nav.navigate(Routes.CHIEF)
            })
        }
    }
}
