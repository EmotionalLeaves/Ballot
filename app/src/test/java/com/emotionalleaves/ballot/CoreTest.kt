package com.emotionalleaves.ballot

import com.emotionalleaves.ballot.core.CampaignMath
import com.emotionalleaves.ballot.core.CaseStats
import com.emotionalleaves.ballot.core.DeadlineLogic
import com.emotionalleaves.ballot.core.MathInput
import com.emotionalleaves.ballot.core.RaceInput
import com.emotionalleaves.ballot.core.RaceScorer
import com.emotionalleaves.ballot.core.ScoreContext
import com.emotionalleaves.ballot.core.ScoreInput
import com.emotionalleaves.ballot.core.Availability
import com.emotionalleaves.ballot.core.Scheduling
import com.emotionalleaves.ballot.core.SpeechScorer
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.StageRules
import com.emotionalleaves.ballot.core.TaskScorer
import com.emotionalleaves.ballot.core.WeekCounts
import com.emotionalleaves.ballot.core.WeeklyScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CampaignMathTest {
    @Test fun singleSeatWinNumberIsHalfPlusOnePlusMargin() {
        assertEquals(501, CampaignMath.winNumber(1000, 1, 0))
        assertEquals(552, CampaignMath.winNumber(1000, 1, 10))
    }

    @Test fun multiSeatUsesDroopStyleThreshold() {
        assertEquals(251, CampaignMath.winNumber(1000, 3, 0))
        assertEquals(277, CampaignMath.winNumber(1000, 3, 10))
    }

    @Test fun zeroTurnoutGivesZero() {
        assertEquals(0, CampaignMath.winNumber(0, 1, 10))
        assertEquals(0, CampaignMath.conversationsNeeded(0, 50))
    }

    @Test fun contactGoalAndHours() {
        assertEquals(1104, CampaignMath.conversationsNeeded(552, 50))
        assertEquals(138, CampaignMath.hoursNeeded(1104, 8))
    }

    @Test fun mathThatDoesNotCloseNamesTheShortLever() {
        val r = CampaignMath.run(MathInput(expectedTurnout = 10_000, weeksRemaining = 4, yourHoursPerWeek = 5))
        assertFalse(r.closes)
        assertTrue(r.shortLevers.any { it.startsWith("Time") })
        assertTrue(r.shortLevers.any { it.startsWith("People") })
    }

    @Test fun mathThatClosesHasNoLevers() {
        val r = CampaignMath.run(MathInput(expectedTurnout = 400, weeksRemaining = 12, yourHoursPerWeek = 6))
        assertTrue(r.closes)
        assertEquals(222, r.winNumber)
    }

    @Test fun identifiedSupportersReduceTheGoal() {
        val a = CampaignMath.run(MathInput(expectedTurnout = 1000, weeksRemaining = 10, yourHoursPerWeek = 10))
        val b = CampaignMath.run(MathInput(expectedTurnout = 1000, weeksRemaining = 10, yourHoursPerWeek = 10, supportersIdentified = 200))
        assertTrue(b.conversationsNeeded < a.conversationsNeeded)
    }
}

class TaskScorerTest {
    private val ctx = ScoreContext(todayEpochDay = 20_000, freeMinutesNow = 30, lowEnergy = false, highEffortDoneToday = false, insideWorkHours = false)
    private fun t(id: Long, due: Long = 0, legal: Boolean = false, human: Boolean = false, exit: Boolean = false, size: Int = 15, high: Boolean = false, skips: Int = 0) =
        ScoreInput(id, "t$id", due, legal, exit, human, 0, size, high, skips)

    @Test fun legalDeadlineOutranksEverything() {
        val ranked = TaskScorer.rank(listOf(t(1, human = true, exit = true), t(2, due = 20_005, legal = true)), ctx)
        assertEquals(2L, ranked.first().id)
    }

    @Test fun humanContactBeatsDeskWork() {
        val ranked = TaskScorer.rank(listOf(t(1), t(2, human = true)), ctx)
        assertEquals(2L, ranked.first().id)
    }

    @Test fun humanContactIsBlockedInsideWorkHours() {
        val s = TaskScorer.score(t(1, human = true), ctx.copy(insideWorkHours = true))
        assertNotNull(s.blocked)
    }

    @Test fun onlyOneHighEffortTaskADay() {
        val s = TaskScorer.score(t(1, high = true), ctx.copy(highEffortDoneToday = true))
        assertNotNull(s.blocked)
        assertNull(TaskScorer.score(t(1, high = true), ctx).blocked)
    }

    @Test fun blockedTasksFallBelowUnblocked() {
        val ranked = TaskScorer.rank(listOf(t(1, high = true, due = 20_001, legal = true), t(2)), ctx.copy(highEffortDoneToday = true))
        assertEquals(2L, ranked.first().id)
    }

    @Test fun threeSkipsMeansReshape() {
        assertTrue(TaskScorer.score(t(1, skips = 3), ctx).needsReshape)
        assertFalse(TaskScorer.score(t(1, skips = 2), ctx).needsReshape)
    }

    @Test fun everyScoreShowsItsWorking() {
        val s = TaskScorer.score(t(1, due = 20_003, legal = true, human = true, exit = true), ctx)
        assertEquals(s.total, s.lines.sumOf { it.points })
        assertTrue(s.lines.any { it.factor == "Deadline pressure" })
    }

    @Test fun deadlinesWithinSevenDaysNeedDrafts() {
        val need = TaskScorer.deadlinesNeedingDrafts(listOf(1L to 20_003L, 2L to 20_030L, 3L to 20_006L), setOf(3L), 20_000)
        assertEquals(listOf(1L), need)
    }
}

class StageRulesTest {
    @Test fun storyNeedsLockAndClear() {
        assertFalse(StageRules.exit(Stage.REASON, CaseStats(storyLocked = true)).met)
        assertTrue(StageRules.exit(Stage.REASON, CaseStats(storyLocked = true, storyRatedClear = true)).met)
    }

    @Test fun showUpNeedsTenMeetingsAndThreeComments() {
        assertFalse(StageRules.exit(Stage.SHOW_UP, CaseStats(meetingsAttended = 10, commentsGiven = 2)).met)
        assertTrue(StageRules.exit(Stage.SHOW_UP, CaseStats(meetingsAttended = 10, commentsGiven = 3)).met)
    }

    @Test fun baseNeedsTwoHundredTwoWayContacts() {
        assertFalse(StageRules.exit(Stage.BASE, CaseStats(twoWayContacts = 199)).met)
        assertTrue(StageRules.exit(Stage.BASE, CaseStats(twoWayContacts = 200)).met)
    }

    @Test fun stagesThreeToFiveCannotBeSkipped() {
        assertFalse(StageRules.reachable(Stage.BASE, Stage.SHOW_UP, CaseStats(), openSeatFastLane = false))
        assertFalse(StageRules.reachable(Stage.SERVE, Stage.SHOW_UP, CaseStats(meetingsAttended = 2), openSeatFastLane = false))
        assertTrue(StageRules.reachable(Stage.SERVE, Stage.SHOW_UP, CaseStats(meetingsAttended = 10, commentsGiven = 3), openSeatFastLane = false))
    }

    @Test fun openSeatFastLaneBringsTheDecisionForward() {
        assertTrue(StageRules.reachable(Stage.CHOOSE, Stage.SHOW_UP, CaseStats(), openSeatFastLane = true))
        assertFalse(StageRules.reachable(Stage.CHOOSE, Stage.GROUND, CaseStats(), openSeatFastLane = true))
    }

    @Test fun everyStageHasAPlaybookWithUniqueKeys() {
        val keys = Stage.entries.flatMap { StageRules.playbook(it) }.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        Stage.entries.forEach { assertTrue("stage ${it.number}", StageRules.playbook(it).isNotEmpty()) }
    }

    @Test fun playbookTasksAreBeginnerSized() {
        Stage.entries.flatMap { StageRules.playbook(it) }.forEach {
            assertTrue(it.sizeMinutes in setOf(5, 15, 45))
            assertTrue(it.steps.isNotEmpty())
            assertTrue(it.why.isNotBlank() && it.doneLooksLike.isNotBlank())
        }
    }
}

class SchedulingTest {
    private val a = Availability(setOf(1, 2, 3, 4, 5), 7 * 60, 15 * 60 + 30, 21 * 60 + 30, 6 * 60)

    @Test fun workHoursAreQuiet() {
        assertTrue(Scheduling.insideWork(a, LocalDateTime.of(2026, 11, 16, 10, 0))) // Monday
        assertFalse(Scheduling.insideWork(a, LocalDateTime.of(2026, 11, 15, 10, 0))) // Sunday
        assertFalse(Scheduling.insideWork(a, LocalDateTime.of(2026, 11, 16, 16, 0)))
    }

    @Test fun sleepWrapsMidnight() {
        assertTrue(Scheduling.insideSleep(a, LocalDateTime.of(2026, 11, 16, 23, 0)))
        assertTrue(Scheduling.insideSleep(a, LocalDateTime.of(2026, 11, 16, 5, 0)))
        assertFalse(Scheduling.insideSleep(a, LocalDateTime.of(2026, 11, 16, 18, 0)))
    }

    @Test fun nextOpenSkipsWork() {
        val n = Scheduling.nextOpen(a, LocalDateTime.of(2026, 11, 16, 9, 0))
        assertFalse(Scheduling.quiet(a, n))
        assertEquals(15, n.hour)
    }

    @Test fun deadlineAlertsFireOncePerLead() {
        assertEquals(listOf(14), DeadlineLogic.alertsDue(100, 86, listOf(14, 7, 2), emptySet()))
        assertEquals(emptyList<Int>(), DeadlineLogic.alertsDue(100, 87, listOf(14, 7, 2), setOf(14)))
        assertEquals(listOf(7), DeadlineLogic.alertsDue(100, 93, listOf(14, 7, 2), setOf(14)))
        assertEquals(emptyList<Int>(), DeadlineLogic.alertsDue(100, 101, listOf(14, 7, 2), emptySet()))
    }

    @Test fun aDeadlineFoundLateGetsOneAlertNotThree() {
        // One day left and nothing sent yet: the 2-day alert fires and covers 7 and 14 too.
        assertEquals(listOf(2), DeadlineLogic.alertsDue(100, 99, listOf(14, 7, 2), emptySet()))
        val sent = DeadlineLogic.coveredBy(2, listOf(14, 7, 2))
        assertEquals(setOf(2, 7, 14), sent)
        assertEquals(emptyList<Int>(), DeadlineLogic.alertsDue(100, 99, listOf(14, 7, 2), sent))
    }

    @Test fun preparedDraftNeededWithinSevenDays() {
        assertTrue(DeadlineLogic.needsPreparedDraft(107, 100, false))
        assertFalse(DeadlineLogic.needsPreparedDraft(108, 100, false))
        assertFalse(DeadlineLogic.needsPreparedDraft(103, 100, true))
    }
}

class ScoresTest {
    @Test fun weeklyScoreIgnoresNothingButRealWork() {
        assertEquals(0, WeeklyScore.score(WeekCounts()))
        assertTrue(WeeklyScore.score(WeekCounts(conversations = 3)) > 0)
    }

    @Test fun droppingTwoWeeksInARowIsATrigger() {
        assertTrue(WeeklyScore.droppingTwice(listOf(30, 20, 10)))
        assertFalse(WeeklyScore.droppingTwice(listOf(30, 20, 25)))
    }

    @Test fun speechScoringIsPlainCode() {
        val words = (1..150).joinToString(" ") { "word" }
        val good = SpeechScorer.score(words, 60, 60)
        assertEquals(150, good.wpm)
        assertEquals(0, good.fillers)
        assertTrue(good.total >= 90)
        val filler = SpeechScorer.score("um so like I mean uh basically we um need like parks", 10, 60)
        assertTrue(filler.fillers >= 6)
        assertTrue(filler.clarity < 50)
        assertEquals(0, SpeechScorer.score("", 30, 60).total)
    }

    @Test fun raceScoresAreBoundedAndExplained() {
        val r = RaceInput(1, "School board", true, false, true, 800, 300_000, 40, 2, 6, 8, 10, 2, true)
        val s = RaceScorer.score(r)
        assertTrue(s.total in 0..100)
        assertEquals(7, s.lines.size)
        val big = RaceScorer.score(r.copy(officeId = 2, name = "State senate", openSeat = false, uncontestedHistory = false, votesNeeded = 40_000, pastWinnerSpendCents = 20_000_000, partTime = false, hoursPerWeek = 30))
        assertTrue(s.total > big.total)
        val rec = RaceScorer.recommend(listOf(s, big), listOf(r, r.copy(officeId = 2, votesNeeded = 40_000)))
        assertEquals(1L, rec.target?.officeId)
        assertEquals(2L, rec.stretch?.officeId)
    }
}

class QuizBuilderTest {
    private val offices = listOf(
        com.emotionalleaves.ballot.core.QuizOffice(1, "City Council", district = "Ward 2", level = "LOCAL", powers = "City budget and zoning", term = "4 years", nextElection = "November 2027", incumbent = "Rosa Lee"),
        com.emotionalleaves.ballot.core.QuizOffice(2, "School Board", level = "SCHOOL", powers = "School budget and hiring the superintendent", term = "4 years", nextElection = "May 2027", incumbent = "Tom Hale"),
        com.emotionalleaves.ballot.core.QuizOffice(3, "County Board", district = "District 5", level = "COUNTY", powers = "Roads, jails and county parks", term = "2 years", nextElection = "November 2026", incumbent = "Ann Cho"),
        com.emotionalleaves.ballot.core.QuizOffice(4, "Parks Commission", level = "LOCAL", powers = "Advises on park plans", appointed = true, incumbent = "Joe Park"),
    )

    @Test fun everyQuestionComesFromTheCaseFileWithTheRightAnswerAmongTheOptions() {
        val qs = com.emotionalleaves.ballot.core.QuizBuilder.build(offices, emptyList(), seed = 42)
        assertEquals(10, qs.size)
        qs.forEach { q ->
            assertTrue(q.q, q.answer in q.options.indices)
            assertTrue(q.q, q.options.size >= 2 && q.options.distinct().size == q.options.size)
            assertTrue(q.source, q.source.matches(Regex("[OR]\\d+")))
        }
        // The holder question's right answer is the stored incumbent.
        qs.filter { it.q.startsWith("Who holds") }.forEach { q ->
            val id = q.source.drop(1).toLong()
            assertEquals(offices.first { it.id == id }.incumbent, q.options[q.answer])
        }
    }

    @Test fun sameSeedSameQuiz() {
        assertEquals(com.emotionalleaves.ballot.core.QuizBuilder.build(offices, emptyList(), 7), com.emotionalleaves.ballot.core.QuizBuilder.build(offices, emptyList(), 7))
    }

    @Test fun anEmptyCaseFileGivesNoQuestionsRatherThanInventedOnes() {
        assertTrue(com.emotionalleaves.ballot.core.QuizBuilder.build(emptyList(), emptyList(), 1).isEmpty())
    }
}

class ValuesInterviewTest {
    private val v = com.emotionalleaves.ballot.core.ValuesInterview
    @Test fun answersRoundTrip() {
        val a = listOf("Potholes", "Fix the bus line", "Night-shift workers", "My mum's clinic closed", "Lie about opponents")
        assertEquals(a, v.decode(v.encode(a)))
        assertEquals(5, v.answered(v.encode(a)))
    }
    @Test fun oldFreeTextIsKept() {
        assertEquals("Fairness and plain talk", v.decode("Fairness and plain talk")[0])
        assertEquals(0, v.answered(""))
    }
}

class TemplatesTest {
    private val f = com.emotionalleaves.ballot.core.TemplateFacts(name = "Jade", place = "Lincoln", why = "I cook for students and see what they go without.",
        issues = listOf("Transit", "Housing"), positions = listOf("Transit" to "Run the 24 bus until midnight."))

    @Test fun aCommentUsesYourOwnWordsAndLeavesGapsMarked() {
        val d = com.emotionalleaves.ballot.core.Templates.draft("PUBLIC_COMMENT", "transit", f)
        assertTrue(d, d.contains("My name is Jade and I live in Lincoln."))
        assertTrue(d, d.contains("Run the 24 bus until midnight."))
        assertTrue(d, d.contains("["))
        assertFalse("no indentation leaks into the draft", d.lines().any { it.startsWith(" ") })
    }

    @Test fun everyTypeGivesADraft() {
        listOf("PUBLIC_COMMENT", "STUMP_SPEECH", "PRESS_STATEMENT", "EMAIL", "DOOR_SCRIPT", "PALM_CARD", "APPLICATION", "LETTER", "QUESTIONNAIRE", "CRISIS_RESPONSE", "OTHER").forEach {
            assertTrue(it, com.emotionalleaves.ballot.core.Templates.draft(it, "parks", f).isNotBlank())
        }
    }

    @Test fun thePlatformListsOnlyWhatYouStated() {
        val p = com.emotionalleaves.ballot.core.Templates.platform(f)
        assertTrue(p, p.contains("Transit\nRun the 24 bus until midnight."))
        assertTrue(p, p.contains("Housing\n[What you'd do"))
    }
}

class BlockersTest {
    @Test fun aQuietWeekGetsPlainSuggestions() {
        val b = WeeklyScore.blockers(WeekCounts(), stage = 3, openTasks = 20, skippedTasks = 4)
        assertEquals(3, b.size)
        assertTrue(b[0], b[0].contains("skipped"))
        assertTrue(WeeklyScore.blockers(WeekCounts(conversations = 2, meetings = 1, speakingReps = 1), stage = 1, openTasks = 3, skippedTasks = 0).single().contains("5-minute"))
    }
}

class PrepCardTest {
    @Test fun aCardUsesOnlyYourOwnRecords() {
        val c = com.emotionalleaves.ballot.core.PrepCard.build("City Council: Transit budget hearing", "", listOf("Transit", "Housing"),
            listOf("Transit" to "Run the 24 bus until midnight."), listOf("Ana" to "Met at the transit hearing", "Ben" to "Neighbor"), hasApprovedComment = false)
        assertTrue(c, c.contains("Transit: Run the 24 bus until midnight."))
        assertTrue(c, c.contains("Ana"))
        assertFalse(c, c.contains("Ben"))
        assertTrue(c, c.contains("Attend once silently first"))
    }
}

class ClaimFinderTest {
    @Test fun findsCheckableSentencesAndSkipsGaps() {
        val found = com.emotionalleaves.ballot.core.ClaimFinder.find(
            "Good evening. The park budget fell 40% since 2020. [One fact from your life, e.g. 3 kids]\nI'm asking you to vote no. Our ward has the most potholes in the city. My two kids walk there every day. My opponent voted against it 3 times.")
        assertEquals(listOf("The park budget fell 40% since 2020.", "Our ward has the most potholes in the city.", "My opponent voted against it 3 times."), found)
    }
}

class GapsTest {
    @Test fun countsOnlyRealGaps() {
        assertEquals(2, com.emotionalleaves.ballot.core.Gaps.count("Hi [name], thanks. [One line.]"))
        assertEquals(0, com.emotionalleaves.ballot.core.Gaps.count("Array [1] and [] stay"))
        assertEquals("[name]", com.emotionalleaves.ballot.core.Gaps.first("Hi [name]"))
    }
}

class LedgerTest {
    @Test fun inKindIsRaisedButNotCash() {
        val t = com.emotionalleaves.ballot.core.Ledger.totals(listOf("IN" to 5000L, "IN_KIND" to 2000L, "OUT" to 1500L))
        assertEquals(7000L, t.raised)
        assertEquals(3500L, t.cashOnHand)
        assertEquals("IN_KIND", com.emotionalleaves.ballot.core.Ledger.parseDirection(" In-kind "))
        assertEquals(null, com.emotionalleaves.ballot.core.Ledger.parseDirection("sideways"))
    }
}

class PageTextTest {
    @Test fun theClockOnAPageIsNotAChange() {
        val a = "Filing period: Jan 5 to Feb 15\nLast updated: March 3, 2026\nVisitors: 10,452\n© 2026 County Clerk"
        val b = "Filing period: Jan 5 to Feb 15\nLast updated: April 9, 2026\nVisitors: 10,990\n© 2027 County Clerk"
        assertEquals(com.emotionalleaves.ballot.core.PageText.stable(a), com.emotionalleaves.ballot.core.PageText.stable(b))
        val c = "Filing period: Jan 5 to Feb 20\nLast updated: April 9, 2026"
        assertFalse(com.emotionalleaves.ballot.core.PageText.stable(a) == com.emotionalleaves.ballot.core.PageText.stable(c))
    }
}
