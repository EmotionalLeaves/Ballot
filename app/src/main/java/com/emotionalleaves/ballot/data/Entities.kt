package com.emotionalleaves.ballot.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/*
 * The case file. Every table carries created, updated, source and confidence fields so the
 * Chief can cite the entry it relied on and say how sure it is.
 *
 * confidence: VERIFIED, LIKELY, UNVERIFIED, or STATED (you told the Chief directly).
 */

object Confidence {
    const val VERIFIED = "VERIFIED"
    const val LIKELY = "LIKELY"
    const val UNVERIFIED = "UNVERIFIED"
    const val STATED = "STATED"
}

fun now(): Long = System.currentTimeMillis()

@Serializable
@Entity(tableName = "profile")
data class Profile(
    @PrimaryKey val id: Long = 1,
    val name: String = "",
    val zip: String = "",
    val address: String = "",
    val why: String = "",
    val coreValues: String = "",
    val issues: String = "",
    val story60: String = "",
    val story3: String = "",
    val story10: String = "",
    val storyDrafts: Int = 0,
    val storyLocked: Boolean = false,
    val storyRatedClear: Boolean = false,
    val voiceProfile: String = "",
    val workDays: String = "1,2,3,4,5",
    val workStartMin: Int = 7 * 60,
    val workEndMin: Int = 15 * 60 + 30,
    val freeEvenings: String = "",
    val energy: String = "MEDIUM",
    val moneyAtRisk: String = "",
    val offLimits: String = "",
    val speakingComfort: Int = 1,
    val peopleKnown: String = "",
    val groups: String = "",
    val volunteering: String = "",
    val employerRules: String = "",
    val employerCleared: Boolean = false,
    val ambition: String = "",
    val timeline: String = "",
    val firstTarget: String = "",
    val stage: Int = 1,
    val quizBest: Int = 0,
    val serveDone: Boolean = false,
    val serveNote: String = "",
    val electionDateEpochDay: Long = 0,
    val electionOutcome: String = "",
    val setupStep: Int = 0,
    val setupDone: Boolean = false,
    val weeklyReviewDay: Int = 7,
    val weeklyHours: Int = 5,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "setup",
    val confidence: String = Confidence.STATED,
)

@Serializable
@Entity(tableName = "position")
data class Position(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issue: String,
    val stance: String = "",
    val reasoning: String = "",
    val counterArgument: String = "",
    val facts: String = "",
    val sourceIds: String = "",
    val statedPubliclyAt: Long = 0,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

@Serializable
@Entity(tableName = "office")
data class Office(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val body: String,
    val title: String = "",
    val district: String = "",
    val level: String = "LOCAL",
    val powers: String = "",
    val term: String = "",
    val nextElection: String = "",
    val nextElectionEpochDay: Long = 0,
    val incumbent: String = "",
    val partTime: Boolean = true,
    val appointed: Boolean = false,
    val officialUrl: String = "",
    val lastChecked: Long = 0,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "",
    val confidence: String = Confidence.UNVERIFIED,
)

@Serializable
@Entity(tableName = "race_card", indices = [Index("officeId")])
data class RaceCard(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val officeId: Long,
    val filingRules: String = "",
    val signaturesNeeded: Int = 0,
    val filingFeeCents: Long = 0,
    val filingDeadlineEpochDay: Long = 0,
    val seats: Int = 1,
    val pastTurnout: Int = 0,
    val pastMarginPct: Double = 0.0,
    val pastWinnerSpendCents: Long = 0,
    val openSeat: Boolean = false,
    val incumbentRetiring: Boolean = false,
    val uncontestedHistory: Boolean = false,
    val hoursPerWeek: Int = 0,
    val score: Int = 0,
    val scoreWorking: String = "",
    val sourceIds: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "",
    val confidence: String = Confidence.UNVERIFIED,
)

@Serializable
@Entity(tableName = "rule")
data class Rule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jurisdiction: String,
    val summary: String,
    val officialUrl: String = "",
    val officePhone: String = "",
    val lastChecked: Long = 0,
    val status: String = Confidence.UNVERIFIED,
    val sourceId: Long = 0,
    /** PER_DONOR (contribution limit), REPORT_TRIGGER (spending that triggers a report), or blank. */
    val limitKind: String = "",
    val limitCents: Long = 0,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "",
    val confidence: String = Confidence.UNVERIFIED,
)

@Serializable
@Entity(tableName = "deadline", indices = [Index("dueEpochDay")])
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long = 0,
    val title: String,
    val dueEpochDay: Long,
    val legal: Boolean = true,
    val leadAlerts: String = "14,7,2",
    val alertsSent: String = "",
    val preparedDraftId: Long = 0,
    val officialUrl: String = "",
    val sourceId: Long = 0,
    val done: Boolean = false,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "",
    val confidence: String = Confidence.UNVERIFIED,
)

object ContactRole {
    const val NONE = ""
    const val MENTOR = "MENTOR"
    const val TREASURER = "TREASURER"
    const val MANAGER = "CAMPAIGN_MANAGER"
    const val VALIDATOR = "VALIDATOR"
    const val PRESS = "PRESS"
    const val VOLUNTEER = "VOLUNTEER"
    const val DONOR = "DONOR"
    const val OFFICIAL = "OFFICIAL"
    const val ORGANIZER = "ORGANIZER"
    val cabinet = listOf(MENTOR, TREASURER, MANAGER, VALIDATOR)
    val all = listOf(NONE, MENTOR, TREASURER, MANAGER, VALIDATOR, PRESS, VOLUNTEER, DONOR, OFFICIAL, ORGANIZER)
}

@Serializable
@Entity(tableName = "contact")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val howMet: String = "",
    val interests: String = "",
    val district: String = "",
    val livesInDistrict: Boolean = false,
    val closeness: Int = 1,
    val role: String = ContactRole.NONE,
    val introducedById: Long = 0,
    val phone: String = "",
    val email: String = "",
    val beat: String = "",
    val endorsementStatus: String = "",
    val influence: Int = 0,
    val suggestedAskCents: Long = 0,
    val pledgeCents: Long = 0,
    val callOutcome: String = "",
    val lastContactAt: Long = 0,
    val nextStep: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

@Serializable
@Entity(tableName = "interaction", indices = [Index("contactId")])
data class Interaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contactId: Long,
    val at: Long = now(),
    val channel: String = "IN_PERSON",
    val notes: String = "",
    val promisesByYou: String = "",
    val promisesByThem: String = "",
    val promiseDueEpochDay: Long = 0,
    val twoWay: Boolean = true,
    val nextStep: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

object EventType {
    const val MEETING = "PUBLIC_MEETING"
    const val COMMUNITY = "COMMUNITY"
    const val VOLUNTEER = "VOLUNTEER"
    const val FORUM = "FORUM"
    const val CAMPAIGN = "CAMPAIGN"
    val all = listOf(MEETING, COMMUNITY, VOLUNTEER, FORUM, CAMPAIGN)
}

@Serializable
@Entity(tableName = "event", indices = [Index("startAt")])
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String = EventType.MEETING,
    val title: String,
    val place: String = "",
    val startAt: Long,
    val goal: String = "",
    val preCard: String = "",
    val attended: Boolean = false,
    val gaveComment: Boolean = false,
    val debrief: String = "",
    val agendaUrl: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
    /** When the "starts soon" reminder went out; 0 = not yet. Added in schema 2. */
    @androidx.room.ColumnInfo(defaultValue = "0") val remindedAt: Long = 0,
)

@Serializable
@Entity(tableName = "task", indices = [Index("done"), Index("templateKey")])
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stage: Int,
    val title: String,
    val why: String = "",
    val steps: String = "",
    val doneLooksLike: String = "",
    val goodEnough: String = "",
    val sizeMinutes: Int = 15,
    val highEffort: Boolean = false,
    val dueEpochDay: Long = 0,
    val legal: Boolean = false,
    val humanContact: Boolean = false,
    val movesExit: Boolean = false,
    val followUpSince: Long = 0,
    val skippedCount: Int = 0,
    val module: String = "",
    val templateKey: String = "",
    val deadlineId: Long = 0,
    val score: Int = 0,
    val done: Boolean = false,
    val doneAt: Long = 0,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "chief",
    val confidence: String = Confidence.STATED,
)

@Serializable
@Entity(tableName = "decision")
data class Decision(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = "GENERAL",
    val question: String,
    val options: String = "",
    val choice: String = "",
    val reasoning: String = "",
    val agreed: Boolean = false,
    val at: Long = now(),
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

object FactStatus {
    const val UNCHECKED = "UNCHECKED"
    const val PASSED = "PASSED"
    const val BLOCKED = "BLOCKED"
}

@Serializable
@Entity(tableName = "document")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val title: String,
    val body: String = "",
    val version: Int = 1,
    val previousBody: String = "",
    val claims: String = "",
    val factCheckStatus: String = FactStatus.UNCHECKED,
    val factCheckNotes: String = "",
    val approved: Boolean = false,
    val aiDrafted: Boolean = true,
    val holdUntil: Long = 0,
    val inVault: Boolean = false,
    val filePath: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "chief",
    val confidence: String = Confidence.UNVERIFIED,
)

@Serializable
@Entity(tableName = "speech_rep")
data class SpeechRep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String = "DAILY",
    val prompt: String = "",
    val recordingPath: String = "",
    val transcript: String = "",
    val durationSec: Int = 0,
    val wordsPerMinute: Int = 0,
    val fillerCount: Int = 0,
    val clarity: Int = 0,
    val pace: Int = 0,
    val lengthScore: Int = 0,
    val total: Int = 0,
    val feedback: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

object MoneyDirection {
    const val IN = "IN"
    const val OUT = "OUT"
    const val IN_KIND = "IN_KIND"
}

@Serializable
@Entity(tableName = "money", indices = [Index("at")])
data class Money(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val direction: String,
    val amountCents: Long,
    val person: String = "",
    val contactId: Long = 0,
    val address: String = "",
    val employer: String = "",
    val occupation: String = "",
    val at: Long = now(),
    val category: String = "",
    val reportPeriod: String = "",
    val memo: String = "",
    val ownFunds: Boolean = false,
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

object DoorOutcome {
    const val NOT_HOME = "NOT_HOME"
    const val SUPPORTER = "SUPPORTER"
    const val UNDECIDED = "UNDECIDED"
    const val OPPOSED = "OPPOSED"
    val all = listOf(NOT_HOME, SUPPORTER, UNDECIDED, OPPOSED)
}

@Serializable
@Entity(tableName = "voter_contact")
data class VoterContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val route: String = "",
    val household: String = "",
    val outcome: String,
    val issueHeard: String = "",
    val followUp: String = "",
    val at: Long = now(),
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

@Serializable
@Entity(tableName = "lesson")
data class Lesson(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val what: String,
    val change: String = "",
    val kind: String = "EVENT",
    val at: Long = now(),
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "you",
    val confidence: String = Confidence.STATED,
)

/** Never editable: the DAO only inserts and reads. */
@Serializable
@Entity(tableName = "audit_log", indices = [Index("at")])
data class AuditEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = now(),
    val actor: String,
    val action: String,
    val tool: String = "",
    val input: String = "",
    val sources: String = "",
    val approvedBy: String = "",
    val outcome: String = "",
)

/** A fetched page or search hit. Research answers must cite one of these. */
@Serializable
@Entity(tableName = "source_record", indices = [Index("url")])
data class SourceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String = "",
    val excerpt: String = "",
    val official: Boolean = false,
    val fetchedAt: Long = now(),
    val crossChecked: Boolean = false,
    val label: String = Confidence.UNVERIFIED,
    val injectionFlagged: Boolean = false,
    val contentHash: String = "",
    val createdAt: Long = now(),
    val updatedAt: Long = now(),
    val source: String = "web",
    val confidence: String = Confidence.UNVERIFIED,
)

@Serializable
@Entity(tableName = "chat_message", indices = [Index("thread")])
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thread: String = "main",
    val role: String,
    val text: String,
    val mode: String = "",
    val cards: String = "",
    val sourceIds: String = "",
    val at: Long = now(),
)

object PendingKind {
    const val SEND = "SEND"
    const val POST = "POST"
    const val FILE = "FILE"
    const val SIGN = "SIGN"
    const val SPEND = "SPEND"
    const val CONFIRM_VOICE = "CONFIRM_VOICE"
    const val ROUTINE = "ROUTINE"
}

/** An action in the "asks first" level, waiting for your tap. */
@Serializable
@Entity(tableName = "pending_action")
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val tool: String = "",
    val summary: String,
    val payload: String = "",
    val draftText: String = "",
    val recipient: String = "",
    val status: String = "WAITING",
    val createdAt: Long = now(),
    val decidedAt: Long = 0,
)

@Serializable
@Entity(tableName = "brief_item")
data class BriefItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayEpoch: Long,
    val kind: String = "NEWS",
    val title: String,
    val body: String = "",
    val url: String = "",
    val action: String = "",
    val sourceId: Long = 0,
    val createdAt: Long = now(),
)

/** Requests made while offline, run when the connection returns. */
@Serializable
@Entity(tableName = "queued_request")
data class QueuedRequest(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val thread: String = "main",
    val prompt: String,
    val createdAt: Long = now(),
    val attempts: Int = 0,
)

@Serializable
@Entity(tableName = "usage_record")
data class UsageRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long = now(),
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val searches: Int = 0,
    val costMicros: Long,
)
