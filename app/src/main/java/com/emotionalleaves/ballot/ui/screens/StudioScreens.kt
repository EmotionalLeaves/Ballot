package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.agent.BallotJson
import com.emotionalleaves.ballot.agent.ClaimRef
import com.emotionalleaves.ballot.core.Verification
import com.emotionalleaves.ballot.data.FactStatus
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.Badge
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.ConfidenceBadge
import com.emotionalleaves.ballot.ui.components.ConfirmDialog
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.theme.Go
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

private val docTypes = listOf(
    "PUBLIC_COMMENT" to "Public comment", "STUMP_SPEECH" to "Stump speech", "PRESS_STATEMENT" to "Press statement",
    "EMAIL" to "Email", "DOOR_SCRIPT" to "Door script", "PALM_CARD" to "Palm card", "APPLICATION" to "Board application",
    "LETTER" to "Letter to the editor", "QUESTIONNAIRE" to "Questionnaire answers", "CRISIS_RESPONSE" to "Crisis response",
)

fun docLabel(type: String) = docTypes.firstOrNull { it.first == type }?.second ?: type.lowercase().replace('_', ' ')

@Composable
fun StudioScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val docs by g.db.documents().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var type by remember { mutableStateOf(docTypes.first()) }
    var topic by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    Screen("Studio", kicker = "Words, in your voice", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Pick what you need and say what it is about. The Chief drafts it in your voice.",
        "Every factual claim is checked. Unverified claims are highlighted and block approval until sourced or cut.",
        "Your edit is always final. Approve only when it sounds like you.",
        "Crisis responses are held for a cooling period before you can approve them.")) {
        item { BusyBar() }
        item {
            Panel {
                Title("New draft")
                ChipRow(docTypes, type, { it.second }) { type = it }
                Field(topic, { topic = it }, "What it's about", minLines = 2, placeholder = "e.g. Tuesday's agenda item on the park budget, 2 minutes")
                PosterButton("Draft it", enabled = topic.isNotBlank() && !ChiefBusy.busy, onClick = {
                    askChief(g, "Draft a ${type.second.lowercase()} about: $topic. Write it in my voice from my case file (story, positions, voice). " +
                        "Keep it short and plain. List every factual claim with source ids; fetch official pages for any fact you use. " +
                        (if (type.first == "CRISIS_RESPONSE") "This is a crisis response: pause, facts first, short truthful response, one channel, one time. Set crisis true. " else "") +
                        "Save it with draft_document type ${type.first}.", "drafting")
                    topic = ""
                })
            }
        }
        item { ReplyPanel { nav.navigate(Routes.CHIEF) } }
        val list = docs.filter { !it.inVault }
        item { Title("Drafts (${list.size})") }
        if (list.isEmpty()) item { EmptyNote("No drafts yet.") }
        items(list.size) { i ->
            val d = list[i]
            Panel(onClick = { nav.navigate(Routes.document(d.id)) }, padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kicker("${docLabel(d.type)} · v${d.version}")
                    when {
                        d.approved -> Badge("Approved", Go)
                        d.factCheckStatus == FactStatus.BLOCKED -> Badge("Unverified claims", c.signal)
                        d.holdUntil > now() -> Badge("Cooling", c.signal)
                    }
                }
                Text(d.title.uppercase(), style = MaterialTheme.typography.titleMedium, color = c.fg)
            }
        }
    }
}

@Composable
fun DocumentScreen(nav: NavHostController, id: Long) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val doc by g.db.documents().observeOne(id).collectAsStateWithLifecycle(initialValue = null)
    var body by remember(doc?.version, doc?.id) { mutableStateOf(doc?.body ?: "") }
    var title by remember(doc?.version, doc?.id) { mutableStateOf(doc?.title ?: "") }
    var nowMs by remember { mutableLongStateOf(now()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); nowMs = now() } }
    val c = LocalBallotColors.current
    val d = doc ?: run { Screen("Document", onBack = { nav.popBackStack() }) { item { EmptyNote("Not found.") } }; return }
    val claims = remember(d.claims) {
        runCatching { if (d.claims.isBlank()) emptyList() else BallotJson.decodeFromString(ListSerializer(ClaimRef.serializer()), d.claims) }.getOrDefault(emptyList())
    }
    val unverified = claims.filter { it.label == Verification.UNVERIFIED }
    val holding = d.holdUntil > nowMs
    val changed = body != d.body || title != d.title
    if (d.inVault) com.emotionalleaves.ballot.ui.components.SecureScreen()
    Screen(d.title, kicker = "${docLabel(d.type)} · version ${d.version}", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Edit freely. Save keeps the previous version.", "Fact-check runs a second pass that tries to disprove each claim.",
        "Approve is blocked while any claim is unverified or the cooling period is running.",
        "Approved drafts can be shared to your own apps. You send them; the app never posts.")) {
        item { BusyBar() }
        if (holding) item {
            Panel(accent = true) {
                Text("COOLING PERIOD: ${((d.holdUntil - nowMs) / 60_000) + 1} MIN LEFT", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                Text("Never reply while angry. Read it again when the timer ends.", style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
            }
        }
        item { Field(title, { title = it }, "Title", singleLine = true) }
        item { Field(body, { body = it }, "Text", minLines = 10) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton("Save", enabled = changed, onClick = {
                    scope.launch {
                        g.db.documents().upsert(d.copy(title = title, body = body, version = d.version + 1, previousBody = d.body, approved = false,
                            factCheckStatus = if (claims.isEmpty()) FactStatus.UNCHECKED else d.factCheckStatus, aiDrafted = d.aiDrafted, updatedAt = now()))
                    }
                })
                PosterButton("Fact-check", primary = false, enabled = !ChiefBusy.busy, onClick = {
                    askChief(g, "Run a two-pass fact check on document ${d.id} (\"${d.title}\"):\n\n$body\n\nPass 1: list every factual claim. Pass 2: try to disprove each one using official sources " +
                        "(fetch them; use verify_claim for numbers and dates). Cut or reword anything you cannot source. Save the result with draft_document document_id ${d.id}, keeping my wording wherever it is accurate.", "fact-checking")
                })
            }
        }
        item {
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kicker("Fact check")
                    when (d.factCheckStatus) {
                        FactStatus.PASSED -> Badge("Passed", Go)
                        FactStatus.BLOCKED -> Badge("Blocked", c.signal)
                        else -> Badge("Not checked", c.muted)
                    }
                }
                if (claims.isEmpty()) Small("No claims listed yet. Run Fact-check before anything goes public.")
                claims.forEach { cl ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConfidenceBadge(cl.label)
                        Small(cl.text + if (cl.sourceIds.isNotEmpty()) " (${cl.sourceIds.joinToString { "S$it" }})" else "", color = if (cl.label == Verification.UNVERIFIED) c.signal else c.fg)
                    }
                }
            }
        }
        item {
            val canApprove = !holding && unverified.isEmpty() && !changed && !d.approved
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton(if (d.approved) "Approved" else "Approve", enabled = canApprove, onClick = {
                    scope.launch {
                        g.db.documents().upsert(d.copy(approved = true, updatedAt = now()))
                        g.case.audit("you", "Approved document", input = d.title, approvedBy = "you")
                    }
                })
                PosterButton("Share", primary = false, enabled = d.approved, onClick = { shareText(ctx, d.body, d.title) })
            }
            if (!canApprove && !d.approved) Small(when {
                holding -> "Waiting for the cooling period."
                unverified.isNotEmpty() -> "${unverified.size} unverified claim(s): source them or cut them."
                changed -> "Save your edits first."
                else -> ""
            }, color = c.signal)
            if (d.aiDrafted) Small("Drafted with AI help. Some places and platforms require AI-content or \"paid for by\" disclosures: ask the Chief to check the official rule before you publish.")
        }
        item {
            Row {
                LinkButton("Consistency check", onClick = {
                    askChief(g, "Check this draft against everything I have said publicly (positions with public dates and approved documents). Flag any contradiction plainly:\n\n$body", "checking consistency")
                    nav.navigate(Routes.CHIEF)
                })
                LinkButton("Rehearse in Gym", onClick = { nav.navigate(Routes.GYM) })
            }
        }
        if (d.previousBody.isNotBlank()) item {
            Panel { Kicker("Previous version"); Small(d.previousBody.take(1500)) }
        }
        item { LinkButton("Delete", onClick = { confirmDelete = true }, color = c.muted) }
    }
    if (confirmDelete) ConfirmDialog("Delete this draft?", "It will be removed from the case file.", "Delete",
        onConfirm = { scope.launch { g.db.documents().delete(d); nav.popBackStack() } }, onDismiss = { confirmDelete = false })
}
