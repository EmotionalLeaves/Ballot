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
}
