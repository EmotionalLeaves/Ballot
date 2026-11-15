package com.emotionalleaves.ballot.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import com.emotionalleaves.ballot.agent.MoneyChecks
import com.emotionalleaves.ballot.data.Confidence
import com.emotionalleaves.ballot.data.Deadline
import com.emotionalleaves.ballot.data.Document
import com.emotionalleaves.ballot.data.Money
import com.emotionalleaves.ballot.data.MoneyDirection
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.BigNumber
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.ConfidenceBadge
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.SecureScreen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.Dispatchers
import com.emotionalleaves.ballot.ui.switchTab
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

object VaultFiles {
    private fun key(ctx: Context) = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    fun dir(ctx: Context) = File(ctx.filesDir, "vault").apply { mkdirs() }

    fun store(ctx: Context, uri: Uri): Pair<String, String> {
        var name = "file"
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
        }
        val f = File(dir(ctx), "${System.currentTimeMillis()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.enc")
        val ef = EncryptedFile.Builder(ctx, f, key(ctx), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
        ctx.contentResolver.openInputStream(uri)?.use { input -> ef.openFileOutput().use { out -> input.copyTo(out) } }
        return name to f.absolutePath
    }

    /** Decrypts to a private cache file (cleared each time). Call off the main thread. */
    fun prepare(ctx: Context, path: String, name: String): File? {
        val src = File(path)
        if (!src.exists()) return null
        val shareDir = File(ctx.cacheDir, "vault_open").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(shareDir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        EncryptedFile.Builder(ctx, src, key(ctx), EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
            .openFileInput().use { input -> out.outputStream().use { input.copyTo(it) } }
        return out
    }

    /** Opens a prepared file with whatever app handles its type. */
    fun view(ctx: Context, out: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", out)
            val type = ctx.contentResolver.getType(uri) ?: "*/*"
            ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
fun VaultScreen(nav: NavHostController) {
    SecureScreen()
    val ctx = LocalContext.current
    val g = ctx.graph
    var tab by remember { mutableStateOf("Deadlines") }
    Screen("Records", kicker = "Rules, money and paper · screenshots blocked", onBack = { nav.popBackStack() }, tabs = recordTabs(nav), selectedTab = 0, walkthrough = listOf(
        "Deadlines: every filing date and report window, each tied to its official page.",
        "Rules: what the Chief has fetched, when it last checked, and the office to call.",
        "Money: every dollar in and out from the first one, including your own, shaped for reports.",
        "Paper: forms, receipts and filings, encrypted on this phone.",
        "Self-vetting: the audit an opponent would run on you, done first.")) {
        item { ChipRow(listOf("Deadlines", "Rules", "Money", "Paper", "Disclaimers", "Self-vetting"), tab, { it }) { tab = it } }
        item { BusyBar() }
        // The Chief's answer shows right here, so asking doesn't take you away from your records.
        item { ReplyPanel { nav.navigate(Routes.CHIEF) } }
        when (tab) {
            "Deadlines" -> item { DeadlinesTab() }
            "Rules" -> item { RulesTab(nav) }
            "Money" -> item { MoneyTab() }
            "Paper" -> item { PaperTab(nav) }
            "Disclaimers" -> item { DisclaimerTab(nav) }
            else -> item { VettingTab(nav) }
        }
    }
}

@Composable
private fun DeadlinesTab() {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val deadlines by g.db.deadlines().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var legal by remember { mutableStateOf(true) }
    var url by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    val today = LocalDate.now().toEpochDay()
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Small("Alerts at 14, 7 and 2 days. A legal deadline never comes within seven days without a prepared draft.")
        if (deadlines.isEmpty()) EmptyNote("No deadlines yet. Build a race card in Offices and races and the Chief adds the filing deadline from the official page.")
        deadlines.forEach { d ->
            val left = d.dueEpochDay - today
            Panel(accent = !d.done && left in 0..7) {
                val ink = if (!d.done && left in 0..7) com.emotionalleaves.ballot.ui.theme.Ink else c.fg
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    // A number only when it's a count; words stay at reading size so the list stays calm.
                    BigNumber(when { d.done -> "✓"; left < 0 -> "Passed"; left == 0L -> "Today"; else -> "$left" },
                        when { d.done -> "Done"; left < 0 -> "${-left} ${if (left == -1L) "day" else "days"} ago"; left == 0L -> "Due today"; left == 1L -> "Day left"; else -> "Days left" },
                        color = if (!d.done && left < 0) c.signal else ink, size = if (d.done || left <= 0) 24 else 32)
                    ConfidenceBadge(d.confidence)
                }
                Text(d.title, style = MaterialTheme.typography.titleMedium, color = ink)
                Small("Due ${day(d.dueEpochDay)}${if (d.legal) " · legal" else ""}", color = ink)
                if (!d.done && left < 0 && d.legal) Small("If this was missed, call the election office: some deadlines have a late option or a fee.", color = c.signal)
                else if (d.confidence != Confidence.VERIFIED) Small("Confirm with the election office.", color = ink)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(d.done, { v -> scope.launch { g.case.setDeadlineDone(d, v) } })
                    Small("Done", color = ink)
                    if (d.officialUrl.isNotBlank()) LinkButton("Official page", onClick = { openUrl(ctx, d.officialUrl) }, color = ink)
                }
            }
        }
        Panel {
            Title("Add one you were given")
            Small("For a date from the clerk or the election office. It stays marked 'confirm' until it has an official page.")
            Field(title, { title = it }, "What is due", singleLine = true)
            Field(date, { date = it }, "Date, e.g. 11/20/2026", singleLine = true)
            Field(url, { url = it }, "Official link (optional)", singleLine = true)
            com.emotionalleaves.ballot.ui.components.SwitchRow("Legal deadline (filing, reports, petitions)", legal, { legal = it })
            PosterButton("Add", enabled = title.isNotBlank() && parseDay(date) != null, onClick = {
                scope.launch {
                    g.case.addDeadlineByHand(title, parseDay(date)!!, url, legal)
                    title = ""; date = ""; url = ""
                }
            })
        }
    }
}

@Composable
private fun RulesTab(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val rules by g.db.rules().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val c = LocalBallotColors.current
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Small("Rules differ by state, county and city. The Chief never states one from memory. Each is fetched from the official authority and re-checked before deadlines.")
        AddRuleByHand()
        PosterButton("Load the rules with the Chief", primary = false, onClick = {
            askChief(g, "Load the official election and campaign finance rules that apply to me: filing requirements, petition rules, contribution limits, prohibited sources, " +
                "reporting deadlines and disclaimer rules, from my state and county election authority pages only. Save each deadline with add_deadline (with rule_summary, " +
                "jurisdiction, office_phone, and limit_kind/limit_dollars for money limits). Say plainly what needs an election lawyer or the clerk.", "loading rules")
        })
        if (rules.isEmpty()) EmptyNote("No rules yet. Add one from your election office's page or a call to the clerk, or have the Chief read them from your state and county election pages.")
        rules.forEach { r ->
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Kicker(r.jurisdiction.ifBlank { "Rule" }); ConfidenceBadge(r.status) }
                Body(r.summary)
                if (r.limitCents > 0) Small("${if (r.limitKind == "PER_DONOR") "Limit per donor" else "Reporting trigger"}: ${dollars(r.limitCents)}", color = c.fg)
                Small("Last checked ${shortDate(r.lastChecked)}")
                Row {
                    if (r.officialUrl.isNotBlank()) LinkButton("Official page", onClick = { openUrl(ctx, r.officialUrl) })
                    if (r.officePhone.isNotBlank()) LinkButton("Call ${r.officePhone}", onClick = { dial(ctx, r.officePhone) })
                }
            }
        }
    }
}

@Composable
private fun MoneyTab() {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val money by g.db.money().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var warnings by remember { mutableStateOf<List<String>>(emptyList()) }
    var dir by remember { mutableStateOf(MoneyDirection.OUT) }
    var amount by remember { mutableStateOf("") }
    var person by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var employer by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var own by remember { mutableStateOf(false) }
    val c = LocalBallotColors.current
    LaunchedEffect(money.size) { warnings = MoneyChecks.warnings(g.case) }
    val totals = com.emotionalleaves.ballot.core.Ledger.totals(money.map { it.direction to it.amountCents })
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Three equal columns at a size that fits a phone even with thousands in each.
            BigNumber(dollars(totals.raised), "Raised", Modifier.weight(1f), size = 24)
            BigNumber(dollars(totals.out), "Spent", Modifier.weight(1f), size = 24)
            BigNumber(dollars(totals.cashOnHand), "Cash on hand", Modifier.weight(1f), size = 24, color = c.signal)
        }
        if (totals.inKind > 0) Small("Raised includes ${dollars(totals.inKind)} given in kind, which isn't cash to spend.")
        warnings.forEach { Small("⚠ $it", color = c.signal) }
        // For a report or a treasurer: the whole ledger as a spreadsheet file, shared from your phone.
        if (money.isNotEmpty()) LinkButton("Share the ledger as a spreadsheet (CSV)", onClick = {
            val rows = money.map { m -> com.emotionalleaves.ballot.core.Ledger.Row(java.time.Instant.ofEpochMilli(m.at).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString(),
                m.direction, m.amountCents, m.person, m.address, m.employer, m.occupation, m.category, m.reportPeriod, m.memo, m.ownFunds) }
            shareText(ctx, com.emotionalleaves.ballot.core.Ledger.csv(rows), "Campaign ledger")
        })
        Panel {
            Title("Log money")
            Small("From the first dollar, including your own.")
            ChipRow(listOf(MoneyDirection.OUT, MoneyDirection.IN, MoneyDirection.IN_KIND), dir, { when (it) { "IN" -> "Money in"; "OUT" -> "Money out"; else -> "In-kind" } }) { dir = it }
            Field(amount, { amount = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Amount in dollars", singleLine = true, number = true)
            Field(person, { person = it }, if (dir == MoneyDirection.OUT) "Paid to" else "From", singleLine = true)
            Field(category, { category = it }, "Category (printing, filing fee, signs…)", singleLine = true)
            if (dir != MoneyDirection.OUT) {
                Field(address, { address = it }, "Donor address", singleLine = true)
                Field(employer, { employer = it }, "Donor employer", singleLine = true)
                Field(occupation, { occupation = it }, "Donor occupation", singleLine = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(own, { own = it }); Small("My own money", color = c.fg) }
            PosterButton("Log", enabled = (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                scope.launch {
                    g.db.money().upsert(Money(direction = dir, amountCents = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong(), person = person, category = category,
                        employer = employer, occupation = occupation, address = address, ownFunds = own))
                    g.case.audit("you", "Logged money", input = "$dir $amount $person")
                    amount = ""; person = ""; category = ""; employer = ""; occupation = ""; address = ""; own = false
                }
            })
        }
        PosterButton("Export for your report (CSV)", primary = false, enabled = money.isNotEmpty(), onClick = {
            val csv = buildString {
                appendLine("date,direction,amount,name,address,employer,occupation,category,own_funds,memo")
                money.sortedBy { it.at }.forEach { m ->
                    val d = java.time.Instant.ofEpochMilli(m.at).atZone(ZoneId.systemDefault()).toLocalDate()
                    appendLine(listOf(d.toString(), m.direction, "%.2f".format(m.amountCents / 100.0), m.person, m.address, m.employer, m.occupation, m.category, m.ownFunds.toString(), m.memo)
                        .joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
                }
            }
            shareText(ctx, csv, "Ballot finance ledger")
        })
        money.take(50).forEach { m ->
            Panel(padding = 12.dp) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text((if (m.direction == MoneyDirection.OUT) "− " else "+ ") + dollars(m.amountCents), style = MaterialTheme.typography.titleMedium, color = if (m.direction == MoneyDirection.OUT) c.fg else c.signal)
                    Small(shortDate(m.at))
                }
                Small(listOf(m.person, m.category, if (m.ownFunds) "own funds" else "").filter { it.isNotBlank() }.joinToString(" · "), color = c.fg)
            }
        }
    }
}

@Composable
private fun PaperTab(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val docs by g.db.documents().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val (name, path) = withContext(Dispatchers.IO) { VaultFiles.store(ctx, uri) }
            g.db.documents().upsert(Document(type = "FILE", title = name, inVault = true, filePath = path, aiDrafted = false, source = "you"))
        }
    }
    val vault = docs.filter { it.inVault }
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PosterButton("Add a file (encrypted)", onClick = { picker.launch(arrayOf("*/*")) })
        Panel {
            Title("Add a note")
            Field(noteTitle, { noteTitle = it }, "Title", singleLine = true)
            Field(noteBody, { noteBody = it }, "Note", minLines = 3)
            PosterButton("Save", enabled = noteTitle.isNotBlank(), onClick = {
                scope.launch {
                    g.db.documents().upsert(Document(type = "NOTE", title = noteTitle, body = noteBody, inVault = true, aiDrafted = false, source = "you"))
                    noteTitle = ""; noteBody = ""
                }
            })
        }
        if (vault.isEmpty()) EmptyNote("Forms, receipts and filings live here, encrypted.")
        vault.forEach { d ->
            Panel(onClick = {
                if (d.filePath.isBlank()) nav.navigate(Routes.document(d.id))
                else scope.launch { withContext(Dispatchers.IO) { runCatching { VaultFiles.prepare(ctx, d.filePath, d.title) }.getOrNull() }?.let { VaultFiles.view(ctx, it) } }
            }, padding = 12.dp) {
                Kicker("${pretty(d.type)} · ${shortDate(d.createdAt)}")
                Text(d.title, style = MaterialTheme.typography.titleMedium, color = c.fg)
            }
        }
    }
}

@Composable
private fun DisclaimerTab(nav: NavHostController) {
    val g = LocalContext.current.graph
    var committee by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Small("Most places require a 'paid for by' line on campaign materials, and some require AI-content disclosures. The exact wording depends on your jurisdiction.")
        PosterButton("Find my disclaimer rules", onClick = {
            askChief(g, "Find the official disclaimer rules for my jurisdiction: required 'paid for by' wording, where it must appear on print, signs, digital and mail, " +
                "and any AI-generated content disclosure rules. Official sources only, cited. Save the rule with add_deadline if it has a date, otherwise save_to_vault.", "finding disclaimer rules")
        })
        Field(committee, { committee = it }, "Committee name, as registered", singleLine = true)
        if (committee.isNotBlank()) Panel {
            Kicker("Draft line")
            Text("Paid for by $committee", style = MaterialTheme.typography.titleLarge, color = c.fg)
            Small("Draft only. Confirm the exact wording against the official rule before printing.", color = c.signal)
        }
    }
}

@Composable
private fun VettingTab(nav: NavHostController) {
    val g = LocalContext.current.graph
    val docs by g.db.documents().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val c = LocalBallotColors.current
    val vetting = docs.filter { it.type == "VETTING" }
    androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Body("Before you go public, run the audit an opponent would run on you, using only what is publicly findable.")
        Small("Repeated before stage 4 and again before stage 7.")
        PosterButton("Run the audit", onClick = {
            askChief(g, "Run my self-vetting audit using only public information: search my name and any usernames I have told you, old public posts, public records. " +
                "List anything a voter or reporter could find. For each: leave it, clean up where the platform allows, or a truthful two-sentence answer. " +
                "Then write a hard-questions list with honest answers. Save it with save_to_vault type VETTING. Ask me for my usernames first if you do not have them.", "self-vetting")
            nav.navigate(Routes.CHIEF)
        })
        PosterButton("Practise the hard questions", primary = false, onClick = { nav.navigate(Routes.room("reporter")) })
        if (vetting.isEmpty()) EmptyNote("Not run yet. It takes a few minutes and only uses what anyone could find about you online.")
        vetting.forEach { d ->
            Panel(onClick = { nav.navigate(Routes.document(d.id)) }) { Kicker("Audit · ${shortDate(d.createdAt)}"); Text(d.title, style = MaterialTheme.typography.titleMedium, color = c.fg) }
        }
    }
}

/** A rule written down from the election office's page or a phone call: no AI needed. */
@Composable
private fun AddRuleByHand() {
    val g = LocalContext.current.graph
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    LinkButton(if (open) "Hide" else "Add a rule by hand", onClick = { open = !open })
    if (!open) return
    Panel {
        Field(place, { place = it }, "Whose rule, e.g. Lancaster County Election Commission", singleLine = true)
        Field(summary, { summary = it }, "What it says, in plain words", minLines = 2)
        Field(limit, { limit = it.filter { ch -> ch.isDigit() || ch == '.' } }, "Contribution limit per donor in dollars (if this is one)", singleLine = true, number = true)
        Field(url, { url = it.trim() }, "Official page", singleLine = true)
        Field(phone, { phone = it.trim() }, "Office phone", singleLine = true)
        PosterButton("Save rule", enabled = place.isNotBlank() && summary.isNotBlank(), onClick = {
            scope.launch {
                val cents = limit.toDoubleOrNull()?.let { Math.round(it * 100) } ?: 0L
                g.db.rules().upsert(com.emotionalleaves.ballot.data.Rule(jurisdiction = place.trim(), summary = summary.trim(), officialUrl = url, officePhone = phone,
                    limitKind = if (cents > 0) "PER_DONOR" else "", limitCents = cents, status = com.emotionalleaves.ballot.data.Confidence.STATED,
                    source = "you", confidence = com.emotionalleaves.ballot.data.Confidence.STATED, lastChecked = com.emotionalleaves.ballot.data.now()))
                g.case.audit("you", "Added a rule by hand", input = place.trim())
                place = ""; summary = ""; url = ""; phone = ""; limit = ""; open = false
            }
        })
        Small("Saved as 'you said'. With an official page it is re-checked for changes like any other rule.")
    }
}

