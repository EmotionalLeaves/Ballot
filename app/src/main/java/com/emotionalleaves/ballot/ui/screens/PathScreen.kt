package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
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
import com.emotionalleaves.ballot.ui.switchTab
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun PathScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val offices by g.db.offices().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val raceCards by g.db.raceCards().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val decisions by g.db.decisions().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val lessons by g.db.lessons().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var stats by remember { mutableStateOf(CaseStats()) }
    var showRecord by remember { mutableStateOf(false) }
    var debriefOpen by remember { mutableStateOf(false) }
    var showLanes by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(profile) { stats = g.case.stats() }
    val p = profile ?: return
    val current = Stage.of(p.stage)
    val c = LocalBallotColors.current
    Screen("Your plan", kicker = "Eight stages. Each opens when the one before is done.", tabs = planTabs(nav, profile?.stage ?: 1), selectedTab = 0, walkthrough = listOf(
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
                    val shape = androidx.compose.foundation.shape.CircleShape
                    Box(Modifier.size(28.dp).clip(shape).background(if (isCurrent || done) c.signal else c.raised).border(1.dp, if (isCurrent || done) c.signal else c.rule, shape),
                        contentAlignment = Alignment.Center) {
                        Text(if (done) "✓" else "${s.number}", style = MaterialTheme.typography.labelLarge, color = if (isCurrent || done) c.onSignal else c.muted)
                    }
                    if (i < Stage.entries.size - 1) Box(Modifier.width(2.dp).height(if (isCurrent) 150.dp else 36.dp).background(if (done) c.signal else c.rule))
                }
                Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.title, style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge, color = if (s.number > current.number) c.muted else c.fg)
                    if (isCurrent) Small(s.window)
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
                                    scope.launch { g.case.updateProfile { it.copy(serveDone = !it.serveDone) }; g.case.advanceIfReady() }
                                })
                            }
                            Stage.RUN -> PosterButton("Campaign HQ", onClick = { nav.navigate(Routes.HQ) })
                            Stage.AFTER -> PosterButton("Write the debrief", onClick = { debriefOpen = true })
                            else -> {}
                        }
                    }
                }
            }
        }
        item { LinkButton(if (showLanes) "Hide shortcuts" else "Shortcuts to the race decision", onClick = { showLanes = !showLanes }, color = c.muted) }
        if (showLanes) item {
            Panel {
                Kicker("Fast lanes")
                Small("Open seat: an uncontested or vacant local seat lets you make the race decision early.", color = c.fg)
                // Read from your own race cards in plain code.
                val openSeats = offices.filter { o -> raceCards.any { it.officeId == o.id && (it.openSeat || it.incumbentRetiring || it.uncontestedHistory) } }
                if (openSeats.isNotEmpty()) Small("From your race cards: " + openSeats.joinToString { "${it.body} ${it.district}".trim() }, color = c.signal)
                else Small("None of your race cards shows an open seat yet. Mark one in Offices and races when you learn of it.")
                Small("Appointment: many boards are filled by appointment. That builds a record without an election.", color = c.fg)
                Small("Apprentice: working on a winning local campaign compresses learning, showing up and base-building into one season.", color = c.fg)
                Small("Party: local party committees often have open volunteer and precinct roles. The Chief explains how each works; the choice is yours.", color = c.fg)
                LinkButton("Have the Chief check official sources", color = c.muted, onClick = {
                    askChief(g, "Check official sources for any open, vacant or historically uncontested local seat I am eligible for, and any appointed boards with openings. Cite sources.", "checking seats")
                    nav.navigate(Routes.CHIEF)
                })
            }
        }
        // A go memo waiting for your agreement is the one thing between you and the Run stage: it never hides.
        decisions.firstOrNull { it.kind == "GO_NO_GO" && it.choice.equals("GO", true) && !it.agreed }?.let { d ->
            item {
                Panel(accent = true) {
                    Kicker("Waiting for you", color = com.emotionalleaves.ballot.ui.theme.Ink)
                    Text(d.question, style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                    Text("All seven points are yes. Read the memo under Decisions, then agree if you're sure.", style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                    LinkButton("I agree: go", color = com.emotionalleaves.ballot.ui.theme.Ink, onClick = {
                        scope.launch { g.db.decisions().upsert(d.copy(agreed = true, updatedAt = now())); g.case.advanceIfReady() }
                    })
                }
            }
        }
        item { LinkButton(if (showRecord) "Hide decisions and lessons" else "Decisions (${decisions.size}) and lessons (${lessons.size})", onClick = { showRecord = !showRecord }, color = c.muted) }
        if (showRecord && decisions.isEmpty()) item { EmptyNote("Big decisions and their reasoning will be kept here.") }
        if (showRecord) items(decisions.size) { i ->
            val d = decisions[i]
            Panel {
                Kicker("${pretty(d.kind)} · ${shortDate(d.at)}")
                Text(d.question, style = MaterialTheme.typography.titleMedium, color = c.fg)
                if (d.choice.isNotBlank()) Body("Choice: ${d.choice}")
                if (d.reasoning.isNotBlank()) Small(d.reasoning)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = d.agreed, onCheckedChange = { v -> scope.launch { g.db.decisions().upsert(d.copy(agreed = v, updatedAt = now())); g.case.advanceIfReady() } })
                    Small("I agree with this", color = c.fg)
                }
            }
        }
        if (showRecord && lessons.isEmpty()) item { EmptyNote("After every event and every week, one lesson goes here.") }
        if (showRecord) items(lessons.take(20).size) { i ->
            val l = lessons[i]
            Panel { Kicker("${pretty(l.kind)} · ${shortDate(l.at)}"); Body(l.what); if (l.change.isNotBlank()) Small("Change: ${l.change}") }
        }
    }
    // The stage 8 debrief is four plain questions; talking it through with the Chief is optional.
    if (debriefOpen) {
        val answers = remember { androidx.compose.runtime.mutableStateListOf("", "", "", "") }
        val qs = listOf("What worked?", "What didn't?", "Who do you need to thank?", "What's your next target?")
        androidx.compose.material3.AlertDialog(onDismissRequest = { debriefOpen = false },
            title = { Text("Debrief", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    qs.forEachIndexed { i, q -> com.emotionalleaves.ballot.ui.components.Field(answers[i], { answers[i] = it }, q, minLines = 2) }
                    LinkButton("Talk it through with the Chief instead", color = c.muted, onClick = {
                        debriefOpen = false
                        askChief(g, "Walk me through the stage 8 debrief: what worked, what did not, who to thank, and the next target. Record it with record_decision kind DEBRIEF when we finish.", "debrief")
                        nav.navigate(Routes.CHIEF)
                    })
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = answers.any { it.isNotBlank() }, onClick = {
                    val memo = qs.indices.filter { answers[it].isNotBlank() }.joinToString("\n") { "${qs[it]} ${answers[it].trim()}" }
                    scope.launch {
                        g.db.decisions().upsert(Decision(kind = "DEBRIEF", question = "Election debrief", choice = answers[3].trim(), reasoning = memo, agreed = true))
                        g.db.lessons().upsert(Lesson(kind = "QUARTER", what = answers[0].trim().ifBlank { "Debrief" }, change = answers[1].trim()))
                        g.case.audit("you", "Wrote the debrief")
                        g.case.advanceIfReady()
                    }
                    debriefOpen = false
                }) { Text("Save") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { debriefOpen = false }) { Text("Cancel") } })
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

    // Built in plain code from your own offices and rules: no AI, nothing invented, works offline.
    fun generate() {
        loading = true; error = ""; submitted = false; answers.clear()
        scope.launch {
            val offices = g.db.offices().all().map { o ->
                com.emotionalleaves.ballot.core.QuizOffice(o.id, o.body, o.title, o.district, o.level, o.powers, o.term, o.nextElection, o.incumbent, o.partTime, o.appointed)
            }
            val rules = g.db.rules().all().map { com.emotionalleaves.ballot.core.QuizRule(it.id, it.jurisdiction, it.summary) }
            val built = com.emotionalleaves.ballot.core.QuizBuilder.build(offices, rules, seed = System.currentTimeMillis())
            if (built.size < 8) {
                error = "Your case file has enough for ${built.size} questions; the quiz needs 8. Add the offices that represent you (with who holds them, " +
                    "term length and what they control) in Offices and races, or load your election rules in the Vault."
            } else {
                questions = built.map { QuizQ(it.q, it.options, it.answer, it.source, it.explain) }
                repeat(questions.size) { answers += -1 }
            }
            loading = false
        }
    }

    Screen("Your town", kicker = "Town quiz · stage 2 exit test · pass is ${StageRules.QUIZ_PASS} of 10", onBack = { nav.popBackStack() }, tabs = townTabs(nav), selectedTab = 1,
        walkthrough = listOf("Ten questions built in the app from your own offices and rules. No AI involved.", "Pick an answer for each, then check.", "Retake as often as you like. Your best score counts.")) {
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
            // The exit test is out of 10. A short quiz (the AI sometimes writes fewer) can't count, or it could never be passed.
            val counts = questions.size >= 8
            val outOf10 = if (counts) Math.round(score * 10f / questions.size) else 0
            if (!submitted) PosterButton("Check answers", enabled = answers.none { it < 0 }, onClick = {
                submitted = true
                scope.launch {
                    if (counts) g.case.updateProfile { it.copy(quizBest = maxOf(it.quizBest, outOf10)) }
                    g.case.audit("you", "Took the town quiz", outcome = "$score/${questions.size}" + if (counts) " ($outOf10 of 10)" else " (too short to count)")
                    g.case.advanceIfReady()
                }
            }) else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                BigNumber("$score/${questions.size}", when {
                    !counts -> "Too few questions to count. Tap New quiz."
                    outOf10 >= StageRules.QUIZ_PASS -> "Passed"
                    else -> "Not yet. ${StageRules.QUIZ_PASS} of 10 passes."
                }, color = c.signal)
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
    val gp by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    Screen("Your plan", kicker = "Run or wait · seven points before stage 7 unlocks", onBack = { nav.popBackStack() }, tabs = planTabs(nav, maxOf(gp?.stage ?: 5, 5)), selectedTab = 2, walkthrough = listOf(
        "Seven honest answers. 'Not yet' is fine.", "Any 'no' becomes the next task, not a reason to quit.",
        "When all seven are yes and you agree with the memo, the Run stage unlocks.")) {
        item { Field(target, { target = it }, "The office you are deciding on") }
        items(goNoGoPoints.size) { i ->
            Panel {
                Text(goNoGoPoints[i], style = MaterialTheme.typography.titleMedium, color = c.fg)
                ChipRow(listOf("YES", "NO", "UNSURE"), answers[i].ifBlank { null }, { when (it) { "YES" -> "Yes"; "NO" -> "No"; else -> "Not sure" } }) { answers[i] = it }
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
                        val key = "gonogo.$i"
                        // One open "get to yes" task per point, however many times the memo is rewritten.
                        if (g.db.tasks().open().none { it.templateKey == key }) g.db.tasks().upsert(Task(stage = g.case.profile().stage, title = "Get to yes: ${goNoGoPoints[i]}", why = "A go or no-go point is not met yet.",
                            doneLooksLike = "This point answered yes with evidence.", goodEnough = "Evidence, not certainty.", movesExit = true, module = "path",
                            templateKey = key, sizeMinutes = 15))
                    }
                    g.case.audit("you", "Wrote go or no-go memo", input = target, outcome = if (allYes) "GO" else "NOT YET")
                    saved = true
                }
            })
            if (saved) {
                Small(if (answers.all { it == "YES" }) "Read the memo on your plan and tap 'I agree' when you're sure." else "Each point that isn't a yes is now a task on your plan.")
                com.emotionalleaves.ballot.ui.components.LinkButton("Open my plan", onClick = { nav.navigate(Routes.PATH) { launchSingleTop = true } })
            } else Small("Then mark the memo 'I agree' on the Plan tab.")
        }
    }
}

@Composable
fun WeeklyReviewScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val open by g.db.tasks().observeOpen().collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
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
    Screen("Your plan", kicker = "Weekly review · score, stop, start, keep, three priorities", onBack = { nav.popBackStack() }, tabs = planTabs(nav, profile?.stage ?: 1), selectedTab = 1, walkthrough = listOf(
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
                Text("A quiet week. That's information, not failure.", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                // Read from your own numbers in plain code; asking the Chief is optional.
                WeeklyScore.blockers(counts, profile?.stage ?: 1, open.size, open.count { it.skippedCount >= 2 }).forEach { b ->
                    Text("• $b", style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                }
                LinkButton("Talk it through with the Chief", color = com.emotionalleaves.ballot.ui.theme.Ink, onClick = {
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
            PosterButton(if (saved > 0) "Saved" else "Save review", enabled = saved == 0, onClick = {
                scope.launch {
                    val what = listOfNotNull("Week score $score.", stop.takeIf { it.isNotBlank() }?.let { "Stop: ${it.trim()}." }, keep.takeIf { it.isNotBlank() }?.let { "Keep: ${it.trim()}." }).joinToString(" ")
                    g.db.lessons().upsert(Lesson(kind = "WEEK", what = what, change = start.takeIf { it.isNotBlank() }?.let { "Start: ${it.trim()}" } ?: ""))
                    // Doing the review is the review task: take it off Today.
                    g.db.tasks().open().filter { it.templateKey.startsWith("review.") }.forEach { g.case.completeTask(it) }
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
