package com.emotionalleaves.ballot.core

/** Template gaps like "[your name]": yours to fill. Nothing goes public while one is left. */
object Gaps {
    private val gap = Regex("\\[[^\\[\\]\\n]{2,}]")
    fun count(text: String): Int = gap.findAll(text).count()
    fun any(text: String): Boolean = gap.containsMatchIn(text)
    /** The first gap, so the screen can say exactly what to fill. */
    fun first(text: String): String? = gap.find(text)?.value
}
