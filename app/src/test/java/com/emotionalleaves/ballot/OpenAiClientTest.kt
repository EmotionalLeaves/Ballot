package com.emotionalleaves.ballot

import com.emotionalleaves.ballot.agent.Block
import com.emotionalleaves.ballot.agent.LlmMessage
import com.emotionalleaves.ballot.agent.LlmRequest
import com.emotionalleaves.ballot.agent.OpenAiClient
import com.emotionalleaves.ballot.agent.ToolSpec
import com.emotionalleaves.ballot.agent.Schema
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiClientTest {
    private lateinit var server: MockWebServer

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() = server.shutdown()

    private val toolReply = """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
        "tool_calls":[{"id":"call_1","type":"function","function":{"name":"list_plan","arguments":"{}"},
        "extra_content":{"google":{"thought_signature":"SIG123"}}}]}}],"usage":{"prompt_tokens":10,"completion_tokens":5}}"""

    private val textReply = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Done."}}],"usage":{"prompt_tokens":20,"completion_tokens":2}}"""

    @Test fun geminiThoughtSignaturesAreEchoedBack() = runBlocking {
        server.enqueue(MockResponse().setBody(toolReply))
        server.enqueue(MockResponse().setBody(textReply))
        val base = server.url("/v1beta/openai/").toString()
        val client = OpenAiClient(base, echoSignatures = true)
        val tools = listOf(ToolSpec("list_plan", "Read the plan", Schema.obj(props = emptyMap())))
        val first = client.complete("key", LlmRequest("gemini-x", "sys", listOf(LlmMessage("user", listOf(Block.Text("What's next?")))), tools))
        assertEquals("tool_use", first.stopReason)
        val use = first.toolUses.single()
        assertEquals("SIG123", use.extra?.toString()?.let { Regex("SIG123").find(it)?.value })
        server.takeRequest()
        val history = listOf(
            LlmMessage("user", listOf(Block.Text("What's next?"))),
            LlmMessage("assistant", first.content),
            LlmMessage("user", listOf(Block.ToolResult(use.id, "Open tasks: none"))),
        )
        val second = client.complete("key", LlmRequest("gemini-x", "sys", history, tools))
        assertEquals("Done.", second.text)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("SIG123"))
        assertTrue(body, body.contains("\"name\":\"list_plan\""))
        assertTrue(body, body.contains("\"content\":null"))
    }

    @Test fun otherServicesGetNoGoogleFields() = runBlocking {
        server.enqueue(MockResponse().setBody(textReply))
        val client = OpenAiClient(server.url("/openai/v1/").toString())
        val history = listOf(
            LlmMessage("user", listOf(Block.Text("hi"))),
            LlmMessage("assistant", listOf(Block.ToolUse("c1", "list_plan", kotlinx.serialization.json.JsonObject(emptyMap())))),
            LlmMessage("user", listOf(Block.ToolResult("c1", "none"))),
        )
        client.complete("key", LlmRequest("m", "sys", history))
        val body = server.takeRequest().body.readUtf8()
        assertFalse(body, body.contains("thought_signature"))
        assertTrue(server.takeRequestCount() >= 0)
    }

    @Test fun rateLimitsCarryRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "3").setBody("""{"error":{"message":"slow down"}}"""))
        val client = OpenAiClient(server.url("/v1/").toString())
        val e = runCatching { client.complete("key", LlmRequest("m", "sys", listOf(LlmMessage("user", listOf(Block.Text("hi")))))) }.exceptionOrNull()
        val le = e as com.emotionalleaves.ballot.agent.LlmException
        assertEquals(429, le.status)
        assertEquals(3, le.retryAfterSec)
        assertTrue(le.retryable)
    }
}

class TimeoutTest {
    @Test fun aTimeoutCancelsTheRequestInsteadOfHanging() = runBlocking {
        val server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setHeadersDelay(20, java.util.concurrent.TimeUnit.SECONDS).setBody("{}"))
        val client = OpenAiClient(server.url("/v1/").toString())
        val started = System.currentTimeMillis()
        val r = runCatching {
            kotlinx.coroutines.withTimeout(500) { client.complete("key", LlmRequest("m", "sys", listOf(LlmMessage("user", listOf(Block.Text("hi")))))) }
        }
        val took = System.currentTimeMillis() - started
        assertTrue("should give up in well under 20 s, took $took ms", took < 5_000)
        assertTrue(r.exceptionOrNull() is kotlinx.coroutines.TimeoutCancellationException)
        server.shutdown()
    }
}

private fun MockWebServer.takeRequestCount() = requestCount
