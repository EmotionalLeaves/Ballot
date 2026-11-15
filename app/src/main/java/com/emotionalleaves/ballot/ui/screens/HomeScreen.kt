package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    var showDetail by remember { mutableStateOf<Pair<Task, Scored>?>(null) }
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
        val hour = LocalDateTime.now().hour
        val greet = when { hour < 12 -> "Good morning"; hour < 18 -> "Good afternoon"; else -> "Good evening" }
        Screen(title = greet + (p.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""),
            // The stage already has its own card below, so the heading carries today's date instead of repeating it.
            kicker = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM")),
            walkthrough = listOf(
                "The card is the one thing worth doing now. The Chief picked it from all your open tasks.",
                "Tap Done when it's finished, or Not today to skip it. Skipping costs nothing.",
                "How and why shows the steps, and exactly how the task was chosen.",
                "Feeling overloaded? Tap Shrink this week and only the most important thing stays.",
            )) {
            // AI is optional, so this is a quiet line, not a banner.
            if (!hasBrain) item {
                Panel(onClick = { nav.navigate(Routes.SETTINGS) }, padding = 14.dp) {
                    Small("Optional: connect a free AI for web research and first drafts. Everything here works without it.")
                }
            }
            if (pending.isNotEmpty()) item {
                Panel(onClick = { nav.navigate(Routes.APPROVALS) }, padding = 14.dp) {
                    Text("${pending.size} waiting for your OK", style = MaterialTheme.typography.titleMedium, color = c.fg)
                    Small("Nothing goes out in your name until you say so.")
                }
            }
            item {
                if (top == null) {
                    Panel(padding = 20.dp) {
                        Text("Nothing due right now", style = MaterialTheme.typography.headlineSmall, color = c.fg)
                        Small("Ask the Chief what would help most, or take the evening off.")
                        PosterButton("Ask the Chief", onClick = { nav.navigate(Routes.CHIEF) })
                    }
                } else {
                    val (t, s) = top
                    Panel(accent = true, padding = 20.dp) {
                        Kicker("Next · about ${t.sizeMinutes} min" + (if (t.dueEpochDay > 0) " · due ${day(t.dueEpochDay)}" else ""), color = Ink)
                        Text(t.title, style = MaterialTheme.typography.headlineSmall, color = Ink)
                        if (t.why.isNotBlank()) Text(t.why, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        if (s.needsReshape) Text("Shrunk to a first step after a few skips. Wrong task? Tell the Chief.", style = MaterialTheme.typography.bodySmall, color = Ink)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PosterButton("Done", onClick = { scope.launch { g.case.completeTask(t); stamp = true; refresh++ } })
                            PosterButton("Not today", primary = false, onClick = { scope.launch { g.case.skipTask(t); refresh++ } })
                        }
                        Row {
                            LinkButton("How and why", onClick = { showDetail = t to s }, color = Ink)
                            if (t.module.isNotBlank()) LinkButton("Open", onClick = { nav.openModule(t.module) }, color = Ink)
                        }
                    }
                }
            }
            item {
                Panel(onClick = { nav.navigate(Routes.PATH) }, padding = 14.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Stage ${stage.number} of 8 · ${stage.title}", style = MaterialTheme.typography.titleMedium, color = c.fg)
                        Small("${(exitFraction * 100).toInt()}%")
                    }
                    Progress(exitFraction)
                    Small(exitLabel)
                }
            }
            item {
                val bits = listOfNotNull(
                    "Week score $weekScore",
                    nextDeadline?.let { d -> "next deadline in ${d.dueEpochDay - LocalDate.now().toEpochDay()} days" },
                    if (brief.isNotEmpty()) "${brief.size} new in your briefing" else null,
                    advanced?.let { "you reached stage ${it.number}" },
                )
                // Tapping the line opens the briefing when there's something new in it.
                Small(bits.joinToString(" · "), Modifier.padding(horizontal = 4.dp)
                    .then(if (brief.isNotEmpty()) Modifier.clickable(onClickLabel = "Open briefing") { nav.navigate(Routes.BRIEFING) } else Modifier))
            }
            if (insideWork) item { Small("You're at work now. Everything keeps until after.", Modifier.padding(horizontal = 4.dp)) }
            item {
                Row {
                    LinkButton("Shrink this week", color = c.muted, onClick = {
                        scope.launch {
                            g.chief.tools.execute("reschedule_plan", JsonObject(mapOf("shrink_week" to JsonPrimitive(true), "reason" to JsonPrimitive("Too much"))),
                                ToolCtx(g.case, g.settings.get(), approved = true, store = g.settings))
                            refresh++
                        }
                    })
                }
            }
        }
        if (stamp) Stamp("Done") { stamp = false }
    }

    // One dialog for the next task: the steps first, then why it outranks everything else.
    showDetail?.let { (t, s) ->
        val steps = t.steps.split("\n").filter { it.isNotBlank() }.ifEmpty {
            listOf("Open the Chief.", "Say: walk me through \"${t.title}\".", "Do the first step only.")
        }
        AlertDialog(onDismissRequest = { showDetail = null }, confirmButton = { TextButton(onClick = { showDetail = null }) { Text("Got it") } },
            title = { Text(t.title, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("How to do it", style = MaterialTheme.typography.titleSmall)
                    steps.forEachIndexed { i, step -> Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium) }
                    if (t.doneLooksLike.isNotBlank()) Text("Done looks like: ${t.doneLooksLike}", style = MaterialTheme.typography.bodySmall)
                    Text("Why it's first", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    s.lines.forEach { l -> Text("${if (l.points >= 0) "+" else ""}${l.points}  ${l.factor}: ${l.note}", style = MaterialTheme.typography.bodyMedium) }
                    Text("Total ${s.total}. Legal deadlines always outrank everything; nothing is placed inside work hours; one high-effort task a day.",
                        style = MaterialTheme.typography.bodySmall)
                }
            })
    }
}
