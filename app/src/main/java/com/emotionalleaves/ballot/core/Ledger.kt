package com.emotionalleaves.ballot.core

/** Money totals with one meaning everywhere: in-kind gifts count as raised but are never cash you can spend. */
data class LedgerTotals(val cashIn: Long, val inKind: Long, val out: Long) {
    val raised: Long get() = cashIn + inKind
    val cashOnHand: Long get() = cashIn - out
}

object Ledger {
    const val IN = "IN"
    const val OUT = "OUT"
    const val IN_KIND = "IN_KIND"

    /** Reads "in", "In-kind", "out " and the like into one stored spelling, or null if it isn't a direction. */
    fun parseDirection(raw: String): String? = when (raw.trim().uppercase().replace('-', '_').replace(' ', '_')) {
        "IN", "CONTRIBUTION", "DONATION", "GIFT" -> IN
        "OUT", "EXPENSE", "SPEND", "SPENT" -> OUT
        "IN_KIND", "INKIND" -> IN_KIND
        else -> null
    }

    /** [entries] are (direction, cents). */
    fun totals(entries: List<Pair<String, Long>>): LedgerTotals = LedgerTotals(
        cashIn = entries.filter { it.first == IN }.sumOf { it.second },
        inKind = entries.filter { it.first == IN_KIND }.sumOf { it.second },
        out = entries.filter { it.first == OUT }.sumOf { it.second },
    )
}
