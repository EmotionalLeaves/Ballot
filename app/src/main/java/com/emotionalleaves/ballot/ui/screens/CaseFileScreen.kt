package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.ConfidenceBadge
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch

private data class Entry(val kicker: String, val title: String, val body: String, val confidence: String, val onDelete: (() -> Unit)?, val onOpen: (() -> Unit)?)

/** The Chief's memory is a structured record, not a chat log. Open, edit or delete any entry. */
@Composable
fun CaseFileScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("Identity") }
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val offices by g.db.offices().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val rules by g.db.rules().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val sources by g.db.sources().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val positions by g.db.positions().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val docs by g.db.documents().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by g.db.tasks().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val decisions by g.db.decisions().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val lessons by g.db.lessons().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val audit by g.db.audit().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var editingTask by remember { mutableStateOf<com.emotionalleaves.ballot.data.Task?>(null) }
    val c = LocalBallotColors.current

    val entries: List<Entry> = when (tab) {
        "Identity" -> profile?.let { p ->
            listOf(
                Entry("Story", "60-second story", p.story60.ifBlank { "—" }, "STATED", null) { nav.navigate(Routes.COMPASS) },
                Entry("Why", "Why you want this", p.why.ifBlank { "—" }, "STATED", null) { nav.navigate(Routes.COMPASS) },
                Entry("Issues", "Your issues", p.issues.ifBlank { "—" }, "STATED", null) { nav.navigate(Routes.COMPASS) },
                Entry("Life", "Work and time", "Work days ${p.workDays}, ${hm(p.workStartMin)}–${hm(p.workEndMin)}. ${p.weeklyHours} free hours a week. Energy ${p.energy.lowercase()}. Off limits: ${p.offLimits.ifBlank { "none" }}", "STATED", null, null),
                Entry("Job", "Employer rules", p.employerRules.ifBlank { "Not asked yet" }, "STATED", null, null),
                Entry("Place", "Where you live", "ZIP ${p.zip.ifBlank { "unknown" }} ${p.address}", "STATED", null, null),
            )
        } ?: emptyList()
        "Ground" -> offices.map { o -> Entry("Office · ${shortDate(o.updatedAt)} · ${o.source}", "${o.body} ${o.district}", o.powers.ifBlank { o.officialUrl }, o.confidence,
            { scope.launch { g.db.offices().delete(o) } }, { nav.navigate(Routes.GROUND) }) } +
            rules.map { r -> Entry("Rule · checked ${shortDate(r.lastChecked)}", r.jurisdiction, r.summary, r.status, { scope.launch { g.db.rules().delete(r) } }, { openUrl(ctx, r.officialUrl) }) }
        "Sources" -> sources.map { s -> Entry("S${s.id} · ${dateTime(s.fetchedAt)}${if (s.injectionFlagged) " · contains instructions (ignored)" else ""}", s.title.ifBlank { s.url }, s.url, s.label, null, { openUrl(ctx, s.url) }) }
        "Record" -> positions.filter { it.statedPubliclyAt > 0 }.map { p -> Entry("Said publicly ${shortDate(p.statedPubliclyAt)}", p.issue, p.stance, "STATED", null) { nav.navigate(Routes.COMPASS) } } +
            docs.filter { it.approved }.map { d -> Entry("Approved ${docLabel(d.type)} · ${shortDate(d.updatedAt)}", d.title, d.body.take(300), d.confidence, null) { nav.navigate(Routes.document(d.id)) } }
        "Plan" -> tasks.map { t -> Entry("Task · stage ${t.stage} · ${if (t.done) "done" else "open"} · score ${t.score}", t.title, t.why, "STATED",
            { scope.launch { g.db.tasks().delete(t) } }, { editingTask = t }) } +
            decisions.map { d -> Entry("Decision · ${shortDate(d.at)}", d.question, "${d.choice}\n${d.reasoning}".trim(), "STATED", { scope.launch { g.db.decisions().delete(d) } }, null) }
        "Lessons" -> lessons.map { l -> Entry("${pretty(l.kind)} · ${shortDate(l.at)}", l.what, l.change, "STATED", { scope.launch { g.db.lessons().delete(l) } }, null) }
        else -> audit.map { a -> Entry("${dateTime(a.at)} · ${a.actor}${if (a.approvedBy.isNotBlank()) " · approved by ${a.approvedBy}" else ""}", "${a.action}${if (a.tool.isNotBlank()) " · ${a.tool}" else ""}",
            listOf(a.input.take(200), a.outcome.take(200), a.sources).filter { it.isNotBlank() }.joinToString("\n"), "STATED", null, null) }
    }

    Screen("Case file", kicker = "Everything the Chief relies on", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Identity, ground, sources, your public record, the plan and lessons. Each entry has a date, a source and a confidence label.",
        "Delete anything you don't want kept. Tap an entry to open where it lives.",
        "The audit log records every agent action, tool call and approval. It cannot be edited.")) {
        item { ChipRow(listOf("Identity", "Ground", "Sources", "Record", "Plan", "Lessons", "Audit log"), tab, { it }) { tab = it } }
        editingTask?.let { t ->
            item {
                var title by remember(t.id) { mutableStateOf(t.title) }
                var why by remember(t.id) { mutableStateOf(t.why) }
                Panel {
                    Field(title, { title = it }, "Task", singleLine = true)
                    Field(why, { why = it }, "Why it matters")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PosterButton("Save", onClick = { scope.launch { g.db.tasks().upsert(t.copy(title = title, why = why, updatedAt = com.emotionalleaves.ballot.data.now())); editingTask = null } })
                        LinkButton("Cancel", onClick = { editingTask = null }, color = c.muted)
                    }
                }
            }
        }
        if (entries.isEmpty()) item { EmptyNote(when (tab) {
            "Ground" -> "No offices or rules yet. They arrive when the Chief finds your offices or loads your election rules."
            "Sources" -> "No sources yet. Every official page the Chief reads is kept here with its date."
            "Record" -> "Nothing public yet. Positions you've stated in public and drafts you've approved show here."
            "Plan" -> "No tasks or decisions yet. Your plan fills in as you move through the stages."
            "Lessons" -> "No lessons yet. After an event or a weekly review, one lesson goes here."
            else -> "Nothing here yet."
        }) }
        items(entries.size) { i ->
            val e = entries[i]
            Panel(onClick = e.onOpen, padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Kicker(e.kicker)
                    if (tab != "Audit log" && tab != "Identity") ConfidenceBadge(e.confidence)
                }
                Text(e.title, style = MaterialTheme.typography.titleMedium, color = c.fg)
                if (e.body.isNotBlank()) Small(e.body.take(500), color = c.fg)
                if (e.onDelete != null) LinkButton("Delete", onClick = e.onDelete, color = c.muted, modifier = Modifier)
            }
        }
    }
}
