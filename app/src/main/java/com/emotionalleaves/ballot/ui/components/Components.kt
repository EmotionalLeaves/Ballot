package com.emotionalleaves.ballot.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emotionalleaves.ballot.ui.theme.Caution
import com.emotionalleaves.ballot.ui.theme.Go
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import com.emotionalleaves.ballot.ui.theme.Numbers

val Gutter = 20.dp

private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

/** The standard screen: a calm title, an optional back arrow, and a "walk me through it" button. */
@Composable
fun Screen(
    title: String,
    kicker: String = "",
    onBack: (() -> Unit)? = null,
    walkthrough: List<String> = emptyList(),
    actions: @Composable RowScope.() -> Unit = {},
    content: LazyListScope.() -> Unit,
) {
    val c = LocalBallotColors.current
    var showHelp by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(start = if (onBack != null) 4.dp else Gutter, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.fg)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = c.fg,
                    modifier = Modifier.semantics { heading() }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (kicker.isNotBlank()) Text(kicker, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            actions()
            if (walkthrough.isNotEmpty()) IconButton(onClick = { showHelp = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Walk me through it", tint = c.muted)
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 40.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
    if (showHelp) WalkthroughDialog(title, walkthrough) { showHelp = false }
}

@Composable
fun WalkthroughDialog(title: String, steps: List<String>, onDismiss: () -> Unit) {
    val c = LocalBallotColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
        title = { Text("How $title works", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                steps.forEachIndexed { i, s ->
                    Row {
                        Text("${i + 1}", style = Numbers.copy(fontSize = 16.sp), color = c.signal, modifier = Modifier.width(24.dp))
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
    )
}

/** A hairline divider. */
@Composable
fun ThickRule(modifier: Modifier = Modifier, thickness: Dp = 1.dp, color: Color = LocalBallotColors.current.rule) {
    HorizontalDivider(modifier, thickness = thickness.coerceAtMost(1.dp), color = color)
}

/** A small label above content. Sentence case, quiet. */
@Composable
fun Kicker(text: String, color: Color = LocalBallotColors.current.muted) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun Body(text: String, modifier: Modifier = Modifier, color: Color = LocalBallotColors.current.fg) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = color, modifier = modifier)
}

@Composable
fun Small(text: String, modifier: Modifier = Modifier, color: Color = LocalBallotColors.current.muted) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, modifier = modifier)
}

/** A section heading. */
@Composable
fun Title(text: String, modifier: Modifier = Modifier, color: Color = LocalBallotColors.current.fg) {
    Text(text, style = MaterialTheme.typography.titleLarge, color = color, modifier = modifier.padding(top = 8.dp).semantics { heading() })
}

/** A rounded card. Accent cards use a soft tint to mark the one thing that matters most. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalBallotColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (accent) c.accentSoft else c.raised)
            .border(BorderStroke(1.dp, if (accent) c.accentSoft else c.rule), CardShape)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun BigNumber(value: String, label: String, modifier: Modifier = Modifier, color: Color = LocalBallotColors.current.fg, size: Int = 32) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(value, style = Numbers.copy(fontSize = size.sp, lineHeight = (size * 1.15).sp), color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = LocalBallotColors.current.muted)
    }
}

/** The main button. Filled for the primary action, outlined for the rest. */
@Composable
fun PosterButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, primary: Boolean = true) {
    val c = LocalBallotColors.current
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.signal, contentColor = c.onSignal,
                disabledContainerColor = c.rule, disabledContentColor = c.muted),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (enabled) c.rule else c.rule.copy(alpha = 0.5f)), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.fg, containerColor = c.raised)) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalBallotColors.current.signal) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun Badge(text: String, color: Color) {
    Box(Modifier.clip(PillShape).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Verified, Likely, Unverified, as the verification gates label them. */
@Composable
fun ConfidenceBadge(label: String) {
    val c = LocalBallotColors.current
    when (label) {
        "VERIFIED" -> Badge("Verified", Go)
        "LIKELY" -> Badge("Likely", Caution)
        "STATED" -> Badge("You said", c.muted)
        "CHANGED" -> Badge("Changed", c.warn)
        else -> Badge("Unverified", c.warn)
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    number: Boolean = false,
    placeholder: String = "",
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, minLines = if (singleLine) 1 else minLines, shape = RoundedCornerShape(12.dp),
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun <T> ChipRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = LocalBallotColors.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(PillShape)
                    .background(if (on) c.accentSoft else c.raised)
                    .border(1.dp, if (on) c.signal else c.rule, PillShape)
                    .semantics { this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(o) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(o), style = MaterialTheme.typography.labelLarge, color = if (on) c.onAccent else c.fg)
            }
        }
    }
}

@Composable
fun Progress(fraction: Float, modifier: Modifier = Modifier) {
    val c = LocalBallotColors.current
    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth().height(6.dp).clip(PillShape),
        color = c.signal, trackColor = c.rule, drawStopIndicator = {})
}

@Composable
fun EmptyNote(text: String) {
    Small(text, Modifier.padding(vertical = 8.dp))
}

/** A quiet confirmation when a task is completed. */
@Composable
fun Stamp(text: String, onDone: () -> Unit) {
    val c = LocalBallotColors.current
    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(150))
        scale.animateTo(1f, tween(150))
        kotlinx.coroutines.delay(700)
        alpha.animateTo(0f, tween(250))
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.scale(scale.value).alpha(alpha.value).clip(PillShape).background(c.signal).padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("✓  $text", style = MaterialTheme.typography.titleLarge, color = c.onSignal)
        }
    }
}

/** Blocks screenshots while this screen is showing (Vault and Money). */
@Composable
fun SecureScreen() {
    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
    )
}

@Composable
fun RowSpaced(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun Gap(h: Dp = 8.dp) = Spacer(Modifier.height(h))

/** A card whose body opens on tap, so long pages show only headings until you need one. */
@Composable
fun Collapsible(title: String, subtitle: String = "", startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalBallotColors.current
    var open by remember { mutableStateOf(startOpen) }
    Panel(padding = 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.semantics { heading() })
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge, color = c.muted)
        }
        if (open) Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}
