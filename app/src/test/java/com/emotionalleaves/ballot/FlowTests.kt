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

    /** "Too much this week": only the most important thing stays in the next seven days. */
    @Test fun shrinkingTheWeekKeepsOneThing() = runTest {
        finishSetup()
        val today = case.today()
        db.tasks().open().forEach { db.tasks().upsert(it.copy(dueEpochDay = today + 2)) }
        tools.execute("reschedule_plan", obj("shrink_week" to true, "reason" to "Too much"), ToolCtx(case, AppSettings()))
        val thisWeek = db.tasks().open().filter { !it.legal && it.dueEpochDay in today..today + 7 }
        assertEquals(1, thisWeek.size)
    }

    @Test fun aRealConversationCountsTowardTheBase() = runTest {
        tools.execute("log_interaction", obj("contact" to "Sam Okafor", "notes" to "Coffee after the meeting", "two_way" to true), ToolCtx(case, AppSettings()))
        tools.execute("log_interaction", obj("contact" to "Sam Okafor", "notes" to "Waved", "two_way" to false), ToolCtx(case, AppSettings()))
        assertEquals(1, case.stats().twoWayContacts)
        assertTrue(db.contacts().all().single().lastContactAt > 0)
    }

    @Test fun panicWipeLeavesNothing() = runTest {
        finishSetup()
        tools.execute("add_contact", obj("name" to "Lee"), ToolCtx(case, AppSettings()))
        case.clearAll()
        assertTrue(db.contacts().all().isEmpty())
        assertTrue(db.tasks().all().isEmpty())
        assertTrue(db.audit().all().isEmpty())
    }

    @Test fun lessonsAndBriefItemsLandWhereTheyShow() = runTest {
        tools.execute("record_lesson", obj("what" to "Arrive early to sign up for comment", "kind" to "EVENT"), ToolCtx(case, AppSettings()))
        tools.execute("save_brief_item", obj("title" to "Park budget on Tuesday's agenda", "kind" to "AGENDA"), ToolCtx(case, AppSettings()))
        assertEquals("Arrive early to sign up for comment", db.lessons().all().single().what)
        assertEquals(1, db.brief().countDay(case.today()))
    }

    @Test fun aSnapshotByAskingSaysPlainlyWhenThereIsNothingYet() = runTest {
        val out = tools.execute("run_job", obj("job" to "snapshot"), ToolCtx(case, AppSettings(), app = ctx))
        assertFalse(out.text, out.isError)
        assertTrue(out.text, out.text.contains("Snapshot saved") || out.text.contains("Nothing to snapshot"))
    }

    @Test fun changedWorkHoursMoveWhatCountsAsWorkTime() = runTest {
        finishSetup()
        tools.execute("update_profile", obj("work_start" to "12:00", "work_end" to "20:00"), ToolCtx(case, AppSettings()))
        // 18:30 on a Monday is now inside work.
        assertTrue(tools.execute("add_event", obj("title" to "Council", "start" to "2030-01-07T18:30"), ToolCtx(case, AppSettings())).isError)
        assertFalse(tools.execute("add_event", obj("title" to "Council", "start" to "2030-01-07T09:00"), ToolCtx(case, AppSettings())).isError)
    }

    /** Stage 1 ends when the story is locked and rated clear; finishing the next task then opens stage 2 with its own plan. */
    @Test fun meetingTheExitTestOpensTheNextStage() = runTest {
        finishSetup()
        case.updateProfile { it.copy(storyLocked = true, storyRatedClear = true) }
        val t = db.tasks().open().first()
        val out = tools.execute("complete_task", obj("task_id" to t.id), ToolCtx(case, AppSettings()))
        assertTrue(out.text, out.text.contains("stage 2"))
        assertEquals(2, case.profile().stage)
        assertTrue("stage 2 tasks are seeded", db.tasks().open().any { it.stage == 2 })
        assertTrue(db.audit().all().any { it.action.startsWith("Stage advanced to 2") })
    }

    @Test fun stagesCannotBeSkippedWithoutTheExitTest() = runTest {
        finishSetup()
        val t = db.tasks().open().first()
        tools.execute("complete_task", obj("task_id" to t.id), ToolCtx(case, AppSettings()))
        assertEquals(1, case.profile().stage)
    }

    /** Inside seven days a legal deadline gets a prepared draft and a task, and that task outranks everything on Today. */
    @Test fun aLegalDeadlineGoesToTheTopOfToday() = runTest {
        finishSetup()
        val src = db.sources().insert(com.emotionalleaves.ballot.data.SourceRecord(url = "https://sos.state.ne.gov/filing", official = true,
            label = com.emotionalleaves.ballot.data.Confidence.LIKELY))
        val due = java.time.LocalDate.now().plusDays(5).toString()
        assertFalse(tools.execute("add_deadline", obj("title" to "Filing period closes", "due_date" to due, "source_id" to src), ToolCtx(case, AppSettings())).isError)
        val top = case.rescore().first { it.blocked == null }
        assertTrue(db.tasks().get(top.id)!!.legal)
    }

    @Test fun aDraftWithAnUnsourcedClaimCannotBeApprovedYet() = runTest {
        val out = tools.execute("draft_document", obj("type" to "PUBLIC_COMMENT", "title" to "Park budget comment", "body" to "The park budget fell 40% since 2020."),
            ToolCtx(case, AppSettings()).let { it })
        // No claims listed: nothing to check, but it still waits for approval.
        assertFalse(out.text, out.isError)
        val d = db.documents().all().single()
        assertFalse(d.approved)
        val withClaim = tools.execute("draft_document", JsonObject(mapOf(
            "type" to JsonPrimitive("PUBLIC_COMMENT"), "title" to JsonPrimitive("Park comment v2"), "body" to JsonPrimitive("The park budget fell 40%."),
            "claims" to kotlinx.serialization.json.JsonArray(listOf(JsonObject(mapOf("text" to JsonPrimitive("The park budget fell 40%."))))))),
            ToolCtx(case, AppSettings()))
        assertTrue(withClaim.text, withClaim.text.contains("must be sourced or cut"))
        assertEquals("Some claims need a source shows on the card", true, withClaim.cards.single().body.contains("need a source"))
        assertTrue(db.documents().all().none { it.approved })
    }

    @Test fun waitingApprovalsAreInTheChiefsBrief() = runTest {
        finishSetup()
        tools.execute("queue_follow_up", obj("contact" to "Ana", "message" to "Thanks for coffee"), ToolCtx(case, AppSettings()))
        assertTrue(case.brief().contains("Waiting for the user's approval"))
    }

    @Test fun moneyInAndOutIsKeptSeparately() = runTest {
        tools.execute("log_money", obj("direction" to "in", "amount_dollars" to 25, "person" to "Ana"), ToolCtx(case, AppSettings()))
        tools.execute("log_money", obj("direction" to "In-kind", "amount_dollars" to 19.99, "person" to "Print shop"), ToolCtx(case, AppSettings()))
        tools.execute("log_money", obj("direction" to "OUT", "amount_dollars" to 10, "purpose" to "Flyers"), ToolCtx(case, AppSettings()))
        val m = db.money().all()
        assertEquals(2500L, m.filter { it.direction == "IN" }.sumOf { it.amountCents })
        assertEquals(1000L, m.filter { it.direction == "OUT" }.sumOf { it.amountCents })
        assertEquals("cents are rounded, not cut", 1999L, m.single { it.direction == "IN_KIND" }.amountCents)
    }

    @Test fun anApprovedFollowUpShowsInTheContactsHistoryButIsNotTwoWay() = runTest {
        tools.execute("add_contact", obj("name" to "Ana"), ToolCtx(case, AppSettings()))
        tools.execute("queue_follow_up", obj("contact" to "Ana", "message" to "Thanks for coffee!"), ToolCtx(case, AppSettings(), approved = true))
        val i = db.interactions().all().single()
        assertTrue(i.notes.contains("Thanks for coffee!"))
        assertFalse(i.twoWay)
        assertEquals(0, case.stats().twoWayContacts)
    }

    /** With no AI at all: offices typed in by hand are enough for the stage 2 quiz. */
    @Test fun withNoAiHandEnteredOfficesBuildTheTownQuiz() = runTest {
        listOf(
            com.emotionalleaves.ballot.data.Office(body = "City Council", district = "Ward 2", level = "LOCAL", incumbent = "Rosa Lee", term = "4 years", nextElection = "Nov 2027", powers = "Budget and zoning"),
            com.emotionalleaves.ballot.data.Office(body = "School Board", level = "SCHOOL", incumbent = "Tom Hale", term = "4 years", nextElection = "May 2027", powers = "Schools"),
            com.emotionalleaves.ballot.data.Office(body = "County Board", level = "COUNTY", incumbent = "Ann Cho", term = "2 years", nextElection = "Nov 2026", powers = "Roads"),
        ).forEach { db.offices().upsert(it) }
        val quiz = case.townQuiz(seed = 3)
        assertTrue("got ${quiz.size}", quiz.size >= 8)
    }

    /** A snapshot taken while the app is open is a complete, readable copy of the case file. */
    @Test fun aSnapshotIsACompleteReadableCopy() = runTest {
        val live = Room.databaseBuilder(ctx, BallotDatabase::class.java, BallotDatabase.NAME).allowMainThreadQueries()
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).build()
        try {
            live.contacts().upsert(com.emotionalleaves.ballot.data.Contact(name = "Mia"))
            val snap = com.emotionalleaves.ballot.data.Snapshots.take(ctx, live)
            assertNotNull(snap)
            val copy = Room.databaseBuilder(ctx, BallotDatabase::class.java, snap!!.absolutePath).allowMainThreadQueries().build()
            try { assertEquals("Mia", copy.contacts().all().single().name) } finally { copy.close() }
        } finally { live.close(); ctx.deleteDatabase(BallotDatabase.NAME) }
    }

    @Test fun importingReplacesWhatWasThereInOneGo() = runTest {
        tools.execute("add_contact", obj("name" to "Old Friend"), ToolCtx(case, AppSettings()))
        val other = Room.inMemoryDatabaseBuilder(ctx, BallotDatabase::class.java).allowMainThreadQueries().build()
        try {
            other.contacts().upsert(com.emotionalleaves.ballot.data.Contact(name = "From the backup"))
            val file = com.emotionalleaves.ballot.data.Backup.export(other, "passphrase1".toCharArray())
            com.emotionalleaves.ballot.data.Backup.import(db, file, "passphrase1".toCharArray())
            assertEquals(listOf("From the backup"), db.contacts().all().map { it.name })
        } finally { other.close() }
    }

    @Test fun aPrepCardIsBuiltAndSavedOnTheEvent() = runTest {
        case.updateProfile { it.copy(issues = "Transit\nHousing") }
        tools.execute("add_event", obj("title" to "Transit board meeting", "start" to "2030-01-07T18:30"), ToolCtx(case, AppSettings()))
        val e = db.events().all().single()
        val card = case.prepCard(e.id)!!
        assertTrue(card, card.contains("Transit"))
        assertEquals(card, db.events().get(e.id)!!.preCard)
    }

    @Test fun aDeadlineTypedTwiceIsOneDeadlineWithItsDraft() = runTest {
        finishSetup()
        val day = java.time.LocalDate.now().plusDays(4).toEpochDay()
        val a = case.addDeadlineByHand("Candidate filing closes", day)
        val b = case.addDeadlineByHand(" candidate filing closes ", day)
        assertEquals(a, b)
        assertEquals(1, db.deadlines().all().size)
        assertTrue("a legal deadline inside a week gets its task", db.tasks().open().any { it.legal })
    }

    @Test fun tasksWithAKeyAreAddedOnce() = runTest {
        val t = com.emotionalleaves.ballot.data.Task(stage = 1, title = "Get donor details", templateKey = "donor.1")
        assertNotNull(case.addTaskOnce(t))
        assertEquals(null, case.addTaskOnce(t))
        val again = com.emotionalleaves.ballot.data.Task(stage = 1, title = "Get to yes", templateKey = "gonogo.0")
        val id = case.addTaskOnce(again, againOnceDone = true)!!
        case.completeTask(db.tasks().get(id)!!)
        assertNotNull("a check can come back once the old one is done", case.addTaskOnce(again, againOnceDone = true))
    }

    @Test fun aBackupBringsBackYourSettingsButNeverTheLock() = runTest {
        val store = SettingsStore(ctx)
        store.update { it.copy(sleepStartMin = 23 * 60, darkTheme = true, appLock = false) }
        val file = com.emotionalleaves.ballot.data.Backup.export(db, "passphrase1".toCharArray(), store)
        store.update { it.copy(sleepStartMin = 20 * 60, darkTheme = false, appLock = true) }
        com.emotionalleaves.ballot.data.Backup.import(db, file, "passphrase1".toCharArray(), store)
        val s = store.get()
        assertEquals(23 * 60, s.sleepStartMin)
        assertTrue(s.darkTheme)
        assertTrue("the lock you have now stays", s.appLock)
        store.update { AppSettings() }
    }

    @Test fun approvalsLeftForAMonthCloseWithoutSending() = runTest {
        val old = db.pending().upsert(com.emotionalleaves.ballot.data.PendingAction(kind = "SEND", summary = "Thank Ana", createdAt = com.emotionalleaves.ballot.data.now() - 31L * 86_400_000L))
        val fresh = db.pending().upsert(com.emotionalleaves.ballot.data.PendingAction(kind = "SEND", summary = "Thank Ben"))
        assertEquals(1, case.expireStaleApprovals())
        assertEquals("EXPIRED", db.pending().get(old)!!.status)
        assertEquals("WAITING", db.pending().get(fresh)!!.status)
    }

    @Test fun aMeetingSoonIsRemindedOnce() = runTest {
        val at = com.emotionalleaves.ballot.data.now()
        db.events().upsert(com.emotionalleaves.ballot.data.Event(title = "Council", startAt = at + 2 * 3_600_000L))
        db.events().upsert(com.emotionalleaves.ballot.data.Event(title = "Next week", startAt = at + 7 * 86_400_000L))
        assertEquals(listOf("Council"), case.eventsToRemind(at).map { it.title })
        assertTrue("only once", case.eventsToRemind(at).isEmpty())
    }

    @Test fun oldBackgroundChatterIsPrunedButYourConversationStays() = runTest {
        val at = com.emotionalleaves.ballot.data.now()
        val old = at - 60L * 86_400_000L
        db.chat().insert(com.emotionalleaves.ballot.data.ChatMessage(thread = "background", role = "assistant", text = "old job", at = old))
        db.chat().insert(com.emotionalleaves.ballot.data.ChatMessage(thread = "main", role = "user", text = "my old question", at = old))
        db.chat().insert(com.emotionalleaves.ballot.data.ChatMessage(thread = "room:door", role = "user", text = "old rep", at = at - 100L * 86_400_000L))
        assertEquals(2, case.prune(at))
        assertEquals(listOf("my old question"), db.chat().all().map { it.text })
    }

    @Test fun noBackupForAMonthPutsOneTaskOnToday() = runTest {
        finishSetup()
        tools.execute("add_contact", obj("name" to "Lee"), ToolCtx(case, AppSettings()))
        assertTrue(case.remindBackup(lastBackupAt = 0))
        assertFalse("once a month at most", case.remindBackup(lastBackupAt = 0))
        assertFalse(case.remindBackup(lastBackupAt = com.emotionalleaves.ballot.data.now()))
    }

    @Test fun mergingTwoRecordsForOnePersonKeepsEverything() = runTest {
        val a = case.logInteraction("Ana", "Met at the library", twoWay = true)
        val b = case.logInteraction("Ana Ruiz", "Coffee", twoWay = true)
        db.contacts().upsert(db.contacts().get(b.id)!!.copy(phone = "555-0100"))
        val m = case.mergeContacts(a.id, b.id)!!
        assertEquals("555-0100", m.phone)
        assertEquals(1, db.contacts().all().size)
        assertEquals(2, db.interactions().all().count { it.contactId == a.id })
    }

    @Test fun restoringASnapshotKeepsAnUndoCopy() = runTest {
        val live = ctx.getDatabasePath(BallotDatabase.NAME).apply { parentFile?.mkdirs(); writeText("current case file") }
        val snap = java.io.File(com.emotionalleaves.ballot.data.Snapshots.dir(ctx), "ballot-2026-01-01.db").apply { writeText("older case file") }
        com.emotionalleaves.ballot.data.Snapshots.restore(ctx, snap)
        assertEquals("older case file", live.readText())
        val undo = com.emotionalleaves.ballot.data.Snapshots.list(ctx).first { it.name.startsWith("undo-") }
        assertEquals("current case file", undo.readText())
        live.delete(); snap.delete(); undo.delete()
    }

    @Test fun markingADeadlineDoneTicksOffItsFilingTask() = runTest {
        finishSetup()
        val id = case.addDeadlineByHand("Filing closes", java.time.LocalDate.now().plusDays(3).toEpochDay())
        assertTrue(db.tasks().open().any { it.deadlineId == id })
        case.setDeadlineDone(db.deadlines().get(id)!!, true)
        assertFalse(db.tasks().open().any { it.deadlineId == id })
    }

    @Test fun voterNotesAreClearedNinetyDaysAfterTheElectionKeepingTotals() = runTest {
        val day = 86_400_000L
        val at = com.emotionalleaves.ballot.data.now()
        case.updateProfile { it.copy(electionDateEpochDay = at / day - 91) }
        db.voterContacts().upsert(com.emotionalleaves.ballot.data.VoterContact(household = "12 Elm", outcome = com.emotionalleaves.ballot.data.DoorOutcome.SUPPORTER))
        case.prune(at)
        assertTrue(db.voterContacts().all().isEmpty())
        assertTrue(db.lessons().all().any { it.what.contains("1 doors, 1 supporters") })
    }

    @Test fun deletingAPersonRemovesTheirConversationsButKeepsGifts() = runTest {
        val a = case.logInteraction("Ana", "Coffee")
        db.money().upsert(com.emotionalleaves.ballot.data.Money(direction = "IN", amountCents = 2500, person = "Ana", contactId = a.id))
        case.deleteContact(db.contacts().get(a.id)!!)
        assertTrue(db.interactions().all().isEmpty())
        assertEquals(0L, db.money().all().single().contactId)
        assertEquals("Ana", db.money().all().single().person)
    }

    @Test fun aBackupCanBeCheckedWithoutImportingIt() = runTest {
        finishSetup()
        case.logInteraction("Ana", "Coffee")
        val file = com.emotionalleaves.ballot.data.Backup.export(db, "passphrase1".toCharArray())
        val summary = com.emotionalleaves.ballot.data.Backup.verify(file, "passphrase1".toCharArray())
        assertTrue(summary, summary.contains("1 people"))
        assertTrue(runCatching { com.emotionalleaves.ballot.data.Backup.verify(file, "wrong pass".toCharArray()) }.isFailure)
    }
}

