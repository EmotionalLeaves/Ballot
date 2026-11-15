package com.emotionalleaves.ballot.core

/** One gift or expense as the limit check needs it. [donorKey] is the contact id when known, else the name. */
data class LimitEntry(val direction: String, val cents: Long, val person: String, val contactId: Long = 0, val ownFunds: Boolean = false)

/** A limit from the official rules: PER_DONOR (per person) or REPORT_TRIGGER (totals that require a report). */
data class Limit(val kind: String, val cents: Long, val where: String)

/**
 * Contribution limits and reporting triggers in plain code. A donor is matched by contact first, then by name
 * with case and spacing ignored, so "Ana Ruiz" and "ana  ruiz" are one person. Warns at 80%; over the limit
 * says plainly what to do.
 */
object LimitCheck {
    fun key(e: LimitEntry) = if (e.contactId != 0L) "c${e.contactId}" else "n" + e.person.trim().lowercase().replace(Regex("\\s+"), " ")

    fun warnings(entries: List<LimitEntry>, limits: List<Limit>): List<String> {
        val out = mutableListOf<String>()
        val gifts = entries.filter { it.direction != Ledger.OUT && !it.ownFunds && it.person.isNotBlank() }
        limits.filter { it.kind == "PER_DONOR" && it.cents > 0 }.forEach { l ->
            gifts.groupBy { key(it) }.values.forEach { list ->
                val total = list.sumOf { it.cents }
                val who = list.first().person.trim()
                when {
                    total > l.cents -> out += "$who has given ${usd(total)}, over the ${usd(l.cents)} limit (${l.where}). The extra ${usd(total - l.cents)} usually has to be refunded: check with the election office."
                    total >= l.cents * 8 / 10 -> out += "$who has given ${usd(total)} of the ${usd(l.cents)} limit (${l.where})."
                }
            }
        }
        val t = Ledger.totals(entries.map { it.direction to it.cents })
        limits.filter { it.kind == "REPORT_TRIGGER" && it.cents > 0 }.forEach { l ->
            if (maxOf(t.raised, t.out) >= l.cents * 8 / 10) out += "Totals are near the ${usd(l.cents)} reporting trigger (${l.where}). Check the report rules and deadline."
        }
        return out
    }

    private fun usd(c: Long) = "$" + "%,d".format(c / 100) + if (c % 100 != 0L) ".%02d".format(c % 100) else ""
}
