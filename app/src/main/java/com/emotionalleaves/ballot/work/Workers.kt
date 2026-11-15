package com.emotionalleaves.ballot.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.emotionalleaves.ballot.MainActivity
import com.emotionalleaves.ballot.R
import com.emotionalleaves.ballot.agent.PageFetcher
import com.emotionalleaves.ballot.core.DeadlineLogic
import com.emotionalleaves.ballot.core.Scheduling
import com.emotionalleaves.ballot.core.Stage
import com.emotionalleaves.ballot.data.ContactRole
import com.emotionalleaves.ballot.data.Snapshots
import com.emotionalleaves.ballot.data.Task
import com.emotionalleaves.ballot.data.now
import com.emotionalleaves.ballot.graph
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Notifications {
    const val DEADLINES = "deadlines"
    const val BRIEF = "brief"
    const val FOLLOW_UPS = "followups"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(DEADLINES, "Deadlines", NotificationManager.IMPORTANCE_HIGH).apply { description = "Legal and filing deadlines" })
        nm.createNotificationChannel(NotificationChannel(BRIEF, "Daily brief", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Your daily brief is ready" })
        nm.createNotificationChannel(NotificationChannel(FOLLOW_UPS, "Follow-ups", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Follow-ups and promises coming due" })
    }

    fun post(context: Context, channel: String, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_ballot)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            // On the lock screen show only the heading: the case file stays private until the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_stat_ballot)
                .setContentTitle(title).setContentText("Open Ballot to see it.").setContentIntent(open).setAutoCancel(true).build())
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

object Workers {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        // Idempotent: KEEP means re-scheduling never duplicates a job.
        wm.enqueueUniquePeriodicWork("hourly", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HourlyWorker>(1, TimeUnit.HOURS).build())
        wm.enqueueUniquePeriodicWork("daily_brief", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<BriefWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        wm.enqueueUniquePeriodicWork("rule_recheck", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RuleRecheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    fun drainQueueWhenOnline(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("drain_queue", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<QueueWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    fun recheckNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("rule_recheck_now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<RuleRecheckWorker>().setInputData(androidx.work.workDataOf("force" to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }

    fun briefNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("brief_now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BriefWorker>().setInputData(androidx.work.workDataOf("force" to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}

/** Deadline alerts, follow-up and promise reminders, playbook seeding, rescoring and the daily snapshot. */
class HourlyWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val g = applicationContext.graph
        val case = g.case
        val s = g.settings.get()
        val p = case.profile()
        if (!p.setupDone) return Result.success()
        val today = case.today()
        val nowT = LocalDateTime.now()
        val avail = case.availability()
        val quiet = Scheduling.quiet(avail, nowT)

        // Daily snapshot, seven kept.
        if (s.lastSnapshotDay != today) {
            runCatching { Snapshots.take(applicationContext) }
            g.settings.update { it.copy(lastSnapshotDay = today) }
        }

        case.seedPlaybook(Stage.of(p.stage))
        case.advanceIfReady()
        case.rescore()

        if (!quiet) {
            // Deadlines: alerts at the lead times, 14, 7 and 2 days by default. Never repeated.
            for (d in case.db.deadlines().open()) {
                val sent = d.alertsSent.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
                val due = DeadlineLogic.alertsDue(d.dueEpochDay, today, DeadlineLogic.parseLeads(d.leadAlerts), sent)
                if (due.isNotEmpty()) {
                    val days = d.dueEpochDay - today
                    Notifications.post(applicationContext, Notifications.DEADLINES, (d.id % 100000).toInt() + 1000,
                        "${d.title}: " + when (days) { 0L -> "due today"; 1L -> "due tomorrow"; else -> "$days days left" },
                        "Due ${LocalDate.ofEpochDay(d.dueEpochDay).format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM"))}. ${if (d.preparedDraftId != 0L) "Draft ready in the Vault." else "Open Ballot to prepare it."}")
                    case.db.deadlines().upsert(d.copy(alertsSent = (sent + due).joinToString(","), updatedAt = now()))
                }
            }
            // Follow-ups three days old, and promises coming due.
            val promises = case.db.interactions().all().filter { it.promisesByYou.isNotBlank() && it.promiseDueEpochDay in today..today + 1 }
            val staleFollowUps = case.db.tasks().open().filter { it.followUpSince > 0 && now() - it.followUpSince >= 3 * 86_400_000L && it.templateKey.isEmpty() }
            if (promises.isNotEmpty() || staleFollowUps.isNotEmpty()) {
                val key = "followup.$today"
                if (case.db.tasks().countTemplate(key) == 0) {
                    case.db.tasks().upsert(Task(stage = p.stage, title = "Clear " + listOfNotNull(
                        staleFollowUps.size.takeIf { it > 0 }?.let { if (it == 1) "1 follow-up" else "$it follow-ups" },
                        promises.size.takeIf { it > 0 }?.let { if (it == 1) "1 promise" else "$it promises" }).joinToString(" and "),
                        why = "Follow-ups lose value each day they wait.", doneLooksLike = "Each one done or rescheduled.", goodEnough = "Short messages are fine.",
                        humanContact = true, templateKey = key, module = "network", sizeMinutes = 15, followUpSince = now() - 3 * 86_400_000L))
                    Notifications.post(applicationContext, Notifications.FOLLOW_UPS, 2001, "Follow-ups waiting",
                        listOfNotNull(
                            promises.size.takeIf { it > 0 }?.let { if (it == 1) "1 promise due" else "$it promises due" },
                            staleFollowUps.size.takeIf { it > 0 }?.let { if (it == 1) "1 follow-up three days old" else "$it follow-ups three days old" },
                        ).joinToString(", ") + ".")
                }
            }
            // The weekly review, on the day you chose.
            val reviewKey = "review.${Scheduling.weekStart(LocalDate.now())}"
            if (LocalDate.now().dayOfWeek.value == p.weeklyReviewDay && case.db.tasks().countTemplate(reviewKey) == 0) {
                case.db.tasks().upsert(Task(stage = p.stage, title = "Weekly review", why = "Ten minutes: see the score, pick one thing to stop, start and keep, and next week's three priorities.",
                    doneLooksLike = "Review saved.", goodEnough = "Rough answers are fine.", templateKey = reviewKey, module = "review", sizeMinutes = 15,
                    dueEpochDay = today, movesExit = true))
                Notifications.post(applicationContext, Notifications.BRIEF, 3003, "Weekly review", "Ten minutes to look back and pick next week's three priorities.")
            }
            // Proactive trigger: the weekly score dropped two weeks in a row.
            val ws = Scheduling.weekStart(LocalDate.now()).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val history = (3 downTo 1).map { w -> com.emotionalleaves.ballot.core.WeeklyScore.score(case.weekCounts(ws - w * 7 * 86_400_000L)) }
            val dropKey = "scoredrop.${Scheduling.weekStart(LocalDate.now())}"
            if (com.emotionalleaves.ballot.core.WeeklyScore.droppingTwice(history) && case.db.tasks().countTemplate(dropKey) == 0) {
                case.db.tasks().upsert(Task(stage = p.stage, title = "Shrink the plan: your score dropped two weeks running",
                    why = "A falling score means something is in the way. Naming it is the fix.", doneLooksLike = "A smaller plan for this week agreed with the Chief.",
                    goodEnough = "One honest sentence about the blocker.", templateKey = dropKey, module = "path", sizeMinutes = 5))
                Notifications.post(applicationContext, Notifications.BRIEF, 3002, "Two quieter weeks", "Tap to shrink this week's plan to what fits.")
            }
            // Mentor requirement: by stage 4 there must be a mentor in the kitchen cabinet.
            if (p.stage >= 4 && case.db.contacts().all().none { it.role == ContactRole.MENTOR } && case.db.tasks().countTemplate("s4.mentor") == 0) {
                case.seedPlaybook(Stage.SERVE)
            }
        }
        return Result.success()
    }
}

/** The daily brief: local news and agendas that touch your issues, waiting when you open the app. */
class BriefWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val g = applicationContext.graph
        val case = g.case
        val p = case.profile()
        if (!p.setupDone || !g.chief.hasKey()) return Result.success()
        val force = inputData.getBoolean("force", false)
        val s = g.settings.get()
        val nowT = LocalDateTime.now()
        val minute = nowT.hour * 60 + nowT.minute
        val today = case.today()
        // Build once a day, after the chosen brief time, never during work or sleep.
        val alreadyBuilt = case.db.brief().countDay(today) > 0
        if (!force && (minute < s.briefMinuteOfDay || alreadyBuilt || Scheduling.quiet(case.availability(), nowT))) return Result.success()
        val issues = p.issues.ifBlank { "local government" }
        val r = runCatching {
            g.chief.ask(
                "Background job: build today's Briefing for ZIP ${p.zip.ifBlank { "unknown" }}. Search for local news from the last few days, upcoming public meeting agendas, " +
                    "open seats, retirements or filing windows that touch these issues: $issues. Save up to five items with save_brief_item, each with a source link and one optional action. " +
                    "If a seat opened or a filing window was announced, say so first. Then reply with one sentence.",
                thread = "background", persist = false, queue = false,
            )
        }.getOrNull() ?: return Result.retry()
        if (r.queued) return Result.retry()
        // An error reply (no key, spend cap, refused) is not a brief: never announce it as one.
        if (r.error) return if (force) Result.failure() else Result.success()
        val built = case.db.brief().countDay(today)
        if (built > 0) Notifications.post(applicationContext, Notifications.BRIEF, 3001, "Your brief is ready", r.text.take(160))
        return Result.success()
    }
}

/** Rules and deadline pages are re-checked; a changed page raises a flag. */
class RuleRecheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val case = applicationContext.graph.case
        val s = applicationContext.graph.settings.get()
        val today = case.today()
        val deadlines = case.db.deadlines().open()
        for (rule in case.db.rules().all()) {
            if (rule.officialUrl.isBlank()) continue
            val due = deadlines.filter { it.ruleId == rule.id }.minOfOrNull { it.dueEpochDay } ?: (today + 365)
            if (!inputData.getBoolean("force", false) && !DeadlineLogic.recheckDue(rule.lastChecked, now(), due, today, s.freshnessDaysRules)) continue
            val page = runCatching { PageFetcher.fetch(rule.officialUrl) }.getOrNull() ?: continue
            // An error page (404, 500, a maintenance notice) is not a rule change: try again next time.
            if (page.status !in 200..299 || page.text.isBlank()) continue
            val prev = case.db.sources().get(rule.sourceId)
            val changed = prev != null && prev.contentHash.isNotEmpty() && prev.contentHash != page.hash
            val official = page.secure && com.emotionalleaves.ballot.core.Verification.isOfficial(page.url, s.officialDomains)
            val label = com.emotionalleaves.ballot.core.Verification.label(official, fresh = true, crossChecked = !changed && prev != null)
            val newSource = case.db.sources().insert(com.emotionalleaves.ballot.data.SourceRecord(url = page.url, title = page.title.take(200),
                excerpt = page.text.take(20_000), official = official, crossChecked = !changed && prev != null, label = label,
                contentHash = page.hash, confidence = label, source = "recheck"))
            case.db.rules().upsert(rule.copy(lastChecked = now(), sourceId = newSource, status = if (changed) "CHANGED" else label, updatedAt = now()))
            val key = "rulechange.${rule.id}.${page.hash}"
            if (changed && case.db.tasks().countTemplate(key) == 0) {
                case.audit("app", "Rule page changed since last check", input = rule.officialUrl)
                case.db.tasks().upsert(Task(stage = case.profile().stage, title = "Re-check a changed rule: ${rule.summary.take(60)}",
                    why = "The official page changed since the last check.", doneLooksLike = "Rule confirmed or updated with the Chief.",
                    goodEnough = "Read the change; call the office if unclear.", legal = true, dueEpochDay = today + 2, module = "vault",
                    templateKey = key))
                Notifications.post(applicationContext, Notifications.DEADLINES, 4000 + (rule.id % 1000).toInt(), "A rule page changed", rule.summary.take(120))
            }
        }
        return Result.success()
    }
}

class QueueWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val n = runCatching { applicationContext.graph.chief.drainQueue() }.getOrElse { return Result.retry() }
        return if (n >= 0) Result.success() else Result.retry()
    }
}
