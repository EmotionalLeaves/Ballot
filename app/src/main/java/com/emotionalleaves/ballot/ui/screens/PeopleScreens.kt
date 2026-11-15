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
import androidx.compose.foundation.layout.padding
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject

private fun roleLabel(r: String) = when (r) {
    "" -> "None"; ContactRole.MANAGER -> "Campaign manager"; else -> r.lowercase().replaceFirstChar { it }
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
    // Keyed on the whole list: logging a conversation updates lastContactAt, which refreshes the count.
    LaunchedEffect(contacts) { twoWay = g.case.stats().twoWayContacts }
    val followUps = pending.filter { it.kind == PendingKind.SEND }
    Screen("People", kicker = "Relationships, not names", walkthrough = listOf(
        "Your private contact book. It never leaves this phone.",
        "Only two-way contact counts toward your base: a real conversation, not a name on a list.",
        "Follow-ups are drafted for you; you approve and send them yourself.",
        "The kitchen cabinet shows the empty seats you need to fill: mentor, treasurer, campaign manager, validators.")) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
                BigNumber("$twoWay", "Two-way contacts", color = c.signal)
                Small("of 200 for stage 5", Modifier.padding(bottom = 6.dp))
            }
        }
        if (followUps.isNotEmpty()) item {
            Panel(accent = true, onClick = { nav.navigate(Routes.APPROVALS) }) {
                Text(if (followUps.size == 1) "1 follow-up drafted" else "${followUps.size} follow-ups drafted", style = MaterialTheme.typography.titleMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
                Text("Approve and send them yourself.", style = MaterialTheme.typography.bodyMedium, color = com.emotionalleaves.ballot.ui.theme.Ink)
            }
        }
        item { ChipRow(listOf("All", "Cabinet", "Map", "Validators", "Press"), view, { it }) { view = it } }
        // People the Chief finds land in the list below; its summary shows here, so there's no jump away.
        item { ReplyPanel { nav.navigate(Routes.CHIEF) } }
        when (view) {
            "Cabinet" -> {
                items(ContactRole.cabinet.size) { i ->
                    val role = ContactRole.cabinet[i]
                    val who = contacts.filter { it.role == role }
                    Panel(accent = who.isEmpty()) {
                        Kicker(roleLabel(role), color = if (who.isEmpty()) com.emotionalleaves.ballot.ui.theme.Ink else c.muted)
                        Text(if (who.isEmpty()) "Empty seat" else who.joinToString { it.name }, style = MaterialTheme.typography.titleLarge,
                            color = if (who.isEmpty()) com.emotionalleaves.ballot.ui.theme.Ink else c.fg)
                        // People you already know well are the likeliest fits, so list them first, in plain code.
                        if (who.isEmpty()) {
                            val fits = contacts.filter { it.role.isBlank() && it.closeness >= 3 }.sortedByDescending { it.closeness }.take(3)
                            if (fits.isNotEmpty()) Text("People you know well: " + fits.joinToString { it.name }, style = MaterialTheme.typography.bodyMedium,
                                color = com.emotionalleaves.ballot.ui.theme.Ink)
                        }
                        if (who.isEmpty()) LinkButton("Talk it through with the Chief", color = com.emotionalleaves.ballot.ui.theme.Ink, onClick = {
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
                        })
                        if (view == "Press") PosterButton("Build press list", primary = false, onClick = {
                            askChief(g, "Build my local press list from public sources: outlets, reporters, their beats and recent local stories. Save each with add_contact role PRESS and beat.", "building press list")
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
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.fg, modifier = Modifier.weight(1f))
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
                        g.case.logInteraction(p.name, notes = note, twoWay = twoWay, promisesByYou = promise, contactId = p.id)
                        note = ""; promise = ""
                    }
                })
            }
        }
        // The same person entered twice (by you and by the Chief, say) shows here with a one-tap merge.
        item {
            val all by g.db.contacts().observe().collectAsStateWithLifecycle(initialValue = emptyList())
            val first = p.name.trim().substringBefore(' ').lowercase()
            val dupes = all.filter { o -> o.id != p.id && (o.name.trim().equals(p.name.trim(), true) ||
                (first.length > 2 && o.name.trim().substringBefore(' ').lowercase() == first && (o.name.trim().contains(' ').not() || p.name.trim().contains(' ').not())) ||
                (p.phone.isNotBlank() && o.phone == p.phone) || (p.email.isNotBlank() && o.email.equals(p.email, true))) }
            if (dupes.isNotEmpty()) Panel {
                Small("Might be the same person:")
                dupes.forEach { d ->
                    LinkButton("Merge ${d.name} into ${p.name}", onClick = { scope.launch { g.case.mergeContacts(p.id, d.id) } })
                }
            }
        }
        // Messages start from a short plain template and wait in Approvals for you to edit and send. The Chief's version is optional.
        item {
            val first = p.name.trim().substringBefore(' ')
            val last = interactions.firstOrNull()?.notes?.trim()?.take(80)
            fun queue(text: String) = scope.launch {
                g.chief.tools.execute("queue_follow_up", JsonObject(mapOf("contact" to JsonPrimitive(p.name), "message" to JsonPrimitive(text))),
                    com.emotionalleaves.ballot.agent.ToolCtx(g.case, g.settings.get()))
                nav.navigate(Routes.APPROVALS)
            }
            Row {
                LinkButton("Write a follow-up", onClick = {
                    queue("Hi $first, thanks again" + (last?.let { " for the chat ($it)" } ?: "") + ". [One line on what you'll do next or when you'll see them.]")
                })
                LinkButton("Ask for intros", onClick = {
                    queue("Hi $first, I'm trying to meet more people who care about " + (p.interests.ifBlank { "[your issue]" }) +
                        ". Is there anyone you'd be happy to introduce me to? Two names would be wonderful. Thank you!")
                })
            }
            LinkButton("Or have the Chief write it", color = c.muted, onClick = {
                askChief(g, "Draft a short, warm follow-up to ${p.name} in my voice based on our last conversation (${interactions.firstOrNull()?.notes?.take(200) ?: "none logged"}). Use queue_follow_up.", "drafting follow-up")
                nav.navigate(Routes.CHIEF)
            })
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
