package com.emotionalleaves.ballot.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.emotionalleaves.ballot.BuildConfig
import com.emotionalleaves.ballot.agent.Providers
import com.emotionalleaves.ballot.agent.clientFor
import com.emotionalleaves.ballot.agent.NetLog
import com.emotionalleaves.ballot.data.AppSettings
import com.emotionalleaves.ballot.data.Backup
import com.emotionalleaves.ballot.data.CrashReports
import com.emotionalleaves.ballot.data.RoutineKind
import com.emotionalleaves.ballot.data.Snapshots
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.Collapsible
import com.emotionalleaves.ballot.ui.components.ConfirmDialog
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Progress
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.SwitchRow
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val s by g.settings.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val monthStart = remember { LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() }
    val spend by g.db.usage().observeSpendSince(monthStart).collectAsStateWithLifecycle(initialValue = 0L)
    var model by remember(s.provider, s.providerModels) { mutableStateOf(Providers.parseModels(s.providerModels)[s.provider] ?: "") }
    var showAdd by remember { mutableStateOf(false) }
    var lockHint by remember { mutableStateOf("") }
    var aiChanged by remember { mutableStateOf(0) }
    var reviewDay by remember { mutableStateOf(7) }
    LaunchedEffect(Unit) { reviewDay = g.case.profile().weeklyReviewDay }
    var showAdvanced by remember { mutableStateOf(false) }
    var baseUrl by remember(s.openAiBaseUrl) { mutableStateOf(s.openAiBaseUrl) }
    var cap by remember(s.monthlyCapCents) { mutableStateOf((s.monthlyCapCents / 100).toString()) }
    var testStatus by remember { mutableStateOf("") }
    var domain by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var passphrase2 by remember { mutableStateOf("") }
    var backupStatus by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(0) }
    var restoreFile by remember { mutableStateOf<File?>(null) }
    var snapshots by remember { mutableStateOf<List<File>>(emptyList()) }
    var crashes by remember { mutableStateOf<List<File>>(emptyList()) }
    var showNet by remember { mutableStateOf(false) }
    var refresh by remember { mutableLongStateOf(0) }
    val c = LocalBallotColors.current
    LaunchedEffect(refresh) { snapshots = Snapshots.list(ctx); crashes = CrashReports.list(ctx) }
    fun upd(t: (AppSettings) -> AppSettings) = scope.launch { g.settings.update(t) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            backupStatus = runCatching {
                val bytes = Backup.export(g.db, passphrase.toCharArray(), g.settings).also { g.settings.update { s -> s.copy(lastBackupAt = com.emotionalleaves.ballot.data.now()) } }
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
                g.case.audit("you", "Exported a backup")
                "Backup saved. Keep the passphrase safe: without it the file cannot be opened."
            }.getOrElse { it.message ?: "Export failed." }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            backupStatus = runCatching {
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("Could not read the file.")
                // Keep what was here first: an import replaces everything, so a snapshot makes it undoable.
                withContext(Dispatchers.IO) { Snapshots.take(ctx, g.db) }
                Backup.import(g.db, bytes, passphrase.toCharArray(), g.settings)
                refresh++
                "Backup restored. What was here before is kept in today's snapshot below."
            }.getOrElse { it.message ?: "Import failed." }
        }
    }

    Screen("Settings", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "AI is optional: it adds web research and first drafts. The spend cap limits any paid service.",
        "The autonomy dial moves routine work between 'does without asking' and 'asks first'. Anything sent, posted, filed, signed or spent always asks.",
        "Backups are encrypted with a passphrase you choose. Snapshots are taken automatically each day, seven kept.",
        "Panic wipe deletes everything on this phone.")) {
        item {
            Collapsible("Quiet hours and look", "Sleep, brief time, review day, theme, text size", startOpen = true) {
                var sleepStart by remember(s.sleepStartMin) { mutableStateOf(hm(s.sleepStartMin)) }
                var sleepEnd by remember(s.sleepEndMin) { mutableStateOf(hm(s.sleepEndMin)) }
                var brief by remember(s.briefMinuteOfDay) { mutableStateOf(hm(s.briefMinuteOfDay)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(sleepStart, { sleepStart = it }, "Sleep from", Modifier.weight(1f), singleLine = true)
                    Field(sleepEnd, { sleepEnd = it }, "Sleep until", Modifier.weight(1f), singleLine = true)
                    Field(brief, { brief = it }, "Brief at", Modifier.weight(1f), singleLine = true)
                }
                LinkButton("Save times", onClick = {
                    upd { it.copy(sleepStartMin = parseHm(sleepStart) ?: it.sleepStartMin, sleepEndMin = parseHm(sleepEnd) ?: it.sleepEndMin, briefMinuteOfDay = parseHm(brief) ?: it.briefMinuteOfDay) }
                })
                Small("Work hours are part of your profile. Tell the Chief if they change.")
                Kicker("Weekly review day")
                ChipRow((1..7).toList(), reviewDay, { listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")[it - 1] }) { d ->
                    reviewDay = d
                    scope.launch { g.case.updateProfile { it.copy(weeklyReviewDay = d) } }
                }
                SwitchRow("Dark theme (for night use)", s.darkTheme, { v -> upd { it.copy(darkTheme = v) } })
                var scale by remember(s.textScale) { mutableStateOf(s.textScale) }
                Small("Text size ${"%.0f".format(scale * 100)}%")
                // Saved when you let go, so dragging doesn't rewrite settings dozens of times.
                Slider(value = scale, onValueChange = { scale = (it * 20).toInt() / 20f }, valueRange = 0.85f..1.5f,
                    onValueChangeFinished = { val v = scale; upd { it.copy(textScale = v) } })
            }
        }
        item {
            Collapsible("Lock", "PIN and fingerprint") {
                SwitchRow("App lock (PIN or biometric)", s.appLock && g.secrets.hasPin(), { v ->
                    lockHint = if (!v || g.secrets.hasPin()) "" else "Set a PIN below first. Fingerprint or face unlock works once a PIN exists."
                    if (!v) upd { it.copy(appLock = false) } else if (g.secrets.hasPin()) upd { it.copy(appLock = true) }
                })
                if (lockHint.isNotBlank()) Small(lockHint, color = c.signal)
                Field(pin, { pin = it.filter(Char::isDigit).take(12) }, "Set a PIN (4 to 12 digits)", singleLine = true, number = true, secret = true)
                LinkButton("Save PIN and turn lock on", onClick = {
                    lockHint = if (pin.length >= 4) "Lock is on." else "A PIN needs at least 4 digits."
                    if (pin.length >= 4) { g.secrets.setPin(pin); pin = ""; com.emotionalleaves.ballot.AppLock.locked = false; upd { it.copy(appLock = true) } }
                })
                var auto by remember(s.autoLockMinutes) { mutableStateOf(s.autoLockMinutes.toString()) }
                // Saves as you type: no separate Save button.
                Field(auto, { t -> auto = t.filter(Char::isDigit).take(3); auto.toIntOrNull()?.let { v -> upd { it.copy(autoLockMinutes = v.coerceIn(0, 120)) } } },
                    "Auto-lock after minutes away (0 to 120)", singleLine = true, number = true)
            }
        }
        item {
            Collapsible("Backup", "Encrypted export and daily snapshots") {
                Small("An encrypted file you keep somewhere safe. You choose the passphrase.")
                Field(passphrase, { passphrase = it }, "Backup passphrase (8+ characters)", singleLine = true, secret = true)
                // A typo in the passphrase would lock you out of your own backup, so exporting asks for it twice.
                Field(passphrase2, { passphrase2 = it }, "Type it again to export", singleLine = true, secret = true)
                if (passphrase2.isNotEmpty() && passphrase2 != passphrase) Small("The two don't match yet.", color = LocalBallotColors.current.signal)
                Small("Write the passphrase down somewhere safe. Without it the backup can't be opened, by you or anyone.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterButton("Export", enabled = passphrase.length >= 8 && passphrase2 == passphrase, onClick = { exportLauncher.launch("ballot-backup-${LocalDate.now()}.ballot") })
                    PosterButton("Import", primary = false, enabled = passphrase.length >= 8, onClick = { importLauncher.launch(arrayOf("*/*")) })
                }
                if (backupStatus.isNotBlank()) Small(backupStatus, color = c.fg)
                Kicker("Daily snapshots (${snapshots.size} of ${Snapshots.KEEP})")
                snapshots.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Body(snapshotLabel(f.name), Modifier.weight(1f))
                        LinkButton("Restore", onClick = { restoreFile = f })
                    }
                }
                LinkButton("Take a snapshot now", onClick = { scope.launch { withContext(Dispatchers.IO) { Snapshots.take(ctx, g.db) }; refresh++ } })
            }
        }
        item {
            Collapsible("AI services", "Optional: web research and first drafts, and free-only mode") {
                Small("Ballot tries your main service first, then any other you have connected. Free services cost nothing.")
                ConnectedServices(outsideChange = aiChanged)
                SwitchRow("Free only (never spend money)", s.freeOnly, { v -> upd { it.copy(freeOnly = v) } })
                SwitchRow("Use my other services when one is busy", s.useBackups, { v -> upd { it.copy(useBackups = v) } })
                LinkButton(if (showAdd) "Hide" else "Add a service", onClick = { showAdd = !showAdd })
                if (showAdd) KeyConnector(makeMain = false, onConnected = { aiChanged++; showAdd = false })
                LinkButton(if (showAdvanced) "Hide advanced" else "Advanced", color = c.muted, onClick = { showAdvanced = !showAdvanced })
                if (showAdvanced) {
                    Small("Model for the main service. Leave blank to let Ballot pick.")
                    Field(model, { model = it }, "Model", singleLine = true)
                    if (s.provider == "openai") Field(baseUrl, { baseUrl = it }, "OpenAI-compatible base URL", singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PosterButton("Save", onClick = {
                            val m = model.trim()
                            upd { st ->
                                val models = Providers.parseModels(st.providerModels).toMutableMap()
                                if (m.isBlank()) models.remove(st.provider) else models[st.provider] = m
                                st.copy(providerModels = Providers.encodeModels(models), model = if (m.isNotBlank()) m else st.model, openAiBaseUrl = baseUrl.trim())
                            }
                        })
                        PosterButton("Test", primary = false, onClick = {
                            scope.launch {
                                testStatus = "Testing…"
                                val st = g.settings.get()
                                val p = Providers.byId(st.provider)
                                testStatus = runCatching { clientFor(p.id, st.openAiBaseUrl).test(g.secrets.keyFor(p.id), g.chief.modelFor(st, p)); "Connected." }
                                    .getOrElse { KeySetup.friendly(p, it) }
                            }
                        })
                    }
                    if (testStatus.isNotBlank()) Small(testStatus, color = c.fg)
                }
            }
        }
        item {
            Collapsible("Spend", "What paid AI has cost this month") {
                val capMicros = (s.monthlyCapCents * 10_000L).coerceAtLeast(1)
                Body("This month: ${dollars(spend / 10_000)} of ${dollars(s.monthlyCapCents.toLong())}")
                Small(if (s.freeOnly) "Free only is on: nothing will be spent." else "Free services never count toward the cap; only paid ones do.", color = c.fg)
                Progress(spend.toFloat() / capMicros)
                Field(cap, { cap = it.filter(Char::isDigit) }, "Monthly cap in dollars", singleLine = true, number = true)
                LinkButton("Save cap", onClick = { cap.toIntOrNull()?.let { v -> upd { it.copy(monthlyCapCents = v * 100) } } })
                Small("Estimate uses \$${s.inputMicrosPerMTok / 1_000_000.0}/M input and \$${s.outputMicrosPerMTok / 1_000_000.0}/M output tokens. Your provider's bill is the real number.")
            }
        }
        item {
            Collapsible("Autonomy dial", "What the Chief does without asking") {
                Small("Routine work the Chief does on its own. Switch any to 'ask me first'.")
                RoutineKind.all.forEach { k ->
                    SwitchRow(RoutineKind.label(k), k in s.askFirst, { on -> upd { it.copy(askFirst = if (on) it.askFirst + k else it.askFirst - k) } },
                        note = if (k in s.askFirst) "Asks first" else "Does it")
                }
                Kicker("Always asks first (fixed)")
                Small("Anything sent, posted, filed, signed or spent in your name.", color = c.fg)
                Kicker("Never does (fixed)")
                Small("Invents facts, quotes or supporters; impersonates anyone; hides who is speaking; skips a legal step.", color = c.fg)
            }
        }
        item {
            Collapsible("Facts and safety", "Official sites, cooling period, win margin") {
                Small("Official sources are .gov, .mil and locality .us sites. Add your city or county site if it uses another domain (for example cityofexample.org).")
                s.officialDomains.forEach { d -> Row(verticalAlignment = Alignment.CenterVertically) { Body(d, Modifier.weight(1f)); LinkButton("Remove", color = c.muted, onClick = { upd { it.copy(officialDomains = it.officialDomains - d) } }) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(domain, { domain = it.trim().lowercase() }, "Add official domain", Modifier.weight(1f), singleLine = true)
                    LinkButton("Add", onClick = { if (domain.contains('.')) { val d = domain; upd { it.copy(officialDomains = it.officialDomains + d) }; domain = "" } })
                }
                var cooling by remember(s.coolingMinutes) { mutableStateOf(s.coolingMinutes.toString()) }
                var margin by remember(s.safetyMarginPct) { mutableStateOf(s.safetyMarginPct.toString()) }
                var fresh by remember(s.freshnessDaysRules) { mutableStateOf(s.freshnessDaysRules.toString()) }
                // Each number saves as you type.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(cooling, { t -> cooling = t.filter(Char::isDigit).take(4); cooling.toIntOrNull()?.let { v -> upd { it.copy(coolingMinutes = v) } } },
                        "Cooling min", Modifier.weight(1f), singleLine = true, number = true)
                    Field(margin, { t -> margin = t.filter(Char::isDigit).take(3); margin.toIntOrNull()?.let { v -> upd { it.copy(safetyMarginPct = v) } } },
                        "Win margin %", Modifier.weight(1f), singleLine = true, number = true)
                    Field(fresh, { t -> fresh = t.filter(Char::isDigit).take(2); fresh.toIntOrNull()?.let { v -> upd { it.copy(freshnessDaysRules = v.coerceIn(1, 30)) } } },
                        "Rule check days", Modifier.weight(1f), singleLine = true, number = true)
                }
            }
        }
        item {
            Collapsible("Diagnostics", "Network log, crash reports, version") {
                LinkButton(if (showNet) "Hide network log" else "Network log", onClick = { showNet = !showNet })
                if (showNet) NetLog.snapshot().take(60).forEach { e -> Small("${shortDate(e.at)} ${e.method} ${e.host}${e.path} → ${e.status}", color = c.fg) }
                Small("Network calls go only to your AI provider and to pages being fetched.")
                Kicker("Crash reports (${crashes.size})")
                crashes.take(5).forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Small(f.name, Modifier.weight(1f))
                        LinkButton("Copy", onClick = { copy(ctx, f.readText()) })
                    }
                }
                Small("Paste a crash report into a GitHub issue on the Ballot repo. Nothing is sent automatically.")
                Small("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            }
        }
        item {
            Panel(accent = true) {
                Text("Panic wipe", style = MaterialTheme.typography.titleLarge, color = com.emotionalleaves.ballot.ui.theme.Ink)
                Text("Deletes the case file, snapshots, recordings, vault files, settings and keys from this phone. Cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                PosterButton("Wipe everything", primary = false, onClick = { confirmWipe = 1 })
            }
        }
    }
    restoreFile?.let { f ->
        ConfirmDialog("Restore ${snapshotLabel(f.name)}?", "The app will restart with the case file as it was then. What's here now is kept as an undo copy, so you can switch back.", "Restore",
            onConfirm = { g.restoreAndRestart(f) }, onDismiss = { restoreFile = null })
    }
    if (confirmWipe == 1) ConfirmDialog("Wipe everything?", "Everything Ballot holds on this phone will be deleted.", "Continue",
        onConfirm = { confirmWipe = 2 }, onDismiss = { if (confirmWipe == 1) confirmWipe = 0 })
    if (confirmWipe == 2) ConfirmDialog("Last chance", "This cannot be undone. Export a backup first if you might want it back.", "Wipe now",
        onConfirm = { g.panicWipe() }, onDismiss = { confirmWipe = 0 })
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Ballot crash", text))
}

/** "ballot-2026-11-15.db" reads "2026-11-15"; an undo copy reads "Before your last restore". */
fun snapshotLabel(name: String): String = when {
    name.startsWith("undo-") -> "Before a restore (" + (name.removePrefix("undo-").removeSuffix(".db").toLongOrNull()?.let {
        java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString() } ?: "earlier") + ")"
    else -> name.removePrefix("ballot-").removeSuffix(".db")
}

