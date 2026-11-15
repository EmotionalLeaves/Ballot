package com.emotionalleaves.ballot.core

/** Facts about the case file the exit tests read. Plain data so the tests run on the JVM. */
data class CaseStats(
    val storyLocked: Boolean = false,
    val storyRatedClear: Boolean = false,
    val quizBest: Int = 0,
    val meetingsAttended: Int = 0,
    val commentsGiven: Int = 0,
    val serveDone: Boolean = false,
    val twoWayContacts: Int = 0,
    val goDecisionAgreed: Boolean = false,
    val electionHeld: Boolean = false,
    val debriefDone: Boolean = false,
    val hasMentor: Boolean = false,
)

data class ExitProgress(val met: Boolean, val label: String, val fraction: Float)

data class TaskTemplate(
    val key: String,
    val title: String,
    val why: String,
    val doneLooksLike: String,
    val goodEnough: String,
    val steps: List<String>,
    val sizeMinutes: Int = 15,
    val highEffort: Boolean = false,
    val humanContact: Boolean = false,
    val movesExit: Boolean = true,
    val module: String = "",
)

enum class Stage(
    val number: Int,
    val title: String,
    val window: String,
    val exitTest: String,
    val leadingIndicator: String,
    val stall: String,
    val counter: String,
) {
    REASON(1, "Find your reason", "Weeks 1 to 2", "You can say why you are doing this without notes.",
        "Story recorded and rated clear", "Endless rewriting", "A hard cap of three drafts, then lock."),
    GROUND(2, "Learn the ground", "Months 1 to 3", "You pass the Chief's quiz on your own town (8 of 10).",
        "Quiz score on your own town", "Reading without retaining", "Short quizzes and teach-back."),
    SHOW_UP(3, "Show up", "Months 2 to 9", "Ten meetings attended, three public comments given.",
        "Meetings attended, comments given", "Nerves", "Rehearse in Practice. Attend once silently first."),
    SERVE(4, "Serve", "Months 6 to 18", "One role or one finished project you can point to.",
        "Applications sent", "Waiting to feel ready", "Apply at the first eligible opening."),
    BASE(5, "Build a base", "Ongoing", "200 people in your network who would take your call.",
        "New relationships a week, follow-ups on time", "Collecting names, not relationships", "Only two-way contact counts."),
    CHOOSE(6, "Choose the race", "When the base is there", "A written go or no-go you agree with.",
        "Go or no-go memo written", "Aiming too high too early", "The winnability score, shown plainly."),
    RUN(7, "Run", "Filing to election day", "Election day is behind you.",
        "Supporters identified against the win number", "Desk work replacing voter contact", "Contact hours are tracked first."),
    AFTER(8, "Serve or regroup", "The two weeks after", "Debrief complete within two weeks.",
        "Debrief complete", "Burning the network after a loss", "Thank everyone within 72 hours.");

    companion object {
        fun of(n: Int): Stage = entries.firstOrNull { it.number == n } ?: REASON
    }
}

object StageRules {
    const val MEETINGS_NEEDED = 10
    const val COMMENTS_NEEDED = 3
    const val NETWORK_NEEDED = 200
    const val QUIZ_PASS = 8
    const val STORY_DRAFT_CAP = 3

    fun exit(stage: Stage, s: CaseStats): ExitProgress = when (stage) {
        Stage.REASON -> {
            val parts = listOf(s.storyLocked, s.storyRatedClear).count { it }
            ExitProgress(parts == 2, "Story locked and rated clear: $parts of 2", parts / 2f)
        }
        Stage.GROUND -> ExitProgress(s.quizBest >= QUIZ_PASS, "Best quiz score: ${s.quizBest} of 10 (pass is $QUIZ_PASS)",
            (s.quizBest / QUIZ_PASS.toFloat()).coerceAtMost(1f))
        Stage.SHOW_UP -> {
            val m = s.meetingsAttended.coerceAtMost(MEETINGS_NEEDED)
            val c = s.commentsGiven.coerceAtMost(COMMENTS_NEEDED)
            ExitProgress(m >= MEETINGS_NEEDED && c >= COMMENTS_NEEDED,
                "Meetings ${s.meetingsAttended} of $MEETINGS_NEEDED · comments ${s.commentsGiven} of $COMMENTS_NEEDED",
                (m + c) / (MEETINGS_NEEDED + COMMENTS_NEEDED).toFloat())
        }
        Stage.SERVE -> ExitProgress(s.serveDone, if (s.serveDone) "Role or project done" else "No role or finished project yet",
            if (s.serveDone) 1f else 0f)
        Stage.BASE -> ExitProgress(s.twoWayContacts >= NETWORK_NEEDED,
            "Two-way contacts: ${s.twoWayContacts} of $NETWORK_NEEDED",
            (s.twoWayContacts / NETWORK_NEEDED.toFloat()).coerceAtMost(1f))
        Stage.CHOOSE -> ExitProgress(s.goDecisionAgreed, if (s.goDecisionAgreed) "Go or no-go written and agreed" else "No agreed go or no-go memo yet",
            if (s.goDecisionAgreed) 1f else 0f)
        Stage.RUN -> ExitProgress(s.electionHeld, if (s.electionHeld) "Election held" else "Campaign in progress",
            if (s.electionHeld) 1f else 0f)
        Stage.AFTER -> ExitProgress(s.debriefDone, if (s.debriefDone) "Debrief done" else "Debrief not written yet",
            if (s.debriefDone) 1f else 0f)
    }

    /**
     * Stages 3 to 5 cannot be skipped. The only bends are the fast lanes: an open-seat alert can
     * bring the race decision forward, which the Chief records as a decision rather than a jump.
     */
    fun canAdvance(stage: Stage, s: CaseStats): Boolean = exit(stage, s).met

    fun reachable(target: Stage, current: Stage, s: CaseStats, openSeatFastLane: Boolean): Boolean {
        if (target.number <= current.number) return true
        if (target == Stage.CHOOSE && openSeatFastLane && current.number >= Stage.SHOW_UP.number) return true
        return target.number == current.number + 1 && canAdvance(current, s)
    }

    fun playbook(stage: Stage): List<TaskTemplate> = when (stage) {
        Stage.REASON -> listOf(
            TaskTemplate("s1.values", "Do the values interview", "Your reason to run is the base of every speech and every ask.",
                "Five answers saved in Who you are.", "Rough answers are fine. Honest beats polished.",
                listOf("Open the Chief.", "Say: start my values interview.", "Answer each question out loud or typed."), 15, module = "compass"),
            TaskTemplate("s1.issues", "Pick three issues", "Three is enough to be clear and few enough to remember.",
                "Three issues saved, each with one sentence on why.", "One sentence each.",
                listOf("Look at your values answers.", "Pick what you would fix first.", "Save three in Who you are."), 15, module = "compass"),
            TaskTemplate("s1.story", "Draft your 60-second story", "People vote for people. The story is how they meet you.",
                "A draft of about 150 words saved in the story bank.", "Three drafts maximum, then it locks.",
                listOf("Open Who you are, then Story bank.", "Ask the Chief for a first draft from your interview.", "Edit it into your own words."), 15, module = "compass"),
            TaskTemplate("s1.record", "Record the 60-second story five times", "Saying it out loud is what makes it yours.",
                "Five recorded reps in Practice, last one rated clear.", "Clear beats perfect.",
                listOf("Open Practice.", "Pick the story drill.", "Record, read the score, repeat."), 15, highEffort = true, module = "gym"),
        )
        Stage.GROUND -> listOf(
            TaskTemplate("s2.offices", "List every office that represents you", "You cannot pick a race you cannot see.",
                "Your offices listed with official links.", "Local offices first.",
                listOf("Open Offices and races.", "Tap Find my offices.", "Check each link the Chief gives."), 15, module = "ground"),
            TaskTemplate("s2.explainer", "Read one body explainer", "Knowing what a council really controls keeps your promises honest.",
                "One explainer read and one teach-back answer given.", "One explainer a day is plenty.",
                listOf("Open Offices and races.", "Pick a body.", "Ask the Chief: explain what this body controls."), 5, movesExit = true, module = "ground"),
            TaskTemplate("s2.recording", "Watch one meeting recording", "Meetings show who holds power and how decisions really happen.",
                "One meeting watched and three notes saved.", "Watch at 1.5x. Note who spoke and what passed.",
                listOf("Ask the Chief where your council posts recordings.", "Watch one.", "Log three notes."), 45, highEffort = true, module = "briefing"),
            TaskTemplate("s2.budget", "Read last year's budget summary", "The budget is the real list of priorities.",
                "Three numbers you can repeat without notes.", "The summary page only.",
                listOf("Ask the Chief to find the official budget summary.", "Read it.", "Save three numbers to Who you are."), 45, highEffort = true, module = "ground"),
            TaskTemplate("s2.quiz", "Take the quiz on your own town", "The quiz turns reading into knowing.",
                "A score of 8 of 10.", "Retake as often as you like.",
                listOf("Open Path.", "Tap Take the quiz.", "Answer ten questions."), 5, module = "path"),
        )
        Stage.SHOW_UP -> listOf(
            TaskTemplate("s3.calendar", "Add the next three public meetings", "Showing up is how people learn your face.",
                "Three meetings in Meetings and events.", "Dates and places are enough.",
                listOf("Open Meetings and events.", "Ask the Chief to find upcoming meetings.", "Add the ones outside work hours."), 15, module = "field"),
            TaskTemplate("s3.silent", "Attend one meeting silently", "The first time is just to learn the room.",
                "One meeting marked attended, with a 60-second debrief.", "Sitting and listening counts.",
                listOf("Go.", "Introduce yourself to two people.", "Debrief by voice afterwards."), 45, highEffort = true, humanContact = true, module = "field"),
            TaskTemplate("s3.comment", "Draft a public comment", "A comment puts your name and view on the public record.",
                "A two-minute comment approved in Drafts.", "Two minutes, one point, one ask.",
                listOf("Pick an agenda item that touches your issues.", "Ask the Chief for a draft.", "Rehearse it in Practice."), 15, module = "studio"),
            TaskTemplate("s3.volunteer", "Sign up to volunteer on a local campaign", "The apprentice route teaches campaigns faster than anything.",
                "One volunteer shift booked.", "Any local campaign you can stand behind.",
                listOf("Ask the Chief for local campaigns needing volunteers.", "Pick one.", "Sign up yourself."), 15, humanContact = true, module = "field"),
            TaskTemplate("s3.group", "Join one neighborhood or civic group", "Groups are where future supporters already gather.",
                "One meeting of the group attended.", "Join, show up, listen.",
                listOf("Ask the Chief for groups near you.", "Pick one.", "Go to its next meeting."), 45, highEffort = true, humanContact = true, module = "field"),
        )
        Stage.SERVE -> listOf(
            TaskTemplate("s4.vacancies", "Find boards and commissions with openings", "Appointed seats build a record without an election.",
                "A list of openings you are eligible for.", "Two or three is enough.",
                listOf("Open Meetings and events, then Board finder.", "Ask the Chief to check official vacancy pages.", "Save the ones you qualify for."), 15, module = "field"),
            TaskTemplate("s4.apply", "Apply to the first eligible opening", "Waiting to feel ready is the usual stall.",
                "One application submitted by you.", "Submitted beats perfect.",
                listOf("Ask the Chief for an application draft.", "Edit and approve it.", "Submit it yourself."), 45, highEffort = true, module = "studio"),
            TaskTemplate("s4.mentor", "Find a mentor who has run or served locally", "Someone who has done it will save you a year.",
                "One mentor seat filled in the kitchen cabinet.", "One coffee is a start.",
                listOf("Ask the Chief who has served locally.", "Ask for an introduction.", "Request 20 minutes."), 15, humanContact = true, module = "network"),
            TaskTemplate("s4.vet", "Run the self-vetting audit", "Find what an opponent would find, before they do.",
                "Every public item has a decision: leave, clean or answer.", "Truthful two-sentence answers.",
                listOf("Open Vault, then Self-vetting.", "Let the Chief list what is public.", "Decide on each item."), 45, highEffort = true, module = "vault"),
        )
        Stage.BASE -> listOf(
            TaskTemplate("s5.coffee", "Ask two people for coffee this week", "Relationships are the base. Coffee is how they start.",
                "Two coffees booked.", "A 20-minute coffee is enough.",
                listOf("Open People.", "Pick two contacts.", "Send the drafted ask yourself."), 15, humanContact = true, module = "network"),
            TaskTemplate("s5.intros", "Ask each new contact for two introductions", "Introductions grow a network faster than anything else.",
                "Two introductions asked for.", "Ask plainly.",
                listOf("After each coffee, ask who else you should meet.", "Log the names.", "Follow up."), 5, humanContact = true, module = "network"),
            TaskTemplate("s5.followups", "Clear the follow-up queue", "A follow-up loses value every day it waits.",
                "Queue empty.", "Short and warm is enough.",
                listOf("Open People, then Follow-ups.", "Approve each draft.", "Send them yourself."), 15, module = "network"),
        )
        Stage.CHOOSE -> listOf(
            TaskTemplate("s6.cards", "Build race cards for your eligible offices", "A race sized to your resources is how newcomers win.",
                "Race cards with official sources and scores.", "Your top three are enough.",
                listOf("Open Offices and races.", "Tap Score races.", "Check each source."), 45, highEffort = true, module = "ground"),
            TaskTemplate("s6.pastcandidates", "Talk to two people who ran for the target office", "They know the real cost and the real hours.",
                "Two conversations logged.", "Ask what they wish they had known.",
                listOf("Ask the Chief who ran before.", "Ask for 20 minutes.", "Log the lessons."), 45, highEffort = true, humanContact = true, module = "network"),
            TaskTemplate("s6.family", "Check with the people closest to you", "Running takes their time too.",
                "Their answer recorded in the go or no-go.", "An honest conversation.",
                listOf("Explain the hours and the cost.", "Ask for their honest view.", "Record it."), 45, highEffort = true, humanContact = true, module = "path"),
            TaskTemplate("s6.memo", "Write the go or no-go memo", "The decision deserves to be written down.",
                "Seven points answered, decision agreed.", "Every no becomes a task.",
                listOf("Open Path.", "Tap Go or no-go.", "Answer the seven points."), 15, module = "path"),
        )
        Stage.RUN -> listOf(
            TaskTemplate("s7.launch", "Work the launch checklist", "Committee, treasurer, bank, filing, announcement, in order.",
                "Every launch item checked.", "One item a day.",
                listOf("Open HQ.", "Do the top unchecked item.", "Confirm rules with the election office."), 45, highEffort = true, module = "hq"),
            TaskTemplate("s7.calltime", "One hour of call time", "Early money decides whether you can reach voters.",
                "Ten calls made and logged.", "Ask, then stop talking.",
                listOf("Open HQ, then Call time.", "Call each name.", "Tap the outcome."), 45, highEffort = true, humanContact = true, module = "hq"),
            TaskTemplate("s7.doors", "Knock doors on one route", "Direct voter contact, in volume, wins local races.",
                "One route finished and tallied.", "Listen first.",
                listOf("Open HQ, then Doors.", "Walk the route.", "Tap an outcome at each door."), 45, highEffort = true, humanContact = true, module = "hq"),
        )
        Stage.AFTER -> listOf(
            TaskTemplate("s8.thanks", "Thank everyone within 72 hours", "The network is the asset you keep either way.",
                "Every volunteer, donor and validator thanked.", "Short and personal.",
                listOf("Open People.", "Approve the thank-you drafts.", "Send them yourself."), 45, highEffort = true, humanContact = true, module = "network"),
            TaskTemplate("s8.debrief", "Write the debrief", "What worked and what did not, while you still remember.",
                "Debrief saved within two weeks.", "One page.",
                listOf("Ask the Chief for the debrief questions.", "Answer them.", "Save the lessons."), 45, module = "path"),
        )
    }
}
