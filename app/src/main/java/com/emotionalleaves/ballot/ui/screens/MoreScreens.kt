package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.emotionalleaves.ballot.agent.ToolCtx
import com.emotionalleaves.ballot.data.PendingKind
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private data class Module(val route: String, val title: String, val line: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val groups: List<Pair<String, List<Module>>> = listOf(
    "Your campaign" to listOf(
        Module(Routes.COMPASS, "Your voice", "Your reason, issues, story, positions and drafts", androidx.compose.material.icons.Icons.Filled.Explore),
        Module(Routes.GROUND, "Your town", "Offices, race cards, the win number and the town quiz", androidx.compose.material.icons.Icons.Filled.AccountBalance),
        Module(Routes.FIELD, "What's on locally", "Meetings, events and the local briefing", androidx.compose.material.icons.Icons.Filled.Event),
        Module(Routes.GYM, "Practice", "Speaking reps, mock rooms and flashcards", androidx.compose.material.icons.Icons.Filled.RecordVoiceOver),
        Module(Routes.HQ, "Campaign HQ", "Launch, field, money and election day", androidx.compose.material.icons.Icons.Filled.Flag),
    ),
    "Records" to listOf(
        Module(Routes.APPROVALS, "Waiting for your OK", "Anything that would go out in your name", androidx.compose.material.icons.Icons.Filled.TaskAlt),
        Module(Routes.VAULT, "Records", "Deadlines, rules, money, papers and the case file", androidx.compose.material.icons.Icons.Filled.Lock),
    ),
    "App" to listOf(
        Module(Routes.SETTINGS, "Settings", "AI services, spending, reminders, lock, backup", androidx.compose.material.icons.Icons.Filled.Settings),
    ),
)

@Composable
fun MoreScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val pending by g.db.pending().observeWaiting().collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    // Campaign HQ only appears once you reach the run stage; before that it would be a locked door on the list.
    val hqOpen = (profile?.stage ?: 0) >= com.emotionalleaves.ballot.core.Stage.RUN.number
    val c = LocalBallotColors.current
    Screen("More", kicker = "Or just ask the Chief for any of these", walkthrough = listOf(
        "Every page here can also be reached by asking the Chief in plain words.",
        "Waiting for your OK holds anything that would go out in your name.")) {
        groups.map { (h, l) -> h to l.filter { it.route != Routes.HQ || hqOpen } }.forEach { (heading, list) ->
            item { Kicker(heading) }
            item {
                Panel(padding = 0.dp) {
                    list.forEachIndexed { i, m ->
                        val count = if (m.route == Routes.APPROVALS) pending.size else 0
                        Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { nav.navigate(m.route) { launchSingleTop = true } }
                            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            androidx.compose.material3.Icon(m.icon, contentDescription = null, tint = c.signal)
                            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                                Text(m.title, style = MaterialTheme.typography.titleMedium, color = c.fg)
                                Text(m.line, style = MaterialTheme.typography.bodySmall, color = c.muted)
                            }
                            if (count > 0) com.emotionalleaves.ballot.ui.components.Badge("$count", c.signal)
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted)
                        }
                        if (i < list.lastIndex) com.emotionalleaves.ballot.ui.components.ThickRule(Modifier.padding(start = 54.dp))
                    }
                }
            }
        }
    }
}

/** Everything in the "asks first" level. Sends happen from your own apps, never from Ballot. */
@Composable
fun ApprovalsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val pending by g.db.pending().observeWaiting().collectAsStateWithLifecycle(initialValue = emptyList())
    val c = LocalBallotColors.current
    Screen("Waiting for your OK", kicker = "Asks first, every time", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Anything sent, posted, filed, signed or spent in your name waits here.",
        "For messages: edit if you like, tap Approve and send, and your own messaging app opens. You press send.",
        "For routine items you moved to 'asks first' with the autonomy dial: Approve runs them.",
        "Voice input that would change money, deadlines or contacts is confirmed here first.")) {
        if (pending.isEmpty()) item { EmptyNote("Nothing waiting. Anything the Chief prepares in your name will appear here first.") }
        items(pending.size) { i ->
            val p = pending[i]
            var draft by remember(p.id) { mutableStateOf(p.draftText) }
            Panel {
                Kicker(when (p.kind) {
                    PendingKind.SEND -> "Message to send yourself"
                    PendingKind.CONFIRM_VOICE -> "Confirm what you said"
                    PendingKind.ROUTINE -> "Routine item you asked to approve"
                    "POST" -> "Post to publish yourself"
                    "FILE" -> "Filing to submit yourself"
                    "SIGN" -> "Something to sign yourself"
                    "SPEND" -> "Spending to make yourself"
                    else -> pretty(p.kind)
                } + " · ${shortDate(p.createdAt)}", color = c.signal)
                Text(p.summary, style = MaterialTheme.typography.titleMedium, color = c.fg)
                if (p.kind == PendingKind.SEND) Field(draft, { draft = it }, "Message", minLines = 3)
                // A template's [gap] is yours to fill; nothing goes out with one left in.
                val gap = p.kind == PendingKind.SEND && Regex("\\[[^\\]]{2,}]").containsMatchIn(draft)
                if (gap) Small("Fill in the part in [brackets] first.", color = c.signal)
                else if (p.kind == PendingKind.CONFIRM_VOICE || p.kind == PendingKind.ROUTINE) Small(prettyPayload(p.payload))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterButton(if (p.kind == PendingKind.SEND) "Approve and send" else "Approve", enabled = !gap, onClick = {
                        scope.launch {
                            val input = runCatching { com.emotionalleaves.ballot.agent.BallotJson.parseToJsonElement(p.payload).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                            val out = g.chief.tools.execute(p.tool, input, ToolCtx(g.case, g.settings.get(), approved = true, store = g.settings, app = ctx.applicationContext))
                            g.db.pending().upsert(p.copy(status = if (out.isError) "FAILED" else "APPROVED", decidedAt = now(), draftText = draft))
                            g.case.audit("you", "Approved", tool = p.tool, input = p.summary, approvedBy = "you", outcome = out.text)
                            // Put the result in the Chief's thread so the next request can build on it.
                            g.db.chat().insert(com.emotionalleaves.ballot.data.ChatMessage(thread = "main", role = "assistant", mode = "Clerk",
                                text = "You approved: ${p.summary}\n${out.text.take(1500)}"))
                            if (p.kind == PendingKind.SEND) shareText(ctx, chiefText(draft).text, subject = "")
                        }
                    })
                    PosterButton("Decline", primary = false, onClick = {
                        scope.launch {
                            g.db.pending().upsert(p.copy(status = "DECLINED", decidedAt = now()))
                            g.case.audit("you", "Declined", tool = p.tool, input = p.summary)
                        }
                    })
                }
            }
        }
    }
}

private fun prettyPayload(json: String): String = runCatching {
    com.emotionalleaves.ballot.agent.BallotJson.parseToJsonElement(json).jsonObject.entries.joinToString("\n") { (k, v) ->
        "${k.replace('_', ' ')}: ${v.toString().trim('"')}"
    }
}.getOrDefault(json)

@Composable
fun Bullet(text: String) {
    Row { Body("• "); Body(text) }
}

@Composable
fun Stack(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
