package com.emotionalleaves.ballot.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

/**
 * Google Gemini through its own API (generateContent), not the OpenAI-compatible layer.
 *
 * Google AI Studio now issues "AQ." auth keys. Those work on the native endpoint but are rejected
 * by the OpenAI-compatible one, so Ballot talks to Gemini natively. The key goes in the
 * x-goog-api-key header, which works for both the new AQ. keys and the older AIza keys.
 */
class GeminiClient(private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta") : LlmClient {
    override val supportsWebSearch = false

    private val json = "application/json".toMediaType()

    private suspend fun call(req: Request): Triple<Int, String, Int> = withContext(Dispatchers.IO) {
        try {
            Http.client.newCall(req).execute().use { r -> Triple(r.code, r.body?.string() ?: "", parseRetryAfter(r.header("Retry-After"))) }
        } catch (e: IOException) {
            throw LlmException("Network problem: ${e.message ?: "no connection"}", retryable = true)
        }
    }

    private fun error(status: Int, body: String, retryAfter: Int): LlmException {
        val err = runCatching { BallotJson.parseToJsonElement(body).jsonObject.obj("error") }.getOrNull()
        val msg = err?.str("message") ?: ""
        val reason = Regex("\"reason\"\\s*:\\s*\"([A-Z_]+)\"").find(body)?.groupValues?.get(1) ?: ""
        // Google sometimes returns a long retry delay inside the body rather than a header.
        val bodyDelay = Regex("\"retryDelay\"\\s*:\\s*\"(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val friendly = when {
            status == 400 && (reason == "API_KEY_INVALID" || msg.contains("API key not valid", true)) -> "Google says this API key is not valid. Copy it again from AI Studio."
            status == 401 && reason == "ACCESS_TOKEN_TYPE_UNSUPPORTED" ->
                "Google refused this key for this project. In AI Studio, delete the key, create a new one in the same screen, and paste the new one."
            status == 401 || status == 403 -> "Google rejected the key${if (msg.isNotBlank()) ": $msg" else "."}"
            status == 404 -> "Google doesn't have that model any more. Tap Re-test and Ballot will pick another."
            status == 429 -> "Gemini's free allowance is used up for now${if (msg.contains("day", true) || msg.contains("quota", true)) " (daily limit)" else ""}."
            status in 500..599 -> "Gemini had an error. Try again in a minute."
            else -> "Gemini returned an error ($status)${if (msg.isNotBlank()) ": $msg" else "."}"
        }
        return LlmException(if (status == 429 && msg.isNotBlank()) "$friendly ($msg)" else friendly, retryable = status == 429 || status >= 500,
            status = status, retryAfterSec = maxOf(retryAfter, bodyDelay))
    }

    override suspend fun listModels(apiKey: String): List<String> {
        val (status, body, ra) = call(Request.Builder().url("$baseUrl/models?pageSize=200").header("x-goog-api-key", apiKey).get().build())
        if (status !in 200..299) throw error(status, body, ra)
        return runCatching {
            BallotJson.parseToJsonElement(body).jsonObject.arr("models")?.mapNotNull { m ->
                val o = m as? JsonObject ?: return@mapNotNull null
                val methods = o.arr("supportedGenerationMethods")?.strings() ?: listOf("generateContent")
                if ("generateContent" !in methods) null else o.str("name")?.removePrefix("models/")
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    override suspend fun test(apiKey: String, model: String): String {
        val r = complete(apiKey, LlmRequest(model = model, system = "Reply with the single word: ready",
            messages = listOf(LlmMessage("user", listOf(Block.Text("Test")))), maxTokens = 64))
        return r.text.ifBlank { "ready" }
    }

    override suspend fun complete(apiKey: String, request: LlmRequest): LlmResponse {
        if (apiKey.isBlank()) throw LlmException("No API key yet. Add one in Settings.", retryable = false)
        val body = encode(request)
        val url = "$baseUrl/models/${request.model.removePrefix("models/")}:generateContent"
        val (status, text, ra) = call(Request.Builder().url(url).header("x-goog-api-key", apiKey).post(body.toString().toRequestBody(json)).build())
        if (status !in 200..299) throw error(status, text, ra)
        return decode(text)
    }

    companion object {
        /** Gemini wants the OpenAPI subset of JSON Schema with upper-case type names. */
        fun toGeminiSchema(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.mapNotNull { (k, v) ->
                when (k) {
                    "type" -> k to JsonPrimitive((v as? JsonPrimitive)?.content?.uppercase() ?: "STRING")
                    "additionalProperties", "\$schema", "default" -> null
                    else -> k to toGeminiSchema(v)
                }
            }.toMap())
            is JsonArray -> JsonArray(e.map { toGeminiSchema(it) })
            else -> e
        }

        fun encode(request: LlmRequest): JsonObject {
            val names = request.messages.flatMap { it.content }.filterIsInstance<Block.ToolUse>().associate { it.id to it.name }
            return buildJsonObject {
                if (request.system.isNotBlank()) putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", request.system) } } }
                put("contents", buildJsonArray {
                    request.messages.forEach { m ->
                        val parts = buildJsonArray {
                            m.content.forEach { b ->
                                when (b) {
                                    is Block.Text -> if (b.text.isNotBlank()) addJsonObject { put("text", b.text) }
                                    is Block.ToolUse -> addJsonObject {
                                        putJsonObject("functionCall") {
                                            put("name", b.name)
                                            put("args", b.input)
                                            if (!b.id.startsWith("g_")) put("id", b.id)
                                        }
                                        // Replayed calls must carry the signature Gemini sent with them.
                                        val sig = b.extra?.str("thoughtSignature")
                                            ?: b.extra?.obj("google")?.str("thought_signature")
                                        put("thoughtSignature", sig ?: "skip_thought_signature_validator")
                                    }
                                    is Block.ToolResult -> addJsonObject {
                                        putJsonObject("functionResponse") {
                                            put("name", names[b.toolUseId] ?: "tool")
                                            if (!b.toolUseId.startsWith("g_")) put("id", b.toolUseId)
                                            putJsonObject("response") { put(if (b.isError) "error" else "content", b.content) }
                                        }
                                    }
                                    is Block.Raw -> {}
                                }
                            }
                        }
                        if (parts.isNotEmpty()) addJsonObject {
                            put("role", if (m.role == "assistant") "model" else "user")
                            put("parts", parts)
                        }
                    }
                })
                if (request.tools.isNotEmpty()) putJsonArray("tools") {
                    addJsonObject {
                        putJsonArray("functionDeclarations") {
                            request.tools.forEach { t ->
                                addJsonObject {
                                    put("name", t.name)
                                    put("description", t.description)
                                    put("parameters", toGeminiSchema(t.inputSchema))
                                }
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") { put("maxOutputTokens", request.maxTokens.coerceAtLeast(256)) }
            }
        }

        fun decode(text: String): LlmResponse {
            val o = runCatching { BallotJson.parseToJsonElement(text).jsonObject }.getOrElse {
                throw LlmException("Gemini sent a reply that could not be read.", retryable = true)
            }
            val cand = o.arr("candidates")?.firstOrNull() as? JsonObject
            if (cand == null) {
                val block = o.obj("promptFeedback")?.str("blockReason")
                throw LlmException(if (block != null) "Gemini declined this request ($block). Try rewording it." else "Gemini sent an empty reply.", retryable = block == null)
            }
            val blocks = mutableListOf<Block>()
            var n = 0
            cand.obj("content")?.arr("parts")?.forEach { pe ->
                val p = pe as? JsonObject ?: return@forEach
                if (p.bool("thought") == true) return@forEach
                p.str("text")?.let { if (it.isNotBlank()) blocks += Block.Text(it) }
                p.obj("functionCall")?.let { fc ->
                    val id = fc.str("id") ?: "g_${System.nanoTime()}_${n++}"
                    val sig = p.str("thoughtSignature")
                    blocks += Block.ToolUse(id, fc.str("name") ?: "", fc.obj("args") ?: JsonObject(emptyMap()),
                        sig?.let { JsonObject(mapOf("thoughtSignature" to JsonPrimitive(it))) })
                }
            }
            val finish = cand.str("finishReason") ?: "STOP"
            if (blocks.isEmpty() && finish in setOf("SAFETY", "RECITATION", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")) {
                throw LlmException("Gemini stopped this reply ($finish). Try rewording the request.", retryable = false)
            }
            val usage = o.obj("usageMetadata")
            return LlmResponse(
                content = blocks,
                stopReason = if (blocks.any { it is Block.ToolUse }) "tool_use" else if (finish == "MAX_TOKENS") "max_tokens" else "end_turn",
                usage = Usage(usage?.long("promptTokenCount") ?: 0, (usage?.long("candidatesTokenCount") ?: 0) + (usage?.long("thoughtsTokenCount") ?: 0), 0),
            )
        }
    }
}
