package com.emotionalleaves.ballot.core

/**
 * The stage 1 values interview as a plain form: five questions, your own answers, no AI needed.
 * Answers are kept in the profile's coreValues as "Q: …" / "A: …" pairs so the Chief can read them too.
 */
object ValuesInterview {
    val questions = listOf(
        "What makes you angry about how things work where you live?",
        "If you could fix one thing here in the next four years, what would it be?",
        "Who do you most want to help, and why them?",
        "Tell a moment that shaped why you care about this.",
        "What will you never do to win?",
    )

    fun encode(answers: List<String>): String = questions.indices.joinToString("\n\n") { i ->
        "Q: ${questions[i]}\nA: ${answers.getOrNull(i)?.trim()?.replace("\n", " ") ?: ""}"
    }

    /** Reads answers back; text written some other way (by the Chief, say) comes back as the first answer so nothing is lost. */
    fun decode(stored: String): List<String> {
        if (stored.isBlank()) return List(questions.size) { "" }
        val found = questions.map { q ->
            Regex("Q: " + Regex.escape(q) + "\\nA: (.*)").find(stored)?.groupValues?.get(1)?.trim()
        }
        if (found.all { it == null }) return listOf(stored.trim()) + List(questions.size - 1) { "" }
        return found.map { it ?: "" }
    }

    fun answered(stored: String): Int = decode(stored).count { it.isNotBlank() }
}
