package com.emotionalleaves.ballot.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

/** Work hours and sleep are quiet hours. Nothing is scheduled inside them. */
data class Availability(
    val workDays: Set<Int>,           // 1 = Monday .. 7 = Sunday
    val workStartMin: Int,
    val workEndMin: Int,
    val sleepStartMin: Int,
    val sleepEndMin: Int,
)

object Scheduling {
    fun parseDays(csv: String): Set<Int> = csv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()

    fun insideWork(a: Availability, t: LocalDateTime): Boolean {
        val m = t.hour * 60 + t.minute
        return t.dayOfWeek.value in a.workDays && m >= a.workStartMin && m < a.workEndMin
    }

    fun insideSleep(a: Availability, t: LocalDateTime): Boolean {
        val m = t.hour * 60 + t.minute
        return if (a.sleepStartMin > a.sleepEndMin) m >= a.sleepStartMin || m < a.sleepEndMin
        else m >= a.sleepStartMin && m < a.sleepEndMin
    }

    fun quiet(a: Availability, t: LocalDateTime) = insideWork(a, t) || insideSleep(a, t)

    /** Minutes free from t until the next work or sleep block (capped at 4 hours). */
    fun freeMinutesFrom(a: Availability, t: LocalDateTime): Int {
        if (quiet(a, t)) return 0
        var cur = t
        var free = 0
        while (free < 240 && !quiet(a, cur)) { cur = cur.plusMinutes(5); free += 5 }
        return free
    }

    /** The next moment that is not quiet, at or after t. */
    fun nextOpen(a: Availability, t: LocalDateTime): LocalDateTime {
        var cur = t
        var guard = 0
        while (quiet(a, cur) && guard < 7 * 24 * 12) { cur = cur.plusMinutes(5); guard++ }
        return cur
    }

    fun weekStart(d: LocalDate): LocalDate = d.with(DayOfWeek.MONDAY)
}

/** Lead-time alerts for deadlines: 14, 7 and 2 days by default. */
object DeadlineLogic {
    fun parseLeads(csv: String): List<Int> = csv.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it >= 0 }.sortedDescending()

    /** The lead alerts that are due today and have not been sent yet. */
    fun alertsDue(dueEpochDay: Long, todayEpochDay: Long, leads: List<Int>, alreadySent: Set<Int>): List<Int> {
        val daysLeft = dueEpochDay - todayEpochDay
        // The tightest lead time that applies: a deadline found late gets one alert, not one per lead time.
        return leads.filter { it !in alreadySent && daysLeft <= it && daysLeft >= 0 }.sorted().take(1)
    }

    /** Lead times an alert at [lead] covers: it and every longer one, so they never fire afterwards. */
    fun coveredBy(lead: Int, leads: List<Int>): Set<Int> = leads.filter { it >= lead }.toSet()

    fun needsPreparedDraft(dueEpochDay: Long, todayEpochDay: Long, hasDraft: Boolean): Boolean =
        !hasDraft && dueEpochDay - todayEpochDay in 0..7

    fun recheckDue(lastCheckedMillis: Long, nowMillis: Long, dueEpochDay: Long, todayEpochDay: Long, freshnessDays: Int): Boolean {
        val window = if (dueEpochDay - todayEpochDay <= 14) 2 else freshnessDays
        return nowMillis - lastCheckedMillis > window * 86_400_000L
    }
}

/** Counts only things that move a real political career. App time is never counted. */
data class WeekCounts(
    val conversations: Int = 0,
    val meetings: Int = 0,
    val comments: Int = 0,
    val newContacts: Int = 0,
    val followUpsOnTime: Int = 0,
    val speakingReps: Int = 0,
    val exitTestsPassed: Int = 0,
    val signatures: Int = 0,
    val dollarsRaised: Long = 0,
    val doors: Int = 0,
    val supportersIdentified: Int = 0,
)

object WeeklyScore {
    fun score(w: WeekCounts): Int =
        w.conversations * 3 + w.meetings * 5 + w.comments * 8 + w.newContacts * 2 + w.followUpsOnTime * 2 +
            w.speakingReps + w.exitTestsPassed * 25 + w.signatures / 5 + (w.dollarsRaised / 100).toInt() / 50 +
            w.doors / 5 + w.supportersIdentified

    /** True when the score dropped two weeks in a row: a proactive trigger. */
    fun droppingTwice(history: List<Int>): Boolean =
        history.size >= 3 && history[history.size - 1] < history[history.size - 2] && history[history.size - 2] < history[history.size - 3]

    fun flat(history: List<Int>): Boolean = history.size >= 2 && history.last() <= history[history.size - 2]
}

/** Deterministic speaking scores. The Chief explains them but does not compute them. */
data class SpeechMetrics(val words: Int, val wpm: Int, val fillers: Int, val clarity: Int, val pace: Int, val lengthScore: Int, val total: Int)

object SpeechScorer {
    private val fillerRegex = Regex("""\b(um+|uh+|erm|like|you know|i mean|sort of|kind of|basically|actually|literally|so yeah)\b""", RegexOption.IGNORE_CASE)

    fun fillers(text: String): Int = fillerRegex.findAll(text).count()

    fun score(transcript: String, durationSec: Int, targetSec: Int): SpeechMetrics {
        val words = transcript.split(Regex("\\s+")).count { it.isNotBlank() }
        val secs = durationSec.coerceAtLeast(1)
        val wpm = (words * 60) / secs
        val f = fillers(transcript)
        val fillerRate = if (words == 0) 1.0 else f.toDouble() / words
        val clarity = (100 - (fillerRate * 600).toInt()).coerceIn(0, 100)
        val pace = when {
            words == 0 -> 0
            wpm in 130..165 -> 100
            wpm in 110..185 -> 75
            wpm in 90..210 -> 45
            else -> 20
        }
        val ratio = secs.toDouble() / targetSec.coerceAtLeast(1)
        val length = when {
            ratio in 0.85..1.1 -> 100
            ratio in 0.7..1.25 -> 70
            ratio in 0.5..1.5 -> 40
            else -> 15
        }
        val total = if (words == 0) 0 else (clarity * 0.4 + pace * 0.3 + length * 0.3).toInt()
        return SpeechMetrics(words, wpm, f, clarity, pace, length, total)
    }

    fun plainFeedback(m: SpeechMetrics, targetSec: Int): List<String> = buildList {
        if (m.words == 0) { add("Nothing was heard. Check the microphone and try again."); return@buildList }
        if (m.fillers > 2) add("${m.fillers} filler words. Pause silently instead.")
        if (m.wpm > 175) add("Fast at ${m.wpm} words a minute. Slow to about 150.")
        if (m.wpm < 120) add("Slow at ${m.wpm} words a minute. Lift the pace a little.")
        if (m.lengthScore < 70) add("Aim for $targetSec seconds.")
        if (isEmpty()) add("Clear, steady and on time. Keep it.")
    }
}
