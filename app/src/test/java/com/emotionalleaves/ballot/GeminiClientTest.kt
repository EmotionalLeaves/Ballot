package com.emotionalleaves.ballot

import com.emotionalleaves.ballot.agent.Block
import com.emotionalleaves.ballot.agent.GeminiClient
import com.emotionalleaves.ballot.agent.LlmException
import com.emotionalleaves.ballot.agent.LlmMessage
import com.emotionalleaves.ballot.agent.LlmRequest
import com.emotionalleaves.ballot.agent.Providers
import com.emotionalleaves.ballot.agent.Schema
import com.emotionalleaves.ballot.agent.ToolSpec
import com.emotionalleaves.ballot.agent.clientFor
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GeminiClientTest {
    private lateinit var server: MockWebServer
    private val aqKey = "AQ.TestKeyForUnitTestsOnly_0123456789abcdefghijklmnop"

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() = server.shutdown()

    private fun client() = GeminiClient(server.url("/v1beta").toString().trimEnd('/'))

    private val callReply = """{"candidates":[{"content":{"role":"model","parts":[
        {"text":"thinking","thought":true},
        {"functionCall":{"name":"list_plan","args":{"limit":3}},"thoughtSignature":"SIGabc"}]},"finishReason":"STOP"}],
        "usageMetadata":{"promptTokenCount":12,"candidatesTokenCount":4}}"""
    private val textReply = """{"candidates":[{"content":{"role":"model","parts":[{"text":"All done."}]},"finishReason":"STOP"}]}"""

    @Test fun newAqKeysAreRecognisedAsGemini() {
        assertEquals("gemini", Providers.detect(aqKey)?.id)
        assertTrue(Providers.looksLikeKey(aqKey))
        assertEquals(aqKey, Providers.cleanKey("  $aqKey \n"))
        assertTrue(clientFor("gemini") is GeminiClient)
    }

    @Test fun usesTheNativeEndpointWithTheKeyInAHeader() = runBlocking {
        server.enqueue(MockResponse().setBody(textReply))
        val r = client().complete(aqKey, LlmRequest("gemini-3.5-flash", "Be brief", listOf(LlmMessage("user", listOf(Block.Text("hi"))))))
        assertEquals("All done.", r.text)
        val req = server.takeRequest()
        assertEquals("/v1beta/models/gemini-3.5-flash:generateContent", req.path)
        assertEquals(aqKey, req.getHeader("x-goog-api-key"))
        assertEquals(null, req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("systemInstruction"))
        assertTrue(body.contains("\"role\":\"user\""))
    }

    @Test fun toolCallsRoundTripWithTheirSignature() = runBlocking {
        server.enqueue(MockResponse().setBody(callReply))
        server.enqueue(MockResponse().setBody(textReply))
        val tools = listOf(ToolSpec("list_plan", "Read the plan", Schema.obj(props = mapOf("limit" to Schema.i("How many")))))
        val first = client().complete(aqKey, LlmRequest("m", "sys", listOf(LlmMessage("user", listOf(Block.Text("plan?")))), tools))
        assertEquals("tool_use", first.stopReason)
        assertFalse("thought parts are not shown", first.text.contains("thinking"))
        val use = first.toolUses.single()
        assertEquals("list_plan", use.name)
        val firstBody = server.takeRequest().body.readUtf8()
        assertTrue(firstBody, firstBody.contains("functionDeclarations"))
        assertTrue("types are upper-case for Gemini", firstBody.contains("\"type\":\"OBJECT\""))
        client().complete(aqKey, LlmRequest("m", "sys", listOf(
            LlmMessage("user", listOf(Block.Text("plan?"))),
            LlmMessage("assistant", first.content),
            LlmMessage("user", listOf(Block.ToolResult(use.id, "Open tasks: none"))),
        ), tools))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"thoughtSignature\":\"SIGabc\""))
        assertTrue(body, body.contains("\"functionResponse\""))
        assertTrue(body, body.contains("\"name\":\"list_plan\""))
        assertTrue(body, body.contains("\"role\":\"model\""))
    }

    @Test fun explainsGoogleKeyErrorsPlainly() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"code":401,"message":"Request had invalid authentication credentials.","status":"UNAUTHENTICATED","details":[{"reason":"ACCESS_TOKEN_TYPE_UNSUPPORTED"}]}}"""))
        val e = runCatching { client().complete(aqKey, LlmRequest("m", "s", listOf(LlmMessage("user", listOf(Block.Text("hi")))))) }.exceptionOrNull() as LlmException
        assertEquals(401, e.status)
        assertTrue(e.message!!, e.message!!.contains("create a new one"))
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"message":"Quota exceeded for metric: requests per day","details":[{"retryDelay":"3600s"}]}}"""))
        val q = runCatching { client().complete(aqKey, LlmRequest("m", "s", listOf(LlmMessage("user", listOf(Block.Text("hi")))))) }.exceptionOrNull() as LlmException
        assertEquals(3600, q.retryAfterSec)
        assertTrue(Providers.isDailyLimit(q.status, q.retryAfterSec, q.message ?: ""))
    }

    @Test fun listsOnlyModelsThatCanChat() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"models":[
            {"name":"models/gemini-3.5-flash","supportedGenerationMethods":["generateContent","countTokens"]},
            {"name":"models/text-embedding-004","supportedGenerationMethods":["embedContent"]}]}"""))
        val ids = client().listModels(aqKey)
        assertEquals(listOf("gemini-3.5-flash"), ids)
        assertEquals("gemini-3.5-flash", Providers.pickModel(Providers.gemini, ids))
    }
}
