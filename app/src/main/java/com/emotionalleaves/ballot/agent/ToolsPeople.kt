package com.emotionalleaves.ballot.agent

import com.emotionalleaves.ballot.core.Approval
import com.emotionalleaves.ballot.core.CampaignMath
import com.emotionalleaves.ballot.core.InjectionDefense
import com.emotionalleaves.ballot.core.SpeechScorer
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.Verification
import com.emotionalleaves.ballot.data.AppSettings
import com.emotionalleaves.ballot.data.CaseFile
import com.emotionalleaves.ballot.data.Confidence
import com.emotionalleaves.ballot.data.Contact
import com.emotionalleaves.ballot.data.ContactRole
import com.emotionalleaves.ballot.data.Deadline
import com.emotionalleaves.ballot.data.Decision
import com.emotionalleaves.ballot.data.Document
import com.emotionalleaves.ballot.data.Event
import com.emotionalleaves.ballot.data.EventType
import com.emotionalleaves.ballot.data.FactStatus
import com.emotionalleaves.ballot.data.Interaction
import com.emotionalleaves.ballot.data.Lesson
import com.emotionalleaves.ballot.data.Money
import com.emotionalleaves.ballot.data.MoneyDirection
import com.emotionalleaves.ballot.data.Office
import com.emotionalleaves.ballot.data.PendingAction
import com.emotionalleaves.ballot.data.PendingKind
import com.emotionalleaves.ballot.data.Position
import com.emotionalleaves.ballot.data.RaceCard
import com.emotionalleaves.ballot.data.RoutineKind
import com.emotionalleaves.ballot.data.Rule
import com.emotionalleaves.ballot.data.SourceRecord
import com.emotionalleaves.ballot.data.Task
import com.emotionalleaves.ballot.data.now
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** The contact book and conversations. Registered by [ToolRegistry]. */
internal fun ToolRegistry.registerPeopleTools() {
    val S = Schema

    reg(ToolDef("add_contact", "Add or update a person in the private contact book.",
        S.obj(listOf("name"), mapOf(
            "name" to S.s("Full name"), "how_met" to S.s("How you met"), "interests" to S.s("What they care about"),
            "district" to S.s("Where they live"), "lives_in_district" to S.b("In the user's district"),
            "closeness" to S.i("1 acquaintance to 5 close"), "role" to S.e("Role", ContactRole.all.filter { it.isNotEmpty() }),
            "phone" to S.s("Phone"), "email" to S.s("Email"), "next_step" to S.s("Next step"),
            "introduced_by" to S.s("Name of who introduced them"), "beat" to S.s("For press: their beat"),
        )), RoutineKind.TRACKER, voiceConfirm = true, required = listOf("name")) { a, c ->
        val name = a.str("name")!!.trim()
        val existing = c.case.db.contacts().byName(name)
        val introducer = a.str("introduced_by")?.let { c.case.db.contacts().byName(it) }
        val contact = (existing ?: Contact(name = name)).copy(
            howMet = a.str("how_met") ?: existing?.howMet ?: "", interests = a.str("interests") ?: existing?.interests ?: "",
            district = a.str("district") ?: existing?.district ?: "", livesInDistrict = a.bool("lives_in_district") ?: existing?.livesInDistrict ?: false,
            closeness = (a.int("closeness") ?: existing?.closeness ?: 1).coerceIn(1, 5), role = a.str("role") ?: existing?.role ?: "",
            phone = a.str("phone") ?: existing?.phone ?: "", email = a.str("email") ?: existing?.email ?: "",
            nextStep = a.str("next_step") ?: existing?.nextStep ?: "", introducedById = introducer?.id ?: existing?.introducedById ?: 0,
            beat = a.str("beat") ?: existing?.beat ?: "", updatedAt = now(),
        )
        val id = c.case.db.contacts().upsert(contact).let { if (it == -1L) contact.id else it }
        ToolOutcome("${if (existing == null) "Added" else "Updated"} contact C$id ${contact.name}.",
            cards = listOf(Card("CONTACT", contact.name, contact.howMet.ifBlank { contact.role.ifBlank { "Contact" } }, "contact", id)))
    })

    reg(ToolDef("find_contacts",
        "Look up people in the user's private contact book by name, interest, role or district. Read only. Use it before suggesting who to ask for anything.",
        S.obj(props = mapOf("query" to S.s("Words to match in name, how met, interests or district; blank lists the closest people"),
            "role" to S.e("Only this role", ContactRole.all.filter { it.isNotEmpty() }), "in_district_only" to S.b("Only people who live in the district"))),
        RoutineKind.RESEARCH) { a, c ->
        val q = a.str("query")?.trim()?.lowercase().orEmpty()
        val role = a.str("role")
        val all = c.case.db.contacts().all()
        val hits = all.filter { p ->
            (role == null || p.role == role) && (a.bool("in_district_only") != true || p.livesInDistrict) &&
                (q.isEmpty() || listOf(p.name, p.howMet, p.interests, p.district, p.beat).any { it.lowercase().contains(q) })
        }.sortedWith(compareByDescending<Contact> { it.closeness }.thenByDescending { it.lastContactAt }).take(25)
        if (hits.isEmpty()) return@ToolDef ToolOutcome(if (all.isEmpty()) "The contact book is empty." else "No one matches \"$q\". ${all.size} people in the book.")
        ToolOutcome(hits.joinToString("\n") { p ->
            "C${p.id} ${p.name}" + listOfNotNull(p.role.takeIf { it.isNotBlank() }?.lowercase(), "closeness ${p.closeness}/5",
                p.howMet.takeIf { it.isNotBlank() }?.let { "met: $it" }, p.interests.takeIf { it.isNotBlank() }?.let { "cares about: $it" },
                if (p.livesInDistrict) "in district" else null, p.nextStep.takeIf { it.isNotBlank() }?.let { "next: $it" }).joinToString(", ", " (", ")")
        } + if (hits.size == 25) "\n(first 25 shown)" else "")
    })

    reg(ToolDef("log_interaction", "Log a conversation with a contact, including promises made in both directions. Two-way contact is what counts.",
        S.obj(listOf("contact"), mapOf(
            "contact" to S.s("Contact name"), "channel" to S.e("Channel", listOf("IN_PERSON", "PHONE", "TEXT", "EMAIL", "EVENT", "VIDEO")),
            "notes" to S.s("What was said"), "promises_by_you" to S.s("What the user promised"), "promises_by_them" to S.s("What they promised"),
            "promise_due" to S.s("YYYY-MM-DD"), "two_way" to S.b("A real two-way conversation"), "next_step" to S.s("Next step"),
        )), RoutineKind.TRACKER, voiceConfirm = true, required = listOf("contact")) { a, c ->
        val contact = c.case.logInteraction(a.str("contact")!!, notes = a.str("notes") ?: "", twoWay = a.bool("two_way") ?: true,
            channel = a.str("channel") ?: "IN_PERSON", promisesByYou = a.str("promises_by_you") ?: "", promisesByThem = a.str("promises_by_them") ?: "",
            promiseDueEpochDay = a.str("promise_due")?.let { ToolRegistry.parseDate(it) } ?: 0, nextStep = a.str("next_step") ?: "")
        ToolOutcome("Logged an interaction with ${contact.name}.", cards = listOf(Card("CONTACT", contact.name, "Interaction logged", "contact", contact.id)))
    })

    reg(ToolDef("queue_follow_up",
        "Draft a follow-up message for the user to approve and send themselves. The app never sends it. Use the user's voice.",
        S.obj(listOf("contact", "message"), mapOf(
            "contact" to S.s("Contact name"), "message" to S.s("The drafted message, short and warm"),
            "channel" to S.e("How it will be sent", listOf("TEXT", "EMAIL", "PHONE_SCRIPT")), "due" to S.s("YYYY-MM-DD"),
        )), PendingKind.SEND, required = listOf("contact", "message")) { a, c ->
        // Reached only after approval: the user sends it themselves from the approval card.
        val contact = c.case.db.contacts().byName(a.str("contact")!!)
        contact?.let {
            c.case.db.contacts().upsert(it.copy(lastContactAt = now(), updatedAt = now()))
            // The contact's history shows what went out. One-way: it only counts toward the base once they answer.
            c.case.db.interactions().upsert(Interaction(contactId = it.id, channel = when (a.str("channel")) { "EMAIL" -> "EMAIL"; "PHONE_SCRIPT" -> "PHONE"; else -> "TEXT" },
                notes = "Follow-up sent: " + a.str("message")!!.take(300), twoWay = false))
        }
        ToolOutcome("Approved. The user is sending it themselves." + if (contact == null) " ${a.str("contact")} isn't in the contact book yet." else "")
    })
}
