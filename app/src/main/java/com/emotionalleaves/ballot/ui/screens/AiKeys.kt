package com.emotionalleaves.ballot.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emotionalleaves.ballot.AppGraph
import com.emotionalleaves.ballot.agent.LlmException
import com.emotionalleaves.ballot.agent.ProviderPreset
import com.emotionalleaves.ballot.agent.Providers
import com.emotionalleaves.ballot.agent.clientFor
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.components.Badge
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.theme.Go
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import com.emotionalleaves.ballot.ui.theme.Numbers
import kotlinx.coroutines.launch

/** Connects one service: saves the key, asks the service for its models, picks one and tests it. */
object KeySetup {
    data class Outcome(val ok: Boolean, val message: String, val model: String = "")

    suspend fun connect(g: AppGraph, p: ProviderPreset, rawKey: String, makeMain: Boolean): Outcome {
        val key = Providers.cleanKey(rawKey)
        if (key.isBlank()) return Outcome(false, "Paste the key first.")
        val s = g.settings.get()
        val client = clientFor(p.id, s.openAiBaseUrl)
        val models = runCatching { client.listModels(key) }
        models.exceptionOrNull()?.let { e -> if (e is LlmException && isKeyProblem(e)) return Outcome(false, friendly(p, e)) }
        // Try models best-first until one answers. A model can be retired or missing from the free
        // tier without anything being wrong with the key.
        val tried = mutableListOf<String>()
        var last: Throwable? = null
        for (model in Providers.candidates(p, models.getOrDefault(emptyList())).take(8)) {
            tried += model
            val r = runCatching { client.test(key, model) }
            if (r.isSuccess) return save(g, p, key, model, makeMain)
            val e = r.exceptionOrNull()
            last = e
            if (e is LlmException && isKeyProblem(e)) return Outcome(false, friendly(p, e))
            if (e is LlmException && e.status == 0) return Outcome(false, friendly(p, e))
        }
        val detail = last?.message?.let { "\n\nLast answer from ${p.name}: $it" } ?: ""
        return Outcome(false, "${p.name} accepted the key but none of the ${tried.size} models it was asked for would answer. " +
            "This usually means the free tier is busy or changing: try again in a few minutes, or connect Groq as a second free service.$detail")
    }

    /** The key itself is wrong, so trying other models won't help. */
    private fun isKeyProblem(e: LlmException): Boolean {
        val m = (e.message ?: "").lowercase()
        return e.status == 401 || (e.status == 403 && "model" !in m) || (e.status == 400 && ("api key" in m || "api_key" in m))
    }

    private suspend fun save(g: AppGraph, p: ProviderPreset, key: String, model: String, makeMain: Boolean): Outcome {
        g.secrets.setKey(p.id, key)
        val hadMain = g.secrets.hasKey(g.settings.get().provider)
        g.settings.update { st ->
            val models = Providers.parseModels(st.providerModels) + (p.id to model)
            val main = makeMain || !hadMain || st.provider == p.id
            st.copy(
                providerModels = Providers.encodeModels(models),
                provider = if (main) p.id else st.provider,
                model = if (main && (p.id == "anthropic" || p.id == "openai")) model else st.model,
            )
        }
        g.chief.clearRest(p.id)
        g.case.audit("you", "Connected AI service", input = "${p.name} · $model")
        return Outcome(true, "Connected to ${p.name} using $model.", model)
    }

    fun friendly(p: ProviderPreset, e: Throwable): String {
        val status = (e as? LlmException)?.status ?: 0
        // The Gemini client already explains Google's specific key errors in plain words.
        if (p.id == "gemini" && e is LlmException && status != 0) return e.message ?: "Gemini returned an error."
        return when (status) {
            401, 403 -> "${p.name} rejected that key. Go back to the key page, copy it again, and paste it here."
            429 -> "${p.name} says it's busy right now. Wait a minute and tap Connect again."
            404 -> "${p.name} didn't recognise the model. ${e.message ?: ""}"
            0 -> "Couldn't reach ${p.name}. Check your connection and try again."
            else -> e.message ?: "Something went wrong."
        }
    }
}

fun readClipboard(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: ""
}

/**
 * The guided "get a free key" flow. Pick a service, open its key page, copy the key, come back and
 * paste. Ballot works out which service a key belongs to, so pasting the wrong one still works.
 */
@Composable
fun KeyConnector(makeMain: Boolean = true, onConnected: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val scope = rememberCoroutineScope()
    val c = LocalBallotColors.current
    var selected by remember { mutableStateOf(Providers.gemini) }
    var key by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var choosing by remember { mutableStateOf(false) }

    fun accept(text: String) {
        val clean = Providers.cleanKey(text)
        key = clean
        ok = false
        val found = Providers.detect(clean)
        status = when {
            clean.isBlank() -> ""
            found != null && found.id != selected.id -> { selected = found; "That's a ${found.name} key, so ${found.name} is selected." }
            found == null && !Providers.looksLikeKey(clean) -> "That doesn't look like a key. Copy it again from the key page."
            else -> ""
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("1 · Pick a service")
        // Only the chosen service shows by default, so the steps are on screen without scrolling.
        val shown = if (choosing) Providers.all else listOf(selected)
        shown.forEach { p ->
            val on = p.id == selected.id
            val connected = remember(refresh, p.id) { g.secrets.hasKey(p.id) }
            Panel(accent = on, onClick = { selected = p; status = ""; ok = false; choosing = false }, padding = 12.dp) {
                val ink = if (on) com.emotionalleaves.ballot.ui.theme.Ink else c.fg
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = ink, modifier = Modifier.weight(1f))
                    if (connected) Badge("Connected", if (on) ink else Go)
                    Badge(if (p.free) "Free" else "Paid", if (on) ink else if (p.free) Go else c.muted)
                }
                Text(p.tagline + if (!p.needsCard) " No card." else "", style = MaterialTheme.typography.bodyMedium, color = ink)
            }
        }
        if (!choosing) LinkButton("Pick a different service", onClick = { choosing = true })
        Panel {
            Kicker("2 · Get the key from ${selected.name}")
            selected.steps.forEachIndexed { i, step ->
                Row {
                    Text("${i + 1}", style = Numbers.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize), color = c.signal, modifier = Modifier.width(26.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium, color = c.fg)
                }
            }
            PosterButton("Open the key page", modifier = Modifier.fillMaxWidth(), onClick = { openUrl(ctx, selected.keyUrl) })
            Small(selected.limitsNote)
            Small(selected.privacyNote)
        }
        Panel {
            Kicker("3 · Paste and connect")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton("Paste key", primary = false, onClick = { accept(readClipboard(ctx)) })
                PosterButton(if (working) "Connecting…" else "Connect", enabled = key.isNotBlank() && !working, onClick = {
                    working = true
                    scope.launch {
                        val out = KeySetup.connect(g, selected, key, makeMain)
                        working = false
                        ok = out.ok
                        status = out.message
                        refresh++
                        if (out.ok) { key = ""; onConnected(selected.id) }
                    }
                })
            }
            Field(key, { accept(it) }, "Or type the key", singleLine = true)
            if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium, color = if (ok) Go else c.signal)
            Small("The key is stored encrypted on this phone and only ever sent to ${selected.name}.")
        }
    }
}

/** One line per connected service with today's use, for Settings. */
@Composable
fun ConnectedServices(onChanged: () -> Unit = {}) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val c = LocalBallotColors.current
    var refresh by remember { mutableIntStateOf(0) }
    var today by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var main by remember { mutableStateOf("") }
    var models by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var resting by remember { mutableStateOf<Set<String>>(emptySet()) }
    var testing by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(refresh) {
        today = g.chief.requestsToday()
        resting = g.chief.restingToday()
        val s = g.settings.get()
        main = s.provider
        models = Providers.parseModels(s.providerModels)
    }
    val connected = remember(refresh) { Providers.all.filter { g.secrets.hasKey(it.id) } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (connected.isEmpty()) Small("No AI service connected yet. Pick a free one below.")
        connected.forEach { p ->
            val used = today[p.id] ?: 0
            Panel(padding = 12.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f))
                    if (p.id == main) Badge("Main", c.signal)
                    Badge(if (p.free) "Free" else "Paid", if (p.free) Go else c.muted)
                }
                Small("Model ${models[p.id] ?: p.defaultModel} · key ••••${g.secrets.keyFor(p.id).takeLast(4)}", color = c.fg)
                Small("Today: $used request(s)" + if (p.dailyHint > 0) " of roughly ${p.dailyHint} free" else "")
                results[p.id]?.let { Small(it, color = if (it.startsWith("Connected")) Go else c.signal) }
                if (p.id in resting) Small("Used up its free allowance today. Ballot is using your other services until tomorrow.", color = c.signal)
                Row {
                    if (p.id != main) LinkButton("Make main", onClick = { scope.launch { g.settings.update { it.copy(provider = p.id) }; refresh++; onChanged() } })
                    LinkButton(if (testing == p.id) "Testing…" else "Re-test", onClick = {
                        testing = p.id
                        scope.launch {
                            val out = KeySetup.connect(g, p, g.secrets.keyFor(p.id), makeMain = false)
                            results = results + (p.id to out.message)
                            testing = ""; refresh++
                        }
                    })
                    LinkButton("Remove", color = c.muted, onClick = { g.secrets.removeKey(p.id); refresh++; onChanged() })
                }
            }
        }
    }
}
