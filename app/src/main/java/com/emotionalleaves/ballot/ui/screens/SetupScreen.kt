package com.emotionalleaves.ballot.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.StageRules
import com.emotionalleaves.ballot.data.Profile
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Gap
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Progress
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.ThickRule
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val stepTitles = listOf(
    "Connect the brain", "Where you live", "Your life as it is", "Why you want this",
    "Your starting point", "Your job's rules", "Ambition and timeline", "Your first week",
)

/** About 15 minutes, one question per screen, skippable and resumable. "I don't know" is valid everywhere. */
@Composable
fun SetupScreen() {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    var p by remember { mutableStateOf<Profile?>(null) }
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { val pr = g.case.profile(); p = pr; step = pr.setupStep.coerceIn(0, 7) }
    val profile = p ?: return

    fun save(next: Profile, toStep: Int) {
        p = next.copy(setupStep = toStep)
        scope.launch { g.case.updateProfile { p!! } }
        step = toStep
    }

    val c = LocalBallotColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Ballot", style = MaterialTheme.typography.headlineLarge, color = c.signal)
        Kicker("Setup · step ${step + 1} of 8")
        Progress((step + 1) / 8f)
        Text(stepTitles[step], style = MaterialTheme.typography.headlineLarge, color = c.fg)
        ThickRule()
        when (step) {
            0 -> { BrainStep(onNext = { save(profile, 1) }); RestoreFromBackup() }
            1 -> WhereStep(profile, onNext = { save(it, 2) })
            2 -> LifeStep(profile, onNext = { save(it, 3) })
            3 -> WhyStep(profile, onNext = { save(it, 4) })
            4 -> StartStep(profile, onNext = { save(it, 5) })
            5 -> JobStep(profile, onNext = { save(it, 6) })
            6 -> AmbitionStep(profile, onNext = { save(it, 7) })
            7 -> FirstWeekStep(profile, onFinish = {
                scope.launch {
                    g.case.updateProfile { it.copy(setupDone = true, setupStep = 8, stage = 1) }
                    g.case.seedPlaybook(Stage.REASON)
                    g.case.rescore()
                    g.case.audit("you", "Finished setup")
                }
            })
        }
        if (step > 0) LinkButton("Back", onClick = { step -= 1 })
        Small("Every answer can be changed later by telling the Chief in plain words. The plan rebuilds itself.")
    }
}

@Composable
private fun NextRow(onNext: () -> Unit, label: String = "Next", onSkip: (() -> Unit)? = onNext) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PosterButton(label, onClick = onNext)
        if (onSkip != null) PosterButton("Skip", onClick = onSkip, primary = false)
    }
}

/** Moving phones or reinstalling: bring the whole case file back without walking through setup. */
@Composable
private fun RestoreFromBackup() {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val c = LocalBallotColors.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                val bytes = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } } ?: error("Could not read the file.")
                com.emotionalleaves.ballot.data.Backup.import(g.db, bytes, passphrase.toCharArray(), g.settings)
                "Backup restored."
            }.getOrElse { it.message ?: "Import failed." }
        }
    }
    Gap(8.dp)
    LinkButton(if (open) "Hide" else "I have a Ballot backup file", onClick = { open = !open })
    if (open) {
        Small("Your case file comes back as it was when you exported it. Connect your AI service again afterwards: keys are never put in backups.")
        Field(passphrase, { passphrase = it }, "Backup passphrase", singleLine = true, secret = true)
        PosterButton("Choose the backup file", enabled = passphrase.length >= 8, onClick = { picker.launch(arrayOf("*/*")) })
        if (status.isNotBlank()) Small(status, color = c.fg)
    }
}

@Composable
private fun BrainStep(onNext: () -> Unit) {
    var connected by remember { mutableStateOf(false) }
    var wantsAi by remember { mutableStateOf(false) }
    Body("Ballot works without AI. Your plan, the town quiz, draft templates, deadlines and reminders all run on this phone.")
    // The next step is one tap either way: carry on, or open the AI setup.
    if (connected) PosterButton("Continue", modifier = Modifier.fillMaxWidth(), onClick = onNext)
    else PosterButton("Continue without AI", modifier = Modifier.fillMaxWidth(), onClick = onNext)
    if (!connected) LinkButton(if (wantsAi) "Hide AI setup" else "Add a free AI for research and drafts (optional)", onClick = { wantsAi = !wantsAi })
    if (wantsAi || connected) {
        Small("It adds web research (finding your offices, rules and meetings) and first drafts. Several are free and need no card: Google Gemini only needs a Google account and takes about two minutes.")
        KeyConnector(makeMain = true, onConnected = { connected = true })
        Small("You can add a second free service later in Settings. If one is busy, Ballot switches to the other on its own.")
    }
}

@Composable
private fun WhereStep(p: Profile, onNext: (Profile) -> Unit) {
    var zip by remember { mutableStateOf(p.zip) }
    var address by remember { mutableStateOf(p.address) }
    Body("Your ZIP code, or your full address if you want the most exact list of offices. It stays on this phone.")
    Field(zip, { zip = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, "ZIP code", singleLine = true, number = true)
    Field(address, { address = it }, "Street address (optional)", singleLine = true)
    Small("The Chief uses this to find every office that represents you: city, county, school district, state and federal.")
    NextRow(onNext = { onNext(p.copy(zip = zip, address = address)) }, onSkip = { onNext(p) })
}

@Composable
private fun LifeStep(p: Profile, onNext: (Profile) -> Unit) {
    var days by remember { mutableStateOf(p.workDays.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()) }
    var start by remember { mutableStateOf(hm(p.workStartMin)) }
    var end by remember { mutableStateOf(hm(p.workEndMin)) }
    var evenings by remember { mutableStateOf(p.freeEvenings) }
    var energy by remember { mutableStateOf(p.energy) }
    var hours by remember { mutableStateOf(p.weeklyHours.toString()) }
    var money by remember { mutableStateOf(p.moneyAtRisk) }
    var off by remember { mutableStateOf(p.offLimits) }
    Body("The plan is built around your real life. Nothing is ever scheduled inside your work hours.")
    Kicker("Work days")
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { d ->
            ChipRow(listOf(d), if (d in days) d else null, { names[it - 1] }) { days = if (it in days) days - it else days + it }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(start, { start = it }, "Work starts, e.g. 7:00am", Modifier.weight(1f), singleLine = true)
        Field(end, { end = it }, "Work ends, e.g. 3:30pm", Modifier.weight(1f), singleLine = true)
    }
    Field(evenings, { evenings = it }, "Free evenings and weekend times", placeholder = "e.g. Tue and Thu evenings, Saturday mornings")
    Field(hours, { hours = it.filter { c -> c.isDigit() }.take(2) }, "Hours a week you can truly give", singleLine = true, number = true)
    Kicker("Energy after work")
    ChipRow(listOf("LOW", "MEDIUM", "HIGH"), energy, { it.lowercase() }) { energy = it }
    Field(money, { money = it }, "Money you could risk on this, if any", singleLine = true, placeholder = "\"none\" is a normal answer")
    Field(off, { off = it }, "Anything off limits", placeholder = "e.g. Sundays are family days")
    NextRow(onNext = {
        onNext(p.copy(workDays = days.sorted().joinToString(","), workStartMin = parseHm(start) ?: p.workStartMin,
            workEndMin = parseHm(end) ?: p.workEndMin, freeEvenings = evenings, energy = energy,
            weeklyHours = hours.toIntOrNull() ?: p.weeklyHours, moneyAtRisk = money, offLimits = off))
    }, onSkip = { onNext(p) })
}

@Composable
private fun WhyStep(p: Profile, onNext: (Profile) -> Unit) {
    var angry by remember { mutableStateOf("") }
    var fix by remember { mutableStateOf(p.issues) }
    var help by remember { mutableStateOf("") }
    Body("A short interview. Rough answers are fine. Honest beats polished.")
    Field(angry, { angry = it }, "What makes you angry about how things are run near you?", minLines = 2)
    Field(fix, { fix = it }, "What would you fix first? (up to three things)", minLines = 2)
    Field(help, { help = it }, "Who do you want to help?", minLines = 2)
    NextRow(onNext = {
        val why = listOf("Angry about: $angry".takeIf { angry.isNotBlank() }, "Wants to help: $help".takeIf { help.isNotBlank() }, p.why.takeIf { it.isNotBlank() && angry.isBlank() })
            .filterNotNull().joinToString("\n")
        onNext(p.copy(why = why, issues = fix))
    }, onSkip = { onNext(p) })
}

@Composable
private fun StartStep(p: Profile, onNext: (Profile) -> Unit) {
    var comfort by remember { mutableIntStateOf(p.speakingComfort) }
    var people by remember { mutableStateOf(p.peopleKnown) }
    var groups by remember { mutableStateOf(p.groups) }
    var vol by remember { mutableStateOf(p.volunteering) }
    Body("Where you are starting from. Zero is a normal answer.")
    Kicker("Comfort speaking in public")
    ChipRow((1..5).toList(), comfort, { listOf("Terrified", "Nervous", "Okay", "Comfortable", "Natural")[it - 1] }) { comfort = it }
    Field(people, { people = it }, "People you know locally (roughly how many, and who)", minLines = 2)
    Field(groups, { groups = it }, "Groups you belong to (work, faith, sports, online, none)")
    Field(vol, { vol = it }, "Past volunteering, if any")
    NextRow(onNext = { onNext(p.copy(speakingComfort = comfort, peopleKnown = people, groups = groups, volunteering = vol)) }, onSkip = { onNext(p) })
}

@Composable
private fun JobStep(p: Profile, onNext: (Profile) -> Unit) {
    var type by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf(p.employerRules) }
    Body("Some employers limit political activity, especially public employers. The Chief will not guess the rules. It gives you the questions to ask HR.")
    ChipRow(listOf("PRIVATE", "PUBLIC", "UNIVERSITY", "CONTRACTOR", "UNSURE"), type.ifBlank { null }, {
        when (it) { "PUBLIC" -> "Government"; "UNIVERSITY" -> "University or school"; "CONTRACTOR" -> "Gov. contractor"; "UNSURE" -> "I don't know"; else -> "Private" }
    }) { type = it }
    Panel {
        Kicker("Ask HR")
        listOf(
            "Do any rules limit employees running for or holding local office?",
            "Can I campaign, fundraise or wear campaign items at or near work?",
            "Do I need to tell you or get approval before I run or accept an appointment?",
            "Can I use work email, phones or time for any of this? (Assume no.)",
        ).forEach { Small("• $it", color = LocalBallotColors.current.fg) }
        if (type == "PUBLIC" || type == "UNIVERSITY" || type == "CONTRACTOR")
            Small("Public employers and some funded jobs often have extra rules on political activity. Ask HR in writing before you file.")
    }
    Field(notes, { notes = it }, "What HR told you, or 'not asked yet'")
    NextRow(onNext = { onNext(p.copy(employerRules = listOf(type, notes).filter { it.isNotBlank() }.joinToString(": "))) }, onSkip = { onNext(p) })
}

@Composable
private fun AmbitionStep(p: Profile, onNext: (Profile) -> Unit) {
    var how by remember { mutableStateOf(p.ambition) }
    var soon by remember { mutableStateOf(p.timeline) }
    Body("How far do you want to go, and how soon?")
    ChipRow(listOf("Local office", "County", "State", "Further", "Not sure"), how.ifBlank { null }, { it }) { how = it }
    ChipRow(listOf("Within a year", "1 to 2 years", "2 to 4 years", "No rush"), soon.ifBlank { null }, { it }) { soon = it }
    Panel(accent = true) {
        Text("A realistic first target", style = MaterialTheme.typography.labelLarge, color = com.emotionalleaves.ballot.ui.theme.Ink)
        Text("Most first offices are local and often part time: school board, city council, township or county seats. " +
            "Aim first for one you can win while keeping your job. The Chief will rank the real options near you once it knows the ground.",
            style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
    }
    NextRow(onNext = { onNext(p.copy(ambition = how, timeline = soon, firstTarget = "A local, part-time seat you can win while working")) }, onSkip = { onNext(p) })
}

@Composable
private fun FirstWeekStep(p: Profile, onFinish: () -> Unit) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var briefTime by remember { mutableStateOf("16:15") }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }
    Body("Three small tasks to start. The first one is on your Home screen when you finish.")
    StageRules.playbook(Stage.REASON).take(3).forEachIndexed { i, t ->
        Panel {
            Kicker("Task ${i + 1} · ${t.sizeMinutes} min")
            Text(t.title, style = MaterialTheme.typography.titleMedium, color = LocalBallotColors.current.fg)
            Small(t.why)
        }
    }
    Field(briefTime, { briefTime = it }, "Daily brief after work at, e.g. 4:15pm", singleLine = true)
    Small("Reminders are few, specific and tied to real deadlines. Quiet hours cover work and sleep.")
    Gap()
    PosterButton("Start", modifier = Modifier.fillMaxWidth(), onClick = {
        scope.launch {
            parseHm(briefTime)?.let { m -> g.settings.update { it.copy(briefMinuteOfDay = m) } }
            if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinish()
        }
    })
}
