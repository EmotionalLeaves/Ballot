package com.emotionalleaves.ballot.core

import kotlin.random.Random

/** One office as the quiz sees it: only facts already in the case file. */
data class QuizOffice(
    val id: Long,
    val body: String,
    val title: String = "",
    val district: String = "",
    val level: String = "",
    val powers: String = "",
    val term: String = "",
    val nextElection: String = "",
    val incumbent: String = "",
    val partTime: Boolean = true,
    val appointed: Boolean = false,
)

/** A rule the quiz can ask about: where it applies and what it says. */
data class QuizRule(val id: Long, val jurisdiction: String, val summary: String)

data class QuizQuestion(val q: String, val options: List<String>, val answer: Int, val source: String, val explain: String)

/**
 * The stage 2 town quiz, built in plain code from the case file. No AI: every question and every
 * right answer comes straight from a stored office or rule, and wrong options come from the other
 * records, so nothing is invented.
 */
object QuizBuilder {
    const val SIZE = 10
    private val levels = listOf("LOCAL", "COUNTY", "SCHOOL", "STATE", "FEDERAL")
    private val levelNames = mapOf("LOCAL" to "City or town", "COUNTY" to "County", "SCHOOL" to "School district", "STATE" to "State", "FEDERAL" to "Federal")

    fun name(o: QuizOffice): String = listOf(o.body, o.title, o.district).filter { it.isNotBlank() }.joinToString(" ").trim()

    /** How many questions the case file can support right now. */
    fun available(offices: List<QuizOffice>, rules: List<QuizRule>): Int = candidates(offices, rules, Random(0)).size

    fun build(offices: List<QuizOffice>, rules: List<QuizRule>, seed: Long): List<QuizQuestion> {
        val r = Random(seed)
        return candidates(offices, rules, r).shuffled(r).take(SIZE)
    }

    private fun candidates(offices: List<QuizOffice>, rules: List<QuizRule>, r: Random): List<QuizQuestion> {
        val out = mutableListOf<QuizQuestion>()
        val named = offices.filter { it.body.isNotBlank() }

        // Who holds the seat: wrong answers are the holders of the other seats.
        val holders = named.map { it.incumbent.trim() }.filter { it.isNotBlank() }.distinct()
        named.filter { it.incumbent.isNotBlank() }.forEach { o ->
            ask(r, "Who holds this seat: ${name(o)}?", o.incumbent.trim(), holders - o.incumbent.trim(), "O${o.id}",
                "${o.incumbent.trim()} holds it now.")?.let { out += it }
        }
        // Term length.
        val terms = (named.map { it.term.trim() }.filter { it.isNotBlank() } + listOf("2 years", "4 years", "6 years")).distinct()
        named.filter { it.term.isNotBlank() }.forEach { o ->
            ask(r, "How long is one term on the ${name(o)}?", o.term.trim(), terms - o.term.trim(), "O${o.id}", "A term is ${o.term.trim()}.")?.let { out += it }
        }
        // Elected or appointed: a true/false question, but the most useful one for a newcomer.
        named.forEach { o ->
            val right = if (o.appointed) "Appointed" else "Elected"
            out += QuizQuestion("How do people get a seat on the ${name(o)}?", listOf("Elected", "Appointed"), if (o.appointed) 1 else 0, "O${o.id}",
                "Seats are ${right.lowercase()}.")
        }
        // Level of government.
        named.filter { it.level.uppercase() in levels }.forEach { o ->
            val right = levelNames.getValue(o.level.uppercase())
            ask(r, "Which level of government is the ${name(o)}?", right, levelNames.values.toList() - right, "O${o.id}", "It is ${right.lowercase()} government.")?.let { out += it }
        }
        // What it controls: wrong answers are the powers of other offices.
        val powers = named.map { it.powers.trim() }.filter { it.isNotBlank() }.distinct()
        named.filter { it.powers.isNotBlank() }.forEach { o ->
            ask(r, "Which of these does the ${name(o)} really control?", clip(o.powers), (powers - o.powers.trim()).map(::clip), "O${o.id}", "From its record: ${clip(o.powers)}")?.let { out += it }
        }
        // Next election.
        val dates = named.map { it.nextElection.trim() }.filter { it.isNotBlank() }.distinct()
        named.filter { it.nextElection.isNotBlank() }.forEach { o ->
            ask(r, "When is the ${name(o)} next on the ballot?", o.nextElection.trim(), dates - o.nextElection.trim(), "O${o.id}", "Next on the ballot: ${o.nextElection.trim()}.")?.let { out += it }
        }
        // Rules: which jurisdiction a rule belongs to.
        val places = rules.map { it.jurisdiction.trim() }.filter { it.isNotBlank() }.distinct()
        rules.filter { it.jurisdiction.isNotBlank() && it.summary.isNotBlank() }.forEach { rule ->
            ask(r, "Whose rule is this? \"${clip(rule.summary)}\"", rule.jurisdiction.trim(), places - rule.jurisdiction.trim(), "R${rule.id}",
                "It comes from ${rule.jurisdiction.trim()}.")?.let { out += it }
        }
        return out
    }

    /** A question needs at least one real wrong answer from the case file; up to three are used. */
    private fun ask(r: Random, q: String, right: String, wrong: List<String>, source: String, explain: String): QuizQuestion? {
        val distractors = wrong.filter { !it.equals(right, ignoreCase = true) }.distinct().shuffled(r).take(3)
        if (distractors.isEmpty()) return null
        val options = (distractors + right).shuffled(r)
        return QuizQuestion(q, options, options.indexOf(right), source, explain)
    }

    private fun clip(s: String): String = s.trim().let { if (it.length <= 90) it else it.take(87).trimEnd() + "…" }
}
