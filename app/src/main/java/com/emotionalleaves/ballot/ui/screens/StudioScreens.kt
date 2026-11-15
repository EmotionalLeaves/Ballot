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
import androidx.compose.ui.Modifier
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
import com.emotionalleaves.ballot.ui.switchTab
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

private val docTypes = listOf(
    "PUBLIC_COMMENT" to "Public comment", "STUMP_SPEECH" to "Stump speech", "PRESS_STATEMENT" to "Press statement",
    "EMAIL" to "Email", "DOOR_SCRIPT" to "Door script", "PALM_CARD" to "Palm card", "APPLICATION" to "Board application",
    "LETTER" to "Letter to the editor", "QUESTIONNAIRE" to "Questionnaire answers", "CRISIS_RESPONSE" to "Crisis response",
)

/** Your own words from the case file, for templates. */
suspend fun templateFacts(g: com.emotionalleaves.ballot.AppGraph): com.emotionalleaves.ballot.core.TemplateFacts {
    val p = g.case.profile()
    return com.emotionalleaves.ballot.core.TemplateFacts(name = p.name, place = p.address.ifBlank { p.zip }, why = p.why, story60 = p.story60,
        issues = p.issues.lines().map { it.trim() }.filter { it.isNotBlank() },
        positions = g.db.positions().all().filter { it.stance.isNotBlank() }.map { it.issue to it.stance })
}

fun docLabel(type: String) = docTypes.firstOrNull { it.first == type }?.second ?: type.lowercase().replace('_', ' ')

@Composable
fun StudioScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val docs by g.db.documents().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var type by remember { mutableStateOf(docTypes.first()) }
    var topic by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    Screen("Your voice", kicker = "Drafts, in your words", onBack = { nav.popBackStack() }, tabs = voiceTabs(nav), selectedTab = 1, walkthrough = listOf(
        "Pick what you need and say what it is about. A template fills in your own words; the Chief can write a first draft if you prefer.",
        "Every factual claim is checked. Unverified claims are highlighted and block approval until sourced or cut.",
        "Your edit is always final. Approve only when it sounds like you.",
        "Crisis responses are held for a cooling period before you can approve them.")) {
        item { BusyBar() }
        item {
            Panel {
                Title("New draft")
                ChipRow(docTypes, type, { it.second }) { type = it }
                Field(topic, { topic = it }, "What it's about", minLines = 2, placeholder = "e.g. Tuesday's agenda item on the park budget, 2 minutes")
                // The template is the main path: built here from your own words, no AI. The AI draft is optional.
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                PosterButton("Start from a template", enabled = topic.isNotBlank(), onClick = {
                    scope.launch {
                        val body = com.emotionalleaves.ballot.core.Templates.draft(type.first, topic, templateFacts(g))
                        val crisis = type.first == "CRISIS_RESPONSE"
                        val s = g.settings.get()
                        val id = g.db.documents().upsert(com.emotionalleaves.ballot.data.Document(type = type.first, title = topic.trim().take(80), body = body,
                            holdUntil = if (crisis) com.emotionalleaves.ballot.data.now() + s.coolingMinutes * 60_000L else 0))
                        g.case.audit("you", "Started a draft from a template", input = "${type.second}: ${topic.take(60)}")
                        topic = ""
                        nav.navigate(Routes.document(id))
                    }
                })
                LinkButton("Or have the Chief write a first draft", color = c.muted, onClick = {
                    if (topic.isNotBlank() && !ChiefBusy.busy) {
                        askChief(g, "Draft a ${type.second.lowercase()} about: $topic. Write it in my voice from my case file (story, positions, voice). " +
                            "Keep it short and plain. List every factual claim with source ids; fetch official pages for any fact you use. " +
                            (if (type.first == "CRISIS_RESPONSE") "This is a crisis response: pause, facts first, short truthful response, one channel, one time. Set crisis true. " else "") +
                            "Save it with draft_document type ${type.first}.", "drafting")
                        topic = ""
                    }
                })
            }
        }
        item { ReplyPanel { nav.navigate(Routes.CHIEF) } }
        val list = docs.filter { !it.inVault }
        item { Title("Drafts (${list.size})") }
        if (list.isEmpty()) item { EmptyNote("No drafts yet. Pick a kind above, say what it's about, and tap Draft it. Drafts stay here until you approve them.") }
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
                Text(d.title, style = MaterialTheme.typography.titleMedium, color = c.fg)
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
                Text("Cooling period: ${((d.holdUntil - nowMs) / 60_000) + 1} min left", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
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
                // Plain-code fact check: find every checkable sentence; each needs an official page that says the same thing.
                PosterButton("Find facts to check", primary = false, enabled = !changed, onClick = {
                    scope.launch {
                        val found = com.emotionalleaves.ballot.core.ClaimFinder.find(d.body)
                        val keep = claims.filter { old -> found.any { it == old.text } }
                        val merged = keep + found.filter { f -> keep.none { it.text == f } }.map { ClaimRef(it) }
                        val labelled = com.emotionalleaves.ballot.agent.FactCheck.label(merged, g.case, g.settings.get().freshnessDaysGeneral)
                        val status = if (labelled.isEmpty()) FactStatus.PASSED else if (labelled.any { it.label == Verification.UNVERIFIED }) FactStatus.BLOCKED else FactStatus.PASSED
                        g.db.documents().upsert(d.copy(claims = BallotJson.encodeToString(ListSerializer(ClaimRef.serializer()), labelled), factCheckStatus = status, updatedAt = now()))
                    }
                })
            }
            LinkButton("Or have the Chief fact-check it", color = c.muted, onClick = {
                if (!ChiefBusy.busy) askChief(g, "Run a two-pass fact check on document ${d.id} (\"${d.title}\"):\n\n$body\n\nPass 1: list every factual claim. Pass 2: try to disprove each one using official sources " +
                        "(fetch them; use verify_claim for numbers and dates). Cut or reword anything you cannot source. Save the result with draft_document document_id ${d.id}, keeping my wording wherever it is accurate.", "fact-checking")
            })
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
                if (claims.isEmpty()) Small("No claims listed yet. Tap Find facts to check before anything goes public.")
                claims.forEachIndexed { ci, cl ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ConfidenceBadge(cl.label)
                        Small(cl.text + if (cl.sourceIds.isNotEmpty()) " (${cl.sourceIds.joinToString { "S$it" }})" else "", color = if (cl.label == Verification.UNVERIFIED) c.signal else c.fg)
                    }
                    if (cl.label == Verification.UNVERIFIED) SourceForClaim(onSourced = { sid ->
                        scope.launch {
                            val updated = claims.mapIndexed { i, x -> if (i == ci) x.copy(sourceIds = (x.sourceIds + sid).distinct()) else x }
                            val labelled = com.emotionalleaves.ballot.agent.FactCheck.label(updated, g.case, g.settings.get().freshnessDaysGeneral)
                            val status = if (labelled.any { it.label == Verification.UNVERIFIED }) FactStatus.BLOCKED else FactStatus.PASSED
                            g.db.documents().upsert(d.copy(claims = BallotJson.encodeToString(ListSerializer(ClaimRef.serializer()), labelled), factCheckStatus = status, updatedAt = now()))
                        }
                    })
                }
                if (claims.any { it.label == Verification.UNVERIFIED }) Small("Add an official page that says the same thing, or cut or reword the sentence. Approval waits until every claim is sourced.")
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
                PosterButton("Share", primary = false, enabled = d.approved, onClick = { shareText(ctx, chiefText(d.body).text, d.title) })
            }
            if (!canApprove && !d.approved) Small(when {
                holding -> "Waiting for the cooling period."
                unverified.isNotEmpty() -> "${count(unverified.size, "unverified claim")}: source or cut ${if (unverified.size == 1) "it" else "them"}."
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
                LinkButton("Rehearse it", onClick = { nav.navigate(Routes.GYM) })
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

/** Paste the official page for one claim; the app reads it and the claim counts only if the page says the same thing. */
@Composable
private fun SourceForClaim(onSourced: (Long) -> Unit) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val c = LocalBallotColors.current
    var open by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    if (!open) { LinkButton("Add a source", onClick = { open = true }); return }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Field(url, { url = it.trim() }, "Official page (https://…)", Modifier.weight(1f), singleLine = true)
        LinkButton("Check", onClick = {
            scope.launch {
                val out = g.chief.tools.execute("fetch_official_page", kotlinx.serialization.json.JsonObject(mapOf("url" to kotlinx.serialization.json.JsonPrimitive(url))),
                    com.emotionalleaves.ballot.agent.ToolCtx(g.case, g.settings.get(), approved = true))
                if (out.isError || out.sourceIds.isEmpty()) note = out.text.take(200) else { onSourced(out.sourceIds.first()); open = false; url = "" }
            }
        })
    }
    if (note.isNotBlank()) Small(note, color = c.signal)
}

