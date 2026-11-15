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

@Serializable
data class Card(val type: String, val title: String, val body: String = "", val target: String = "", val refId: Long = 0)

object CardTypes {
    val valid = setOf("TASK", "CONTACT", "DEADLINE", "DOCUMENT", "OFFICE", "RACE", "APPROVAL", "SOURCE", "EVENT", "POSITION", "MONEY", "LESSON", "DECISION", "INFO")
}

fun validCards(cards: List<Card>): List<Card> = cards.filter { it.type in CardTypes.valid && it.title.isNotBlank() && it.title.length <= 200 }

data class ToolCtx(
    val case: CaseFile,
    val settings: AppSettings,
    val fromVoice: Boolean = false,
    val approved: Boolean = false,
    /** Lets change_setting save; null where settings can't change. */
    val store: com.emotionalleaves.ballot.data.SettingsStore? = null,
    /** Lets run_job start background work; null where it can't. */
    val app: android.content.Context? = null,
)

data class ToolOutcome(
    val text: String,
    val isError: Boolean = false,
    val sourceIds: List<Long> = emptyList(),
    val cards: List<Card> = emptyList(),
)

class ToolDef(
    val name: String,
    val description: String,
    val schema: JsonObject,
    /** RESEARCH, DRAFT, REMINDER, TRACKER, FORM_PREP, or SEND/POST/FILE/SIGN/SPEND. */
    val kind: String,
    /** Changes money, deadlines or contacts: confirm first when the input came from voice. */
    val voiceConfirm: Boolean = false,
    val required: List<String> = emptyList(),
    val handler: suspend (JsonObject, ToolCtx) -> ToolOutcome,
)

/** Pages the open_page tool can point at; the keys are the card targets the app knows how to open. */
val PAGES: Map<String, String> = linkedMapOf(
    "home" to "Today", "path" to "Plan", "network" to "People", "compass" to "Who you are", "ground" to "Offices and races",
    "field" to "Meetings and events", "briefing" to "the local briefing", "gym" to "Practice", "studio" to "Drafts",
    "approvals" to "Waiting for your OK", "vault" to "Vault", "casefile" to "Case file", "review" to "Weekly review",
    "hq" to "Campaign HQ", "settings" to "Settings", "quiz" to "the town quiz", "gonogo" to "the run-or-wait check",
)

/** The typed tools. Each one is a real function in the app. */
class ToolRegistry {
    private val defs = LinkedHashMap<String, ToolDef>()

    fun all(): List<ToolDef> = defs.values.toList()
    fun get(name: String): ToolDef? = defs[name]
    fun specs(): List<ToolSpec> = defs.values.map { ToolSpec(it.name, it.description, it.schema) }

    internal fun reg(d: ToolDef) { defs[d.name] = d }

    /** Validates required fields, applies the approval levels, then runs the handler. */
    suspend fun execute(name: String, input: JsonObject, ctx: ToolCtx): ToolOutcome {
        val d = defs[name] ?: return ToolOutcome("Unknown tool $name", isError = true)
        val missing = d.required.filter { val v = input[it]; v == null || v is kotlinx.serialization.json.JsonNull || input.str(it)?.isBlank() == true }
        if (missing.isNotEmpty()) return ToolOutcome("Invalid input: missing ${missing.joinToString()}. Call again with those fields.", isError = true)
        if (!ctx.approved) {
            val level = Approval.level(d.kind, ctx.settings.askFirst)
            val needsVoiceConfirm = ctx.fromVoice && d.voiceConfirm
            if (level == Approval.Level.ASKS_FIRST || needsVoiceConfirm) {
                val kind = when {
                    d.kind in Approval.alwaysAsk -> d.kind
                    needsVoiceConfirm -> PendingKind.CONFIRM_VOICE
                    else -> PendingKind.ROUTINE
                }
                val summary = summarize(name, input)
                val id = ctx.case.db.pending().upsert(
                    PendingAction(kind = kind, tool = name, summary = summary, payload = input.toString(),
                        draftText = input.str("message") ?: input.str("body") ?: "", recipient = input.str("contact") ?: ""),
                )
                ctx.case.audit("chief", "Queued for approval", tool = name, input = input.toString(), outcome = kind)
                return ToolOutcome(
                    "Waiting for the user's approval (${kind.lowercase()}). Tell them it is ready to approve. Do not claim it was done.",
                    cards = listOf(Card("APPROVAL", summary, "Tap to approve or decline.", "approvals", id)),
                )
            }
        }
        return try {
            val out = d.handler(input, ctx)
            ctx.case.audit(if (ctx.approved) "you" else "chief", if (out.isError) "Tool error" else "Tool call", tool = name,
                input = input.toString(), sources = out.sourceIds.joinToString { "S$it" }, approvedBy = if (ctx.approved) "you" else "",
                outcome = out.text.take(500))
            out
        } catch (e: Exception) {
            ctx.case.audit("chief", "Tool failed", tool = name, input = input.toString(), outcome = e.message ?: "error")
            ToolOutcome("Tool failed: ${e.message ?: e::class.simpleName}", isError = true)
        }
    }

    fun summarize(name: String, input: JsonObject): String = when (name) {
        "queue_follow_up" -> "Message to ${input.str("contact") ?: "a contact"}: ${(input.str("message") ?: "").take(80)}"
        "add_contact" -> "Add contact ${input.str("name") ?: ""}"
        "log_money" -> "Log ${input.str("direction") ?: ""} ${input.dbl("amount_dollars")?.let { "$" + "%.2f".format(it) } ?: ""} ${input.str("person") ?: ""}".trim()
        "add_deadline" -> "Deadline: ${input.str("title") ?: ""} on ${input.str("due_date") ?: ""}"
        "draft_document" -> "Draft: ${input.str("title") ?: ""}"
        "create_task" -> "Task: ${input.str("title") ?: ""}"
        else -> "${name.replace('_', ' ')}: ${input.toString().take(80)}"
    }

    init {
        // Each area keeps its tools in its own file: ToolsResearch, ToolsPeople, ToolsPlan, ToolsRecords, ToolsApp.
        registerResearchTools()
        registerPeopleTools()
        registerPlanTools()
        registerRecordsTools()
        registerAppTools()
    }

    companion object {
        fun parseDate(s: String): Long? = com.emotionalleaves.ballot.core.DateText.day(s)?.toEpochDay()
    }
}

@Serializable
data class ClaimRef(val text: String, val sourceIds: List<Long> = emptyList(), val label: String = Confidence.UNVERIFIED)

object FactCheck {
    /** Labels each claim from its sources. A claim with no official, fresh source stays UNVERIFIED. */
    suspend fun label(claims: List<ClaimRef>, case: CaseFile, freshnessDays: Int = 30): List<ClaimRef> {
        return claims.map { c ->
            val sources = case.db.sources().getMany(c.sourceIds)
            val best = sources.map { s ->
                val fresh = Verification.isFresh(s.fetchedAt, now(), freshnessDays)
                val matches = Verification.crossCheck(c.text, s.excerpt) || s.excerpt.isEmpty()
                if (!matches) Verification.UNVERIFIED else Verification.label(s.official, fresh, s.crossChecked)
            }
            val label = when {
                best.contains(Verification.VERIFIED) -> Verification.VERIFIED
                best.contains(Verification.LIKELY) -> Verification.LIKELY
                else -> Verification.UNVERIFIED
            }
            c.copy(label = label)
        }
    }
}

object MoneyChecks {
    /** Warns before any limit or reporting trigger stored from the official rules (plain code: core LimitCheck). */
    suspend fun warnings(case: CaseFile): List<String> {
        val rules = case.db.rules().all().filter { it.limitCents > 0 }
        val money = case.db.money().all()
        if (money.isNotEmpty() && rules.isEmpty()) return listOf("No contribution limits saved yet. Add them in Records, Rules (from the election office's page), so gifts can be checked.")
        return com.emotionalleaves.ballot.core.LimitCheck.warnings(
            money.map { com.emotionalleaves.ballot.core.LimitEntry(it.direction, it.amountCents, it.person, it.contactId, it.ownFunds) },
            rules.map { com.emotionalleaves.ballot.core.Limit(it.limitKind, it.limitCents, it.jurisdiction) })
    }
}

/** [questions] is the plain question bank: the room works from it with no AI connected. */
data class MockRoom(val id: String, val title: String, val setup: String, val persona: String, val questions: List<String> = emptyList())

object MockRooms {
    val rooms = listOf(
        MockRoom("door", "Hostile voter at the door", "A resident opens the door, skeptical and short on time.",
            "You are a skeptical resident at your front door. You are busy, a little irritated, and doubt politicians. Push back on vague answers. Keep replies to one or two sentences. Stay in character.",
            questions = listOf("I've got a minute. Who are you and what do you want?", "Why should I trust another politician?", "What would you actually change on this street?", "I don't even vote in local elections. Why should I?", "How are you different from the person who has the seat now?", "OK. What's the one thing you want me to remember?")),
        MockRoom("reporter", "Local reporter", "A local reporter on a deadline wants a quote and a straight answer.",
            "You are a fair but probing local news reporter interviewing a first-time candidate. Ask one question at a time, follow up on dodges, ask about qualifications and specifics. Stay in character.",
            questions = listOf("Why are you running, in one sentence?", "What qualifies you for this seat?", "What's the first thing you'd do in office?", "Your opponent says you're too new to this. Your answer?", "Where do you stand on the budget, and what would you cut?", "Is there anything in your past voters should hear from you first?")),
        MockRoom("forum", "Candidate forum", "A moderator asks timed questions in front of a neighborhood audience.",
            "You are a candidate forum moderator. Ask one question at a time on local issues, enforce a 60-second answer, and occasionally relay a pointed audience question. Stay in character.",
            questions = listOf("Introduce yourself in 60 seconds.", "What is the biggest problem facing our neighborhood?", "How would you pay for your ideas?", "An audience member asks: will you return my calls?", "Name one thing you agree with your opponent on.", "Closing statement: why you?")),
        MockRoom("donor", "Donor call", "A potential donor who knows you a little picks up the phone.",
            "You are a potential donor who knows the candidate slightly. You are friendly but cautious with money. Ask what the money is for and why they can win. Only commit if the ask is clear. Stay in character.",
            questions = listOf("Hi, nice to hear from you. What's this about?", "What exactly would the money be spent on?", "Why do you think you can win?", "How much are you asking for?", "Who else is supporting you?", "What happens if you lose?")),
        MockRoom("interview", "Board appointment interview", "A council panel interviews you for a commission seat.",
            "You are a city council panel interviewing an applicant for a volunteer board or commission. Ask about motivation, time, conflicts of interest and relevant experience. One question at a time. Stay in character.",
            questions = listOf("Why do you want to serve on this board?", "How much time can you give each month?", "Do you have any conflicts of interest we should know about?", "Tell us about a time you worked through a disagreement.", "What do you know about how this board makes decisions?", "Is there anything else we should know?")),
        MockRoom("debate", "Debate practice", "An opponent challenges your positions directly.",
            "You are a debate opponent in a local race. Challenge the candidate's stated positions respectfully but hard, with plausible counter-arguments. Never invent facts about the real world; argue from reasoning. One point at a time.",
            questions = listOf("My opponent's plan sounds nice. How would you pay for it?", "You've never held office. Why should anyone take a chance on you?", "Your position will hurt local businesses. Respond.", "Give one specific example of something you've done for this community.", "Isn't your top issue really a state matter, not ours?", "Final word: why are you the better choice?")),
    )
    val ids = rooms.map { it.id }
    fun byId(id: String) = rooms.firstOrNull { it.id == id }
}
