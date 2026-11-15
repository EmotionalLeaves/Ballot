package com.emotionalleaves.ballot

import com.emotionalleaves.ballot.agent.FreeSearch
import com.emotionalleaves.ballot.agent.parseModelIds
import com.emotionalleaves.ballot.agent.parseRetryAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSearchTest {
    private val html = """
        <div class="result results_links results_links_deep web-result ">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.sos.state.mn.us%2Felections%2Dvoting%2F&amp;rut=abc">Elections &amp; <b>Voting</b> | Minnesota Secretary of State</a>
          </h2>
          <a class="result__snippet" href="//duckduckgo.com/l/?uddg=x">Find <b>filing</b> dates and candidate information.</a>
        </div>
        <div class="result results_links">
          <a rel="nofollow" class="result__a" href="https://duckduckgo.com/y.js?ad_domain=ads.example">Sponsored</a>
        </div>
        <div class="result">
          <a rel="nofollow" class="result__a" href="//duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.minneapolismn.gov%2Fgovernment%2Fcity-council%2F&amp;rut=def">City Council - City of Minneapolis</a>
          <a class="result__snippet">Meets on Thursdays.</a>
        </div>
    """.trimIndent()

    @Test fun readsDuckDuckGoResults() {
        val hits = FreeSearch.parse(html)
        assertEquals(2, hits.size)
        assertEquals("https://www.sos.state.mn.us/elections-voting/", hits[0].url)
        assertEquals("Elections & Voting | Minnesota Secretary of State", hits[0].title)
        assertTrue(hits[0].snippet.startsWith("Find filing dates"))
        assertEquals("Meets on Thursdays.", hits[1].snippet)
    }

    @Test fun readsTheLitePage() {
        val lite = """<a rel="nofollow" href="https://www.fec.gov/help-candidates-and-committees/" class='result-link'>Help for candidates | FEC</a>
            <td class='result-snippet'>Registering and reporting.</td>"""
        val hits = FreeSearch.parse(lite)
        assertEquals("https://www.fec.gov/help-candidates-and-committees/", hits.single().url)
        assertEquals("Registering and reporting.", hits.single().snippet)
    }

    @Test fun skipsAds() {
        assertNull(FreeSearch.realUrl("https://duckduckgo.com/y.js?ad_domain=x"))
    }

    @Test fun parsesRetryAfterAndModelLists() {
        assertEquals(7, parseRetryAfter("7"))
        assertEquals(2, parseRetryAfter("1.2"))
        assertEquals(0, parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"))
        assertEquals(0, parseRetryAfter(null))
        assertEquals(listOf("gemini-3.5-flash", "gpt-oss-120b"), parseModelIds("""{"object":"list","data":[{"id":"models/gemini-3.5-flash"},{"id":"gpt-oss-120b"}]}"""))
        assertEquals(emptyList<String>(), parseModelIds("not json"))
    }
}
