package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.core.StageRules
import com.emotionalleaves.ballot.data.Position
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.Body
import com.emotionalleaves.ballot.ui.components.ChipRow
import com.emotionalleaves.ballot.ui.components.ConfirmDialog
import com.emotionalleaves.ballot.ui.components.EmptyNote
import com.emotionalleaves.ballot.ui.components.Field
import com.emotionalleaves.ballot.ui.components.Kicker
import com.emotionalleaves.ballot.ui.components.LinkButton
import com.emotionalleaves.ballot.ui.components.Panel
import com.emotionalleaves.ballot.ui.components.PosterButton
import com.emotionalleaves.ballot.ui.components.Screen
import com.emotionalleaves.ballot.ui.components.Small
import com.emotionalleaves.ballot.ui.components.Title
import com.emotionalleaves.ballot.ui.theme.LocalBallotColors
import kotlinx.coroutines.launch

@Composable
fun CompassScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val profile by g.db.profile().observe().collectAsStateWithLifecycle(initialValue = null)
    val positions by g.db.positions().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val p = profile ?: return
    var story by remember(p.storyDrafts, p.storyLocked) { mutableStateOf(p.story60) }
    var story3 by remember(p.id) { mutableStateOf(p.story3) }
    var story10 by remember(p.id) { mutableStateOf(p.story10) }
    var issues by remember(p.id) { mutableStateOf(p.issues) }
    var voice by remember(p.id) { mutableStateOf(p.voiceProfile) }
    var version by remember { mutableStateOf("60") }
    var editing by remember { mutableStateOf<Position?>(null) }
    var confirmUnlock by remember { mutableStateOf(false) }
    val c = LocalBallotColors.current
    Screen("Compass", kicker = "Who you are and what you stand for", onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Your reason, your issues, your positions, your story. In your words, never the Chief's.",
        "The 60-second story has a hard cap of three drafts, then it locks. That stops endless rewriting.",
        "Each position holds your stance, the strongest counter-argument and the facts with sources.",
        "Anything you say publicly is checked against what you said before.")) {
        item { BusyBar() }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosterButton("Values interview", onClick = {
                    askChief(g, "Run my values interview: ask me one question at a time (what makes me angry, what I would fix, who I want to help, a moment that shaped me, what I will never do). " +
                        "After five answers, sum up my values in my own words and suggest three issues for me to confirm.", "interviewing")
                    nav.navigate(Routes.CHIEF)
                })
                PosterButton("One-page platform", primary = false, onClick = {
                    askChief(g, "Turn my values, issues and positions into a one-page platform in my own words. No positions I have not stated. Save it with draft_document type PLATFORM.", "writing platform")
                })
            }
        }
        item { ReplyPanel { nav.navigate(Routes.CHIEF) } }
        item {
            Panel {
                Title("Three issues")
                Field(issues, { issues = it }, "Your issues, one per line", minLines = 3)
                PosterButton("Save", enabled = issues != p.issues, onClick = { scope.launch { g.case.updateProfile { it.copy(issues = issues) } } })
            }
        }
        item {
            Panel {
                Title("Story bank")
                ChipRow(listOf("60", "3", "10"), version, { if (it == "60") "60 seconds" else "$it minutes" }) { version = it }
                when (version) {
                    "60" -> {
                        Small(if (p.storyLocked) "Locked after ${p.storyDrafts} drafts. Now rehearse it in the Gym until it's rated clear."
                            else "Draft ${p.storyDrafts} of ${StageRules.STORY_DRAFT_CAP}. About 150 words.", color = if (p.storyLocked) c.signal else c.muted)
                        Field(story, { if (!p.storyLocked) story = it }, "Why you are doing this, as a story", minLines = 6)
                        Small("${story.split(Regex("\\s+")).count { it.isNotBlank() }} words")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (!p.storyLocked) {
                                PosterButton("Save draft", enabled = story.isNotBlank() && story != p.story60, onClick = {
                                    scope.launch {
                                        g.case.updateProfile {
                                            val n = it.storyDrafts + 1
                                            it.copy(story60 = story, storyDrafts = n, storyLocked = n >= StageRules.STORY_DRAFT_CAP)
                                        }
                                    }
                                })
                                PosterButton("Lock it", primary = false, enabled = p.story60.isNotBlank(), onClick = { scope.launch { g.case.updateProfile { it.copy(storyLocked = true) } } })
                            } else {
                                PosterButton("Rehearse", onClick = { nav.navigate(Routes.GYM) })
                                LinkButton("Unlock", onClick = { confirmUnlock = true }, color = c.muted)
                            }
                        }
                        if (!p.storyLocked) LinkButton("Ask the Chief for a draft", onClick = {
                            askChief(g, "Draft my 60-second story (about 150 words) from my values interview and why I want this. In my voice, plain and specific, no slogans. Show it to me; I will save it myself.", "drafting story")
                            nav.navigate(Routes.CHIEF)
                        })
                    }
                    "3" -> {
                        Field(story3, { story3 = it }, "3-minute version", minLines = 8)
                        PosterButton("Save", enabled = story3 != p.story3, onClick = { scope.launch { g.case.updateProfile { it.copy(story3 = story3) } } })
                    }
                    else -> {
                        Field(story10, { story10 = it }, "10-minute version", minLines = 10)
                        PosterButton("Save", enabled = story10 != p.story10, onClick = { scope.launch { g.case.updateProfile { it.copy(story10 = story10) } } })
                    }
                }
            }
        }
        item {
            Panel {
                Title("Your voice")
                Small("Paste a few things you have written or said: texts, posts, a speech. Drafts will sound like this.")
                Field(voice, { voice = it }, "Voice samples", minLines = 4)
                PosterButton("Save", enabled = voice != p.voiceProfile, onClick = { scope.launch { g.case.updateProfile { it.copy(voiceProfile = voice) } } })
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Title("Positions (${positions.size})")
                LinkButton("Add", onClick = { editing = Position(issue = "") })
            }
        }
        editing?.let { e ->
            item { PositionEditor(e, onDone = { editing = null }) }
        }
        if (positions.isEmpty()) item { EmptyNote("No positions yet. Add one per issue in your own words.") }
        items(positions.size) { i ->
            val pos = positions[i]
            Panel(onClick = { editing = pos }) {
                Kicker(if (pos.statedPubliclyAt > 0) "Stated publicly ${shortDate(pos.statedPubliclyAt)}" else "Private")
                Text(pos.issue.uppercase(), style = MaterialTheme.typography.titleLarge, color = c.fg)
                Body(pos.stance.ifBlank { "No stance written yet." })
                if (pos.counterArgument.isNotBlank()) Small("Strongest counter: ${pos.counterArgument}")
                if (pos.sourceIds.isNotBlank()) Small("Sources: ${pos.sourceIds}")
            }
        }
    }
    if (confirmUnlock) ConfirmDialog("Unlock the story?", "Endless rewriting is the usual stall here. Unlocking gives you one more draft.", "Unlock",
        onConfirm = { scope.launch { g.case.updateProfile { it.copy(storyLocked = false, storyDrafts = StageRules.STORY_DRAFT_CAP - 1) } } }, onDismiss = { confirmUnlock = false })
}

@Composable
private fun PositionEditor(start: Position, onDone: () -> Unit) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    var issue by remember(start.id) { mutableStateOf(start.issue) }
    var stance by remember(start.id) { mutableStateOf(start.stance) }
    var reasoning by remember(start.id) { mutableStateOf(start.reasoning) }
    var counter by remember(start.id) { mutableStateOf(start.counterArgument) }
    var facts by remember(start.id) { mutableStateOf(start.facts) }
    var publicNow by remember(start.id) { mutableStateOf(false) }
    val c = LocalBallotColors.current
    Panel(accent = false) {
        Title(if (start.id == 0L) "New position" else "Edit position")
        Field(issue, { issue = it }, "Issue", singleLine = true)
        Field(stance, { stance = it }, "Your stance, in your words", minLines = 2)
        Field(reasoning, { reasoning = it }, "Why", minLines = 2)
        Field(counter, { counter = it }, "Strongest counter-argument", minLines = 2)
        Field(facts, { facts = it }, "Facts, with sources [S#]", minLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(publicNow, { publicNow = it }); Small("I have said this in public", color = c.fg) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PosterButton("Save", enabled = issue.isNotBlank(), onClick = {
                scope.launch {
                    g.db.positions().upsert(start.copy(issue = issue, stance = stance, reasoning = reasoning, counterArgument = counter, facts = facts,
                        statedPubliclyAt = if (publicNow) now() else start.statedPubliclyAt, updatedAt = now()))
                    onDone()
                }
            })
            PosterButton("Sharpen", primary = false, enabled = issue.isNotBlank(), onClick = {
                askChief(g, "Test my position on $issue: \"$stance\". Give me the strongest honest counter-argument and the key facts with official sources. Do not change my stance; " +
                    "tell me if the evidence cuts against it. Save the counter-argument and facts with update_platform, keeping my stance as written.", "sharpening")
                onDone()
            })
            LinkButton("Cancel", onClick = onDone, color = c.muted)
        }
        if (start.id != 0L) LinkButton("Delete", color = c.muted, onClick = { scope.launch { g.db.positions().delete(start); onDone() } })
    }
}
