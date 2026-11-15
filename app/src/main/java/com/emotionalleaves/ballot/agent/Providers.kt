package com.emotionalleaves.ballot.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

private val JSON_TYPE = "application/json".toMediaType()

private data class HttpReply(val status: Int, val body: String, val retryAfterSec: Int)

private suspend fun send(req: Request): HttpReply = withContext(Dispatchers.IO) {
    try {
        Http.client.newCall(req).execute().use { r ->
            HttpReply(r.code, r.body?.string() ?: "", parseRetryAfter(r.header("Retry-After")))
        }
    } catch (e: IOException) {
        throw LlmException("Network problem: ${e.message ?: "no connection"}", retryable = true)
    }
}

private suspend fun post(url: String, headers: Map<String, String>, body: String): HttpReply {
    val b = Request.Builder().url(url).post(body.toRequestBody(JSON_TYPE))
    headers.forEach { (k, v) -> b.header(k, v) }
    return send(b.build())
}

private suspend fun get(url: String, headers: Map<String, String>): HttpReply {
    val b = Request.Builder().url(url).get()
    headers.forEach { (k, v) -> b.header(k, v) }
    return send(b.build())
}

/** Retry-After in seconds; HTTP-date values and junk count as unknown (0). */
fun parseRetryAfter(v: String?): Int = v?.trim()?.toDoubleOrNull()?.let { kotlin.math.ceil(it).toInt().coerceIn(0, 3600) } ?: 0

/** Model ids from an OpenAI-style or Anthropic-style list response. */
fun parseModelIds(body: String): List<String> = runCatching {
    val o = BallotJson.parseToJsonElement(body).jsonObject
    (o.arr("data") ?: o.arr("models") ?: JsonArray(emptyList())).mapNotNull { (it as? JsonObject)?.let { m -> m.str("id") ?: m.str("name") } }
        .map { it.removePrefix("models/") }
}.getOrDefault(emptyList())

/** Builds the client for a service. Everything except Claude speaks the OpenAI chat format. */
fun clientFor(providerId: String, customOpenAiBase: String = ""): LlmClient = when (providerId) {
    "anthropic" -> AnthropicClient()
    "openai" -> OpenAiClient(customOpenAiBase.ifBlank { Providers.openAi.baseUrl })
    else -> OpenAiClient(Providers.byId(providerId).baseUrl)
}

private fun plainError(status: Int, body: String, retryAfterSec: Int = 0): LlmException {
    val msg = runCatching { BallotJson.parseToJsonElement(body).jsonObject.obj("error")?.str("message") }.getOrNull()
    val friendly = when (status) {
        401 -> "The API key was rejected. Check it in Settings."
        403 -> "The API key is not allowed to use this model."
        404 -> "The model name was not found. Check it in Settings."
        429 -> "The AI provider is rate limiting. Try again in a minute."
        in 500..599 -> "The AI provider had an error."
        else -> "The AI provider returned an error ($status)."
    }
    return LlmException(if (msg != null) "$friendly ($msg)" else friendly, retryable = status == 429 || status >= 500, status = status, retryAfterSec = retryAfterSec)
}

/** Anthropic Messages API with tool calling and the server-side web search tool. */
class AnthropicClient(private val baseUrl: String = "https://api.anthropic.com/v1") : LlmClient {
    override val supportsWebSearch = true

    override suspend fun test(apiKey: String, model: String): String {
        val r = complete(apiKey, LlmRequest(model = model, system = "Reply with the single word: ready", messages = listOf(
            LlmMessage("user", listOf(Block.Text("Test"))),
        ), maxTokens = 16))
        return r.text.ifBlank { "ready" }
    }

    override suspend fun complete(apiKey: String, request: LlmRequest): LlmResponse {
        if (apiKey.isBlank()) throw LlmException("No API key yet. Add one in Settings.", retryable = false)
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("system", request.system)
            put("messages", buildJsonArray { request.messages.forEach { add(encode(it)) } })
            if (request.tools.isNotEmpty() || request.webSearch) {
                putJsonArray("tools") {
                    request.tools.forEach { t ->
                        addJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("input_schema", t.inputSchema)
                        }
                    }
                    if (request.webSearch) addJsonObject {
                        put("type", "web_search_20250305")
                        put("name", "web_search")
                        put("max_uses", 5)
                        putJsonObject("user_location") {
                            put("type", "approximate")
                            put("country", "US")
                        }
                    }
                }
            }
        }
        val reply = post("$baseUrl/messages", mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
        ), body.toString())
        val text = reply.body
        if (reply.status !in 200..299) throw plainError(reply.status, text, reply.retryAfterSec)
        val o = runCatching { BallotJson.parseToJsonElement(text).jsonObject }.getOrElse {
            throw LlmException("The AI provider sent a reply that could not be read.", retryable = true)
        }
        val blocks = (o.arr("content") ?: JsonArray(emptyList())).mapNotNull { el ->
            val b = el as? JsonObject ?: return@mapNotNull null
            when (b.str("type")) {
                "text" -> Block.Text(b.str("text") ?: "", (b.arr("citations") ?: JsonArray(emptyList())).mapNotNull { c ->
                    val co = c as? JsonObject ?: return@mapNotNull null
                    val url = co.str("url") ?: return@mapNotNull null
                    Citation(url, co.str("title") ?: "", co.str("cited_text") ?: "")
                })
                "tool_use" -> Block.ToolUse(b.str("id") ?: "", b.str("name") ?: "", b.obj("input") ?: JsonObject(emptyMap()))
                else -> Block.Raw(b)
            }
        }
        val usage = o.obj("usage")
        return LlmResponse(
            content = blocks,
            stopReason = o.str("stop_reason") ?: "end_turn",
            usage = Usage(
                inputTokens = usage?.long("input_tokens") ?: 0,
                outputTokens = usage?.long("output_tokens") ?: 0,
                searches = usage?.obj("server_tool_use")?.int("web_search_requests") ?: 0,
            ),
        )
    }

    override suspend fun listModels(apiKey: String): List<String> {
        val r = get("$baseUrl/models?limit=100", mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01"))
        if (r.status !in 200..299) throw plainError(r.status, r.body, r.retryAfterSec)
        return parseModelIds(r.body)
    }

    private fun encode(m: LlmMessage): JsonObject = buildJsonObject {
        put("role", m.role)
        put("content", buildJsonArray {
            m.content.forEach { b ->
                when (b) {
                    // Citations are not sent back; the text carries [S#] markers instead.
                    is Block.Text -> if (b.text.isNotEmpty()) addJsonObject { put("type", "text"); put("text", b.text) }
                    is Block.ToolUse -> addJsonObject { put("type", "tool_use"); put("id", b.id); put("name", b.name); put("input", b.input) }
                    is Block.ToolResult -> addJsonObject {
                        put("type", "tool_result"); put("tool_use_id", b.toolUseId); put("content", b.content)
                        if (b.isError) put("is_error", true)
                    }
                    is Block.Raw -> add(b.json)
                }
            }
        })
    }
}

/** OpenAI-compatible chat completions with function calling. No built-in web search here. */
class OpenAiClient(private val baseUrl: String, private val echoSignatures: Boolean = baseUrl.contains("generativelanguage.googleapis.com")) : LlmClient {
    override val supportsWebSearch = false

    override suspend fun listModels(apiKey: String): List<String> {
        val r = get("${baseUrl.trimEnd('/')}/models", mapOf("Authorization" to "Bearer $apiKey"))
        if (r.status !in 200..299) throw plainError(r.status, r.body, r.retryAfterSec)
        return parseModelIds(r.body)
    }

    override suspend fun test(apiKey: String, model: String): String {
        val r = complete(apiKey, LlmRequest(model = model, system = "Reply with the single word: ready",
            messages = listOf(LlmMessage("user", listOf(Block.Text("Test")))), maxTokens = 16))
        return r.text.ifBlank { "ready" }
    }

    override suspend fun complete(apiKey: String, request: LlmRequest): LlmResponse {
        if (apiKey.isBlank()) throw LlmException("No API key yet. Add one in Settings.", retryable = false)
        val toolNames = request.messages.flatMap { it.content }.filterIsInstance<Block.ToolUse>().associate { it.id to it.name }
        val msgs = buildJsonArray {
            addJsonObject { put("role", "system"); put("content", request.system) }
            request.messages.forEach { m ->
                val results = m.content.filterIsInstance<Block.ToolResult>()
                if (results.isNotEmpty()) {
                    results.forEach { r ->
                        addJsonObject {
                            put("role", "tool"); put("tool_call_id", r.toolUseId); put("content", r.content)
                            // Gemini matches results to calls by name as well as id.
                            if (echoSignatures) toolNames[r.toolUseId]?.let { put("name", it) }
                        }
                    }
                    val texts = m.content.filterIsInstance<Block.Text>()
                    if (texts.isNotEmpty()) addJsonObject { put("role", m.role); put("content", texts.joinToString("\n") { it.text }) }
                } else {
                    addJsonObject {
                        put("role", m.role)
                        val joined = m.content.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                        val uses = m.content.filterIsInstance<Block.ToolUse>()
                        // Some services reject empty text beside tool calls; null is the standard form.
                        if (joined.isEmpty() && uses.isNotEmpty()) put("content", JsonNull) else put("content", joined)
                        if (uses.isNotEmpty()) putJsonArray("tool_calls") {
                            uses.forEach { u ->
                                addJsonObject {
                                    put("id", u.id); put("type", "function")
                                    putJsonObject("function") { put("name", u.name); put("arguments", u.input.toString()) }
                                    // Gemini rejects a replayed tool call without its thought signature.
                                    when {
                                        u.extra != null -> put("extra_content", u.extra)
                                        echoSignatures -> putJsonObject("extra_content") { putJsonObject("google") { put("thought_signature", "skip_thought_signature_validator") } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.maxTokens)
            put("messages", msgs)
            if (request.tools.isNotEmpty()) putJsonArray("tools") {
                request.tools.forEach { t ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") { put("name", t.name); put("description", t.description); put("parameters", t.inputSchema) }
                    }
                }
            }
        }
        val reply = post("${baseUrl.trimEnd('/')}/chat/completions", mapOf("Authorization" to "Bearer $apiKey", "X-Title" to "Ballot"), body.toString())
        val text = reply.body
        if (reply.status !in 200..299) throw plainError(reply.status, text, reply.retryAfterSec)
        val o = runCatching { BallotJson.parseToJsonElement(text).jsonObject }.getOrElse {
            throw LlmException("The AI provider sent a reply that could not be read.", retryable = true)
        }
        val choice = o.arr("choices")?.firstOrNull() as? JsonObject ?: throw LlmException("Empty reply from the AI provider.", retryable = true)
        val msg = choice.obj("message") ?: JsonObject(emptyMap())
        val blocks = mutableListOf<Block>()
        msg.str("content")?.takeIf { it.isNotBlank() }?.let { blocks += Block.Text(it) }
        msg.arr("tool_calls")?.forEach { tc ->
            val t = tc as? JsonObject ?: return@forEach
            val f = t.obj("function") ?: return@forEach
            val args = runCatching { BallotJson.parseToJsonElement(f.str("arguments") ?: "{}").jsonObject }.getOrDefault(JsonObject(emptyMap()))
            blocks += Block.ToolUse(t.str("id") ?: "", f.str("name") ?: "", args, t.obj("extra_content"))
        }
        val finish = choice.str("finish_reason") ?: "stop"
        val usage = o.obj("usage")
        return LlmResponse(
            content = blocks,
            stopReason = if (finish == "tool_calls") "tool_use" else "end_turn",
            usage = Usage(usage?.long("prompt_tokens") ?: 0, usage?.long("completion_tokens") ?: 0, 0),
        )
    }
}

/** Helpers for building JSON schemas for tools. */
object Schema {
    fun obj(required: List<String> = emptyList(), props: Map<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", JsonObject(props))
        if (required.isNotEmpty()) put("required", JsonArray(required.map { JsonPrimitive(it) }))
    }

    fun s(desc: String) = buildJsonObject { put("type", "string"); put("description", desc) }
    fun i(desc: String) = buildJsonObject { put("type", "integer"); put("description", desc) }
    fun n(desc: String) = buildJsonObject { put("type", "number"); put("description", desc) }
    fun b(desc: String) = buildJsonObject { put("type", "boolean"); put("description", desc) }
    fun e(desc: String, values: List<String>) = buildJsonObject {
        put("type", "string"); put("description", desc); put("enum", JsonArray(values.map { JsonPrimitive(it) }))
    }
    fun arrOf(desc: String, item: JsonObject) = buildJsonObject { put("type", "array"); put("description", desc); put("items", item) }
    fun strArr(desc: String) = arrOf(desc, buildJsonObject { put("type", "string") })
    fun intArr(desc: String) = arrOf(desc, buildJsonObject { put("type", "integer") })
}
