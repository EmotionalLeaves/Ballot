package com.emotionalleaves.ballot.core

/**
 * What counts as "the page changed": the words of the page, not the clock on it. Lines that change on every
 * visit (last updated, today's date, visitor counts, copyright years, "page generated in") are dropped and
 * spacing is collapsed before the fingerprint is taken, so a rule page only flags when its wording moves.
 */
object PageText {
    const val VERSION = "v2:"
    private val volatile = listOf(
        Regex("(?i)\\b(last (updated|modified|reviewed)|page (generated|updated)|updated on|as of)\\b"),
        Regex("(?i)©|\\bcopyright\\b|\\(c\\)\\s*\\d{4}"),
        Regex("(?i)\\b(visitors?|views?|hits)\\b\\s*:?\\s*[\\d,]+"),
        Regex("(?i)\\b(today is|current time|server time)\\b"),
        Regex("(?i)\\b(cookie|accept all|skip to (main )?content)\\b"),
    )

    fun stable(text: String): String = text.lines()
        .map { it.trim() }
        .filter { line -> line.isNotEmpty() && volatile.none { it.containsMatchIn(line) } }
        .joinToString("\n") { it.replace(Regex("\\s+"), " ") }

    /** True when a stored fingerprint was made the same way, so comparing them means something. */
    fun comparable(stored: String): Boolean = stored.startsWith(VERSION)
}
