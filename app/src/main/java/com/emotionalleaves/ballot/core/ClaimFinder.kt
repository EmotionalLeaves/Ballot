package com.emotionalleaves.ballot.core

/**
 * Finds the sentences in a draft that state checkable facts (numbers, money, percentages, dates, records),
 * in plain code. These are the claims that need a source before anything goes public. Template gaps in
 * [brackets] are skipped: they're yours to fill, not facts yet.
 */
object ClaimFinder {
    private val months = "january|february|march|april|may|june|july|august|september|october|november|december"
    private val factual = Regex(
        "\\d|\\bpercent\\b|\\b(doubled|tripled|halved|record|highest|lowest|most|fewest)\\b|\\b($months)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Your own story ("my two kids", "I moved here in 2015") is yours to tell, not something an official page can confirm. */
    private val personalStart = Regex("^(i|i'm|i've|i'd|my|me)\\b", RegexOption.IGNORE_CASE)
    private val publicWords = Regex("%|\\$|\\b(percent|opponent|budget|tax|taxes|council|board|city|county|state|district|crime|vote|voted)\\b", RegexOption.IGNORE_CASE)
    private fun isPersonal(s: String) = personalStart.containsMatchIn(s) && !publicWords.containsMatchIn(s)

    fun find(text: String): List<String> = text
        .replace(Regex("\\[[^\\]]*]"), " ")
        .split(Regex("(?<=[.!?])\\s+|\\n+"))
        .map { it.trim().trim('-', '•', ' ') }
        .filter { s -> s.length >= 12 && factual.containsMatchIn(s) && !isPersonal(s) && !s.startsWith("Contact:") && !s.startsWith("FOR IMMEDIATE RELEASE") }
        .distinct()
        .take(20)
}
