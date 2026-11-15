package com.emotionalleaves.ballot.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

data class FetchedPage(val url: String, val status: Int, val title: String, val text: String, val hash: String)

/** Direct fetches of official pages. Results are cached in the case file with source and date. */
object PageFetcher {
    private const val MAX_BYTES = 1_500_000L

    suspend fun fetch(url: String): FetchedPage = withContext(Dispatchers.IO) {
        val clean = url.trim()
        require(clean.startsWith("https://") || clean.startsWith("http://")) { "Only web addresses can be fetched." }
        val https = if (clean.startsWith("http://")) "https://" + clean.removePrefix("http://") else clean
        val req = Request.Builder().url(https)
            .header("User-Agent", "Ballot/1.0 (personal civic research app)")
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
            .build()
        try {
            Http.pages.newCall(req).await().use { r ->
                // The address that actually answered, after redirects: the official check must judge this one,
                // or a .gov link that redirects elsewhere would pass as official.
                val finalUrl = r.request.url.toString()
                val body = r.body ?: return@use FetchedPage(finalUrl, r.code, "", "", "")
                val type = body.contentType()?.toString() ?: ""
                if (type.contains("pdf")) return@use FetchedPage(finalUrl, r.code, "PDF document", "[This is a PDF. Open it in a browser to read it.]", "")
                val raw = body.source().let { src ->
                    src.request(MAX_BYTES)
                    src.buffer.clone().readUtf8()
                }
                val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .find(raw)?.groupValues?.get(1)?.let { decode(it).trim() } ?: ""
                val text = if (type.contains("html") || raw.trimStart().startsWith("<")) htmlToText(raw) else raw
                FetchedPage(finalUrl, r.code, title, text, sha(text))
            }
        } catch (e: IOException) {
            throw LlmException("Could not reach $https (${e.message ?: "network"})", retryable = true)
        }
    }

    fun htmlToText(html: String): String {
        var s = html
        s = s.replace(Regex("<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        s = s.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), " ")
        s = s.replace(Regex("<(br|/p|/div|/li|/tr|/h[1-6]|/table|/section)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        s = s.replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "\n• ")
        s = s.replace(Regex("<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { m ->
            val label = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            if (label.isEmpty()) " " else "$label (${m.groupValues[1]})"
        }
        s = s.replace(Regex("<[^>]+>"), " ")
        s = decode(s)
        s = s.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        s = s.replace(Regex("\\n\\s*\\n+"), "\n\n")
        return s.trim()
    }

    fun decode(s: String): String = s
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&#8217;", "'")
        .replace("&#8220;", "\"").replace("&#8221;", "\"").replace("&ndash;", "–").replace("&mdash;", "—")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { runCatching { String(Character.toChars(it)) }.getOrNull() } ?: " " }

    fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
}
