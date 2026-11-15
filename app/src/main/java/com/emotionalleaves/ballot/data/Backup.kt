package com.emotionalleaves.ballot.data

import android.content.Context
import androidx.room.withTransaction
import com.emotionalleaves.ballot.agent.BallotJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.SecureRandom
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupBundle(
    val format: Int = 1,
    val schemaVersion: Int = BallotDatabase.VERSION,
    val exportedAt: Long = now(),
    val profile: List<Profile> = emptyList(),
    val positions: List<Position> = emptyList(),
    val offices: List<Office> = emptyList(),
    val raceCards: List<RaceCard> = emptyList(),
    val rules: List<Rule> = emptyList(),
    val deadlines: List<Deadline> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val interactions: List<Interaction> = emptyList(),
    val events: List<Event> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val decisions: List<Decision> = emptyList(),
    val documents: List<Document> = emptyList(),
    val speech: List<SpeechRep> = emptyList(),
    val money: List<Money> = emptyList(),
    val voterContacts: List<VoterContact> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val audit: List<AuditEntry> = emptyList(),
    val sources: List<SourceRecord> = emptyList(),
    val chat: List<ChatMessage> = emptyList(),
    val pending: List<PendingAction> = emptyList(),
    val brief: List<BriefItem> = emptyList(),
    val queue: List<QueuedRequest> = emptyList(),
    val usage: List<UsageRecord> = emptyList(),
)

/** Encrypted export and import, started by you. AES-256-GCM with a key derived from your passphrase. */
object Backup {
    private val MAGIC = "BALLOT1".toByteArray()
    private const val ITER = 120_000

    suspend fun bundle(db: BallotDatabase): BackupBundle = BackupBundle(
        profile = db.profile().all(), positions = db.positions().all(), offices = db.offices().all(),
        raceCards = db.raceCards().all(), rules = db.rules().all(), deadlines = db.deadlines().all(),
        contacts = db.contacts().all(), interactions = db.interactions().all(), events = db.events().all(),
        tasks = db.tasks().all(), decisions = db.decisions().all(), documents = db.documents().all(),
        speech = db.speech().all(), money = db.money().all(), voterContacts = db.voterContacts().all(),
        lessons = db.lessons().all(), audit = db.audit().all(), sources = db.sources().all(), chat = db.chat().all(),
        pending = db.pending().all(), brief = db.brief().all(), queue = db.queue().all(), usage = db.usage().all(),
    )

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        require(data.size > MAGIC.size + 28 && data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "This is not a Ballot backup file." }
        val salt = data.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = data.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        return try {
            cipher.doFinal(data.copyOfRange(MAGIC.size + 28, data.size))
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong passphrase, or the file is damaged.")
        }
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(passphrase, salt, ITER, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encode(b: BackupBundle): ByteArray = BallotJson.encodeToString(b).toByteArray()
    fun decode(bytes: ByteArray): BackupBundle = BallotJson.decodeFromString(BackupBundle.serializer(), String(bytes))

    suspend fun export(db: BallotDatabase, passphrase: CharArray): ByteArray = withContext(Dispatchers.IO) {
        encrypt(encode(bundle(db)), passphrase)
    }

    /** Replaces everything with the backup, in one transaction after a clear. */
    suspend fun import(db: BallotDatabase, data: ByteArray, passphrase: CharArray) = withContext(Dispatchers.IO) {
        val b = decode(decrypt(data, passphrase))
        require(b.schemaVersion <= BallotDatabase.VERSION) { "This backup comes from a newer version of Ballot. Update the app first." }
        db.clearAllTables()
        db.withTransaction {
            b.profile.forEach { db.profile().upsert(it) }
            b.positions.forEach { db.positions().upsert(it) }
            b.offices.forEach { db.offices().upsert(it) }
            b.raceCards.forEach { db.raceCards().upsert(it) }
            b.rules.forEach { db.rules().upsert(it) }
            b.deadlines.forEach { db.deadlines().upsert(it) }
            b.contacts.forEach { db.contacts().upsert(it) }
            b.interactions.forEach { db.interactions().upsert(it) }
            b.events.forEach { db.events().upsert(it) }
            b.tasks.forEach { db.tasks().upsert(it) }
            b.decisions.forEach { db.decisions().upsert(it) }
            b.documents.forEach { db.documents().upsert(it) }
            b.speech.forEach { db.speech().upsert(it) }
            b.money.forEach { db.money().upsert(it) }
            b.voterContacts.forEach { db.voterContacts().upsert(it) }
            b.lessons.forEach { db.lessons().upsert(it) }
            b.audit.forEach { db.audit().insert(it) }
            b.sources.forEach { db.sources().upsert(it) }
            b.chat.forEach { db.chat().insert(it) }
            b.pending.forEach { db.pending().upsert(it) }
            b.brief.forEach { db.brief().upsert(it) }
            b.queue.forEach { db.queue().upsert(it) }
            b.usage.forEach { db.usage().insert(it) }
        }
        db.audit().insert(AuditEntry(actor = "you", action = "Imported a backup", outcome = "exported ${b.exportedAt}"))
    }
}

/** Automatic local snapshot each day, seven kept, with a restore screen. Files stay encrypted by SQLCipher. */
object Snapshots {
    const val KEEP = 7

    fun dir(context: Context): File = File(context.filesDir, "snapshots").apply { mkdirs() }

    fun list(context: Context): List<File> = dir(context).listFiles { f -> f.name.endsWith(".db") }?.sortedByDescending { it.name } ?: emptyList()

    /**
     * Copies the database file. With [db] given, the copy is made inside a write transaction, so no other
     * write can land halfway through it and the snapshot is never torn. The journal mode is TRUNCATE, so the
     * main file holds every committed change.
     */
    fun take(context: Context, db: BallotDatabase? = null, day: LocalDate = LocalDate.now()): File? {
        val src = context.getDatabasePath(BallotDatabase.NAME)
        if (!src.exists()) return null
        val out = File(dir(context), "ballot-$day.db")
        val tmp = File(dir(context), "ballot-$day.db.tmp")
        if (db != null && db.isOpen) db.runInTransaction { src.copyTo(tmp, overwrite = true) } else src.copyTo(tmp, overwrite = true)
        if (tmp.length() != src.length()) { tmp.delete(); return null }
        if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
        list(context).drop(KEEP).forEach { it.delete() }
        return out
    }

    /** Copies a snapshot over the live database. The caller must close the database first and restart. */
    fun restore(context: Context, snapshot: File) {
        val dst = context.getDatabasePath(BallotDatabase.NAME)
        listOf("-journal", "-wal", "-shm").forEach { File(dst.path + it).delete() }
        snapshot.copyTo(dst, overwrite = true)
    }
}

/** Crash reports stay on the device, with a one-tap copy to paste into a GitHub issue. */
object CrashReports {
    fun dir(context: Context): File = File(context.filesDir, "crashes").apply { mkdirs() }

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val f = File(dir(context), "crash-${System.currentTimeMillis()}.txt")
                f.writeText("Ballot crash on thread ${t.name}\n${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n\n${e.stackTraceToString()}")
                list(context).drop(20).forEach { it.delete() }
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun list(context: Context): List<File> = dir(context).listFiles()?.sortedByDescending { it.name } ?: emptyList()
}
