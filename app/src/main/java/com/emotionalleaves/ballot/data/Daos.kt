package com.emotionalleaves.ballot.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<Profile?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): Profile?

    @Upsert
    suspend fun upsert(p: Profile)

    @Query("SELECT * FROM profile")
    suspend fun all(): List<Profile>
}

@Dao
interface PositionDao {
    @Query("SELECT * FROM position ORDER BY updatedAt DESC")
    fun observe(): Flow<List<Position>>

    @Query("SELECT * FROM position ORDER BY updatedAt DESC")
    suspend fun all(): List<Position>

    @Query("SELECT * FROM position WHERE issue = :issue LIMIT 1")
    suspend fun byIssue(issue: String): Position?

    @Upsert
    suspend fun upsert(p: Position): Long

    @Delete
    suspend fun delete(p: Position)
}

@Dao
interface OfficeDao {
    @Query("SELECT * FROM office ORDER BY level, body")
    fun observe(): Flow<List<Office>>

    @Query("SELECT * FROM office ORDER BY level, body")
    suspend fun all(): List<Office>

    @Query("SELECT * FROM office WHERE id = :id")
    suspend fun get(id: Long): Office?

    @Query("SELECT * FROM office WHERE body = :body AND district = :district LIMIT 1")
    suspend fun find(body: String, district: String): Office?

    @Upsert
    suspend fun upsert(o: Office): Long

    @Delete
    suspend fun delete(o: Office)
}

@Dao
interface RaceCardDao {
    @Query("SELECT * FROM race_card ORDER BY score DESC")
    fun observe(): Flow<List<RaceCard>>

    @Query("SELECT * FROM race_card ORDER BY score DESC")
    suspend fun all(): List<RaceCard>

    @Query("SELECT * FROM race_card WHERE officeId = :officeId LIMIT 1")
    suspend fun forOffice(officeId: Long): RaceCard?

    @Upsert
    suspend fun upsert(r: RaceCard): Long

    @Delete
    suspend fun delete(r: RaceCard)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM rule ORDER BY jurisdiction")
    fun observe(): Flow<List<Rule>>

    @Query("SELECT * FROM rule")
    suspend fun all(): List<Rule>

    @Query("SELECT * FROM rule WHERE id = :id")
    suspend fun get(id: Long): Rule?

    @Upsert
    suspend fun upsert(r: Rule): Long

    @Delete
    suspend fun delete(r: Rule)
}

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadline ORDER BY done, dueEpochDay")
    fun observe(): Flow<List<Deadline>>

    @Query("SELECT * FROM deadline ORDER BY dueEpochDay")
    suspend fun all(): List<Deadline>

    @Query("SELECT * FROM deadline WHERE done = 0 ORDER BY dueEpochDay")
    suspend fun open(): List<Deadline>

    @Query("SELECT * FROM deadline WHERE id = :id")
    suspend fun get(id: Long): Deadline?

    @Upsert
    suspend fun upsert(d: Deadline): Long

    @Delete
    suspend fun delete(d: Deadline)
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contact ORDER BY name COLLATE NOCASE")
    fun observe(): Flow<List<Contact>>

    @Query("SELECT * FROM contact ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Contact>

    @Query("SELECT * FROM contact WHERE id = :id")
    suspend fun get(id: Long): Contact?

    @Query("SELECT * FROM contact WHERE id = :id")
    fun observeOne(id: Long): Flow<Contact?>

    @Query("SELECT * FROM contact WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): Contact?

    @Upsert
    suspend fun upsert(c: Contact): Long

    @Delete
    suspend fun delete(c: Contact)
}

@Dao
interface InteractionDao {
    @Query("SELECT * FROM interaction WHERE contactId = :contactId ORDER BY at DESC")
    fun forContact(contactId: Long): Flow<List<Interaction>>

    @Query("SELECT * FROM interaction ORDER BY at DESC")
    suspend fun all(): List<Interaction>

    @Query("SELECT * FROM interaction WHERE at >= :since")
    suspend fun since(since: Long): List<Interaction>

    @Upsert
    suspend fun upsert(i: Interaction): Long

    @Delete
    suspend fun delete(i: Interaction)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM event ORDER BY startAt DESC")
    fun observe(): Flow<List<Event>>

    @Query("SELECT * FROM event ORDER BY startAt DESC")
    suspend fun all(): List<Event>

    @Query("SELECT * FROM event WHERE id = :id")
    suspend fun get(id: Long): Event?

    @Upsert
    suspend fun upsert(e: Event): Long

    @Delete
    suspend fun delete(e: Event)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM task WHERE done = 0 ORDER BY score DESC")
    fun observeOpen(): Flow<List<Task>>

    @Query("SELECT * FROM task ORDER BY done, score DESC")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM task WHERE done = 0")
    suspend fun open(): List<Task>

    @Query("SELECT * FROM task")
    suspend fun all(): List<Task>

    @Query("SELECT * FROM task WHERE id = :id")
    suspend fun get(id: Long): Task?

    @Query("SELECT COUNT(*) FROM task WHERE templateKey = :key")
    suspend fun countTemplate(key: String): Int

    @Query("SELECT * FROM task WHERE done = 1 AND doneAt >= :since")
    suspend fun doneSince(since: Long): List<Task>

    @Upsert
    suspend fun upsert(t: Task): Long

    @Update
    suspend fun updateAll(tasks: List<Task>)

    @Delete
    suspend fun delete(t: Task)
}

@Dao
interface DecisionDao {
    @Query("SELECT * FROM decision ORDER BY at DESC")
    fun observe(): Flow<List<Decision>>

    @Query("SELECT * FROM decision ORDER BY at DESC")
    suspend fun all(): List<Decision>

    @Query("SELECT * FROM decision WHERE kind = :kind ORDER BY at DESC LIMIT 1")
    suspend fun latest(kind: String): Decision?

    @Upsert
    suspend fun upsert(d: Decision): Long

    @Delete
    suspend fun delete(d: Decision)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM document ORDER BY updatedAt DESC")
    fun observe(): Flow<List<Document>>

    @Query("SELECT * FROM document ORDER BY updatedAt DESC")
    suspend fun all(): List<Document>

    @Query("SELECT * FROM document WHERE id = :id")
    suspend fun get(id: Long): Document?

    @Query("SELECT * FROM document WHERE id = :id")
    fun observeOne(id: Long): Flow<Document?>

    @Upsert
    suspend fun upsert(d: Document): Long

    @Delete
    suspend fun delete(d: Document)
}

@Dao
interface SpeechRepDao {
    @Query("SELECT * FROM speech_rep ORDER BY createdAt DESC")
    fun observe(): Flow<List<SpeechRep>>

    @Query("SELECT * FROM speech_rep ORDER BY createdAt DESC")
    suspend fun all(): List<SpeechRep>

    @Upsert
    suspend fun upsert(s: SpeechRep): Long

    @Delete
    suspend fun delete(s: SpeechRep)
}

@Dao
interface MoneyDao {
    @Query("SELECT * FROM money ORDER BY at DESC")
    fun observe(): Flow<List<Money>>

    @Query("SELECT * FROM money ORDER BY at DESC")
    suspend fun all(): List<Money>

    @Upsert
    suspend fun upsert(m: Money): Long

    @Delete
    suspend fun delete(m: Money)
}

@Dao
interface VoterContactDao {
    @Query("SELECT * FROM voter_contact ORDER BY at DESC")
    fun observe(): Flow<List<VoterContact>>

    @Query("SELECT * FROM voter_contact ORDER BY at DESC")
    suspend fun all(): List<VoterContact>

    @Upsert
    suspend fun upsert(v: VoterContact): Long

    @Delete
    suspend fun delete(v: VoterContact)
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lesson ORDER BY at DESC")
    fun observe(): Flow<List<Lesson>>

    @Query("SELECT * FROM lesson ORDER BY at DESC")
    suspend fun all(): List<Lesson>

    @Upsert
    suspend fun upsert(l: Lesson): Long

    @Delete
    suspend fun delete(l: Lesson)
}

/** Insert and read only. There is deliberately no update or delete. */
@Dao
interface AuditDao {
    @Insert
    suspend fun insert(e: AuditEntry): Long

    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit")
    fun observe(limit: Int = 500): Flow<List<AuditEntry>>

    @Query("SELECT * FROM audit_log ORDER BY at")
    suspend fun all(): List<AuditEntry>
}

@Dao
interface SourceDao {
    @Query("SELECT * FROM source_record ORDER BY fetchedAt DESC")
    fun observe(): Flow<List<SourceRecord>>

    @Query("SELECT * FROM source_record ORDER BY fetchedAt DESC")
    suspend fun all(): List<SourceRecord>

    @Query("SELECT * FROM source_record WHERE id = :id")
    suspend fun get(id: Long): SourceRecord?

    @Query("SELECT * FROM source_record WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<SourceRecord>

    @Query("SELECT * FROM source_record WHERE url = :url ORDER BY fetchedAt DESC LIMIT 1")
    suspend fun latestFor(url: String): SourceRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(s: SourceRecord): Long

    @Upsert
    suspend fun upsert(s: SourceRecord): Long
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_message WHERE thread = :thread ORDER BY at")
    fun observe(thread: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM (SELECT * FROM chat_message WHERE thread = :thread ORDER BY at DESC LIMIT :limit) ORDER BY at")
    suspend fun recent(thread: String, limit: Int): List<ChatMessage>

    @Query("SELECT * FROM chat_message ORDER BY at")
    suspend fun all(): List<ChatMessage>

    @Insert
    suspend fun insert(m: ChatMessage): Long

    @Query("DELETE FROM chat_message WHERE thread = :thread")
    suspend fun clear(thread: String)
}

@Dao
interface PendingDao {
    @Query("SELECT * FROM pending_action WHERE status = 'WAITING' ORDER BY createdAt DESC")
    fun observeWaiting(): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_action")
    suspend fun all(): List<PendingAction>

    @Query("SELECT * FROM pending_action WHERE id = :id")
    suspend fun get(id: Long): PendingAction?

    @Upsert
    suspend fun upsert(p: PendingAction): Long
}

@Dao
interface BriefDao {
    @Query("SELECT * FROM brief_item WHERE dayEpoch = :day ORDER BY id")
    fun observeDay(day: Long): Flow<List<BriefItem>>

    @Query("SELECT * FROM brief_item ORDER BY dayEpoch DESC, id LIMIT 60")
    fun observeRecent(): Flow<List<BriefItem>>

    @Query("SELECT * FROM brief_item")
    suspend fun all(): List<BriefItem>

    @Query("SELECT COUNT(*) FROM brief_item WHERE dayEpoch = :day AND kind = :kind")
    suspend fun countFor(day: Long, kind: String): Int

    @Upsert
    suspend fun upsert(b: BriefItem): Long
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queued_request ORDER BY createdAt")
    suspend fun all(): List<QueuedRequest>

    @Query("SELECT COUNT(*) FROM queued_request")
    fun observeCount(): Flow<Int>

    @Insert
    suspend fun insert(q: QueuedRequest): Long

    @Upsert
    suspend fun upsert(q: QueuedRequest): Long

    @Delete
    suspend fun delete(q: QueuedRequest)
}

@Dao
interface UsageDao {
    @Query("SELECT COALESCE(SUM(costMicros), 0) FROM usage_record WHERE at >= :since")
    fun observeSpendSince(since: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(costMicros), 0) FROM usage_record WHERE at >= :since")
    suspend fun spendSince(since: Long): Long

    @Query("SELECT * FROM usage_record")
    suspend fun all(): List<UsageRecord>

    @Insert
    suspend fun insert(u: UsageRecord): Long
}
