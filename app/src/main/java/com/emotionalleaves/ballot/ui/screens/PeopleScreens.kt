package com.emotionalleaves.ballot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.emotionalleaves.ballot.data.Contact
import com.emotionalleaves.ballot.data.ContactRole
import com.emotionalleaves.ballot.data.Interaction
import com.emotionalleaves.ballot.data.PendingKind
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import com.emotionalleaves.ballot.ui.Routes
import com.emotionalleaves.ballot.ui.components.BigNumber
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

private fun roleLabel(r: String) = when (r) {
    "" -> "None"; ContactRole.MANAGER -> "Campaign manager"; else -> r.lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
fun PeopleScreen(nav: NavHostController) {
    val g = LocalContext.current.graph
    val scope = rememberCoroutineScope()
    val contacts by g.db.contacts().observe().collectAsStateWithLifecycle(initialValue = emptyList())
    val pending by g.db.pending().observeWaiting().collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var view by remember { mutableStateOf("All") }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var howMet by remember { mutableStateOf("") }
    var twoWay by remember { mutableIntStateOf(0) }
    val c = LocalBallotColors.current
    LaunchedEffect(contacts.size) { twoWay = g.case.stats().twoWayContacts }
    val followUps = pending.filter { it.kind == PendingKind.SEND }
    Screen("People", kicker = "Relationships, not names", walkthrough = listOf(
        "Your private contact book. It never leaves this phone.",
        "Only two-way contact counts toward your base: a real conversation, not a name on a list.",
        "Follow-ups are drafted for you; you approve and send them yourself.",
        "The kitchen cabinet shows the empty seats you need to fill: mentor, treasurer, campaign manager, validators.")) {
        item {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier) {
                BigNumber("$twoWay", "Two-way contacts", color = c.signal)
                Small("   of 200 for stage 5")
            }
        }
        if (followUps.isNotEmpty()) item {
            Panel(accent = true, onClick = { nav.navigate(Routes.APPROVALS) }) {
                Text("${followUps.size} FOLLOW-UP(S) DRAFTED", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                Text("Approve and send them yourself.", style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
            }
        }
        item { ChipRow(listOf("All", "Cabinet", "Map", "Validators", "Press"), view, { it }) { view = it } }
        when (view) {
            "Cabinet" -> {
                items(ContactRole.cabinet.size) { i ->
                    val role = ContactRole.cabinet[i]
                    val who = contacts.filter { it.role == role }
                    Panel(accent = who.isEmpty()) {
                        Kicker(roleLabel(role), color = if (who.isEmpty()) com.emotionalleaves.ballot.ui.theme.Ink else c.muted)
                        Text(if (who.isEmpty()) "EMPTY SEAT" else who.joinToString { it.name }, style = MaterialTheme.typography.titleLarge,
                            color = if (who.isEmpty()) com.emotionalleaves.ballot.ui.theme.Ink else c.fg)
                        if (who.isEmpty()) LinkButton("Help me find one", color = com.emotionalleaves.ballot.ui.theme.Ink, onClick = {
                            askChief(g, "Help me find a ${roleLabel(role).lowercase()} for my kitchen cabinet. Who in my contacts could fit, who could introduce me, and how do I ask? Draft the ask as a follow-up.", "finding a ${roleLabel(role).lowercase()}")
                            nav.navigate(Routes.CHIEF)
                        })
                    }
                }
            }
            "Map" -> {
                val byId = contacts.associateBy { it.id }
                val introducers = contacts.filter { it.introducedById != 0L }.groupBy { it.introducedById }
                if (introducers.isEmpty()) item { EmptyNote("When you log who introduced you to whom, the chain shows here. Ask each new contact for two introductions.") }
                introducers.forEach { (from, list) ->
                    item {
                        Panel {
                            Kicker("Introduced by")
                            Text(byId[from]?.name ?: "Unknown", style = MaterialTheme.typography.titleMedium, color = c.fg)
                            list.forEach { Small("→ ${it.name}", color = c.fg) }
                        }
                    }
                }
            }
            else -> {
                val filtered = contacts.filter {
                    (query.isBlank() || it.name.contains(query, true) || it.interests.contains(query, true) || it.howMet.contains(query, true)) &&
                        when (view) { "Validators" -> it.role == ContactRole.VALIDATOR || it.endorsementStatus.isNotBlank(); "Press" -> it.role == ContactRole.PRESS; else -> true }
                }
                item { Field(query, { query = it }, "Search", singleLine = true) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PosterButton(if (adding) "Close" else "Add person", onClick = { adding = !adding })
                        if (view == "Validators") PosterButton("Build the pipeline", primary = false, onClick = {
                            askChief(g, "Build my endorsements and validators pipeline: people and groups whose support matters locally, ranked by influence in my district, from public sources. For each: what they care about, who can introduce me, the ask. Save them with add_contact role VALIDATOR.", "building the pipeline")
                            nav.navigate(Routes.CHIEF)
                        })
                        if (view == "Press") PosterButton("Build press list", primary = false, onClick = {
                            askChief(g, "Build my local press list from public sources: outlets, reporters, their beats and recent local stories. Save each with add_contact role PRESS and beat.", "building press list")
                            nav.navigate(Routes.CHIEF)
                        })
                    }
                }
                if (adding) item {
                    Panel {
                        Field(name, { name = it }, "Name", singleLine = true)
                        Field(howMet, { howMet = it }, "How you met", singleLine = true)
                        PosterButton("Save", enabled = name.isNotBlank(), onClick = {
                            scope.launch {
                                val id = g.db.contacts().upsert(Contact(name = name.trim(), howMet = howMet))
                                g.case.audit("you", "Added contact", input = name)
                                name = ""; howMet = ""; adding = false
                                nav.navigate(Routes.contact(id))
                            }
                        })
                    }
                }
                if (filtered.isEmpty()) item { EmptyNote(if (contacts.isEmpty()) "No one yet. After your first meeting, debrief by voice and the Chief will add people for you to confirm." else "No match.") }
                items(filtered.size) { i ->
                    val p = filtered[i]
                    Panel(onClick = { nav.navigate(Routes.contact(p.id)) }, padding = 12.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.name.uppercase(), style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f))
                            if (p.role.isNotBlank()) Small(roleLabel(p.role))
                        }
                        Small(listOfNotNull(p.howMet.takeIf { it.isNotBlank() }, p.beat.takeIf { it.isNotBlank() }?.let { "beat: $it" },
                            "last contact ${shortDate(p.lastContactAt)}", p.nextStep.takeIf { it.isNotBlank() }?.let { "next: $it" }).joinToString(" · "))
                    }
                }
            }
        }
    }
}

@Composable
fun ContactScreen(nav: NavHostController, id: Long) {
    val g = LocalContext.current.graph
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val contact by g.db.contacts().observeOne(id).collectAsStateWithLifecycle(initialValue = null)
    val interactions by g.db.interactions().forContact(id).collectAsStateWithLifecycle(initialValue = emptyList())
    var edit by remember { mutableStateOf<Contact?>(null) }
    var note by remember { mutableStateOf("") }
    var promise by remember { mutableStateOf("") }
    var twoWay by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = LocalBallotColors.current
    val p = contact ?: run {
        Screen("Contact", onBack = { nav.popBackStack() }) { item { EmptyNote("This contact was deleted.") } }
        return
    }
    val e = edit ?: p
    Screen(p.name, kicker = roleLabel(p.role), onBack = { nav.popBackStack() }, walkthrough = listOf(
        "Edit what you know about them and tap Save.", "Log every real conversation. Two-way contact is what counts.",
        "Promises you make become tasks so nothing is dropped.", "Draft follow-up writes a message for you to approve and send yourself.")) {
        item {
            Panel {
                Field(e.howMet, { edit = e.copy(howMet = it) }, "How you met")
                Field(e.interests, { edit = e.copy(interests = it) }, "What they care about")
                Field(e.district, { edit = e.copy(district = it) }, "Where they live", singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(e.livesInDistrict, { edit = e.copy(livesInDistrict = it) }); Small("Lives in my district", color = c.fg)
                }
                Kicker("Closeness")
                ChipRow((1..5).toList(), e.closeness, { listOf("Met once", "Acquaintance", "Friendly", "Ally", "Close")[it - 1] }) { edit = e.copy(closeness = it) }
                Kicker("Role")
                ChipRow(ContactRole.all, e.role, { roleLabel(it) }) { edit = e.copy(role = it) }
                Field(e.phone, { edit = e.copy(phone = it) }, "Phone", singleLine = true)
                Field(e.email, { edit = e.copy(email = it) }, "Email", singleLine = true)
                if (e.role == ContactRole.PRESS) Field(e.beat, { edit = e.copy(beat = it) }, "Beat", singleLine = true)
                if (e.role == ContactRole.VALIDATOR) Field(e.endorsementStatus, { edit = e.copy(endorsementStatus = it) }, "Endorsement status: the ask, the answer", singleLine = true)
                Field(e.nextStep, { edit = e.copy(nextStep = it) }, "Next step", singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PosterButton("Save", enabled = edit != null, onClick = { scope.launch { g.db.contacts().upsert(e.copy(updatedAt = now())); edit = null } })
                    if (p.phone.isNotBlank()) PosterButton("Call", primary = false, onClick = { dial(ctx, p.phone) })
                }
            }
        }
        item {
            Panel {
                Title("Log a conversation")
                Field(note, { note = it }, "What you talked about", minLines = 2)
                Field(promise, { promise = it }, "Anything you promised", singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(twoWay, { twoWay = it }); Small("A real two-way conversation", color = c.fg) }
                PosterButton("Log it", enabled = note.isNotBlank(), onClick = {
                    scope.launch {
                        g.db.interactions().upsert(Interaction(contactId = p.id, notes = note, promisesByYou = promise, twoWay = twoWay,
                            promiseDueEpochDay = if (promise.isNotBlank()) g.case.today() + 3 else 0))
                        g.db.contacts().upsert(p.copy(lastContactAt = now(), updatedAt = now()))
                        if (promise.isNotBlank()) g.db.tasks().upsert(com.emotionalleaves.ballot.data.Task(stage = g.case.profile().stage,
                            title = "Keep your promise to ${p.name}: ${promise.take(60)}", why = "Promises kept are how trust is built.",
                            doneLooksLike = "Done and told them.", humanContact = true, followUpSince = now(), dueEpochDay = g.case.today() + 3, module = "network"))
                        note = ""; promise = ""
                        g.case.advanceIfReady()
                    }
                })
            }
        }
        item {
            Row {
                LinkButton("Draft follow-up", onClick = {
                    askChief(g, "Draft a short, warm follow-up to ${p.name} in my voice based on our last conversation (${interactions.firstOrNull()?.notes?.take(200) ?: "none logged"}). Use queue_follow_up.", "drafting follow-up")
                    nav.navigate(Routes.CHIEF)
                })
                LinkButton("Ask for intros", onClick = {
                    askChief(g, "Draft a short ask to ${p.name} for two introductions to people I should meet, given what they care about (${p.interests}). Use queue_follow_up.", "drafting intro ask")
                    nav.navigate(Routes.CHIEF)
                })
            }
        }
        item { Title("History (${interactions.size})") }
        items(interactions.size) { i ->
            val ix = interactions[i]
            Panel(padding = 12.dp) {
                Kicker("${ix.channel.replace('_', ' ')} · ${dateTime(ix.at)}${if (ix.twoWay) " · two-way" else ""}")
                Body(ix.notes)
                if (ix.promisesByYou.isNotBlank()) Small("You promised: ${ix.promisesByYou}", color = c.signal)
                if (ix.promisesByThem.isNotBlank()) Small("They promised: ${ix.promisesByThem}")
            }
        }
        item { LinkButton("Delete contact", onClick = { confirmDelete = true }, color = c.muted) }
    }
    if (confirmDelete) ConfirmDialog("Delete ${p.name}?", "This removes them from your contact book. Logged conversations stay in the record.", "Delete",
        onConfirm = { scope.launch { g.db.contacts().delete(p); nav.popBackStack() } }, onDismiss = { confirmDelete = false })
}
