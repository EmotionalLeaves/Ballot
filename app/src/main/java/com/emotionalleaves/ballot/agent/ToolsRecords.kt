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

/** Deadlines, money, drafts, practice and the Vault. Registered by [ToolRegistry]. */
internal fun ToolRegistry.registerRecordsTools() {
    val S = Schema

    reg(ToolDef("add_deadline",
        "Add a legal or filing deadline. Requires a source_id from a fetched official page with label VERIFIED or LIKELY. Unverified sources cannot drive a deadline.",
        S.obj(listOf("title", "due_date", "source_id"), mapOf(
            "title" to S.s("What is due"), "due_date" to S.s("YYYY-MM-DD"), "source_id" to S.i("Source record id"),
            "legal" to S.b("Legal or filing"), "lead_alerts" to S.s("Days before to alert, e.g. 14,7,2"),
            "rule_summary" to S.s("The rule in plain words"), "jurisdiction" to S.s("State, county or city"),
            "office_phone" to S.s("Election office phone"),
            "limit_kind" to S.e("If this rule sets a money limit", listOf("PER_DONOR", "REPORT_TRIGGER")),
            "limit_dollars" to S.n("The limit in dollars"),
        )), RoutineKind.REMINDER, voiceConfirm = true, required = listOf("title", "due_date", "source_id")) { a, c ->
        val src = a.long("source_id")?.let { c.case.db.sources().get(it) }
            ?: return@ToolDef ToolOutcome("No such source. Fetch the official page first.", isError = true)
        if (!Verification.canDriveDeadline(src.label)) return@ToolDef ToolOutcome(
            "Source S${src.id} is ${src.label}. Unverified sources cannot drive a deadline. Find the official page or tell the user to confirm with the election office.", isError = true)
        val due = ToolRegistry.parseDate(a.str("due_date")!!) ?: return@ToolDef ToolOutcome("due_date must be YYYY-MM-DD.", isError = true)
        val ruleId = a.str("rule_summary")?.let { summary ->
            c.case.db.rules().upsert(Rule(jurisdiction = a.str("jurisdiction") ?: "", summary = summary, officialUrl = src.url,
                officePhone = a.str("office_phone") ?: "", lastChecked = src.fetchedAt, status = src.label, sourceId = src.id,
                limitKind = a.str("limit_kind") ?: "", limitCents = a.dbl("limit_dollars")?.let { (it * 100).toLong() } ?: 0,
                source = "S${src.id}", confidence = src.label))
        } ?: 0
        val d = Deadline(ruleId = ruleId, title = a.str("title")!!, dueEpochDay = due, legal = a.bool("legal") ?: true,
            leadAlerts = a.str("lead_alerts") ?: "14,7,2", officialUrl = src.url, sourceId = src.id, source = "S${src.id}", confidence = src.label)
        val id = c.case.db.deadlines().upsert(d)
        c.case.ensureDeadlineDrafts()
        ToolOutcome("Deadline D$id saved for ${LocalDate.ofEpochDay(due)} (${src.label}" +
            if (src.label != Verification.VERIFIED) ", confirm with the election office)" else ")", sourceIds = listOf(src.id),
            cards = listOf(Card("DEADLINE", d.title, "${LocalDate.ofEpochDay(due)} · ${src.label}", "vault", id)))
    })

    reg(ToolDef("log_money", "Log a dollar in or out, including the user's own money, with the fields reports require. Warns before limits.",
        S.obj(listOf("direction", "amount_dollars"), mapOf(
            "direction" to S.e("In, out or in-kind", listOf(MoneyDirection.IN, MoneyDirection.OUT, MoneyDirection.IN_KIND)),
            "amount_dollars" to S.n("Amount"), "person" to S.s("Donor or payee"), "date" to S.s("YYYY-MM-DD"),
            "category" to S.s("e.g. printing, filing fee, contribution"), "address" to S.s("Donor address"),
            "employer" to S.s("Donor employer"), "occupation" to S.s("Donor occupation"), "memo" to S.s("Memo"),
            "own_funds" to S.b("The user's own money"),
        )), RoutineKind.TRACKER, voiceConfirm = true, required = listOf("direction", "amount_dollars")) { a, c ->
        // Models write "in", "In" or "in-kind": store the one spelling every report and total looks for.
        val direction = com.emotionalleaves.ballot.core.Ledger.parseDirection(a.str("direction")!!)
            ?: return@ToolDef ToolOutcome("direction must be IN, OUT or IN_KIND.", isError = true)
        val cents = Math.round((a.dbl("amount_dollars") ?: 0.0) * 100)
        if (cents <= 0) return@ToolDef ToolOutcome("Amount must be above zero.", isError = true)
        val at = a.str("date")?.let { ToolRegistry.parseDate(it) }?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: now()
        val m = Money(direction = direction, amountCents = cents, person = a.str("person") ?: "", at = at,
            category = a.str("category") ?: "", address = a.str("address") ?: "", employer = a.str("employer") ?: "",
            occupation = a.str("occupation") ?: "", memo = a.str("memo") ?: "", ownFunds = a.bool("own_funds") ?: false)
        c.case.db.money().upsert(m)
        ToolOutcome("Logged. " + MoneyChecks.warnings(c.case).joinToString(" "))
    })

    reg(ToolDef("draft_document",
        "Save a draft (public comment, speech, statement, email, door script, palm card, application, letter). List every factual claim with its source ids. " +
            "Unsourced claims are highlighted and block approval.",
        S.obj(listOf("type", "title", "body"), mapOf(
            "type" to S.e("Type", listOf("PUBLIC_COMMENT", "STUMP_SPEECH", "PRESS_STATEMENT", "EMAIL", "DOOR_SCRIPT", "PALM_CARD", "APPLICATION", "LETTER", "STORY", "PLATFORM", "QUESTIONNAIRE", "CRISIS_RESPONSE", "OTHER")),
            "title" to S.s("Title"), "body" to S.s("The full draft in the user's voice"),
            "claims" to S.arrOf("Factual claims in the draft", S.obj(listOf("text"), mapOf("text" to S.s("The claim"), "source_ids" to S.intArr("Source ids")))),
            "crisis" to S.b("Written during a crisis: hold for the cooling period"),
            "document_id" to S.i("Update this existing document instead of creating a new one (keeps the previous version)"),
        )), RoutineKind.DRAFT, required = listOf("type", "title", "body")) { a, c ->
        val claims = a.arr("claims")?.mapNotNull { it as? JsonObject }?.map { o ->
            ClaimRef(o.str("text") ?: "", o.arr("source_ids")?.longs() ?: emptyList())
        } ?: emptyList()
        val labelled = FactCheck.label(claims, c.case, c.settings.freshnessDaysGeneral)
        val blocked = labelled.any { it.label == Verification.UNVERIFIED }
        val crisis = a.bool("crisis") == true || a.str("type") == "CRISIS_RESPONSE"
        val status = if (claims.isEmpty()) FactStatus.UNCHECKED else if (blocked) FactStatus.BLOCKED else FactStatus.PASSED
        val existing = a.long("document_id")?.let { c.case.db.documents().get(it) }
        val d = existing?.copy(title = a.str("title")!!, body = a.str("body")!!, version = existing.version + 1, previousBody = existing.body,
            claims = BallotJson.encodeToString(labelled), factCheckStatus = status, approved = false, updatedAt = now(),
            holdUntil = if (crisis) now() + c.settings.coolingMinutes * 60_000L else existing.holdUntil)
            ?: Document(type = a.str("type")!!, title = a.str("title")!!, body = a.str("body")!!,
                claims = BallotJson.encodeToString(labelled), factCheckStatus = status,
                holdUntil = if (crisis) now() + c.settings.coolingMinutes * 60_000L else 0)
        val id = c.case.db.documents().upsert(d).let { if (it == -1L) d.id else it }
        ToolOutcome("Draft saved as document ${id}. Fact check: ${d.factCheckStatus}" +
            (if (blocked) ". Unverified claims are highlighted and must be sourced or cut before approval." else "") +
            (if (crisis) ". Held for the ${c.settings.coolingMinutes}-minute cooling period." else ""),
            cards = listOf(Card("DOCUMENT", d.title, "${d.type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }} · " + when (d.factCheckStatus) {
                FactStatus.BLOCKED -> "some claims need a source"; FactStatus.PASSED -> "facts checked"; else -> "no facts to check"
            }, "document", id)))
    })

    reg(ToolDef("score_speech", "Score a speaking rep from its transcript in plain code: clarity, pace, filler words and length. The rep is saved and counts toward the weekly score.",
        S.obj(listOf("transcript", "duration_sec"), mapOf("transcript" to S.s("What was said"), "duration_sec" to S.i("Seconds"), "target_sec" to S.i("Target seconds"),
            "drill" to S.e("Which drill, if any", listOf("INTRO", "STORY", "COMMENT", "OTHER")))),
        RoutineKind.TRACKER, required = listOf("transcript", "duration_sec")) { a, c ->
        val target = a.int("target_sec") ?: 60
        val transcript = a.str("transcript")!!
        val secs = a.int("duration_sec") ?: 0
        val m = SpeechScorer.score(transcript, secs, target)
        val drill = a.str("drill")?.uppercase() ?: "OTHER"
        // A rep done with the Chief is a rep: saved like one from Practice, and a clear story counts toward stage 1.
        c.case.db.speech().upsert(com.emotionalleaves.ballot.data.SpeechRep(kind = drill, prompt = "With the Chief", transcript = transcript, durationSec = secs,
            wordsPerMinute = m.wpm, fillerCount = m.fillers, clarity = m.clarity, pace = m.pace, lengthScore = m.lengthScore, total = m.total,
            feedback = SpeechScorer.plainFeedback(m, target).joinToString(" ")))
        if (drill == "STORY" && m.total >= 70) c.case.updateProfile { it.copy(storyRatedClear = true) }
        val next = c.case.advanceIfReady()
        ToolOutcome("Score ${m.total}/100. Clarity ${m.clarity}, pace ${m.pace} (${m.wpm} wpm), length ${m.lengthScore}, fillers ${m.fillers}. " +
            SpeechScorer.plainFeedback(m, target).joinToString(" ") + (next?.let { " The exit test is met: stage ${it.number}, ${it.title}, is now open." } ?: ""))
    })

    reg(ToolDef("start_mock_room", "Open a mock room in Practice where the Chief plays the other side.",
        S.obj(listOf("room"), mapOf("room" to S.e("Room", MockRooms.ids), "difficulty" to S.e("Difficulty", listOf("EASY", "MEDIUM", "HARD")))),
        RoutineKind.TRACKER, required = listOf("room")) { a, _ ->
        val room = MockRooms.byId(a.str("room") ?: "") ?: return@ToolDef ToolOutcome("Unknown room.", isError = true)
        ToolOutcome("Mock room ready: ${room.title}. The user can open it from Practice.", cards = listOf(Card("INFO", room.title, room.setup, "gym", 0)))
    })

    reg(ToolDef("update_platform",
        "Save or update the user's own position on an issue. Never pick positions for them: record theirs. Returns any earlier public statements for a consistency check.",
        S.obj(listOf("issue"), mapOf(
            "issue" to S.s("Issue"), "stance" to S.s("The user's stance in their words"), "reasoning" to S.s("Their reasoning"),
            "counter_argument" to S.s("The strongest counter-argument"), "facts" to S.s("Facts with [S#] citations"),
            "source_ids" to S.intArr("Source ids"), "stated_publicly" to S.b("Said in public now"),
        )), RoutineKind.TRACKER, required = listOf("issue")) { a, c ->
        val issue = a.str("issue")!!.trim()
        val existing = c.case.db.positions().byIssue(issue)
        val p = (existing ?: Position(issue = issue)).copy(
            stance = a.str("stance") ?: existing?.stance ?: "", reasoning = a.str("reasoning") ?: existing?.reasoning ?: "",
            counterArgument = a.str("counter_argument") ?: existing?.counterArgument ?: "", facts = a.str("facts") ?: existing?.facts ?: "",
            sourceIds = a.arr("source_ids")?.longs()?.joinToString(",") { "S$it" } ?: existing?.sourceIds ?: "",
            statedPubliclyAt = if (a.bool("stated_publicly") == true) now() else existing?.statedPubliclyAt ?: 0, updatedAt = now())
        val id = c.case.db.positions().upsert(p).let { if (it == -1L) p.id else it }
        val prior = existing?.takeIf { it.statedPubliclyAt > 0 && it.stance.isNotBlank() && it.stance != p.stance }
        ToolOutcome("Position POS$id saved." + (prior?.let { " Earlier public stance on record: \"${it.stance.take(200)}\". Check the new one is consistent and tell the user if it is not." } ?: ""),
            cards = listOf(Card("POSITION", issue, p.stance.take(100), "compass", id)))
    })

    reg(ToolDef("save_to_vault", "Save a note, form or record into the encrypted Vault.",
        S.obj(listOf("title", "body"), mapOf("title" to S.s("Title"), "body" to S.s("Content"), "type" to S.s("FORM, RECEIPT, FILING, NOTE"))),
        RoutineKind.TRACKER, required = listOf("title", "body")) { a, c ->
        val id = c.case.db.documents().upsert(Document(type = a.str("type") ?: "NOTE", title = a.str("title")!!, body = a.str("body")!!, inVault = true, aiDrafted = true))
        ToolOutcome("Saved to the Vault.", cards = listOf(Card("DOCUMENT", a.str("title")!!, "Vault", "document", id)))
    })
}
