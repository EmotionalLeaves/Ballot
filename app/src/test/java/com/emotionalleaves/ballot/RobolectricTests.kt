package com.emotionalleaves.ballot

import android.app.Application
import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.emotionalleaves.ballot.agent.ToolCtx
import com.emotionalleaves.ballot.agent.ToolRegistry
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.data.AppSettings
import com.emotionalleaves.ballot.data.Backup
import com.emotionalleaves.ballot.data.BallotDatabase
import com.emotionalleaves.ballot.data.CaseFile
import com.emotionalleaves.ballot.data.Confidence
import com.emotionalleaves.ballot.data.Contact
import com.emotionalleaves.ballot.data.Deadline
import com.emotionalleaves.ballot.data.PendingKind
import com.emotionalleaves.ballot.data.RoutineKind
import com.emotionalleaves.ballot.data.SettingsStore
import com.emotionalleaves.ballot.data.SourceRecord
import com.emotionalleaves.ballot.ui.components.ConfidenceBadge
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.WalkthroughDialog
import com.emotionalleaves.ballot.ui.theme.BallotTheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A bare Application so tests never start WorkManager or the crash handler. */
class TestApp : Application()

private fun obj(vararg pairs: Pair<String, Any>) = JsonObject(pairs.associate { (k, v) ->
    k to when (v) { is Number -> JsonPrimitive(v); is Boolean -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
})

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class ApprovalFlowTest {
    private lateinit var db: BallotDatabase
    private lateinit var case: CaseFile
    private val tools = ToolRegistry()

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BallotDatabase::class.java).allowMainThreadQueries().build()
        case = CaseFile(db, SettingsStore(ctx))
    }

    @After fun tearDown() = db.close()

    @Test fun messagesWaitForApprovalAndAreNeverSent() = runTest {
        val out = tools.execute("queue_follow_up", obj("contact" to "Ana", "message" to "Thanks for coffee!"), ToolCtx(case, AppSettings()))
        assertTrue(out.text.contains("Waiting"))
        val pending = db.pending().all()
        assertEquals(1, pending.size)
        assertEquals(PendingKind.SEND, pending[0].kind)
        assertEquals("Thanks for coffee!", pending[0].draftText)
    }

    @Test fun autonomyDialCannotReleaseSends() = runTest {
        val s = AppSettings(askFirst = emptySet())
        tools.execute("queue_follow_up", obj("contact" to "Ana", "message" to "Hi"), ToolCtx(case, s))
        assertEquals(1, db.pending().all().size)
    }

    @Test fun routineWorkRunsUnlessTheDialSaysAsk() = runTest {
        tools.execute("create_task", obj("title" to "Call the clerk"), ToolCtx(case, AppSettings()))
        assertEquals(1, db.tasks().all().count { it.title == "Call the clerk" })
        tools.execute("create_task", obj("title" to "Second"), ToolCtx(case, AppSettings(askFirst = setOf(RoutineKind.REMINDER))))
        assertEquals(0, db.tasks().all().count { it.title == "Second" })
        assertEquals(PendingKind.ROUTINE, db.pending().all().single().kind)
    }

    @Test fun voiceInputConfirmsMoneyContactsAndDeadlines() = runTest {
        tools.execute("log_money", obj("direction" to "OUT", "amount_dollars" to 40), ToolCtx(case, AppSettings(), fromVoice = true))
        tools.execute("add_contact", obj("name" to "Ben"), ToolCtx(case, AppSettings(), fromVoice = true))
        assertEquals(0, db.money().all().size)
        assertEquals(0, db.contacts().all().size)
        assertTrue(db.pending().all().all { it.kind == PendingKind.CONFIRM_VOICE })
        // Approving runs it.
        tools.execute("log_money", obj("direction" to "OUT", "amount_dollars" to 40), ToolCtx(case, AppSettings(), fromVoice = true, approved = true))
        assertEquals(4000L, db.money().all().single().amountCents)
    }

    @Test fun unverifiedSourcesCannotDriveADeadline() = runTest {
        val id = db.sources().insert(SourceRecord(url = "https://someblog.com/filing", label = Confidence.UNVERIFIED))
        val out = tools.execute("add_deadline", obj("title" to "Filing", "due_date" to "2027-03-03", "source_id" to id), ToolCtx(case, AppSettings()))
        assertTrue(out.isError)
        assertEquals(0, db.deadlines().all().size)
        val ok = db.sources().insert(SourceRecord(url = "https://sos.state.mn.us/filing", official = true, label = Confidence.LIKELY))
        val out2 = tools.execute("add_deadline", obj("title" to "Filing", "due_date" to "2027-03-03", "source_id" to ok), ToolCtx(case, AppSettings()))
        assertFalse(out2.text, out2.isError)
        assertEquals(1, db.deadlines().all().size)
    }

    @Test fun invalidToolInputIsRejectedNotRun() = runTest {
        val out = tools.execute("add_contact", JsonObject(emptyMap()), ToolCtx(case, AppSettings()))
        assertTrue(out.isError)
        assertTrue(out.text.contains("missing"))
    }

    @Test fun everyToolCallIsAudited() = runTest {
        tools.execute("create_task", obj("title" to "Audited"), ToolCtx(case, AppSettings()))
        assertTrue(db.audit().all().any { it.tool == "create_task" })
    }

    @Test fun legalDeadlinesGetAPreparedDraftOnce() = runTest {
        db.deadlines().upsert(Deadline(title = "Petition due", dueEpochDay = LocalDate.now().toEpochDay() + 3, legal = true))
        case.ensureDeadlineDrafts()
        case.ensureDeadlineDrafts()
        assertEquals(1, db.tasks().all().count { it.legal && it.title.contains("Petition due") })
        assertTrue(db.deadlines().all().single().preparedDraftId != 0L)
    }

    @Test fun playbookSeedingIsIdempotent() = runTest {
        case.seedPlaybook(Stage.REASON)
        val n = db.tasks().all().size
        case.seedPlaybook(Stage.REASON)
        assertEquals(n, db.tasks().all().size)
        assertTrue(n > 0)
    }

    @Test fun threeSkipsShrinkTheTask() = runTest {
        case.seedPlaybook(Stage.REASON)
        var t = db.tasks().all().first { it.sizeMinutes == 15 }
        repeat(3) { case.skipTask(t); t = db.tasks().get(t.id)!! }
        assertEquals(5, t.sizeMinutes)
        assertTrue(t.title.startsWith("First step"))
    }

    @Test fun backupBundleRoundTrips() = runTest {
        db.contacts().upsert(Contact(name = "Cara"))
        val bytes = Backup.encode(Backup.bundle(db))
        val back = Backup.decode(bytes)
        assertEquals("Cara", back.contacts.single().name)
    }

    @Test fun briefIsCompact() = runTest {
        case.seedPlaybook(Stage.REASON)
        val b = case.brief(1200)
        assertTrue(b.length <= 1200)
        assertTrue(b.contains("Stage 1"))
    }

    @Test fun migrationsCoverEveryVersion() {
        // Every schema version after the first needs a migration from the one before it.
        val covered = BallotDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }.toSet()
        (2..BallotDatabase.VERSION).forEach { v -> assertTrue("missing migration ${v - 1}->$v", (v - 1 to v) in covered) }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class)
class UiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun approvalButtonResponds() {
        var clicked by mutableStateOf(false)
        compose.setContent {
            BallotTheme {
                Panel {
                    PosterButton("Approve", onClick = { clicked = true })
                    if (clicked) Text("approved")
                }
            }
        }
        compose.onNodeWithText("Approve").performClick()
        compose.onNodeWithText("approved").assertIsDisplayed()
    }

    @Test fun unverifiedIsLabelledPlainly() {
        compose.setContent { BallotTheme { ConfidenceBadge("UNVERIFIED") } }
        compose.onNodeWithText("Unverified").assertIsDisplayed()
    }

    @Test fun walkMeThroughShowsEveryStep() {
        compose.setContent { BallotTheme { WalkthroughDialog("Test", listOf("First step here", "Second step here")) {} } }
        compose.onNodeWithText("First step here").assertIsDisplayed()
        compose.onNodeWithText("Second step here").assertIsDisplayed()
    }
}
