package com.emotionalleaves.ballot.core

/**
 * Finds the sentences in a draft that state checkable facts (numbers, money, percentages, dates, records),
 * in plain code. These are the claims that need a source before anything goes public. Template gaps in
 * [brackets] are skipped: they're yours to fill, not facts yet.
 */
object ClaimFinder {
    private val months = "january|february|march|april|may|june|july|august|september|october|november|december"
    private val factual = Regex(
        "\\d|\\bpercent\\b|\\b(doubled|tripled|halved|record|highest|lowest|most|fewest|first|only|every|never|always)\\b|\\b($months)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String): List<String> = text
        .replace(Regex("\\[[^\\]]*]"), " ")
        .split(Regex("(?<=[.!?])\\s+|\\n+"))
        .map { it.trim().trim('-', '•', ' ') }
        .filter { s -> s.length >= 12 && factual.containsMatchIn(s) && !s.startsWith("Contact:") && !s.startsWith("FOR IMMEDIATE RELEASE") }
        .distinct()
        .take(20)
}
