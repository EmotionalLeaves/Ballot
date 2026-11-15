package com.emotionalleaves.ballot.ui.screens

import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.switchTab

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emotionalleaves.ballot.AppGraph
import com.emotionalleaves.ballot.agent.ChiefReply
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import com.emotionalleaves.ballot.work.Workers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Whether the Chief is working on something, so every screen can show it. */
object ChiefBusy {
    /** Number of requests in flight; busy stays true until the last one returns. */
    var running by mutableStateOf(0)
    val busy: Boolean get() = running > 0
    var label by mutableStateOf("")
    var lastReply by mutableStateOf<ChiefReply?>(null)
    /** Requests in flight, so Stop can cancel them. */
    val jobs = java.util.concurrent.CopyOnWriteArrayList<kotlinx.coroutines.Job>()

    fun stopAll() {
        jobs.toList().forEach { it.cancel() }
        jobs.clear()
        running = 0
    }
}

/**
 * Runs a Chief request in the app scope so it survives leaving the screen. The reply lands in the
 * chat thread and the case file; screens observe those.
 */
fun askChief(g: AppGraph, prompt: String, label: String = "Working", thread: String = "main", fromVoice: Boolean = false, onReply: (ChiefReply) -> Unit = {}) {
    ChiefBusy.running += 1
    ChiefBusy.label = label
    val job = g.scope.launch {
        val r = try {
            g.chief.ask(prompt, thread = thread, fromVoice = fromVoice)
        } catch (e: kotlinx.coroutines.CancellationException) {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.Main) { ChiefBusy.lastReply = null }
            throw e
        } catch (e: Exception) {
            ChiefReply(e.message ?: "Something went wrong.", com.emotionalleaves.ballot.agent.Mode.STRATEGIST, emptyList(), emptyList(), error = true)
        }
        if (r.queued) Workers.drainQueueWhenOnline(g.context)
        withContext(Dispatchers.Main) {
            ChiefBusy.running = (ChiefBusy.running - 1).coerceAtLeast(0)
            ChiefBusy.lastReply = r
            onReply(r)
        }
    }
    ChiefBusy.jobs += job
    job.invokeOnCompletion { ChiefBusy.jobs.remove(job) }
}

@Composable
fun BusyBar() {
    if (!ChiefBusy.busy) return
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), color = LocalBallotColors.current.signal, strokeWidth = 3.dp)
            Text("The Chief · ${ChiefBusy.label.replaceFirstChar { it.uppercase() }}…", style = MaterialTheme.typography.labelLarge,
                color = LocalBallotColors.current.fg, modifier = Modifier.weight(1f))
            com.emotionalleaves.ballot.ui.components.LinkButton("Stop", onClick = { ChiefBusy.stopAll() }, color = LocalBallotColors.current.muted)
        }
    }
}

/** Shows the Chief's latest reply inline on module screens. */
@Composable
fun ReplyPanel(onOpenChief: () -> Unit) {
    val r = ChiefBusy.lastReply ?: return
    Panel(onClick = onOpenChief) {
        Small("The Chief · ${r.mode.label} · tap for the whole conversation")
        Text(chiefText(r.text.take(600)), style = MaterialTheme.typography.bodyMedium, color = LocalBallotColors.current.fg, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

// Pages that share one job are combined: one entry in More, tabs at the top.
fun voiceTabs(nav: androidx.navigation.NavHostController) = listOf("Who you are" to { nav.switchTab(Routes.COMPASS) }, "Drafts" to { nav.switchTab(Routes.STUDIO) })
fun recordTabs(nav: androidx.navigation.NavHostController) = listOf("Vault" to { nav.switchTab(Routes.VAULT) }, "Case file" to { nav.switchTab(Routes.CASEFILE) })
fun townTabs(nav: androidx.navigation.NavHostController) = listOf("Offices and races" to { nav.switchTab(Routes.GROUND) }, "Town quiz" to { nav.switchTab(Routes.QUIZ) })
fun planTabs(nav: androidx.navigation.NavHostController) = listOf("Plan" to { nav.switchTab(Routes.PATH) }, "Weekly review" to { nav.switchTab(Routes.REVIEW) })

/** "1 request", "3 requests". */
fun count(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

/** A stored code like GO_NO_GO shown as "Go no go". */
fun pretty(code: String): String = code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

fun day(epochDay: Long): String = if (epochDay <= 0) "—" else LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("EEE d MMM yyyy"))

fun dateTime(millis: Long): String = if (millis <= 0) "—" else
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm"))

fun shortDate(millis: Long): String = if (millis <= 0) "—" else
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))

fun dollars(cents: Long): String = "$" + "%,d".format(cents / 100) + if (cents % 100 != 0L) ".%02d".format(kotlin.math.abs(cents % 100)) else ""

fun hm(min: Int): String = "%02d:%02d".format(min / 60, min % 60)

fun parseHm(s: String): Int? = s.trim().split(":").takeIf { it.size == 2 }?.let { (h, m) ->
    val hh = h.toIntOrNull() ?: return null
    val mm = m.toIntOrNull() ?: return null
    if (hh in 0..23 && mm in 0..59) hh * 60 + mm else null
}

fun parseDay(s: String): Long? = runCatching { LocalDate.parse(s.trim()).toEpochDay() }.getOrNull()

fun shareText(context: Context, text: String, subject: String = "") {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    context.startActivity(Intent.createChooser(i, "Send it yourself").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun dial(context: Context, phone: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun Column2(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
