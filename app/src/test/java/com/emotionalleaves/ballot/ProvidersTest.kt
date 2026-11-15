package com.emotionalleaves.ballot

import com.emotionalleaves.ballot.agent.Providers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProvidersTest {
    @Test fun detectsEveryServiceFromItsKey() {
        assertEquals("gemini", Providers.detect("AIzaSyA1234567890abcdefghijklmnopqrstu")?.id)
        assertEquals("groq", Providers.detect("gsk_abcdefghijklmnopqrstuvwxyz0123456789")?.id)
        assertEquals("openrouter", Providers.detect("sk-or-v1-0123456789abcdef0123456789abcdef")?.id)
        assertEquals("anthropic", Providers.detect("sk-ant-api03-abcdefghijklmnop")?.id)
        assertEquals("cerebras", Providers.detect("csk-abcdefghijklmnopqrstuvwxyz")?.id)
        assertEquals("openai", Providers.detect("sk-proj-abcdefghijklmnopqrstuvwxyz")?.id)
        assertNull(Providers.detect("hello"))
    }

    @Test fun cleansPastedKeys() {
        assertEquals("gsk_abc123abc123abc123abc123", Providers.cleanKey("  \"gsk_abc123abc123abc123abc123\"\n"))
        assertEquals("AIzaSyA1234567890abcdefghijklmnop", Providers.cleanKey("GEMINI_API_KEY=AIzaSyA1234567890abcdefghijklmnop"))
        assertEquals("sk-or-v1-abcdefabcdefabcdefabcdef", Providers.cleanKey("Your key: sk-or-v1-abcdefabcdefabcdefabcdef copy it now"))
        assertTrue(Providers.looksLikeKey("gsk_abc123abc123abc123abc123"))
        assertFalse(Providers.looksLikeKey("not a key at all"))
    }

    @Test fun picksAChatModelThatExistsToday() {
        val gem = listOf("models/gemini-2.5-pro", "models/text-embedding-004", "models/gemini-3.5-flash", "models/gemini-3.5-flash-lite", "models/gemini-2.5-flash-preview-tts")
        assertEquals("gemini-3.5-flash", Providers.pickModel(Providers.gemini, gem))
        assertEquals("openai/gpt-oss-120b", Providers.pickModel(Providers.groq, listOf("whisper-large-v3", "openai/gpt-oss-20b", "openai/gpt-oss-120b")))
        assertEquals("openrouter/free", Providers.pickModel(Providers.openRouter, listOf("openai/gpt-5", "openrouter/free", "x/y:free")))
        assertEquals(Providers.mistral.defaultModel, Providers.pickModel(Providers.mistral, emptyList()))
    }

    @Test fun freeCallsCostNothing() {
        assertTrue(Providers.isFreeCall(Providers.gemini, "gemini-3.5-flash"))
        assertTrue(Providers.isFreeCall(Providers.openRouter, "meta/llama:free"))
        assertFalse(Providers.isFreeCall(Providers.openRouter, "openai/gpt-5"))
        assertFalse(Providers.isFreeCall(Providers.anthropic, "claude-sonnet-5-5"))
    }

    @Test fun modelMapRoundTrips() {
        val m = mapOf("gemini" to "gemini-3.5-flash", "groq" to "openai/gpt-oss-120b")
        assertEquals(m, Providers.parseModels(Providers.encodeModels(m)))
    }

    @Test fun everyFreeServiceHasStepsAndAKeyPage() {
        Providers.all.forEach { p ->
            assertTrue(p.id, p.steps.size >= 3)
            assertTrue(p.id, p.keyUrl.startsWith("https://"))
        }
        assertEquals("gemini", Providers.all.first().id)
    }
}

class FreeTierTest {
    @Test fun tellsADailyCapFromAShortPause() {
        assertTrue(Providers.isDailyLimit(429, 0, "Quota exceeded for requests per day"))
        assertTrue(Providers.isDailyLimit(429, 7200, "Rate limited"))
        assertFalse(Providers.isDailyLimit(429, 5, "Rate limit reached for requests per minute"))
        assertFalse(Providers.isDailyLimit(500, 0, "daily"))
    }

    @Test fun liteToolsStillResearchAndTrack() {
        listOf("search_web", "fetch_official_page", "create_task", "add_deadline", "queue_follow_up").forEach { assertTrue(it, it in Providers.liteTools) }
    }
}

class ModelCandidatesTest {
    @Test fun newestFlashFirstThenKnownGoodNames() {
        val c = Providers.candidates(Providers.gemini, listOf("gemini-2.5-flash", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.9-flash-preview", "gemini-3.5-flash-lite", "gemini-3.8-pro"))
        assertEquals("gemini-3.8-flash", c[0])
        assertEquals("gemini-3.6-flash", c[1])
        assertTrue("previews come after stable", c.indexOf("gemini-3.9-flash-preview") > c.indexOf("gemini-2.5-flash"))
        assertFalse("pro is excluded on the free tier", "gemini-3.8-pro" in c)
        assertTrue(c.containsAll(Providers.gemini.knownGood))
        assertEquals(c.size, c.toSet().size)
    }

    @Test fun withNoListBallotStillHasNamesToTry() {
        val c = Providers.candidates(Providers.gemini, emptyList())
        assertEquals("gemini-flash-latest", c.first())
        assertTrue(c.size >= 5)
    }

    @Test fun tellsModelProblemsFromKeyProblems() {
        assertTrue(Providers.isModelProblem(404, "models/gemini-2.5-flash is not found for API version v1beta, or is not supported for generateContent"))
        assertTrue(Providers.isModelProblem(400, "This model is not supported"))
        assertTrue(Providers.isModelProblem(429, "Quota exceeded for metric generate_content_free_tier_requests, limit: 0, model: gemini-3.8-pro"))
        assertFalse(Providers.isModelProblem(400, "API key not valid. Please pass a valid API key."))
        assertFalse(Providers.isModelProblem(401, "Request had invalid authentication credentials"))
        assertFalse(Providers.isModelProblem(429, "Rate limit reached, try again in 5 seconds"))
    }
}

class ToolIdParsingTest {
    @org.junit.Test fun readsIdsTheWayModelsEchoThem() {
        listOf("12", "S12", "[S12]", "T12", "#12", " s12 ").forEach { org.junit.Assert.assertEquals(it, 12L, com.emotionalleaves.ballot.agent.idText(it)) }
        listOf("", "abc", "S12a", "12 and 13").forEach { org.junit.Assert.assertNull(it, com.emotionalleaves.ballot.agent.idText(it)) }
    }
}
