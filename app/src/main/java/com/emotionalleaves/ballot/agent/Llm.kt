package com.emotionalleaves.ballot.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

val BallotJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; isLenient = true }

/** Provider-neutral message blocks. Raw keeps provider-specific blocks (web search results) intact. */
sealed class Block {
    data class Text(val text: String, val citations: List<Citation> = emptyList()) : Block()
    /** extra carries provider data that must be echoed back, such as Gemini's thought signature. */
    data class ToolUse(val id: String, val name: String, val input: JsonObject, val extra: JsonObject? = null) : Block()
    data class ToolResult(val toolUseId: String, val content: String, val isError: Boolean = false) : Block()
    data class Raw(val json: JsonObject) : Block()
}

data class Citation(val url: String, val title: String, val citedText: String)

data class LlmMessage(val role: String, val content: List<Block>)

data class ToolSpec(val name: String, val description: String, val inputSchema: JsonObject)

data class LlmRequest(
    val model: String,
    val system: String,
    val messages: List<LlmMessage>,
    val tools: List<ToolSpec> = emptyList(),
    val webSearch: Boolean = false,
    val maxTokens: Int = 2048,
)

data class Usage(val inputTokens: Long = 0, val outputTokens: Long = 0, val searches: Int = 0)

data class LlmResponse(val content: List<Block>, val stopReason: String, val usage: Usage) {
    val text: String get() = content.filterIsInstance<Block.Text>().joinToString("\n") { it.text }.trim()
    val toolUses: List<Block.ToolUse> get() = content.filterIsInstance<Block.ToolUse>()
}

class LlmException(message: String, val retryable: Boolean, val status: Int = 0, val retryAfterSec: Int = 0) : Exception(message)

interface LlmClient {
    val supportsWebSearch: Boolean
    suspend fun complete(apiKey: String, request: LlmRequest): LlmResponse
    suspend fun test(apiKey: String, model: String): String
    /** The service's current model ids, so Ballot can pick one that exists today. */
    suspend fun listModels(apiKey: String): List<String> = emptyList()
}

/** Every network call is listed in a visible log: time, host, path, status. No bodies are kept. */
object NetLog {
    data class Entry(val at: Long, val method: String, val host: String, val path: String, val status: Int)

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun add(e: Entry) {
        entries.addFirst(e)
        while (entries.size > 300) entries.removeLast()
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    val interceptor = Interceptor { chain ->
        val req = chain.request()
        var status = -1
        try {
            val resp: Response = chain.proceed(req)
            status = resp.code
            resp
        } finally {
            add(Entry(System.currentTimeMillis(), req.method, req.url.host, req.url.encodedPath, status))
        }
    }
}

/**
 * Runs a call so that cancelling the coroutine (a timeout, a Cancel button, leaving the screen)
 * cancels the network request too, instead of waiting for it to finish.
 */
suspend fun okhttp3.Call.await(): Response = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
    enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if (cont.isActive) cont.resumeWith(Result.failure(e)) }
        override fun onResponse(call: okhttp3.Call, response: Response) {
            if (cont.isActive) cont.resumeWith(Result.success(response)) else response.close()
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

object Http {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .addInterceptor(NetLog.interceptor)
            .build()
    }
}

// Small JSON helpers used across the agent.
fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() ?: it.content.toIntOrNull() }
fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: idText(it.content) }

/** Models often echo an id the way Ballot shows it ("S12", "[T4]", "#7"), so read the number out of that. */
fun idText(s: String): Long? = Regex("^\\[?\\s*[#A-Za-z]{0,2}\\s*(\\d+)\\s*]?$").find(s.trim())?.groupValues?.get(1)?.toLongOrNull()
fun JsonObject.dbl(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.content.lowercase().let { c -> if (c == "true" || c == "yes") true else if (c == "false" || c == "no") false else null } }
fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
fun JsonElement.strOrNull(): String? = (this as? JsonPrimitive)?.content
fun JsonArray.strings(): List<String> = mapNotNull { (it as? JsonPrimitive)?.content }
fun JsonArray.longs(): List<Long> = mapNotNull { e -> (e as? JsonPrimitive)?.let { it.longOrNull ?: idText(it.content) } }
fun JsonPrimitive.asText(): String = jsonPrimitive.content
