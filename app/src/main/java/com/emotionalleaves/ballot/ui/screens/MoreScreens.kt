package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
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

private data class Module(val route: String, val title: String, val line: String)

private val modules = listOf(
    Module(Routes.COMPASS, "Compass", "Who you are and what you stand for"),
    Module(Routes.GROUND, "Ground", "Your offices, race cards and the win number"),
    Module(Routes.BRIEFING, "Briefing", "What is happening near you"),
    Module(Routes.FIELD, "Field", "Meetings, events, boards to apply for"),
    Module(Routes.GYM, "Gym", "Speaking reps and mock rooms"),
    Module(Routes.STUDIO, "Studio", "Drafts in your voice, fact-checked"),
    Module(Routes.VAULT, "Vault", "Deadlines, rules, money and paper"),
    Module(Routes.HQ, "Campaign HQ", "Unlocks at stage 7"),
    Module(Routes.APPROVALS, "Approvals", "Everything waiting for your tap"),
    Module(Routes.CASEFILE, "Case file", "Open, edit or delete anything the Chief knows"),
    Module(Routes.SETTINGS, "Settings", "Key, spend cap, autonomy, backup, lock"),
)

@Composable
fun MoreScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val pending by g.db.pending().observeWaiting().collectAsStateWithLifecycle(initialValue = emptyList())
    val c = LocalBallotColors.current
    Screen("Everything else", kicker = "Or just ask the Chief", walkthrough = listOf(
        "Every module is also reachable by asking the Chief in plain words.", "Approvals holds anything that would go out in your name.")) {
        items(modules.size) { i ->
            val m = modules[i]
            Panel(onClick = { nav.navigate(m.route) { launchSingleTop = true } }, accent = m.route == Routes.APPROVALS && pending.isNotEmpty()) {
                val onAccent = m.route == Routes.APPROVALS && pending.isNotEmpty()
                Text((m.title + if (m.route == Routes.APPROVALS && pending.isNotEmpty()) " · ${pending.size}" else "").uppercase(),
                    style = MaterialTheme.typography.headlineSmall, color = if (onAccent) com.emotionalleaves.ballot.ui.theme.Ink else c.fg)
                Text(m.line, style = MaterialTheme.typography.bodyMedium, color = if (onAccent) com.emotionalleaves.ballot.ui.theme.Ink else c.muted)
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
    Screen("Approvals", kicker = "Asks first, every time", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Anything sent, posted, filed, signed or spent in your name waits here.",
        "For messages: edit if you like, tap APPROVE AND SEND, and your own messaging app opens. You press send.",
        "For routine items you moved to 'asks first' with the autonomy dial: APPROVE runs them.",
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
                    else -> p.kind.lowercase()
                } + " · ${shortDate(p.createdAt)}", color = c.signal)
                Text(p.summary, style = MaterialTheme.typography.titleMedium, color = c.fg)
                if (p.kind == PendingKind.SEND) Field(draft, { draft = it }, "Message", minLines = 3)
                else if (p.kind == PendingKind.CONFIRM_VOICE || p.kind == PendingKind.ROUTINE) Small(prettyPayload(p.payload))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterButton(if (p.kind == PendingKind.SEND) "Approve and send" else "Approve", onClick = {
                        scope.launch {
                            val input = runCatching { com.emotionalleaves.ballot.agent.BallotJson.parseToJsonElement(p.payload).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                            val out = g.chief.tools.execute(p.tool, input, ToolCtx(g.case, g.settings.get(), approved = true))
                            g.db.pending().upsert(p.copy(status = if (out.isError) "FAILED" else "APPROVED", decidedAt = now(), draftText = draft))
                            g.case.audit("you", "Approved", tool = p.tool, input = p.summary, approvedBy = "you", outcome = out.text)
                            // Put the result in the Chief's thread so the next request can build on it.
                            g.db.chat().insert(com.emotionalleaves.ballot.data.ChatMessage(thread = "main", role = "assistant", mode = "Clerk",
                                text = "You approved: ${p.summary}\n${out.text.take(1500)}"))
                            if (p.kind == PendingKind.SEND) shareText(ctx, draft, subject = "")
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
