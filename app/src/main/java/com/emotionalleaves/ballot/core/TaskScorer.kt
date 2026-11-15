package com.emotionalleaves.ballot.core

/**
 * Picks the next action. Runs in plain code; the Chief explains the result but never computes it.
 * Every score comes with its working so you can tap a task and see why it ranked where it did.
 */
data class ScoreInput(
    val id: Long,
    val title: String,
    val dueEpochDay: Long,
    val legal: Boolean,
    val movesExit: Boolean,
    val humanContact: Boolean,
    val followUpSinceEpochDay: Long,
    val sizeMinutes: Int,
    val highEffort: Boolean,
    val skippedCount: Int,
    val hasPreparedDraft: Boolean = true,
)

data class ScoreContext(
    val todayEpochDay: Long,
    val freeMinutesNow: Int,
    val lowEnergy: Boolean,
    val highEffortDoneToday: Boolean,
    val insideWorkHours: Boolean,
)

data class ScoreLine(val factor: String, val points: Int, val note: String)

data class Scored(
    val id: Long,
    val total: Int,
    val lines: List<ScoreLine>,
    val blocked: String? = null,
    val needsReshape: Boolean = false,
)

object TaskScorer {
    const val AVOIDANCE_LIMIT = 3

    fun score(t: ScoreInput, c: ScoreContext): Scored {
        val lines = mutableListOf<ScoreLine>()

        // Deadline pressure: highest weight. Legal and filing dates outrank everything.
        if (t.dueEpochDay > 0) {
            val days = t.dueEpochDay - c.todayEpochDay
            val base = when {
                days < 0 -> 120
                days <= 2 -> 100
                days <= 7 -> 80
                days <= 14 -> 55
                days <= 30 -> 30
                else -> 10
            }
            val pts = if (t.legal) base + 40 else base
            lines += ScoreLine("Deadline pressure", pts,
                (if (days < 0) "Overdue by ${-days} day(s)" else "Due in $days day(s)") + if (t.legal) ", legal" else "")
        }
        if (t.movesExit) lines += ScoreLine("Stage progress", 30, "Moves the current exit test")
        if (t.humanContact) lines += ScoreLine("Human contact", 30, "Puts you in front of real people")
        if (t.followUpSinceEpochDay > 0) {
            val waited = (c.todayEpochDay - t.followUpSinceEpochDay).coerceAtLeast(0)
            val pts = (waited * 4).toInt().coerceAtMost(25)
            if (pts > 0) lines += ScoreLine("Decay", pts, "Waiting $waited day(s), losing value daily")
        }
        // Fit: matches the time and energy you have right now.
        val fit = when {
            t.sizeMinutes <= c.freeMinutesNow && !(t.highEffort && c.lowEnergy) -> 15
            t.highEffort && c.lowEnergy -> -15
            t.sizeMinutes > c.freeMinutesNow -> -10
            else -> 0
        }
        lines += ScoreLine("Fit", fit, when {
            fit > 0 -> "Fits your ${c.freeMinutesNow} free minutes"
            t.highEffort && c.lowEnergy -> "High effort on a low-energy day"
            else -> "Longer than the time you have"
        })
        // Avoidance: a task skipped three times is shrunk, re-explained or questioned, not repeated.
        val reshape = t.skippedCount >= AVOIDANCE_LIMIT
        if (t.skippedCount > 0) {
            lines += ScoreLine("Avoidance", -8 * t.skippedCount,
                if (reshape) "Skipped ${t.skippedCount} times: needs shrinking or rethinking" else "Skipped ${t.skippedCount} time(s)")
        }

        // Hard rules sit above the score.
        val blocked = when {
            c.insideWorkHours && t.humanContact -> "Not during work hours"
            t.highEffort && c.highEffortDoneToday -> "One high-effort task a day"
            else -> null
        }
        return Scored(t.id, lines.sumOf { it.points }, lines, blocked, reshape)
    }

    /** Highest score first; blocked tasks fall below every unblocked one. */
    fun rank(tasks: List<ScoreInput>, c: ScoreContext): List<Scored> =
        tasks.map { score(it, c) }.sortedWith(compareBy<Scored> { it.blocked != null }.thenByDescending { it.total })

    /** Legal deadlines within seven days that have no prepared draft yet. */
    fun deadlinesNeedingDrafts(deadlines: List<Pair<Long, Long>>, preparedIds: Set<Long>, today: Long): List<Long> =
        deadlines.filter { (id, due) -> due - today in 0..7 && id !in preparedIds }.map { it.first }
}
