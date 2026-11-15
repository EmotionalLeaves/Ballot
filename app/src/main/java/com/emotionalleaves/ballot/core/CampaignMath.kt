package com.emotionalleaves.ballot.core

import kotlin.math.ceil
import kotlin.math.max

/**
 * Campaign math. Inputs come from official past results for your district and your own measured
 * rates, never from estimates in the model's head.
 */
data class MathInput(
    val expectedTurnout: Int,
    val seats: Int = 1,
    val safetyMarginPct: Int = 10,
    val supportRatePct: Int = 50,
    val contactsPerHour: Int = 8,
    val weeksRemaining: Int,
    val yourHoursPerWeek: Int,
    val volunteerHoursPerWeek: Int = 0,
    val budgetCents: Long = 0,
    val raisedCents: Long = 0,
    val supportersIdentified: Int = 0,
)

data class MathResult(
    val winNumber: Int,
    val conversationsNeeded: Int,
    val hoursNeeded: Int,
    val hoursAvailable: Int,
    val weeklyConversations: Int,
    val weeklyHours: Int,
    val weeklyDollarsCents: Long,
    val closes: Boolean,
    val shortLevers: List<String>,
    val explanation: List<String>,
)

object CampaignMath {

    /**
     * Single seat: half the expected turnout, plus one, plus your safety margin.
     * Multi-seat (vote for N): a candidate is safe above turnout / (N + 1), so that threshold is used
     * instead, plus one, plus the margin. The Chief labels multi-seat numbers as an estimate.
     */
    fun winNumber(expectedTurnout: Int, seats: Int, safetyMarginPct: Int): Int {
        if (expectedTurnout <= 0) return 0
        val base = if (seats <= 1) expectedTurnout / 2 + 1 else expectedTurnout / (seats + 1) + 1
        return ceil(base * (1 + safetyMarginPct.coerceAtLeast(0) / 100.0)).toInt()
    }

    fun conversationsNeeded(winNumber: Int, supportRatePct: Int): Int {
        if (winNumber <= 0) return 0
        val rate = supportRatePct.coerceIn(1, 100) / 100.0
        return ceil(winNumber / rate).toInt()
    }

    fun hoursNeeded(conversations: Int, contactsPerHour: Int): Int =
        if (conversations <= 0) 0 else ceil(conversations / contactsPerHour.coerceAtLeast(1).toDouble()).toInt()

    fun run(i: MathInput): MathResult {
        val win = winNumber(i.expectedTurnout, i.seats, i.safetyMarginPct)
        val stillNeeded = max(0, win - i.supportersIdentified)
        val convs = conversationsNeeded(stillNeeded, i.supportRatePct)
        val hours = hoursNeeded(convs, i.contactsPerHour)
        val weeks = i.weeksRemaining.coerceAtLeast(1)
        val available = (i.yourHoursPerWeek + i.volunteerHoursPerWeek) * weeks
        val moneyLeft = max(0L, i.budgetCents - i.raisedCents)
        val short = mutableListOf<String>()
        if (hours > available) {
            if (i.volunteerHoursPerWeek == 0) short += "People: no volunteer hours yet"
            short += "Time: need $hours hours, have $available"
        }
        if (i.budgetCents > 0 && moneyLeft > 0 && i.weeksRemaining <= 4) short += "Money: ${moneyLeft / 100} dollars still to raise with ${i.weeksRemaining} week(s) left"
        val exp = listOf(
            if (i.seats <= 1) "Win number: ${i.expectedTurnout} expected votes ÷ 2 + 1, plus ${i.safetyMarginPct}% margin = $win"
            else "Win number (estimate, ${i.seats} seats): ${i.expectedTurnout} ÷ ${i.seats + 1} + 1, plus ${i.safetyMarginPct}% margin = $win",
            "Still needed: $win − ${i.supportersIdentified} identified = $stillNeeded supporters",
            "Conversations: $stillNeeded ÷ ${i.supportRatePct}% support rate = $convs",
            "Hours: $convs ÷ ${i.contactsPerHour} per hour = $hours",
            "Available: (${i.yourHoursPerWeek} yours + ${i.volunteerHoursPerWeek} volunteer) × $weeks weeks = $available",
        )
        return MathResult(
            winNumber = win,
            conversationsNeeded = convs,
            hoursNeeded = hours,
            hoursAvailable = available,
            weeklyConversations = ceil(convs / weeks.toDouble()).toInt(),
            weeklyHours = ceil(hours / weeks.toDouble()).toInt(),
            weeklyDollarsCents = ceil(moneyLeft / weeks.toDouble()).toLong(),
            closes = short.isEmpty(),
            shortLevers = short,
            explanation = exp,
        )
    }

    /** A line-item budget built backward from the plan. */
    data class BudgetLine(val item: String, val cents: Long)

    fun budget(filingFeeCents: Long, doors: Int, signs: Int, mailPieces: Int): List<BudgetLine> = listOf(
        BudgetLine("Filing fee", filingFeeCents),
        BudgetLine("Palm cards (${doors} doors, 1 each)", doors * 12L),
        BudgetLine("Yard signs ($signs)", signs * 450L),
        BudgetLine("Mail ($mailPieces pieces)", mailPieces * 65L),
        BudgetLine("Tools and printing", 15_000L),
    ).filter { it.cents > 0 }
}
