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

/** Tasks, the plan and events after they happen. Registered by [ToolRegistry]. */
internal fun ToolRegistry.registerPlanTools() {
    val S = Schema

    reg(ToolDef("create_task", "Create a task. Keep it small, say why it matters and what done looks like. Never inside work hours.",
        S.obj(listOf("title"), mapOf(
            "title" to S.s("Imperative, short"), "why" to S.s("Why it matters"), "steps" to S.strArr("Beginner steps"),
            "done_looks_like" to S.s("Clear finish line"), "good_enough" to S.s("Where polishing stops"),
            "size_minutes" to S.e("5, 15 or 45", listOf("5", "15", "45")), "high_effort" to S.b("High effort"),
            "due_date" to S.s("YYYY-MM-DD"), "human_contact" to S.b("Puts the user in front of people"),
            "legal" to S.b("A legal or filing deadline"), "module" to S.s("compass, ground, field, network, gym, studio, vault, hq"),
        )), RoutineKind.REMINDER, required = listOf("title")) { a, c ->
        val size = a.str("size_minutes")?.toIntOrNull() ?: a.int("size_minutes") ?: 15
        val t = Task(stage = c.case.profile().stage, title = a.str("title")!!, why = a.str("why") ?: "",
            steps = a.arr("steps")?.strings()?.joinToString("\n") ?: "", doneLooksLike = a.str("done_looks_like") ?: "",
            goodEnough = a.str("good_enough") ?: "", sizeMinutes = listOf(5, 15, 45).minByOrNull { kotlin.math.abs(it - size) } ?: 15,
            highEffort = a.bool("high_effort") ?: false, dueEpochDay = a.str("due_date")?.let { ToolRegistry.parseDate(it) } ?: 0,
            humanContact = a.bool("human_contact") ?: false, legal = a.bool("legal") ?: false, module = a.str("module") ?: "", movesExit = true)
        val id = c.case.db.tasks().upsert(t)
        c.case.rescore()
        ToolOutcome("Created task T$id.", cards = listOf(Card("TASK", t.title, "${t.sizeMinutes} min", "home", id)))
    })

    reg(ToolDef("list_plan", "Read the plan: open tasks with ids and scores, upcoming deadlines and upcoming events. Read only.",
        S.obj(props = mapOf("limit" to S.i("How many of each, default 10"))), RoutineKind.RESEARCH) { a, c ->
        val n = (a.int("limit") ?: 10).coerceIn(1, 30)
        val today = c.case.today()
        val tasks = c.case.db.tasks().open().sortedByDescending { it.score }.take(n)
        val deadlines = c.case.db.deadlines().open().filter { it.dueEpochDay >= today - 1 }.take(n)
        val events = c.case.db.events().all().filter { it.startAt >= now() - 3 * 3_600_000L }.sortedBy { it.startAt }.take(n)
        ToolOutcome(buildString {
            appendLine("Open tasks:"); if (tasks.isEmpty()) appendLine("  none")
            tasks.forEach { appendLine("  T${it.id} ${it.title} (score ${it.score}, ${it.sizeMinutes} min${if (it.dueEpochDay > 0) ", due ${LocalDate.ofEpochDay(it.dueEpochDay)}" else ""})") }
            appendLine("Deadlines:"); if (deadlines.isEmpty()) appendLine("  none")
            deadlines.forEach { appendLine("  D${it.id} ${it.title} ${LocalDate.ofEpochDay(it.dueEpochDay)} (${it.confidence})") }
            appendLine("Events:"); if (events.isEmpty()) appendLine("  none")
            events.forEach { e -> appendLine("  E${e.id} ${e.title} ${java.time.Instant.ofEpochMilli(e.startAt).atZone(ZoneId.systemDefault()).toLocalDateTime().toString().replace('T', ' ')}${if (e.attended) " (attended)" else ""}") }
        }.trim())
    })

    reg(ToolDef("complete_task", "Mark a task done when the user says they did it. Use the T id from the brief or list_plan.",
        S.obj(listOf("task_id"), mapOf("task_id" to S.i("Task id"))), RoutineKind.TRACKER, required = listOf("task_id")) { a, c ->
        val t = c.case.db.tasks().get(a.long("task_id")!!) ?: return@ToolDef ToolOutcome("No such task.", isError = true)
        if (t.done) return@ToolDef ToolOutcome("T${t.id} was already done.")
        c.case.completeTask(t)
        val next = c.case.advanceIfReady()
        ToolOutcome("Marked done: ${t.title}." + (next?.let { " The exit test is met: stage ${it.number}, ${it.title}, is now open." } ?: ""),
            cards = listOf(Card("TASK", t.title, "Done", "home", t.id)))
    })

    reg(ToolDef("update_event", "Record what happened at an event: attended, gave a public comment, and a short debrief. Use the E id.",
        S.obj(listOf("event_id"), mapOf("event_id" to S.i("Event id"), "attended" to S.b("Attended"), "gave_comment" to S.b("Spoke at public comment"),
            "debrief" to S.s("What happened, who was there"))), RoutineKind.TRACKER, required = listOf("event_id")) { a, c ->
        val e = c.case.db.events().get(a.long("event_id")!!) ?: return@ToolDef ToolOutcome("No such event.", isError = true)
        val spoke = a.bool("gave_comment") ?: e.gaveComment
        val updated = e.copy(attended = a.bool("attended") ?: (e.attended || spoke), gaveComment = spoke,
            debrief = a.str("debrief")?.let { d -> if (e.debrief.isBlank()) d else e.debrief + "\n" + d } ?: e.debrief, updatedAt = now())
        c.case.db.events().upsert(updated)
        val next = c.case.advanceIfReady()
        ToolOutcome("Event updated: ${e.title}${if (updated.attended) ", attended" else ""}${if (updated.gaveComment) ", spoke" else ""}." +
            (next?.let { " Exit test met: stage ${it.number} is open." } ?: ""), cards = listOf(Card("EVENT", e.title, "Updated", "field", e.id)))
    })

    reg(ToolDef("reschedule_plan", "Re-flow the plan after life changes: push overdue non-legal tasks forward, reseed the stage playbook, rescore.",
        S.obj(props = mapOf("reason" to S.s("What changed"), "shrink_week" to S.b("Shrink the week to its single most important item"))),
        RoutineKind.REMINDER) { a, c ->
        val today = c.case.today()
        val open = c.case.db.tasks().open()
        var moved = 0
        open.forEach { t ->
            if (!t.legal && t.dueEpochDay in 1 until today) { c.case.db.tasks().upsert(t.copy(dueEpochDay = today + 2 + (moved % 5), updatedAt = now())); moved++ }
        }
        c.case.seedPlaybook(Stage.of(c.case.profile().stage))
        val ranked = c.case.rescore()
        if (a.bool("shrink_week") == true) {
            val keep = ranked.firstOrNull { it.blocked == null }?.id
            c.case.db.tasks().open().filter { it.id != keep && !it.legal && it.dueEpochDay in today..today + 7 }.forEach {
                c.case.db.tasks().upsert(it.copy(dueEpochDay = today + 8, updatedAt = now()))
            }
        }
        ToolOutcome("Plan re-flowed: $moved overdue task(s) moved, ${ranked.size} open task(s) rescored.")
    })
}
