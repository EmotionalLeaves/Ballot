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

    /** One row of the money ledger as a report needs it. */
    data class Row(val date: String, val direction: String, val cents: Long, val person: String, val address: String,
                   val employer: String, val occupation: String, val category: String, val period: String, val memo: String, val ownFunds: Boolean)

    /** The ledger as CSV for a filing or a treasurer: quoted where needed, amounts in dollars, totals at the end. */
    fun csv(rows: List<Row>): String {
        fun q(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
        fun money(c: Long) = "%d.%02d".format(c / 100, kotlin.math.abs(c % 100))
        val head = "Date,Direction,Amount,Name,Address,Employer,Occupation,Category,Report period,Memo,Own funds"
        val body = rows.sortedBy { it.date }.map { r ->
            listOf(r.date, r.direction, money(r.cents), r.person, r.address, r.employer, r.occupation, r.category, r.period, r.memo, if (r.ownFunds) "yes" else "no")
                .joinToString(",") { q(it) }
        }
        val t = totals(rows.map { it.direction to it.cents })
        return (listOf(head) + body + listOf("", "Raised,,${money(t.raised)}", "In kind,,${money(t.inKind)}", "Spent,,${money(t.out)}", "Cash on hand,,${money(t.cashOnHand)}"))
            .joinToString("\n")
    }
}

