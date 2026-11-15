package com.emotionalleaves.ballot.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        Profile::class, Position::class, Office::class, RaceCard::class, Rule::class, Deadline::class,
        Contact::class, Interaction::class, Event::class, Task::class, Decision::class, Document::class,
        SpeechRep::class, Money::class, VoterContact::class, Lesson::class, AuditEntry::class,
        SourceRecord::class, ChatMessage::class, PendingAction::class, BriefItem::class,
        QueuedRequest::class, UsageRecord::class,
    ],
    version = BallotDatabase.VERSION,
    exportSchema = true,
)
abstract class BallotDatabase : RoomDatabase() {
    abstract fun profile(): ProfileDao
    abstract fun positions(): PositionDao
    abstract fun offices(): OfficeDao
    abstract fun raceCards(): RaceCardDao
    abstract fun rules(): RuleDao
    abstract fun deadlines(): DeadlineDao
    abstract fun contacts(): ContactDao
    abstract fun interactions(): InteractionDao
    abstract fun events(): EventDao
    abstract fun tasks(): TaskDao
    abstract fun decisions(): DecisionDao
    abstract fun documents(): DocumentDao
    abstract fun speech(): SpeechRepDao
    abstract fun money(): MoneyDao
    abstract fun voterContacts(): VoterContactDao
    abstract fun lessons(): LessonDao
    abstract fun audit(): AuditDao
    abstract fun sources(): SourceDao
    abstract fun chat(): ChatDao
    abstract fun pending(): PendingDao
    abstract fun brief(): BriefDao
    abstract fun queue(): QueueDao
    abstract fun usage(): UsageDao

    companion object {
        const val VERSION = 2
        const val NAME = "ballot.db"

        /**
         * Every schema change adds a Migration here and a test in MigrationsTest.
         * A change that cannot migrate is dropped (see the loop rules).
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            // 1 -> 2: events remember when their "starts soon" reminder went out.
            object : Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE `event` ADD COLUMN `remindedAt` INTEGER NOT NULL DEFAULT 0")
                }
            },
        )

        fun open(context: Context, passphrase: ByteArray): BallotDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, BallotDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }
}
