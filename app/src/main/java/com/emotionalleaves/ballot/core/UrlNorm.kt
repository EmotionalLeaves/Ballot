package com.emotionalleaves.ballot.core

/**
 * One spelling per page, so "the same page" is recognised however a link was written: lower-case host, no
 * "www.", no #fragment, no tracking parameters (utm_*, fbclid, gclid), no trailing slash. Paths keep their case.
 */
object UrlNorm {
    private val tracking = Regex("^(utm_[a-z]+|fbclid|gclid|mc_cid|mc_eid|ref)$", RegexOption.IGNORE_CASE)

    fun normalize(raw: String): String {
        val u = raw.trim().substringBefore('#')
        val m = Regex("^(https?)://([^/?]+)([^?]*)(\\?.*)?$", RegexOption.IGNORE_CASE).find(u) ?: return u
        val scheme = m.groupValues[1].lowercase()
        val host = m.groupValues[2].lowercase().removePrefix("www.").removeSuffix(":443").removeSuffix(":80")
        val path = m.groupValues[3].trimEnd('/')
        val query = m.groupValues[4].removePrefix("?").split('&').filter { it.isNotBlank() && !tracking.matches(it.substringBefore('=')) }
        return "$scheme://$host$path" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }
}
