package com.emotionalleaves.ballot.core

/**
 * The pre-event card, in plain code: your goal, the issues and stances of yours this meeting is likely
 * to touch, people you already know from similar meetings, and a short checklist. Nothing invented.
 */
object PrepCard {
    private val stop = setOf("the", "and", "of", "a", "an", "for", "to", "in", "on", "at", "meeting", "regular", "special", "session", "public", "city", "county")

    private fun words(s: String) = s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length > 2 && it !in stop }.toSet()

    fun build(
        title: String,
        goal: String,
        issues: List<String>,
        positions: List<Pair<String, String>>,
        /** Name to how you met, for people already in your book. */
        people: List<Pair<String, String>>,
        hasApprovedComment: Boolean,
    ): String {
        val t = words(title)
        val lines = mutableListOf<String>()
        lines += "**Goal:** " + goal.ifBlank { "Attend, listen, and introduce yourself to one person." }
        val touching = positions.filter { (issue, _) -> words(issue).any { it in t } }
        val related = issues.filter { i -> words(i).any { it in t } && touching.none { it.first.equals(i, true) } }
        if (touching.isNotEmpty() || related.isNotEmpty()) {
            lines += "**Your issues that may come up:**"
            touching.take(3).forEach { (issue, stance) -> lines += "- $issue: $stance" }
            related.take(3).forEach { lines += "- $it (no stance written yet)" }
        } else if (issues.isNotEmpty()) {
            lines += "**Listen for:** " + issues.take(3).joinToString(", ")
        }
        val known = people.filter { (_, how) -> words(how).any { it in t } }.map { it.first }.distinct()
        if (known.isNotEmpty()) lines += "**People you know from meetings like this:** " + known.take(5).joinToString(", ")
        lines += "**Before you go:**"
        lines += "- Arrive 15 minutes early and sign the public comment sheet if you want to speak."
        lines += "- Read the agenda item you care about once."
        lines += if (hasApprovedComment) "- Bring your approved comment (Drafts). Two minutes is the usual limit." else "- Not speaking tonight is fine. Attend once silently first."
        lines += "- Afterwards: debrief here in a minute while it's fresh."
        return lines.joinToString("\n")
    }
}
