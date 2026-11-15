package com.emotionalleaves.ballot.agent

/**
 * Every AI service Ballot can think with. Free ones come first; most need no card.
 * Free tiers and model names change often, so Ballot asks each service for its current model list
 * and picks one, rather than trusting a hard-coded name.
 */
data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    /** The page where you create a key. */
    val keyUrl: String,
    val keyPrefixes: List<String>,
    val free: Boolean,
    val needsCard: Boolean,
    val webSearch: Boolean,
    val defaultModel: String,
    val fallbackModel: String,
    /** Model ids matching these are preferred, in order. */
    val prefer: List<Regex>,
    /** Model ids matching these are never picked for chat. */
    val exclude: List<Regex>,
    val steps: List<String>,
    val limitsNote: String,
    val privacyNote: String,
    /** Rough requests a day on the free tier, for the meter only. 0 when unknown or paid. */
    val dailyHint: Int,
    val tagline: String,
    /** Small free context window: send a lighter request (fewer tools, shorter results). */
    val lite: Boolean = false,
    /** Model names to try, in order, when the service's own list is unavailable or a model is refused. */
    val knownGood: List<String> = emptyList(),
)

object Providers {
    private val nonChat = listOf(
        Regex("embed", RegexOption.IGNORE_CASE), Regex("tts|audio|whisper|speech|transcri", RegexOption.IGNORE_CASE),
        Regex("image|imagen|vision-only|veo|guard|safeguard|moderation|rerank|ocr", RegexOption.IGNORE_CASE),
    )

    val gemini = ProviderPreset(
        id = "gemini", name = "Google Gemini", baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        // AI Studio now issues "AQ." auth keys; older keys start "AIza". Both work on the native API.
        keyUrl = "https://aistudio.google.com/apikey", keyPrefixes = listOf("AIza", "AQ."), free = true, needsCard = false, webSearch = false,
        defaultModel = "gemini-flash-latest", fallbackModel = "gemini-flash-lite-latest",
        prefer = listOf(Regex("^gemini-[0-9.]+-flash$"), Regex("^gemini-flash-latest$"), Regex("^gemini-[0-9.]+-flash-lite$"), Regex("^gemini-flash-lite-latest$"), Regex("flash")),
        exclude = nonChat + Regex("pro", RegexOption.IGNORE_CASE) +
            Regex("preview-tts|live|thinking-exp|aqa|learnlm|robotics|omni|transcribe|computer-use|nano|banana|deep-research|antigravity|lyria", RegexOption.IGNORE_CASE),
        steps = listOf(
            "Tap Open the key page. Sign in with any Google account.",
            "Tap Create API key. If it asks for a project, let it make one.",
            "Tap the copy icon next to the new key. New keys start with AQ. and that's normal.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Free tier, no card. Limited requests per minute and per day; when you hit the cap it simply waits until tomorrow, it never charges you.",
        privacyNote = "Google may use free-tier requests to improve its products.",
        dailyHint = 250, tagline = "Recommended. Free with a Google account.",
        knownGood = listOf("gemini-flash-latest", "gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite"),
    )

    val groq = ProviderPreset(
        id = "groq", name = "Groq", baseUrl = "https://api.groq.com/openai/v1",
        keyUrl = "https://console.groq.com/keys", keyPrefixes = listOf("gsk_"), free = true, needsCard = false, webSearch = false,
        defaultModel = "openai/gpt-oss-120b", fallbackModel = "openai/gpt-oss-20b",
        prefer = listOf(Regex("gpt-oss-120b"), Regex("gpt-oss-20b"), Regex("llama-4"), Regex("llama-3\\.3-70b"), Regex("qwen")),
        exclude = nonChat + Regex("compound", RegexOption.IGNORE_CASE),
        steps = listOf(
            "Tap Open the key page. Sign up with email, Google or GitHub.",
            "Tap Create API Key and give it any name, like Ballot.",
            "Copy the key straight away. Groq shows it only once.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Free tier, no card. Very fast. Limits per minute and per day.",
        privacyNote = "Check Groq's terms for how free-tier data is handled.",
        dailyHint = 1000, tagline = "Free and very fast.",
        knownGood = listOf("openai/gpt-oss-120b", "openai/gpt-oss-20b", "llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
    )

    val openRouter = ProviderPreset(
        id = "openrouter", name = "OpenRouter (free models)", baseUrl = "https://openrouter.ai/api/v1",
        keyUrl = "https://openrouter.ai/settings/keys", keyPrefixes = listOf("sk-or-"), free = true, needsCard = false, webSearch = false,
        defaultModel = "openrouter/free", fallbackModel = "openrouter/free",
        prefer = listOf(Regex("^openrouter/free$"), Regex(":free$")),
        exclude = nonChat,
        steps = listOf(
            "Tap Open the key page. Sign in with Google or GitHub.",
            "Tap Create Key and name it Ballot. Leave the credit limit empty.",
            "Copy the key.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Free models only: about 50 requests a day and 20 a minute. Free models come and go, so Ballot picks one each time you test.",
        privacyNote = "Free models are run by different companies; some log prompts.",
        dailyHint = 50, tagline = "Many free models through one key.",
        knownGood = listOf("openrouter/free"),
    )

    val cerebras = ProviderPreset(
        id = "cerebras", name = "Cerebras", baseUrl = "https://api.cerebras.ai/v1",
        keyUrl = "https://cloud.cerebras.ai", keyPrefixes = listOf("csk-"), free = true, needsCard = false, webSearch = false,
        defaultModel = "gpt-oss-120b", fallbackModel = "gpt-oss-120b",
        prefer = listOf(Regex("gpt-oss-120b"), Regex("qwen"), Regex("llama")),
        exclude = nonChat,
        steps = listOf(
            "Tap Open the key page. Sign up with email or Google.",
            "Open API Keys in the menu and create a key.",
            "Copy the key.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Free tier, no card. Daily token allowance and shorter context than the others.",
        privacyNote = "Check Cerebras's terms for how free-tier data is handled.",
        dailyHint = 1000, tagline = "Free, fast, good as a backup.", lite = true,
        knownGood = listOf("gpt-oss-120b", "qwen-3-235b-a22b-instruct-2507", "llama3.1-8b"),
    )

    val mistral = ProviderPreset(
        id = "mistral", name = "Mistral", baseUrl = "https://api.mistral.ai/v1",
        keyUrl = "https://console.mistral.ai/api-keys", keyPrefixes = emptyList(), free = true, needsCard = false, webSearch = false,
        defaultModel = "mistral-small-latest", fallbackModel = "open-mistral-nemo",
        prefer = listOf(Regex("^mistral-small-latest$"), Regex("^mistral-medium-latest$"), Regex("^mistral-large-latest$"), Regex("small")),
        exclude = nonChat + Regex("codestral|devstral|pixtral|voxtral|magistral", RegexOption.IGNORE_CASE),
        steps = listOf(
            "Tap Open the key page and create an account. Mistral checks your phone number by text.",
            "Choose the free plan if it asks.",
            "Tap Create new key, then copy it.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Free plan, no card, phone check required. About one request a second.",
        privacyNote = "On the free plan Mistral may use your requests to train its models.",
        dailyHint = 0, tagline = "Free with a phone check.",
        knownGood = listOf("mistral-small-latest", "mistral-medium-latest", "open-mistral-nemo"),
    )

    val anthropic = ProviderPreset(
        id = "anthropic", name = "Claude (Anthropic)", baseUrl = "https://api.anthropic.com/v1",
        keyUrl = "https://console.anthropic.com/settings/keys", keyPrefixes = listOf("sk-ant-"), free = false, needsCard = true, webSearch = true,
        defaultModel = "claude-sonnet-5-5", fallbackModel = "claude-haiku-5-5",
        prefer = listOf(Regex("sonnet"), Regex("haiku")), exclude = emptyList(),
        steps = listOf(
            "Tap Open the key page and sign in.",
            "Add a payment method under Billing. You pay per use.",
            "Tap Create Key, then copy it.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Paid per use. Built-in web search, the strongest research mode.",
        privacyNote = "Anthropic does not train on API requests by default.",
        dailyHint = 0, tagline = "Paid. Best research, web search built in.",
        knownGood = listOf("claude-sonnet-5-5", "claude-haiku-5-5"),
    )

    val openAi = ProviderPreset(
        id = "openai", name = "OpenAI", baseUrl = "https://api.openai.com/v1",
        keyUrl = "https://platform.openai.com/api-keys", keyPrefixes = listOf("sk-"), free = false, needsCard = true, webSearch = false,
        defaultModel = "gpt-4o", fallbackModel = "gpt-4o-mini",
        prefer = listOf(Regex("^gpt-4o$"), Regex("^gpt-4\\.1$"), Regex("^gpt-4o-mini$")), exclude = nonChat + Regex("realtime|search|instruct", RegexOption.IGNORE_CASE),
        steps = listOf(
            "Tap Open the key page and sign in.",
            "Add a payment method under Billing.",
            "Tap Create new secret key, then copy it.",
            "Come back here and tap Paste key.",
        ),
        limitsNote = "Paid per use.", privacyNote = "OpenAI does not train on API requests by default.",
        dailyHint = 0, tagline = "Paid.",
        knownGood = listOf("gpt-4o", "gpt-4o-mini"),
    )

    /** Free first, recommended first. */
    val all = listOf(gemini, groq, openRouter, cerebras, mistral, anthropic, openAi)

    fun byId(id: String): ProviderPreset = all.firstOrNull { it.id == id } ?: anthropic

    /**
     * Works out which service a pasted key belongs to from its prefix. Longer, more specific
     * prefixes are checked first, so an OpenRouter or Anthropic key is never mistaken for OpenAI.
     */
    fun detect(key: String): ProviderPreset? {
        val k = key.trim()
        if (k.isEmpty()) return null
        return all.flatMap { p -> p.keyPrefixes.map { it to p } }
            .sortedByDescending { it.first.length }
            .firstOrNull { (prefix, _) -> k.startsWith(prefix) }?.second
    }

    /** Pulls a key out of copied text: trims spaces, quotes and "KEY=" style labels. */
    fun cleanKey(raw: String): String {
        var s = raw.trim().trim('"', '\'', '`')
        if ('=' in s && s.substringBefore('=').all { it.isLetterOrDigit() || it == '_' }) s = s.substringAfter('=').trim().trim('"', '\'')
        val token = s.split(Regex("\\s+")).firstOrNull { t -> detect(t) != null } ?: s.split(Regex("\\s+")).maxByOrNull { it.length } ?: s
        return token.trim().trim('"', '\'', ',', ';')
    }

    fun looksLikeKey(text: String): Boolean {
        val k = cleanKey(text)
        return k.length in 20..300 && !k.contains(' ') && k.all { it.isLetterOrDigit() || it in "-_.:" }
    }

    private fun version(id: String): Double = Regex("(\\d+(?:\\.\\d+)?)").find(id)?.value?.toDoubleOrNull() ?: 0.0

    /**
     * Every model worth trying, best first: the service's own list ranked by preference (newest
     * first, previews last), then the known-good names, then the preset defaults.
     */
    fun candidates(p: ProviderPreset, available: List<String>): List<String> {
        val ids = available.map { it.removePrefix("models/") }.filter { id -> p.exclude.none { it.containsMatchIn(id) } }.distinct()
        val ranked = mutableListOf<String>()
        for (rx in p.prefer) {
            ids.filter { rx.containsMatchIn(it) && it !in ranked }
                .sortedWith(compareBy<String> { it.contains("preview") || it.contains("exp") }.thenByDescending { version(it) }.thenBy { it.length })
                .forEach { ranked += it }
        }
        ranked += ids.filter { it !in ranked }.sortedWith(compareBy<String> { it.contains("preview") || it.contains("exp") }.thenByDescending { version(it) })
        return (ranked + p.knownGood + listOf(p.defaultModel, p.fallbackModel)).filter { it.isNotBlank() }.distinct()
    }

    /** Chooses a chat model from the service's current list, falling back to the preset default. */
    fun pickModel(p: ProviderPreset, available: List<String>): String = candidates(p, available).first()

    /**
     * True when an error is about the model (retired, not on the free tier, not allowed) rather than
     * the key, so trying another model can help.
     */
    fun isModelProblem(status: Int, message: String): Boolean {
        val m = message.lowercase()
        return status == 404 ||
            (status == 400 && ("model" in m || "not supported" in m) && "api key" !in m) ||
            (status == 403 && "model" in m) ||
            (status == 429 && ("limit: 0" in m || "limit 0" in m || "free tier" in m && "model" in m))
    }

    /** Free when the service is free and, for OpenRouter, the model is one of its free ones. */
    fun isFreeCall(p: ProviderPreset, model: String): Boolean =
        p.free && (p.id != "openrouter" || model.endsWith(":free") || model == "openrouter/free")

    /** True when a 429 means "come back tomorrow" rather than "slow down for a moment". */
    fun isDailyLimit(status: Int, retryAfterSec: Int, message: String): Boolean =
        status == 429 && (retryAfterSec > 600 || Regex("per.?day|daily|quota|exhausted|RPD|tomorrow", RegexOption.IGNORE_CASE).containsMatchIn(message))

    /** The tools a small free model gets: enough to research, track and draft. */
    val liteTools = setOf("search_web", "fetch_official_page", "find_offices", "create_task", "complete_task", "list_plan", "add_contact", "find_contacts", "log_interaction",
        "queue_follow_up", "draft_document", "add_deadline", "add_event", "update_platform", "record_lesson", "open_page", "search_case_file")

    fun parseModels(csv: String): Map<String, String> = csv.split(';').mapNotNull { e ->
        val i = e.indexOf('=')
        if (i <= 0) null else e.substring(0, i).trim() to e.substring(i + 1).trim()
    }.filter { it.second.isNotBlank() }.toMap()

    fun encodeModels(m: Map<String, String>): String = m.entries.joinToString(";") { "${it.key}=${it.value}" }
}
