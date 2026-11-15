package com.emotionalleaves.ballot.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Dates and times as people (and models) actually write them: 2026-11-20, 11/20/2026, Nov 20 2026,
 * "2026-11-20 6:30pm", "2026-11-20T18:30". US order for slashes (month first). Anything unclear gives null.
 */
object DateText {
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    fun day(raw: String): LocalDate? {
        val s = raw.trim().lowercase().removeSuffix(".").replace(",", " ").replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return null
        runCatching { return LocalDate.parse(s.take(10)) }
        Regex("^(\\d{1,2})/(\\d{1,2})/(\\d{2,4})$").find(s)?.let { m ->
            val (mo, d, y) = m.destructured
            return runCatching { LocalDate.of(year(y), mo.toInt(), d.toInt()) }.getOrNull()
        }
        Regex("^([a-z]{3})[a-z]* (\\d{1,2})(st|nd|rd|th)? (\\d{4})$").find(s)?.let { m ->
            val mo = months.indexOf(m.groupValues[1]).takeIf { it >= 0 } ?: return null
            return runCatching { LocalDate.of(m.groupValues[4].toInt(), mo + 1, m.groupValues[2].toInt()) }.getOrNull()
        }
        Regex("^(\\d{1,2}) ([a-z]{3})[a-z]* (\\d{4})$").find(s)?.let { m ->
            val mo = months.indexOf(m.groupValues[2]).takeIf { it >= 0 } ?: return null
            return runCatching { LocalDate.of(m.groupValues[3].toInt(), mo + 1, m.groupValues[1].toInt()) }.getOrNull()
        }
        return null
    }

    fun time(raw: String): LocalTime? {
        val s = raw.trim().lowercase().replace(" ", "").replace(".", "")
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?(?::\\d{2})?(am|pm)?$").find(s) ?: return null
        var h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        when (m.groupValues[3]) { "pm" -> if (h < 12) h += 12; "am" -> if (h == 12) h = 0 }
        if (m.groupValues[3].isEmpty() && m.groupValues[2].isEmpty()) return null
        return runCatching { LocalTime.of(h, min) }.getOrNull()
    }

    fun dateTime(raw: String): LocalDateTime? {
        val s = raw.trim()
        val split = Regex("^(.*?)[T ](\\d{1,2}(:\\d{2})?(:\\d{2})?\\s*([aApP]\\.?[mM]\\.?)?)$").find(s) ?: return null
        val d = day(split.groupValues[1]) ?: return null
        val t = time(split.groupValues[2]) ?: return null
        return d.atTime(t)
    }

    private fun year(y: String) = if (y.length == 2) 2000 + y.toInt() else y.toInt()
}
