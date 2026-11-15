package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.emotionalleaves.ballot.agent.ToolCtx
import com.emotionalleaves.ballot.core.Scheduling
import com.emotionalleaves.ballot.core.Scored
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.StageRules
import com.emotionalleaves.ballot.core.WeeklyScore
import com.emotionalleaves.ballot.data.Task
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.BigNumber
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Progress
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.Stamp
import com.emotionalleaves.ballot.ui.components.WalkthroughDialog
import com.emotionalleaves.ballot.ui.openModule
import com.emotionalleaves.ballot.ui.theme.Ink
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun HomeScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val tasks by g.db.tasks().observeOpen().collectAsStateWithLifecycle(initialValue = emptyList())
    val deadlines by g.db.deadlines().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val pending by g.db.pending().observeWaiting().collectAsStateWithLifecycle(initialValue = emptyList())
    val brief by g.db.brief().observeDay(LocalDate.now().toEpochDay()).collectAsStateWithLifecycle(initialValue = emptyList())
    var ranked by remember { mutableStateOf<List<Scored>>(emptyList()) }
    var exitLabel by remember { mutableStateOf("") }
    var exitFraction by remember { mutableStateOf(0f) }
    var weekScore by remember { mutableIntStateOf(0) }
    var insideWork by remember { mutableStateOf(false) }
    var hasBrain by remember { mutableStateOf(true) }
    var stamp by remember { mutableStateOf(false) }
    var showWhy by remember { mutableStateOf<Scored?>(null) }
    var showSteps by remember { mutableStateOf<Task?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var advanced by remember { mutableStateOf<Stage?>(null) }

    LaunchedEffect(refresh, tasks.size) {
        advanced = g.case.advanceIfReady() ?: advanced
        ranked = g.case.rescore()
        val p = g.case.profile()
        val ex = StageRules.exit(Stage.of(p.stage), g.case.stats())
        exitLabel = ex.label; exitFraction = ex.fraction
        val ws = Scheduling.weekStart(LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        weekScore = WeeklyScore.score(g.case.weekCounts(ws))
        insideWork = Scheduling.insideWork(g.case.availability(), LocalDateTime.now())
        hasBrain = g.chief.hasKey()
    }

    val p = profile ?: return
    val stage = Stage.of(p.stage)
    val top = ranked.firstOrNull { it.blocked == null }?.let { s -> tasks.firstOrNull { it.id == s.id }?.let { it to s } }
    val nextDeadline = deadlines.filter { !it.done && it.dueEpochDay >= LocalDate.now().toEpochDay() }.minByOrNull { it.dueEpochDay }
    val c = LocalBallotColors.current

    Box(Modifier.fillMaxSize()) {
        Screen(title = "Today", kicker = "Ballot · ${p.name.ifBlank { "your campaign" }}",
            walkthrough = listOf(
                "The big card is the single most useful thing to do now. The Chief picked it from every open task.",
                "Tap DONE when it's finished. Tap SKIP if not today: skipping costs nothing.",
                "Tap WHY THIS to see exactly how it was ranked.",
                "TOO MUCH shrinks the week to its single most important item.",
                "Talk to the Chief any time from the CHIEF tab.",
            )) {
            // The three-line brief: where you stand, what changed, what is next.
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Kicker("Brief")
                    Body("Stand: stage ${stage.number}, ${stage.title.lowercase()}. $exitLabel.")
                    Body("Changed: " + listOfNotNull(
                        "${pending.size} waiting for your approval".takeIf { pending.isNotEmpty() },
                        "${brief.size} new in the Briefing".takeIf { brief.isNotEmpty() },
                        advanced?.let { "you reached stage ${it.number}" },
                    ).joinToString(", ").ifBlank { "nothing new since last time" } + ".")
                    Body("Next: " + (top?.first?.title ?: "talk to the Chief about your first step") + ".")
                }
            }
            if (!hasBrain) item {
                Panel(accent = true, onClick = { nav.navigate(Routes.SETTINGS) }) {
                    Text("CONNECT A FREE AI", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text("The Chief needs one to research and draft. Google Gemini is free, needs no card, and takes about two minutes. Tap here.",
                        style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
            if (insideWork) item { Small("You're at work now. The Chief keeps everything for after.") }
            if (pending.isNotEmpty()) item {
                Panel(accent = true, onClick = { nav.navigate(Routes.APPROVALS) }) {
                    Text("${pending.size} WAITING FOR YOUR TAP", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text("Anything sent, posted, filed, signed or spent waits for you.", style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
            }
            item {
                if (top == null) {
                    Panel {
                        Kicker("Today's action")
                        Text("NOTHING DUE. GOOD.", style = MaterialTheme.typography.headlineMedium, color = c.fg)
                        Small("Ask the Chief what would move your stage forward, or take the night off.")
                        PosterButton("Ask the Chief", onClick = { nav.navigate(Routes.CHIEF) })
                    }
                } else {
                    val (t, s) = top
                    Panel(padding = 20.dp) {
                        Kicker("Today's action · ${t.sizeMinutes} min${if (t.highEffort) " · high effort" else ""}${if (t.legal) " · legal" else ""}", color = c.signal)
                        Text(t.title.uppercase(), style = MaterialTheme.typography.displaySmall, color = c.fg)
                        if (t.why.isNotBlank()) Body(t.why)
                        if (t.doneLooksLike.isNotBlank()) Small("Done looks like: ${t.doneLooksLike}", color = c.fg)
                        if (t.goodEnough.isNotBlank()) Small("Good enough: ${t.goodEnough}")
                        if (t.dueEpochDay > 0) Small("Due ${day(t.dueEpochDay)}")
                        if (s.needsReshape) Small("You've skipped this ${t.skippedCount} times, so it's been shrunk to a first step. If it's the wrong task, tell the Chief.", color = c.signal)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PosterButton("Done", onClick = {
                                scope.launch { g.case.completeTask(t); stamp = true; refresh++ }
                            })
                            PosterButton("Skip", primary = false, onClick = { scope.launch { g.case.skipTask(t); refresh++ } })
                        }
                        Row {
                            LinkButton("Walk me through it", onClick = { showSteps = t })
                            LinkButton("Why this?", onClick = { showWhy = s })
                        }
                        if (t.module.isNotBlank()) LinkButton("Open ${t.module}", onClick = { nav.openModule(t.module) })
                    }
                }
            }
            item {
                Panel(onClick = { nav.navigate(Routes.PATH) }) {
                    Kicker("Stage ${stage.number} of 8 · ${stage.window}")
                    Text(stage.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = c.fg)
                    Progress(exitFraction)
                    Small("Exit: ${stage.exitTest}")
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BigNumber(weekScore.toString(), "Week score", color = c.signal)
                    BigNumber(nextDeadline?.let { (it.dueEpochDay - LocalDate.now().toEpochDay()).toString() } ?: "—", "Days to deadline")
                    BigNumber(tasks.size.toString(), "Open tasks")
                }
            }
            if (brief.isNotEmpty()) item {
                Panel(onClick = { nav.navigate(Routes.BRIEFING) }) {
                    Kicker("Briefing · ${brief.size} item(s)")
                    brief.take(2).forEach { Body("• ${it.title}") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterButton("Too much", primary = false, onClick = {
                        scope.launch {
                            g.chief.tools.execute("reschedule_plan", JsonObject(mapOf("shrink_week" to JsonPrimitive(true), "reason" to JsonPrimitive("Too much"))),
                                ToolCtx(g.case, g.settings.get(), approved = true))
                            refresh++
                        }
                    })
                    PosterButton("Weekly review", primary = false, onClick = { nav.navigate(Routes.REVIEW) })
                }
            }
            item { Small("Missed days cost nothing. The plan quietly re-flows.") }
        }
        if (stamp) Stamp("Done") { stamp = false }
    }

    showWhy?.let { s ->
        AlertDialog(onDismissRequest = { showWhy = null }, confirmButton = { TextButton(onClick = { showWhy = null }) { Text("OK") } },
            title = { Text("WHY THIS RANKED FIRST", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.lines.forEach { l -> Text("${if (l.points >= 0) "+" else ""}${l.points}  ${l.factor}: ${l.note}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Total ${s.total}. Legal deadlines always outrank everything; nothing is placed inside work hours; one high-effort task a day.",
                        style = MaterialTheme.typography.bodySmall)
                }
            })
    }
    showSteps?.let { t ->
        val steps = t.steps.split("\n").filter { it.isNotBlank() }.ifEmpty {
            listOf("Open the Chief.", "Say: walk me through \"${t.title}\".", "Do the first step only.")
        }
        WalkthroughDialog(t.title, steps) { showSteps = null }
    }
}
