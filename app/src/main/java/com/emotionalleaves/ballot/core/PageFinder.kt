package com.emotionalleaves.ballot.core

/**
 * Without AI, the Chief still understands where things are: words in what you type are matched to the page that
 * does that job, in plain code. Returns page keys (the same ones the Chief's cards open), best match first.
 */
object PageFinder {
    private val table: List<Pair<String, List<String>>> = listOf(
        "home" to listOf("today", "next", "task", "do now", "what should i"),
        "path" to listOf("plan", "stage", "roadmap", "goal", "progress"),
        "review" to listOf("weekly review", "review", "week", "blocker", "stuck"),
        "gonogo" to listOf("run or wait", "ready to run", "go or no", "should i run"),
        "compass" to listOf("values", "interview", "story", "issues", "platform", "positions", "why i"),
        "studio" to listOf("draft", "speech", "comment", "letter", "email", "press", "write", "template", "palm card"),
        "ground" to listOf("office", "offices", "race", "council", "board", "seat", "win number", "district"),
        "quiz" to listOf("quiz", "test me", "town quiz"),
        "field" to listOf("meeting", "event", "calendar", "agenda", "debrief", "prep"),
        "briefing" to listOf("news", "briefing", "brief"),
        "network" to listOf("people", "contact", "person", "friend", "volunteer", "donor", "cabinet", "follow up", "follow-up"),
        "gym" to listOf("practice", "rehearse", "mock", "speaking", "flashcard"),
        "vault" to listOf("deadline", "rules", "money", "ledger", "donation", "contribution", "receipt", "filing", "limit"),
        "casefile" to listOf("case file", "sources", "what do you know"),
        "approvals" to listOf("approve", "approval", "waiting", "send"),
        "hq" to listOf("campaign hq", "launch", "doors", "election day", "turnout"),
        "settings" to listOf("setting", "backup", "lock", "pin", "theme", "dark", "quiet hours", "key"),
    )

    /** Every page key this can return, so a test can check each one really opens. */
    val pages: Set<String> get() = table.map { it.first }.toSet()

    fun find(text: String, max: Int = 3): List<String> {
        val t = " " + text.lowercase().replace(Regex("[^a-z0-9 -]"), " ").replace(Regex("\\s+"), " ") + " "
        return table.map { (page, words) -> page to words.sumOf { w -> if (t.contains(" $w ") || t.contains(" ${w}s ")) w.length else 0 } }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(max).map { it.first }
    }
}
