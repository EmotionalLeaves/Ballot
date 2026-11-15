package com.emotionalleaves.ballot.core

/** What a template can draw on: only what you have already written in your case file. */
data class TemplateFacts(
    val name: String = "",
    val place: String = "",
    val why: String = "",
    val story60: String = "",
    val issues: List<String> = emptyList(),
    /** Issue to your stance, in your words. */
    val positions: List<Pair<String, String>> = emptyList(),
)

/**
 * First drafts in plain code: a proven structure for each kind of document, filled with your own
 * words from the case file, with [brackets] where only you can fill the gap. No AI, and no facts
 * the user hasn't stated, so nothing in a template can need a source it doesn't have.
 */
object Templates {
    fun draft(type: String, topic: String, f: TemplateFacts): String {
        val me = f.name.ifBlank { "[your name]" }
        val place = f.place.ifBlank { "[where you live]" }
        val t = topic.trim().ifBlank { "[the topic]" }
        val stance = f.positions.firstOrNull { (issue, _) -> t.contains(issue, ignoreCase = true) || issue.contains(t, ignoreCase = true) }?.second
        val story = f.story60.ifBlank { f.why }.ifBlank { "[one sentence on why you care, from your own life]" }
        val issueLine = f.issues.take(3).joinToString(", ").ifBlank { "[your three issues]" }
        return when (type) {
            "PUBLIC_COMMENT" -> """
                Good evening. My name is $me and I live in $place.
                I'm here about $t.
                ${stance ?: "[What you want the council to do, in one sentence.]"}
                [One fact or example from your own life or neighborhood.]
                I'm asking you to [the specific vote or action]. Thank you for your time.
                """
            "STUMP_SPEECH" -> """
                My name is $me. $story
                I'm running because of three things: $issueLine.
                ${f.positions.take(3).joinToString("\n") { (issue, s) -> "On $issue: $s" }.ifBlank { "[One plain sentence on each issue.]" }}
                [Who you've talked to and what they told you.]
                I'm asking for your vote, and for one more thing: tell a neighbor.
                """
            "PRESS_STATEMENT" -> """
                FOR IMMEDIATE RELEASE · [date]
                $me on $t
                ${stance ?: "[Your position in one sentence.]"}
                "[A short quote in your own words.]" said $me.
                [One or two facts, each with its source.]
                Contact: $me, [phone or email]
                """
            "EMAIL" -> """
                Subject: $t
                Hi [name],
                [Why you're writing, in one sentence.]
                ${stance ?: "[What you think or want.]"}
                [The one thing you're asking them to do, and by when.]
                Thanks,
                $me
                """
            "DOOR_SCRIPT" -> """
                Hi, I'm $me. I live in $place.
                I'm talking with neighbors about $issueLine. What matters most to you right now?
                [Listen. Note their answer.]
                [If it matches an issue:] ${f.positions.firstOrNull()?.let { "On ${it.first}: ${it.second}" } ?: "[Your stance in one sentence.]"}
                Can I count on your support? [Yes / Maybe / No: thank them either way.]
                """
            "PALM_CARD" -> """
                $me
                For [office]
                $issueLine
                ${story.take(160)}
                [Election date] · [how to reach you]
                """
            "APPLICATION" -> """
                I'm applying to serve on the $t.
                About me: $me, living in $place. [Work, volunteering, anything relevant.]
                Why this board: $story
                What I'd bring: [two or three plain points].
                I can attend [days and times].
                """
            "LETTER" -> """
                To the editor:
                $t matters to everyone in $place.
                ${stance ?: "[Your point in one sentence.]"}
                [One example or fact, with its source.]
                [What readers can do.]
                $me, $place
                """
            "QUESTIONNAIRE" -> f.positions.joinToString("\n\n") { (issue, s) -> "Q: [their question on $issue]\nA: $s" }
                .ifBlank { "Q: [their question]\nA: [your answer, two or three sentences]" }
            "CRISIS_RESPONSE" -> """
                [Wait for the cooling period. Facts first.]
                What happened: [only what you know for certain].
                What I'm doing about it: [one step].
                $me
                """
            else -> """
                $t
                [Your words.]
                $me
                """
        }.lines().joinToString("\n") { it.trim() }.trim()
    }

    /** A one-page platform from what you've already said: why, your issues and your stated positions. */
    fun platform(f: TemplateFacts): String = buildString {
        appendLine(f.name.ifBlank { "[Your name]" } + (if (f.place.isNotBlank()) " · ${f.place}" else ""))
        appendLine()
        appendLine("Why I'm doing this")
        appendLine(f.story60.ifBlank { f.why }.ifBlank { "[Your reason, in a few sentences.]" })
        appendLine()
        val stated = f.positions.toMap()
        val issues = (f.issues + stated.keys).map { it.trim() }.filter { it.isNotBlank() }.distinctBy { it.lowercase() }
        if (issues.isEmpty()) appendLine("[Your three issues, each with one sentence on what you'd do.]")
        issues.forEach { issue ->
            appendLine(issue)
            appendLine(stated.entries.firstOrNull { it.key.equals(issue, true) }?.value ?: "[What you'd do about this, in one or two sentences.]")
            appendLine()
        }
    }.trim()
}
