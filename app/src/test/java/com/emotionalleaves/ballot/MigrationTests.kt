package com.emotionalleaves.ballot

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.emotionalleaves.ballot.data.BallotDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Never lose data: every exported schema version is created, filled with a row, migrated to the current
 * version and checked against the entities. A schema change without a version bump or a migration fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class MigrationTests {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), BallotDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun everyPastVersionMigratesToTodaysSchemaKeepingRows() {
        for (from in 1..BallotDatabase.VERSION) {
            val name = "migrate-$from"
            helper.createDatabase(name, from).apply {
                execSQL("INSERT INTO queued_request (thread, prompt, createdAt, attempts) VALUES ('main', 'Kept across versions', 1, 0)")
                close()
            }
            val db = helper.runMigrationsAndValidate(name, BallotDatabase.VERSION, true, *BallotDatabase.MIGRATIONS)
            db.query("SELECT prompt FROM queued_request").use { c ->
                check(c.moveToFirst() && c.getString(0) == "Kept across versions") { "row lost migrating from $from" }
            }
            db.close()
        }
    }
}
