package com.emotionalleaves.ballot.agent

import com.emotionalleaves.ballot.core.Approval
import com.emotionalleaves.ballot.core.CampaignMath
import com.emotionalleaves.ballot.core.InjectionDefense
import com.emotionalleaves.ballot.core.SpeechScorer
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.Verification
import com.emotionalleaves.ballot.data.AppSettings
import com.emotionalleaves.ballot.data.CaseFile
import com.emotionalleaves.ballot.data.Confidence
import com.emotionalleaves.ballot.data.Contact
import com.emotionalleaves.ballot.data.ContactRole
import com.emotionalleaves.ballot.data.Deadline
import com.emotionalleaves.ballot.data.Decision
import com.emotionalleaves.ballot.data.Document
import com.emotionalleaves.ballot.data.Event
import com.emotionalleaves.ballot.data.EventType
import com.emotionalleaves.ballot.data.FactStatus
import com.emotionalleaves.ballot.data.Interaction
import com.emotionalleaves.ballot.data.Lesson
import com.emotionalleaves.ballot.data.Money
import com.emotionalleaves.ballot.data.MoneyDirection
import com.emotionalleaves.ballot.data.Office
import com.emotionalleaves.ballot.data.PendingAction
import com.emotionalleaves.ballot.data.PendingKind
import com.emotionalleaves.ballot.data.Position
import com.emotionalleaves.ballot.data.RaceCard
import com.emotionalleaves.ballot.data.RoutineKind
import com.emotionalleaves.ballot.data.Rule
import com.emotionalleaves.ballot.data.SourceRecord
import com.emotionalleaves.ballot.data.Task
import com.emotionalleaves.ballot.data.now
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Calendar, profile, settings, pages and the briefing. Registered by [ToolRegistry]. */
internal fun ToolRegistry.registerAppTools() {
    val S = Schema

    reg(ToolDef("add_event", "Add a public meeting, community event or volunteer shift to the Meetings and events calendar. Never inside work hours.",
        S.obj(listOf("title", "start"), mapOf(
            "title" to S.s("Title"), "type" to S.e("Type", EventType.all), "place" to S.s("Address or room"),
            "start" to S.s("YYYY-MM-DDTHH:MM local time"), "goal" to S.s("One goal for the night"), "agenda_url" to S.s("Agenda link"),
        )), RoutineKind.REMINDER, required = listOf("title", "start")) { a, c ->
        val start = com.emotionalleaves.ballot.core.DateText.dateTime(a.str("start")!!)
            ?: return@ToolDef ToolOutcome("start must be a date and a time, e.g. 2026-11-20T18:30.", isError = true)
        val avail = c.case.availability()
        if (com.emotionalleaves.ballot.core.Scheduling.insideWork(avail, start)) return@ToolDef ToolOutcome("That is inside the user's work hours. Pick another time or tell the user.", isError = true)
        val e = Event(type = a.str("type") ?: EventType.MEETING, title = a.str("title")!!, place = a.str("place") ?: "",
            startAt = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), goal = a.str("goal") ?: "", agendaUrl = a.str("agenda_url") ?: "")
        val id = c.case.db.events().upsert(e)
        ToolOutcome("Event added.", cards = listOf(Card("EVENT", e.title, start.toString().replace('T', ' '), "field", id)))
    })

    reg(ToolDef("record_lesson", "Write down what worked and what did not, after an event or a week.",
        S.obj(listOf("what"), mapOf("what" to S.s("What happened"), "change" to S.s("What to change"), "kind" to S.e("Kind", listOf("EVENT", "WEEK", "MONTH", "QUARTER")))),
        RoutineKind.TRACKER, required = listOf("what")) { a, c ->
        val id = c.case.db.lessons().upsert(Lesson(what = a.str("what")!!, change = a.str("change") ?: "", kind = a.str("kind") ?: "EVENT"))
        ToolOutcome("Lesson saved.", cards = listOf(Card("LESSON", a.str("what")!!.take(80), a.str("change") ?: "", "path", id)))
    })

    reg(ToolDef("record_decision", "Record a decision, its options and the reasoning (for example which office to target, or the go or no-go).",
        S.obj(listOf("question"), mapOf(
            "kind" to S.e("Kind", listOf("GENERAL", "TARGET_OFFICE", "GO_NO_GO", "DEBRIEF")), "question" to S.s("The question"),
            "options" to S.strArr("Options"), "choice" to S.s("The user's choice"), "reasoning" to S.s("Reasoning, with pros and cons"),
        )), RoutineKind.TRACKER, required = listOf("question")) { a, c ->
        val d = Decision(kind = a.str("kind") ?: "GENERAL", question = a.str("question")!!, options = a.arr("options")?.strings()?.joinToString("\n") ?: "",
            choice = a.str("choice") ?: "", reasoning = a.str("reasoning") ?: "", agreed = false)
        val id = c.case.db.decisions().upsert(d)
        ToolOutcome("Decision recorded. The user must mark it agreed themselves.", cards = listOf(Card("DECISION", d.question, d.choice, "path", id)))
    })

    reg(ToolDef("update_profile", "Change a setup answer when the user tells you something changed (work hours, energy, free hours, ZIP, ambition, off limits, employer rules).",
        S.obj(props = mapOf(
            "name" to S.s("Name"), "zip" to S.s("ZIP"), "work_days" to S.s("CSV of 1-7, Monday is 1"), "work_start" to S.s("HH:MM"),
            "work_end" to S.s("HH:MM"), "energy" to S.e("Energy", listOf("LOW", "MEDIUM", "HIGH")), "weekly_hours" to S.i("Free hours a week"),
            "off_limits" to S.s("Off limits"), "employer_rules" to S.s("Employer rules"), "ambition" to S.s("Ambition"), "timeline" to S.s("Timeline"),
            "why" to S.s("Why they want this"), "issues" to S.s("Their issues"), "first_target" to S.s("First target"),
            "serve_done" to S.b("They now hold a role (board, commission, appointment) or finished a project they can point to"),
        )), RoutineKind.TRACKER) { a, c ->
        fun hm(s: String?): Int? = s?.let { com.emotionalleaves.ballot.core.DateText.time(it) }?.let { it.hour * 60 + it.minute }
        c.case.updateProfile { p ->
            p.copy(name = a.str("name") ?: p.name, zip = a.str("zip") ?: p.zip, workDays = a.str("work_days") ?: p.workDays,
                workStartMin = hm(a.str("work_start")) ?: p.workStartMin, workEndMin = hm(a.str("work_end")) ?: p.workEndMin,
                energy = a.str("energy") ?: p.energy, weeklyHours = a.int("weekly_hours") ?: p.weeklyHours,
                offLimits = a.str("off_limits") ?: p.offLimits, employerRules = a.str("employer_rules") ?: p.employerRules,
                ambition = a.str("ambition") ?: p.ambition, timeline = a.str("timeline") ?: p.timeline, why = a.str("why") ?: p.why,
                issues = a.str("issues") ?: p.issues, firstTarget = a.str("first_target") ?: p.firstTarget,
                serveDone = a.bool("serve_done") ?: p.serveDone)
        }
        val next = c.case.advanceIfReady()
        c.case.rescore()
        ToolOutcome("Profile updated. The plan has been rescored." + (next?.let { " The exit test is met: stage ${it.number}, ${it.title}, is now open." } ?: ""))
    })

    // Look-and-feel settings by asking. Nothing here can raise spending, loosen the autonomy dial or turn off the lock:
    // those stay in Settings, behind your own taps.
    reg(ToolDef("change_setting", "Change an everyday setting when the user asks: free_only (on only), use_backups, dark_theme, text_size, brief_time, sleep_start, sleep_end.",
        S.obj(listOf("setting", "value"), mapOf(
            "setting" to S.e("Setting", listOf("free_only", "use_backups", "dark_theme", "text_size", "brief_time", "sleep_start", "sleep_end")),
            "value" to S.s("true/false, a size from 0.85 to 1.5, or a time as HH:MM"))),
        RoutineKind.TRACKER, required = listOf("setting", "value")) { a, c ->
        val store = c.store ?: return@ToolDef ToolOutcome("Settings can't be changed from here. Open Settings.", isError = true)
        val v = a.str("value")!!.trim().lowercase()
        fun flag(): Boolean? = when (v) { "true", "on", "yes" -> true; "false", "off", "no" -> false; else -> null }
        fun hm(): Int? = com.emotionalleaves.ballot.core.DateText.time(v)?.let { it.hour * 60 + it.minute }
        val bad = ToolOutcome("That value doesn't fit ${a.str("setting")}.", isError = true)
        when (a.str("setting")) {
            "free_only" -> { if (flag() != true) return@ToolDef ToolOutcome("Turning free-only off allows spending, so only you can do that in Settings.", isError = true); store.update { it.copy(freeOnly = true) } }
            "use_backups" -> { val f = flag() ?: return@ToolDef bad; store.update { it.copy(useBackups = f) } }
            "dark_theme" -> { val f = flag() ?: return@ToolDef bad; store.update { it.copy(darkTheme = f) } }
            "text_size" -> { val f = v.toFloatOrNull()?.takeIf { it in 0.85f..1.5f } ?: return@ToolDef bad; store.update { it.copy(textScale = f) } }
            "brief_time" -> { val m = hm() ?: return@ToolDef bad; store.update { it.copy(briefMinuteOfDay = m) } }
            "sleep_start" -> { val m = hm() ?: return@ToolDef bad; store.update { it.copy(sleepStartMin = m) } }
            "sleep_end" -> { val m = hm() ?: return@ToolDef bad; store.update { it.copy(sleepEndMin = m) } }
            else -> return@ToolDef bad
        }
        c.case.audit("chief", "Changed a setting", input = "${a.str("setting")} = $v")
        ToolOutcome("Done: ${a.str("setting")} is now $v.")
    })

    // Background jobs by asking: "build my brief now", "check the rule pages", "take a snapshot".
    reg(ToolDef("run_job", "Start a background job now when the user asks: brief (build today's Briefing), recheck_rules (re-read every rule page for changes), snapshot (save a copy of the case file).",
        S.obj(listOf("job"), mapOf("job" to S.e("Job", listOf("brief", "recheck_rules", "snapshot")))),
        RoutineKind.TRACKER, required = listOf("job")) { a, c ->
        val app = c.app ?: return@ToolDef ToolOutcome("Jobs can't start from here.", isError = true)
        when (a.str("job")) {
            "brief" -> { com.emotionalleaves.ballot.work.Workers.briefNow(app); ToolOutcome("Building today's Briefing now; you'll get a notification when it's ready.") }
            "recheck_rules" -> { com.emotionalleaves.ballot.work.Workers.recheckNow(app); ToolOutcome("Re-checking every rule page now. A changed page becomes a task.") }
            "snapshot" -> {
                val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.emotionalleaves.ballot.data.Snapshots.take(app, c.case.db) }
                ToolOutcome(if (f != null) "Snapshot saved (${f.name}). Restore it from Settings, Backup." else "Nothing to snapshot yet.")
            }
            else -> ToolOutcome("Use brief, recheck_rules or snapshot.", isError = true)
        }
    })

    // The same plain-code search as the More page, so the Chief looks things up instead of guessing.
    reg(ToolDef("search_case_file", "Search the user's own case file (people, tasks, drafts, events, deadlines, offices, lessons) by words. Read only. Use before saying you don't know something about them.",
        S.obj(listOf("query"), mapOf("query" to S.s("Words to find, e.g. 'library board'"))), RoutineKind.RESEARCH, required = listOf("query")) { a, c ->
        val hits = c.case.search(a.str("query")!!, max = 12)
        if (hits.isEmpty()) return@ToolDef ToolOutcome("Nothing in the case file matches \"${a.str("query")}\".")
        ToolOutcome(hits.joinToString("\n") { "${it.kind}: ${it.title}" + if (it.line.isNotBlank()) " (${it.line.take(80)})" else "" },
            cards = hits.filter { it.refId > 0 }.take(3).map { Card(if (it.target == "contact") "CONTACT" else "DOCUMENT", it.title, it.kind, it.target, it.refId) })
    })

    // Everything in the app is reachable by asking: "take me to my drafts" gives a card that opens the page.
    reg(ToolDef("open_page", "Give the user a one-tap card that opens a page of the app, when they ask to go somewhere or see something there.",
        S.obj(listOf("page"), mapOf("page" to S.e("Page", PAGES.keys.toList()), "why" to S.s("One line on what they'll find"))),
        RoutineKind.RESEARCH, required = listOf("page")) { a, _ ->
        val page = a.str("page")!!.lowercase()
        val title = PAGES[page] ?: return@ToolDef ToolOutcome("Unknown page. Use one of: ${PAGES.keys.joinToString()}.", isError = true)
        ToolOutcome("Card shown: tap to open $title.", cards = listOf(Card("INFO", "Open $title", a.str("why") ?: "", target = page)))
    })

    reg(ToolDef("save_brief_item", "Save one item to today's Briefing: local news, an agenda or a decision that touches the user's issues, with one optional action.",
        S.obj(listOf("title"), mapOf("title" to S.s("Headline"), "body" to S.s("Two sentences"), "url" to S.s("Source link"),
            "action" to S.s("One optional action, e.g. speak at Tuesday's meeting"), "kind" to S.e("Kind", listOf("NEWS", "AGENDA", "DECISION", "SEAT", "LONG_READ")))),
        RoutineKind.RESEARCH, required = listOf("title")) { a, c ->
        c.case.db.brief().upsert(com.emotionalleaves.ballot.data.BriefItem(dayEpoch = c.case.today(), kind = a.str("kind") ?: "NEWS",
            title = a.str("title")!!, body = a.str("body") ?: "", url = a.str("url") ?: "", action = a.str("action") ?: ""))
        ToolOutcome("Saved to the Briefing.")
    })
}
