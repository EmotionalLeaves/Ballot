package com.emotionalleaves.ballot

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.ballot.agent.ToolCtx
import com.emotionalleaves.ballot.agent.ToolRegistry
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.data.AppSettings
import com.emotionalleaves.ballot.data.BallotDatabase
import com.emotionalleaves.ballot.data.CaseFile
import com.emotionalleaves.ballot.data.SettingsStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** End-to-end flows a new user goes through, run against a real (in-memory) case file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class FlowTests {
    private lateinit var ctx: Context
    private lateinit var db: BallotDatabase
    private lateinit var case: CaseFile
    private val tools = ToolRegistry()

    private fun obj(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) ->
        k to when (v) { is Number -> JsonPrimitive(v); is Boolean -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
    })

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, BallotDatabase::class.java).allowMainThreadQueries().build()
        case = CaseFile(db, SettingsStore(ctx))
    }

    @After fun tearDown() = db.close()

    /** Finishing setup the way the last setup screen does leaves Today with a real first task. */
    private suspend fun finishSetup() {
        case.updateProfile { it.copy(setupDone = true, setupStep = 8, stage = 1) }
        case.seedPlaybook(Stage.REASON)
        case.rescore()
    }

    @Test fun newUserGetsAFirstTaskAfterSetup() = runTest {
        finishSetup()
        assertTrue(case.profile().setupDone)
        val ranked = case.rescore()
        assertTrue("setup should leave open tasks", ranked.isNotEmpty())
        assertTrue("at least one task is doable right now", ranked.any { it.blocked == null })
        val top = db.tasks().get(ranked.first { it.blocked == null }.id)
        assertNotNull(top)
        assertTrue(top!!.title.isNotBlank() && top.why.isNotBlank())
    }

    @Test fun doneMovesTodayToTheNextTask() = runTest {
        finishSetup()
        val first = case.rescore().first { it.blocked == null }.id
        case.completeTask(db.tasks().get(first)!!)
        val after = case.rescore()
        assertFalse("a finished task leaves Today", after.any { it.id == first })
        assertTrue("another task takes its place", after.isNotEmpty())
        assertTrue(db.audit().all().any { it.action == "Completed task" })
    }

    /** The move to a new phone or a reinstall: export here, import there, everything comes back. */
    @Test fun exportThenImportOnAFreshInstallRestoresEverything() = runTest {
        finishSetup()
        tools.execute("add_contact", obj("name" to "Dana Ruiz"), ToolCtx(case, AppSettings()))
        val file = com.emotionalleaves.ballot.data.Backup.export(db, "correct horse".toCharArray())

        val fresh = Room.inMemoryDatabaseBuilder(ctx, BallotDatabase::class.java).allowMainThreadQueries().build()
        try {
            val wrong = runCatching { com.emotionalleaves.ballot.data.Backup.import(fresh, file, "wrong pass".toCharArray()) }
            assertTrue(wrong.isFailure)
            assertEquals("Wrong passphrase, or the file is damaged.", wrong.exceptionOrNull()?.message)
            assertTrue("a failed import changes nothing", fresh.contacts().all().isEmpty())

            com.emotionalleaves.ballot.data.Backup.import(fresh, file, "correct horse".toCharArray())
            assertTrue("setup is done, so the app opens straight to Today", fresh.profile().get()!!.setupDone)
            assertEquals("Dana Ruiz", fresh.contacts().all().single().name)
            assertEquals(db.tasks().all().size, fresh.tasks().all().size)
        } finally { fresh.close() }
    }

    /** "I did the first one" → the model echoes the id the way the brief shows it. */
    @Test fun completingByTheIdTheBriefShowsWorks() = runTest {
        finishSetup()
        val t = db.tasks().open().first()
        val out = tools.execute("complete_task", obj("task_id" to "T${t.id}"), ToolCtx(case, AppSettings()))
        assertFalse(out.text, out.isError)
        assertTrue(db.tasks().get(t.id)!!.done)
    }

    @Test fun eventsNeverLandInsideWorkHours() = runTest {
        finishSetup()
        // Default work hours are Monday to Friday, 7:00 to 15:30. 2030-01-07 is a Monday.
        val atWork = tools.execute("add_event", obj("title" to "Council", "start" to "2030-01-07T10:00"), ToolCtx(case, AppSettings()))
        assertTrue(atWork.isError)
        val evening = tools.execute("add_event", obj("title" to "Council", "start" to "2030-01-07T18:30"), ToolCtx(case, AppSettings()))
        assertFalse(evening.text, evening.isError)
        assertEquals(1, db.events().all().size)
    }

    @Test fun peopleAddedByTheChiefCanBeFoundAgain() = runTest {
        tools.execute("add_contact", obj("name" to "Priya Shah", "how_met" to "Library board meeting"), ToolCtx(case, AppSettings()))
        val out = tools.execute("find_contacts", obj("query" to "library"), ToolCtx(case, AppSettings()))
        assertTrue(out.text, out.text.contains("Priya Shah"))
    }

    @Test fun everyPageCanBeOpenedByAsking() = runTest {
        com.emotionalleaves.ballot.agent.PAGES.keys.forEach { page ->
            val out = tools.execute("open_page", obj("page" to page), ToolCtx(case, AppSettings()))
            assertFalse(out.text, out.isError)
            assertEquals(page, out.cards.single().target)
        }
        assertTrue(tools.execute("open_page", obj("page" to "nowhere"), ToolCtx(case, AppSettings())).isError)
    }

    @Test fun settingsByAskingCannotTurnOnSpending() = runTest {
        val store = SettingsStore(ctx)
        val c = ToolCtx(case, AppSettings(), store = store)
        assertTrue(tools.execute("change_setting", obj("setting" to "free_only", "value" to "false"), c).isError)
        assertFalse(tools.execute("change_setting", obj("setting" to "dark_theme", "value" to "on"), c).isError)
        assertTrue(store.get().darkTheme)
        assertTrue(tools.execute("change_setting", obj("setting" to "brief_time", "value" to "25:00"), c).isError)
        assertFalse(tools.execute("change_setting", obj("setting" to "brief_time", "value" to "19:15"), c).isError)
        assertEquals(19 * 60 + 15, store.get().briefMinuteOfDay)
        store.update { it.copy(darkTheme = false) }
    }
}

