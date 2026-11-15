package com.emotionalleaves.ballot.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder

/**
 * Keyless web search for services without a built-in one (every free service). Results are leads,
 * not sources: the Chief must fetch the official page before it states anything from it.
 */
object FreeSearch {
    data class Hit(val title: String, val url: String, val snippet: String)

    private val endpoints = listOf("https://html.duckduckgo.com/html/", "https://lite.duckduckgo.com/lite/")

    suspend fun search(query: String, max: Int = 8): List<Hit> = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (base in endpoints) {
            val url = base.toHttpUrl().newBuilder().addQueryParameter("q", query).addQueryParameter("kl", "us-en").build()
            val req = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Ballot/1.0")
                .header("Accept", "text/html")
                .build()
            try {
                Http.pages.newCall(req).await().use { r ->
                    val html = r.body?.string() ?: ""
                    if (r.isSuccessful) {
                        val hits = parse(html).take(max)
                        if (hits.isNotEmpty()) return@withContext hits
                    } else lastError = "status ${r.code}"
                }
            } catch (e: IOException) {
                lastError = e.message
            }
        }
        if (lastError != null) throw LlmException("Search is unavailable right now ($lastError).", retryable = true)
        emptyList()
    }

    private val anchor = Regex("""<a\b([^>]*)>(.*?)</a>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val href = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val classAttr = Regex("""class\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val snippetBlock = Regex("""<(?:a|td|div)\b[^>]*class\s*=\s*["'][^"']*result[_-]+snippet[^"']*["'][^>]*>(.*?)</(?:a|td|div)>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    /** Reads DuckDuckGo's HTML or lite result pages. */
    fun parse(html: String): List<Hit> {
        val links = anchor.findAll(html).mapNotNull { m ->
            val attrs = m.groupValues[1]
            val cls = classAttr.find(attrs)?.groupValues?.get(1) ?: ""
            if (!cls.contains("result__a") && !cls.contains("result-link")) return@mapNotNull null
            val raw = href.find(attrs)?.groupValues?.get(1) ?: return@mapNotNull null
            val url = realUrl(PageFetcher.decode(raw)) ?: return@mapNotNull null
            Triple(m.range.first, PageFetcher.decode(m.groupValues[2].replace(Regex("<[^>]+>"), "")).trim(), url)
        }.toList()
        val snippets = snippetBlock.findAll(html).map { it.range.first to PageFetcher.decode(it.groupValues[1].replace(Regex("<[^>]+>"), "")).replace(Regex("\\s+"), " ").trim() }.toList()
        return links.mapIndexed { i, (pos, title, url) ->
            val nextPos = links.getOrNull(i + 1)?.first ?: Int.MAX_VALUE
            val snip = snippets.firstOrNull { it.first in pos until nextPos }?.second ?: ""
            Hit(title.ifBlank { url }, url, snip)
        }.filter { !it.url.contains("duckduckgo.com") }.distinctBy { it.url }
    }

    /** DuckDuckGo wraps results as //duckduckgo.com/l/?uddg=<encoded url>; ads go through y.js. */
    fun realUrl(href: String): String? {
        val h = if (href.startsWith("//")) "https:$href" else href
        if (h.contains("duckduckgo.com/y.js")) return null
        val uddg = Regex("""[?&]uddg=([^&]+)""").find(h)?.groupValues?.get(1)
        val target = if (uddg != null) runCatching { URLDecoder.decode(uddg, "UTF-8") }.getOrNull() ?: return null else h
        return target.takeIf { it.startsWith("http://") || it.startsWith("https://") }
    }
}
