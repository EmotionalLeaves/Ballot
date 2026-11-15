package com.emotionalleaves.ballot.data

import androidx.room.withTransaction
import com.emotionalleaves.ballot.core.Availability
import com.emotionalleaves.ballot.core.CaseStats
import com.emotionalleaves.ballot.core.DeadlineLogic
import com.emotionalleaves.ballot.core.ScoreContext
import com.emotionalleaves.ballot.core.ScoreInput
import com.emotionalleaves.ballot.core.Scheduling
import com.emotionalleaves.ballot.core.Scored
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.core.StageRules
import com.emotionalleaves.ballot.core.TaskScorer
import com.emotionalleaves.ballot.core.WeekCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The repository over the case file. Single source of truth: screens observe the database and never
 * hold their own copy. Every multi-row write runs in a transaction.
 */
class CaseFile(val db: BallotDatabase, private val settings: SettingsStore) {

    fun today(): Long = LocalDate.now().toEpochDay()

    suspend fun profile(): Profile = db.profile().get() ?: Profile().also { db.profile().upsert(it) }

    suspend fun updateProfile(transform: (Profile) -> Profile) {
        db.withTransaction {
            val p = profile()
            db.profile().upsert(transform(p).copy(updatedAt = now()))
        }
    }

    suspend fun audit(actor: String, action: String, tool: String = "", input: String = "", sources: String = "", approvedBy: String = "", outcome: String = "") {
        db.audit().insert(AuditEntry(actor = actor, action = action, tool = tool, input = input.take(4000), sources = sources, approvedBy = approvedBy, outcome = outcome.take(2000)))
    }

    suspend fun availability(): Availability {
        val p = profile()
        val s = settings.get()
        return Availability(Scheduling.parseDays(p.workDays), p.workStartMin, p.workEndMin, s.sleepStartMin, s.sleepEndMin)
    }

    suspend fun stats(): CaseStats {
        val p = profile()
        val events = db.events().all()
        val interactions = db.interactions().all()
        val twoWayContacts = interactions.filter { it.twoWay }.map { it.contactId }.toSet().size
        val go = db.decisions().latest("GO_NO_GO")
        val debrief = db.decisions().latest("DEBRIEF")
        val mentor = db.contacts().all().any { it.role == ContactRole.MENTOR }
        return CaseStats(
            storyLocked = p.storyLocked,
            storyRatedClear = p.storyRatedClear,
            quizBest = p.quizBest,
            meetingsAttended = events.count { it.attended && (it.type == EventType.MEETING || it.type == EventType.FORUM) },
            commentsGiven = events.count { it.gaveComment },
            serveDone = p.serveDone,
            twoWayContacts = twoWayContacts,
            goDecisionAgreed = go?.agreed == true && go.choice.equals("GO", ignoreCase = true),
            electionHeld = p.electionDateEpochDay in 1..today(),
            debriefDone = debrief != null,
            hasMentor = mentor,
        )
    }

    /** Seeds the current stage's playbook. Idempotent: a template is only ever created once. */
    suspend fun seedPlaybook(stage: Stage) {
        db.withTransaction {
            StageRules.playbook(stage).forEachIndexed { i, t ->
                if (db.tasks().countTemplate(t.key) == 0) {
                    db.tasks().upsert(
                        Task(
                            stage = stage.number, title = t.title, why = t.why, steps = t.steps.joinToString("\n"),
                            doneLooksLike = t.doneLooksLike, goodEnough = t.goodEnough, sizeMinutes = t.sizeMinutes,
                            highEffort = t.highEffort, humanContact = t.humanContact, movesExit = t.movesExit,
                            module = t.module, templateKey = t.key,
                            dueEpochDay = if (i == 0) today() + 3 else 0,
                        ),
                    )
                }
            }
        }
    }

    /** Advance a stage when its exit test is met. Stages 3 to 5 cannot be skipped. */
    suspend fun advanceIfReady(): Stage? {
        val p = profile()
        val stage = Stage.of(p.stage)
        if (stage == Stage.AFTER) return null
        if (!StageRules.canAdvance(stage, stats())) return null
        val next = Stage.of(stage.number + 1)
        updateProfile { it.copy(stage = next.number) }
        seedPlaybook(next)
        audit("app", "Stage advanced to ${next.number}. ${next.title}", outcome = "Exit test met")
        return next
    }

    suspend fun scoreContext(at: LocalDateTime = LocalDateTime.now()): ScoreContext {
        val a = availability()
        val p = profile()
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val highToday = db.tasks().doneSince(startOfDay).any { it.highEffort }
        return ScoreContext(
            todayEpochDay = today(),
            freeMinutesNow = Scheduling.freeMinutesFrom(a, at).let { if (it == 0) 30 else it },
            lowEnergy = p.energy == "LOW",
            highEffortDoneToday = highToday,
            insideWorkHours = Scheduling.insideWork(a, at),
        )
    }

    fun toInput(t: Task, preparedDeadlines: Set<Long> = emptySet()): ScoreInput = ScoreInput(
        id = t.id, title = t.title, dueEpochDay = t.dueEpochDay, legal = t.legal, movesExit = t.movesExit,
        humanContact = t.humanContact, followUpSinceEpochDay = if (t.followUpSince > 0) t.followUpSince / 86_400_000L else 0,
        sizeMinutes = t.sizeMinutes, highEffort = t.highEffort, skippedCount = t.skippedCount,
        hasPreparedDraft = t.deadlineId == 0L || t.deadlineId in preparedDeadlines,
    )

    /** Recompute every open task's score and enforce the hard rules. */
    suspend fun rescore(): List<Scored> {
        ensureDeadlineDrafts()
        val ctx = scoreContext()
        val open = db.tasks().open()
        val ranked = TaskScorer.rank(open.map { toInput(it) }, ctx)
        val byId = ranked.associateBy { it.id }
        db.tasks().updateAll(open.map { t -> t.copy(score = byId[t.id]?.total ?: 0) })
        return ranked
    }

    /** Hard rule: never let a legal deadline come within seven days without a prepared draft. */
    suspend fun ensureDeadlineDrafts() {
        val today = today()
        for (d in db.deadlines().open()) {
            if (!d.legal) continue
            if (DeadlineLogic.needsPreparedDraft(d.dueEpochDay, today, d.preparedDraftId != 0L)) {
                val key = "deadline.${d.id}.draft"
                if (db.tasks().countTemplate(key) == 0) {
                    val docId = db.documents().upsert(
                        Document(type = "FORM_PREP", title = "Prepared: ${d.title}",
                            body = "Draft prepared for ${d.title}, due ${LocalDate.ofEpochDay(d.dueEpochDay)}.\n\n" +
                                "Official page: ${d.officialUrl.ifBlank { "confirm with the election office" }}\n\n" +
                                "Steps:\n1. Open the official page.\n2. Fill in the form with the Chief.\n3. Submit it yourself.",
                            inVault = true),
                    )
                    db.deadlines().upsert(d.copy(preparedDraftId = docId, updatedAt = now()))
                    db.tasks().upsert(
                        Task(stage = profile().stage, title = "Finish and file: ${d.title}", why = "A legal deadline is within seven days.",
                            doneLooksLike = "Filed by you, receipt saved to the Vault.", goodEnough = "Filed on time.",
                            steps = "Open the Vault.\nOpen the prepared draft.\nFile it yourself.\nSave the receipt.",
                            dueEpochDay = d.dueEpochDay, legal = true, sizeMinutes = 45, highEffort = true,
                            module = "vault", templateKey = key, deadlineId = d.id),
                    )
                    audit("chief", "Prepared a draft for a legal deadline", tool = "add_deadline", input = d.title)
                }
            }
        }
    }

    suspend fun completeTask(t: Task) {
        db.withTransaction {
            db.tasks().upsert(t.copy(done = true, doneAt = now(), updatedAt = now()))
            if (t.deadlineId != 0L) db.deadlines().get(t.deadlineId)?.let { db.deadlines().upsert(it.copy(done = true, updatedAt = now())) }
        }
        audit("you", "Completed task", input = t.title)
    }

    /** Skipping costs nothing, but three skips make the Chief shrink or question the task. */
    suspend fun skipTask(t: Task) {
        val skips = t.skippedCount + 1
        val shrunk = if (skips >= TaskScorer.AVOIDANCE_LIMIT && t.sizeMinutes > 5) {
            t.copy(sizeMinutes = if (t.sizeMinutes >= 45) 15 else 5, highEffort = false,
                title = if (t.title.startsWith("First step: ")) t.title else "First step: ${t.title}",
                goodEnough = "Just the first step counts today.")
        } else t
        db.tasks().upsert(shrunk.copy(skippedCount = skips, updatedAt = now()))
    }

    /** Counts for one week: from weekStartMillis up to (not including) a week later. */
    suspend fun weekCounts(weekStartMillis: Long, weekEndMillis: Long = weekStartMillis + 7 * 86_400_000L): WeekCounts {
        fun inWeek(t: Long) = t >= weekStartMillis && t < weekEndMillis
        val interactions = db.interactions().since(weekStartMillis).filter { inWeek(it.at) }
        val events = db.events().all().filter { inWeek(it.startAt) }
        val contacts = db.contacts().all().filter { inWeek(it.createdAt) }
        val reps = db.speech().all().filter { inWeek(it.createdAt) }
        val money = db.money().all().filter { inWeek(it.at) }
        val doors = db.voterContacts().all().filter { inWeek(it.at) }
        val tasksDone = db.tasks().doneSince(weekStartMillis).filter { inWeek(it.doneAt) }
        val stageUps = db.audit().all().count { inWeek(it.at) && it.action.startsWith("Stage advanced") }
        return WeekCounts(
            conversations = interactions.count { it.twoWay },
            meetings = events.count { it.attended },
            comments = events.count { it.gaveComment },
            newContacts = contacts.size,
            followUpsOnTime = tasksDone.count { it.followUpSince > 0 && (it.dueEpochDay == 0L || it.doneAt / 86_400_000L <= it.dueEpochDay) },
            speakingReps = reps.size,
            exitTestsPassed = stageUps,
            dollarsRaised = com.emotionalleaves.ballot.core.Ledger.totals(money.map { it.direction to it.amountCents }).raised,
            doors = doors.count { it.outcome != DoorOutcome.NOT_HOME },
            supportersIdentified = doors.count { it.outcome == DoorOutcome.SUPPORTER },
        )
    }

    /** Compact brief of the case file per request, so long use does not degrade answers. */
    suspend fun brief(maxChars: Int = 3500): String {
        val p = profile()
        val stage = Stage.of(p.stage)
        val exit = StageRules.exit(stage, stats())
        val tasks = db.tasks().open().sortedByDescending { it.score }.take(5)
        val deadlines = db.deadlines().open().take(5)
        val positions = db.positions().all().take(6)
        val offices = db.offices().all().take(8)
        val recentEvents = db.events().all().take(3)
        val lessons = db.lessons().all().take(3)
        val cabinet = db.contacts().all().filter { it.role in ContactRole.cabinet }
        val sb = StringBuilder()
        sb.appendLine("[P] Profile: ${p.name.ifBlank { "unnamed" }}, ZIP ${p.zip.ifBlank { "unknown" }}. Works ${p.workDays} ${hm(p.workStartMin)}-${hm(p.workEndMin)}. Energy ${p.energy}. Free hours/week ${p.weeklyHours}.")
        if (p.why.isNotBlank()) sb.appendLine("[P] Why: ${p.why.take(300)}")
        if (p.issues.isNotBlank()) sb.appendLine("[P] Issues: ${p.issues.take(200)}")
        if (p.offLimits.isNotBlank()) sb.appendLine("[P] Off limits: ${p.offLimits.take(200)}")
        if (p.employerRules.isNotBlank()) sb.appendLine("[P] Employer rules: ${p.employerRules.take(200)} (cleared: ${p.employerCleared})")
        sb.appendLine("Stage ${stage.number} ${stage.title}. Exit: ${stage.exitTest} Progress: ${exit.label}.")
        if (p.story60.isNotBlank()) sb.appendLine("[P] 60-second story: ${p.story60.take(400)}")
        if (p.voiceProfile.isNotBlank()) sb.appendLine("[P] Voice (write drafts like this): ${p.voiceProfile.take(400)}")
        tasks.forEach { sb.appendLine("[T${it.id}] Task: ${it.title} (score ${it.score}${if (it.dueEpochDay > 0) ", due ${LocalDate.ofEpochDay(it.dueEpochDay)}" else ""})") }
        deadlines.forEach { sb.appendLine("[D${it.id}] Deadline: ${it.title} ${LocalDate.ofEpochDay(it.dueEpochDay)} (${it.confidence})") }
        positions.forEach { sb.appendLine("[POS${it.id}] Position on ${it.issue}: ${it.stance.take(160)}") }
        offices.forEach { sb.appendLine("[O${it.id}] Office: ${it.body} ${it.district} held by ${it.incumbent.ifBlank { "?" }}, next ${it.nextElection.ifBlank { "?" }} (${it.confidence})") }
        recentEvents.forEach { sb.appendLine("[E${it.id}] Event: ${it.title} ${if (it.attended) "attended" else "planned"}") }
        lessons.forEach { sb.appendLine("[L${it.id}] Lesson: ${it.what.take(120)} → ${it.change.take(120)}") }
        val waiting = db.pending().all().filter { it.status == "WAITING" }
        if (waiting.isNotEmpty()) sb.appendLine("Waiting for the user's approval (not done yet): " + waiting.take(5).joinToString("; ") { it.summary.take(80) })
        val seats = ContactRole.cabinet.map { r -> r to cabinet.firstOrNull { it.role == r }?.name }
        sb.appendLine("Kitchen cabinet: " + seats.joinToString { (r, n) -> "${r.lowercase()}=${n ?: "EMPTY"}" })
        return sb.toString().take(maxChars)
    }

    private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    /** Removes everything. Used by the panic wipe and before an import. */
    suspend fun clearAll() {
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }

    // ---- Plain-code helpers: built from the case file, no AI. Screens and tools call these, so each has one home. ----

    /** Your own words, for templates and the platform. */
    suspend fun templateFacts(): com.emotionalleaves.ballot.core.TemplateFacts {
        val p = profile()
        return com.emotionalleaves.ballot.core.TemplateFacts(name = p.name, place = p.address.ifBlank { p.zip }, why = p.why, story60 = p.story60,
            issues = p.issues.lines().map { it.trim() }.filter { it.isNotBlank() },
            positions = db.positions().all().filter { it.stance.isNotBlank() }.map { it.issue to it.stance })
    }

    /** The stage 2 quiz from your stored offices and rules. */
    suspend fun townQuiz(seed: Long): List<com.emotionalleaves.ballot.core.QuizQuestion> {
        val offices = db.offices().all().map { o ->
            com.emotionalleaves.ballot.core.QuizOffice(o.id, o.body, o.title, o.district, o.level, o.powers, o.term, o.nextElection, o.incumbent, o.partTime, o.appointed)
        }
        val rules = db.rules().all().map { com.emotionalleaves.ballot.core.QuizRule(it.id, it.jurisdiction, it.summary) }
        return com.emotionalleaves.ballot.core.QuizBuilder.build(offices, rules, seed)
    }

    /** Builds and saves the prep card for one event. Returns it, or null if the event is gone. */
    suspend fun prepCard(eventId: Long): String? {
        val e = db.events().get(eventId) ?: return null
        val f = templateFacts()
        val card = com.emotionalleaves.ballot.core.PrepCard.build(e.title, e.goal, f.issues, f.positions,
            db.contacts().all().map { it.name to it.howMet },
            db.documents().all().any { it.approved && it.type == "PUBLIC_COMMENT" })
        db.events().upsert(e.copy(preCard = card, updatedAt = now()))
        return card
    }

    /**
     * A deadline you typed in yourself. One path for every screen: no duplicates, legal ones get their prepared
     * draft and task straight away, and it's audited. Saved as "you said" until it has an official page.
     */
    suspend fun addDeadlineByHand(title: String, dueEpochDay: Long, officialUrl: String = "", legal: Boolean = true): Long {
        val t = title.trim()
        db.deadlines().all().firstOrNull { !it.done && it.title.equals(t, true) && it.dueEpochDay == dueEpochDay }?.let { return it.id }
        val id = db.deadlines().upsert(Deadline(title = t, dueEpochDay = dueEpochDay, officialUrl = officialUrl.trim(), legal = legal, source = "you", confidence = Confidence.STATED))
        audit("you", "Added a deadline by hand", input = "$t · ${LocalDate.ofEpochDay(dueEpochDay)}")
        ensureDeadlineDrafts()
        rescore()
        return id
    }

    /**
     * Adds a task unless one with the same templateKey exists: ever (default), or only among open tasks when
     * [againOnceDone] is true (for checks that can come back). Rescores, so Today shows it at once.
     */
    suspend fun addTaskOnce(task: Task, againOnceDone: Boolean = false): Long? {
        if (task.templateKey.isNotBlank()) {
            val exists = if (againOnceDone) db.tasks().open().any { it.templateKey == task.templateKey } else db.tasks().countTemplate(task.templateKey) > 0
            if (exists) return null
        }
        val id = db.tasks().upsert(task)
        rescore()
        return id
    }

    /**
     * Logs one conversation with someone: by name (adding them if new) or by id. Updates their last-talked date,
     * turns a promise into a task, and checks the stage exit. The tools and the People page both use this.
     */
    suspend fun logInteraction(
        name: String, notes: String, twoWay: Boolean = true, channel: String = "IN_PERSON", promisesByYou: String = "",
        promisesByThem: String = "", promiseDueEpochDay: Long = 0, nextStep: String = "", contactId: Long? = null,
    ): Contact {
        val contact = contactId?.let { db.contacts().get(it) } ?: db.contacts().byName(name.trim())
            ?: Contact(name = name.trim(), howMet = notes.take(80)).let { it.copy(id = db.contacts().upsert(it)) }
        val due = if (promisesByYou.isNotBlank() && promiseDueEpochDay == 0L) today() + 3 else promiseDueEpochDay
        db.interactions().upsert(Interaction(contactId = contact.id, channel = channel, notes = notes, promisesByYou = promisesByYou,
            promisesByThem = promisesByThem, promiseDueEpochDay = due, twoWay = twoWay, nextStep = nextStep))
        db.contacts().upsert(contact.copy(lastContactAt = now(), nextStep = nextStep.ifBlank { contact.nextStep }, updatedAt = now()))
        if (promisesByYou.isNotBlank()) db.tasks().upsert(Task(stage = profile().stage, title = "Keep your promise to ${contact.name}: ${promisesByYou.take(60)}",
            why = "Promises kept are how trust is built.", doneLooksLike = "Done and told them.", goodEnough = "Done, briefly.",
            dueEpochDay = due, humanContact = true, followUpSince = now(), module = "network", sizeMinutes = 15))
        advanceIfReady()
        rescore()
        return contact
    }

    /**
     * Approvals left waiting for a month are closed as EXPIRED (kept, audited, never sent): a thank-you or a
     * filing drafted weeks ago is likely out of date, and a long queue hides what matters now.
     */
    suspend fun expireStaleApprovals(days: Int = 30, at: Long = now()): Int {
        val cutoff = at - days * 86_400_000L
        val stale = db.pending().all().filter { it.status == "WAITING" && it.createdAt < cutoff }
        stale.forEach { p ->
            db.pending().upsert(p.copy(status = "EXPIRED", decidedAt = at))
            audit("app", "Closed an approval left waiting $days days", input = p.summary.take(120), outcome = "expired, not sent")
        }
        return stale.size
    }

    /** Events starting within [hours] that haven't had their reminder; marks them reminded and returns them. */
    suspend fun eventsToRemind(at: Long = now(), hours: Int = 3): List<Event> {
        val soon = db.events().all().filter { it.remindedAt == 0L && it.startAt > at && it.startAt <= at + hours * 3_600_000L }
        soon.forEach { db.events().upsert(it.copy(remindedAt = at)) }
        return soon
    }

    /**
     * Keeps only what's useful (privacy rule: don't hoard). Background job chatter goes after 30 days and
     * practice-room transcripts after 90; your own conversation with the Chief and the audit log are kept.
     * Spending records go after 13 months, once no monthly cap can need them; briefing items after 60 days.
     */
    suspend fun prune(at: Long = now()): Int {
        val day = 86_400_000L
        var n = db.chat().deleteOlder("background", at - 30 * day)
        db.chat().threads().filter { it.startsWith("room:") }.forEach { n += db.chat().deleteOlder(it, at - 90 * day) }
        n += db.usage().deleteOlder(at - 400 * day)
        // Practice reps older than a year go, except your best one for each drill (it's your benchmark).
        val reps = db.speech().all()
        val best = reps.groupBy { it.kind }.mapValues { (_, l) -> l.maxByOrNull { it.total }?.id }.values.toSet()
        reps.filter { it.createdAt < at - 365 * day && it.id !in best }.forEach { db.speech().delete(it); n++ }
        // Local news goes stale: briefing items older than 60 days go (any action you added is already on your plan).
        n += db.brief().deleteOlder(at / day - 60)
        // Door-knocking notes are about voters, not you: 90 days after election day only the totals are kept, as a lesson.
        val election = profile().electionDateEpochDay
        if (election > 0 && at / day > election + 90) {
            val doors = db.voterContacts().all()
            if (doors.isNotEmpty()) {
                db.lessons().upsert(Lesson(kind = "QUARTER", what = "Door knocking: ${doors.size} doors, ${doors.count { it.outcome == DoorOutcome.SUPPORTER }} supporters identified.",
                    change = "Household notes were cleared 90 days after the election, as promised."))
                doors.forEach { db.voterContacts().delete(it) }
                audit("app", "Cleared door-knocking notes 90 days after the election", outcome = "${doors.size} removed, totals kept")
                n += doors.size
            }
        }
        return n
    }

    /**
     * Snapshots stay on the phone, so a lost or broken phone loses them too. If you haven't exported a backup for
     * [days] days (or ever, once the case file has something in it), Today gets one small task to do it.
     */
    suspend fun remindBackup(lastBackupAt: Long, at: Long = now(), days: Int = 30): Boolean {
        val due = if (lastBackupAt == 0L) db.contacts().all().isNotEmpty() || db.tasks().all().count { it.done } >= 5 else at - lastBackupAt > days * 86_400_000L
        if (!due) return false
        val key = "backup.${LocalDate.now().withDayOfMonth(1)}"
        return addTaskOnce(Task(stage = profile().stage, title = "Save a backup of your case file", templateKey = key, sizeMinutes = 5,
            why = "Everything lives on this phone. A backup file you keep elsewhere (email it to yourself, or save it to a drive) means a lost phone loses nothing.",
            doneLooksLike = "Backup file saved somewhere off this phone.", goodEnough = "Settings, Backup, Export.", module = "settings")) != null
    }

    /**
     * Two records for one person: keeps [keepId], fills its blanks from the other, moves every conversation, gift
     * and introduction over, then removes the duplicate. All in one transaction, audited.
     */
    suspend fun mergeContacts(keepId: Long, dropId: Long): Contact? {
        if (keepId == dropId) return null
        val keep = db.contacts().get(keepId) ?: return null
        val drop = db.contacts().get(dropId) ?: return null
        fun pick(a: String, b: String) = a.ifBlank { b }
        val merged = keep.copy(howMet = pick(keep.howMet, drop.howMet), interests = listOf(keep.interests, drop.interests).filter { it.isNotBlank() }.distinct().joinToString("; "),
            district = pick(keep.district, drop.district), livesInDistrict = keep.livesInDistrict || drop.livesInDistrict, closeness = maxOf(keep.closeness, drop.closeness),
            role = if (keep.role == ContactRole.NONE) drop.role else keep.role, phone = pick(keep.phone, drop.phone), email = pick(keep.email, drop.email),
            beat = pick(keep.beat, drop.beat), pledgeCents = maxOf(keep.pledgeCents, drop.pledgeCents), lastContactAt = maxOf(keep.lastContactAt, drop.lastContactAt),
            nextStep = pick(keep.nextStep, drop.nextStep), introducedById = if (keep.introducedById != 0L) keep.introducedById else drop.introducedById, updatedAt = now())
        db.withTransaction {
            db.interactions().all().filter { it.contactId == dropId }.forEach { db.interactions().upsert(it.copy(contactId = keepId)) }
            db.money().all().filter { it.contactId == dropId }.forEach { db.money().upsert(it.copy(contactId = keepId)) }
            db.contacts().all().filter { it.introducedById == dropId }.forEach { db.contacts().upsert(it.copy(introducedById = keepId)) }
            db.contacts().upsert(merged)
            db.contacts().delete(drop)
        }
        audit("you", "Merged two contacts", input = "${drop.name} into ${keep.name}")
        return merged
    }

    /** Marks a deadline done (or not). Done also ticks off its "finish and file" task, so Today stops showing it. */
    suspend fun setDeadlineDone(d: Deadline, done: Boolean) {
        db.deadlines().upsert(d.copy(done = done, updatedAt = now()))
        if (done) db.tasks().open().filter { it.deadlineId == d.id }.forEach { completeTask(it) }
        audit("you", if (done) "Marked a deadline done" else "Reopened a deadline", input = d.title)
        rescore()
    }

    /** Deleting a person removes their conversations too, and unlinks (but keeps) gifts, which reports still need. */
    suspend fun deleteContact(c: Contact) {
        db.withTransaction {
            db.interactions().all().filter { it.contactId == c.id }.forEach { db.interactions().delete(it) }
            db.money().all().filter { it.contactId == c.id }.forEach { db.money().upsert(it.copy(contactId = 0, person = it.person.ifBlank { c.name })) }
            db.contacts().all().filter { it.introducedById == c.id }.forEach { db.contacts().upsert(it.copy(introducedById = 0)) }
            db.contacts().delete(c)
        }
        audit("you", "Deleted a contact", input = c.name)
    }

    /** Deleting an office removes its race card with it. */
    suspend fun deleteOffice(o: Office) {
        db.withTransaction {
            db.raceCards().all().filter { it.officeId == o.id }.forEach { db.raceCards().delete(it) }
            db.offices().delete(o)
        }
        audit("you", "Deleted an office", input = o.body)
    }

    /** One search hit: what it is, its title, a short line, and where tapping it goes (a card target and id). */
    data class Hit(val kind: String, val title: String, val line: String, val target: String, val refId: Long = 0)

    /**
     * Searches the whole case file in plain code: people, tasks, drafts, events, deadlines, offices and lessons.
     * Every word you type must appear (in any order, any case). Newest and best matches first, at most [max].
     */
    suspend fun search(query: String, max: Int = 30): List<Hit> {
        val words = query.lowercase().split(Regex("\\s+")).filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        fun hit(vararg fields: String) = fields.joinToString(" ").lowercase().let { all -> words.all { it in all } }
        val out = mutableListOf<Hit>()
        db.contacts().all().filter { hit(it.name, it.howMet, it.interests, it.district, it.role, it.nextStep) }
            .forEach { out += Hit("Person", it.name, it.howMet.ifBlank { it.interests }, "contact", it.id) }
        db.tasks().all().filter { !it.done && hit(it.title, it.why) }.forEach { out += Hit("Task", it.title, it.why, "path") }
        db.documents().all().filter { hit(it.title, it.body) }.forEach { out += Hit("Draft", it.title, it.body.take(80), "document", it.id) }
        db.events().all().filter { hit(it.title, it.place, it.goal, it.debrief) }.forEach { out += Hit("Event", it.title, it.place, "field") }
        db.deadlines().all().filter { !it.done && hit(it.title) }.forEach { out += Hit("Deadline", it.title, LocalDate.ofEpochDay(it.dueEpochDay).toString(), "vault") }
        db.offices().all().filter { hit(it.body, it.title, it.district, it.incumbent, it.powers) }.forEach { out += Hit("Office", "${it.body} ${it.district}".trim(), it.incumbent, "ground") }
        db.lessons().all().filter { hit(it.what, it.change) }.forEach { out += Hit("Lesson", it.what.take(80), it.change, "casefile") }
        return out.take(max)
    }

    /**
     * The week ahead from your own records, no AI: meetings in the next 7 days, deadlines in the next 14,
     * promises coming due and rule pages that changed. Each line says when.
     */
    suspend fun weekAhead(at: Long = now()): List<String> {
        val day = 86_400_000L
        val today = at / day
        val zone = ZoneId.systemDefault()
        val out = mutableListOf<String>()
        db.events().all().filter { it.startAt in at..(at + 7 * day) }.sortedBy { it.startAt }.forEach { e ->
            val t = java.time.Instant.ofEpochMilli(e.startAt).atZone(zone)
            out += "Meeting · ${t.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())} " +
                "${t.toLocalTime().format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))}: ${e.title}"
        }
        db.deadlines().all().filter { !it.done && it.dueEpochDay in today..(today + 14) }.sortedBy { it.dueEpochDay }.forEach { d ->
            val left = d.dueEpochDay - today
            out += "Deadline · ${if (left == 0L) "today" else if (left == 1L) "tomorrow" else "in $left days"}: ${d.title}"
        }
        db.interactions().all().filter { it.promisesByYou.isNotBlank() && it.promiseDueEpochDay in today..(today + 7) }.forEach { i ->
            val who = db.contacts().get(i.contactId)?.name ?: "someone"
            out += "Promise to $who: ${i.promisesByYou.take(60)}"
        }
        db.rules().all().filter { it.status == "CHANGED" }.forEach { out += "Rule page changed: ${it.summary.take(60)}" }
        return out
    }

    /**
     * People worth a check-in, in plain code: anyone close (closeness 3+) you haven't talked to in 30 days, and
     * anyone with a next step written down and no contact for 14. Longest gap first, at most [max].
     */
    suspend fun checkIns(at: Long = now(), max: Int = 5): List<Pair<Contact, String>> {
        val day = 86_400_000L
        return db.contacts().all().mapNotNull { c ->
            val gap = if (c.lastContactAt == 0L) Long.MAX_VALUE else (at - c.lastContactAt) / day
            when {
                c.nextStep.isNotBlank() && gap >= 14 -> c to "Next step: ${c.nextStep.take(60)}"
                c.closeness >= 3 && gap >= 30 -> c to (if (gap == Long.MAX_VALUE) "No conversation logged yet" else "Last talked $gap days ago")
                else -> null
            }
        }.sortedByDescending { (c, _) -> if (c.lastContactAt == 0L) Long.MAX_VALUE else at - c.lastContactAt }.take(max)
    }
}

