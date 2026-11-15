package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.agent.BallotJson
import com.emotionalleaves.ballot.agent.Card
import com.emotionalleaves.ballot.data.ChatMessage
import com.emotionalleaves.ballot.data.SourceRecord
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.ConfidenceBadge
import com.emotionalleaves.ballot.ui.components.Gutter
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.ThickRule
import com.emotionalleaves.ballot.ui.components.rememberMicGate
import com.emotionalleaves.ballot.ui.components.rememberSpeechCapture
import com.emotionalleaves.ballot.ui.openCard
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import com.emotionalleaves.ballot.ui.theme.Signal
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

private val starters = listOf(
    "What should I do this week?",
    "Find every office that represents me",
    "Start my values interview",
    "Explain what a city council controls",
    "Help me draft a public comment",
)

@Composable
fun ChiefScreen(nav: NavHostController, thread: String = "main") {
    val g = LocalContext.current.graph
    val messages by g.db.chat().observe(thread).collectAsStateWithLifecycle(initialValue = emptyList())
    val sources by g.db.sources().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val queued by g.db.queue().observeCount().collectAsStateWithLifecycle(initialValue = 0)
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val speech = rememberSpeechCapture()
    val mic = rememberMicGate()
    val scope = rememberCoroutineScope()
    val c = LocalBallotColors.current
    val byId = remember(sources) { sources.associateBy { it.id } }
    LaunchedEffect(messages.size, ChiefBusy.busy) { if (messages.isNotEmpty()) list.animateScrollToItem((messages.size - 1 + if (ChiefBusy.busy) 1 else 0).coerceAtLeast(0)) }

    fun send(text: String, voice: Boolean = false) {
        if (text.isBlank() || ChiefBusy.busy) return
        askChief(g, text.trim(), label = "thinking", thread = thread, fromVoice = voice)
        input = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
                Text("C", style = MaterialTheme.typography.titleLarge, color = c.onAccent)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("The Chief", style = MaterialTheme.typography.headlineMedium, color = c.fg)
                Small(if (queued > 0) "$queued request(s) queued until you're online" else "Your chief of staff. Asks before anything goes out in your name.")
            }
            if (messages.isNotEmpty()) LinkButton("Clear", onClick = { confirmClear = true }, color = c.muted)
        }
        ThickRule(Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("What do you need?", style = MaterialTheme.typography.displaySmall, color = c.fg)
                    Small("Ask in plain words. Tap a starter:")
                    starters.forEach { s -> Panel(onClick = { send(s) }) { Text(s, style = MaterialTheme.typography.bodyLarge, color = c.fg) } }
                }
            }
            items(messages, key = { it.id }) { m -> MessageView(m, byId, onCard = { nav.openCard(it) }) }
            if (ChiefBusy.busy) item { BusyBar() }
        }
        if (speech.listening) Text(speech.partial.ifBlank { "Listening… tap stop when you're done." }, style = MaterialTheme.typography.bodyMedium,
            color = c.signal, modifier = Modifier.padding(horizontal = Gutter))
        if (speech.error.isNotEmpty()) Small(speech.error, Modifier.padding(horizontal = Gutter), color = Signal)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask the Chief…") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                maxLines = 5, textStyle = MaterialTheme.typography.bodyLarge)
            // One button: the microphone while the box is empty (or while listening), send once there's text.
            val sendMode = input.isNotBlank() && !speech.listening
            IconButton(onClick = {
                when {
                    sendMode -> send(input)
                    speech.listening -> speech.stop()
                    else -> mic { speech.start { text, _ -> if (text.isNotBlank()) send(text, voice = true) } }
                }
            }, enabled = !(sendMode && ChiefBusy.busy),
                modifier = Modifier.padding(start = 8.dp).size(48.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (sendMode || speech.listening) c.signal else c.raised)) {
                Icon(when { sendMode -> Icons.AutoMirrored.Filled.Send; speech.listening -> Icons.Filled.Stop; else -> Icons.Filled.Mic },
                    contentDescription = when { sendMode -> "Send"; speech.listening -> "Stop voice input"; else -> "Voice input" },
                    tint = if (sendMode || speech.listening) c.onSignal else c.fg)
            }
        }
    }
    if (confirmClear) ClearDialog(onConfirm = { scope.launch { g.db.chat().clear(thread) } }, onDismiss = { confirmClear = false })
}

@Composable
private fun ClearDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    com.emotionalleaves.ballot.ui.components.ConfirmDialog("Clear the conversation?",
        "This removes the chat only. Everything the Chief filed (contacts, tasks, sources, drafts) stays in your case file.", "Clear", onConfirm, onDismiss)
}

@Composable
fun MessageView(m: ChatMessage, sources: Map<Long, SourceRecord>, onCard: (Card) -> Unit) {
    val c = LocalBallotColors.current
    val ctx = LocalContext.current
    if (m.role == "user") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Text(m.text, style = MaterialTheme.typography.bodyLarge, color = c.onAccent,
                modifier = Modifier.padding(start = 48.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(c.accentSoft).padding(horizontal = 14.dp, vertical = 10.dp))
        }
        return
    }
    val cards = remember(m.cards) {
        runCatching { if (m.cards.isBlank()) emptyList() else BallotJson.decodeFromString(ListSerializer(Card.serializer()), m.cards) }.getOrDefault(emptyList())
    }
    val ids = remember(m.sourceIds) { m.sourceIds.split(',').mapNotNull { it.trim().toLongOrNull() } }
    Column(Modifier.fillMaxWidth().padding(end = 24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(c.raised)
        .border(1.dp, c.rule, androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker("Chief${if (m.mode.isNotBlank()) " · ${m.mode}" else ""} · ${shortDate(m.at)}", color = c.muted)
        Text(m.text, style = MaterialTheme.typography.bodyLarge, color = c.fg)
        cards.forEach { card ->
            Panel(onClick = { onCard(card) }, padding = 12.dp) {
                if (card.type != "INFO") Kicker(pretty(card.type), color = c.muted)
                Text(card.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = c.fg)
                if (card.body.isNotBlank()) Small(card.body)
            }
        }
        val srcs = ids.mapNotNull { sources[it] }
        if (srcs.isNotEmpty()) {
            Kicker("Sources")
            srcs.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("S${s.id}", style = MaterialTheme.typography.labelMedium, color = c.muted)
                    ConfidenceBadge(s.label)
                    LinkButton(s.title.ifBlank { com.emotionalleaves.ballot.core.Verification.host(s.url) }.take(40), onClick = { openUrl(ctx, s.url) })
                }
            }
        }
    }
}

@Composable
fun StarterChips(options: List<String>, onPick: (String) -> Unit) {
    ChipRow(options, null, { it }) { onPick(it) }
}
