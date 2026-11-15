package com.emotionalleaves.ballot.core

/** Scores every office you are eligible for on 0 to 100 and shows the working. */
data class RaceInput(
    val officeId: Long,
    val name: String,
    val openSeat: Boolean,
    val incumbentRetiring: Boolean,
    val uncontestedHistory: Boolean,
    val votesNeeded: Int,
    val pastWinnerSpendCents: Long,
    val supportersInDistrict: Int,
    val issueOverlap: Int,          // 0..3: how many of your issues the office really controls
    val hoursPerWeek: Int,          // hours the office demands
    val yourFreeHoursPerWeek: Int,
    val monthsToFiling: Int,
    val stagesLeftBeforeRun: Int,
    val partTime: Boolean,
)

data class RaceScore(val officeId: Long, val name: String, val total: Int, val lines: List<ScoreLine>)

data class RaceRecommendation(val target: RaceScore?, val stretch: RaceScore?, val fallback: RaceScore?)

object RaceScorer {
    fun score(r: RaceInput): RaceScore {
        val lines = mutableListOf<ScoreLine>()
        val openness = (if (r.openSeat) 10 else 0) + (if (r.incumbentRetiring) 5 else 0) + (if (r.uncontestedHistory) 5 else 0)
        lines += ScoreLine("Openness", openness.coerceAtMost(20), listOfNotNull(
            "open seat".takeIf { r.openSeat }, "incumbent retiring".takeIf { r.incumbentRetiring },
            "history of uncontested races".takeIf { r.uncontestedHistory }).joinToString().ifEmpty { "incumbent running" })
        val size = when {
            r.votesNeeded <= 0 -> 5
            r.votesNeeded <= 1_500 -> 20
            r.votesNeeded <= 5_000 -> 15
            r.votesNeeded <= 15_000 -> 9
            r.votesNeeded <= 50_000 -> 4
            else -> 0
        }
        lines += ScoreLine("Size", size, if (r.votesNeeded > 0) "${r.votesNeeded} votes to win" else "No turnout data yet")
        val dollars = r.pastWinnerSpendCents / 100
        val cost = when {
            r.pastWinnerSpendCents <= 0 -> 5
            dollars <= 5_000 -> 15
            dollars <= 20_000 -> 11
            dollars <= 75_000 -> 6
            else -> 1
        }
        lines += ScoreLine("Cost", cost, if (dollars > 0) "Past winner spent about $$dollars" else "No finance data yet")
        val base = when {
            r.votesNeeded <= 0 -> (r.supportersInDistrict / 20).coerceAtMost(10)
            else -> ((r.supportersInDistrict.toDouble() / r.votesNeeded) * 60).toInt().coerceAtMost(15)
        }
        lines += ScoreLine("Your base", base, "${r.supportersInDistrict} contacts in the district")
        val fit = (r.issueOverlap.coerceIn(0, 3) * 4)
        lines += ScoreLine("Fit", fit, "${r.issueOverlap} of your issues are within its real powers")
        val load = when {
            r.hoursPerWeek <= 0 -> 4
            r.hoursPerWeek <= r.yourFreeHoursPerWeek && r.partTime -> 10
            r.hoursPerWeek <= r.yourFreeHoursPerWeek -> 7
            else -> 0
        }
        lines += ScoreLine("Load", load, if (r.hoursPerWeek > 0) "${r.hoursPerWeek} h/week against your ${r.yourFreeHoursPerWeek} free" else "Hours unknown")
        val timing = when {
            r.monthsToFiling <= 0 -> 2
            r.stagesLeftBeforeRun == 0 -> 8
            r.monthsToFiling >= r.stagesLeftBeforeRun * 3 -> 8
            r.monthsToFiling >= r.stagesLeftBeforeRun -> 4
            else -> 0
        }
        lines += ScoreLine("Timing", timing, "${r.monthsToFiling} months to filing, ${r.stagesLeftBeforeRun} stage(s) left")
        return RaceScore(r.officeId, r.name, lines.sumOf { it.points }.coerceIn(0, 100), lines)
    }

    /** One target, one stretch (bigger, still possible) and one fallback (smaller, safer). */
    fun recommend(scores: List<RaceScore>, inputs: List<RaceInput>): RaceRecommendation {
        if (scores.isEmpty()) return RaceRecommendation(null, null, null)
        val sorted = scores.sortedByDescending { it.total }
        val target = sorted.first()
        val votesOf = inputs.associate { it.officeId to it.votesNeeded }
        val tv = votesOf[target.officeId] ?: 0
        val rest = sorted.drop(1)
        val stretch = rest.filter { (votesOf[it.officeId] ?: 0) > tv }.maxByOrNull { it.total }
        val fallback = rest.filter { it != stretch && (votesOf[it.officeId] ?: 0) <= tv }.maxByOrNull { it.total }
            ?: rest.firstOrNull { it != stretch }
        return RaceRecommendation(target, stretch, fallback)
    }
}
