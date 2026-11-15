# Feature inventory

Checked each phase. Controls may move or merge; a function is only removed when phase 4 judged it useless, with the reason in DECISIONS.md.

## Works without AI (owner rule, 2026-11-15)
- [x] Plan, Today, scoring, stages and reminders run in plain code
- [x] Town quiz built from your offices and rules (QuizBuilder)
- [x] Values interview form; one-page platform and every draft type from templates (Templates)
- [x] Offices and rules added by hand; events and contacts by hand
- [x] Pre-event prep card (PrepCard), debrief saved by hand, quiet-week blockers (WeeklyScore.blockers)
- [x] Follow-ups, intro asks and HQ thank-yous from templates, waiting in Approvals
- [x] Race cards by hand (filing deadline goes on the deadline wall); fast lanes read from race cards
- [x] Mock rooms run from a question bank with a plain score; drafts fact-checked in plain code with official pages pasted by you
- [x] Stage 8 debrief as four questions
- [x] AI optional: web research and first drafts only, each beside a by-hand path

## Spine
- [x] Setup walkthrough: 8 steps, one question per screen, skippable, resumable (setup step saved)
- [x] AI key entry and test, cost explained plainly, Claude or OpenAI-compatible provider
- [x] Home: three-line brief, one primary action, why-this score breakdown, walk-me-through, done stamp, skip, too-much button
- [x] Path: 8 stages, exit tests, stall and counter per stage, fast lanes, decisions with "I agree", lessons
- [x] Town quiz built only from the case file (stage 2 exit)
- [x] Go or no-go: seven points, every "no" becomes a task
- [x] Weekly review: score, stop/start/keep, three priorities, flat-week blocker, monthly and quarterly prompts

## The Chief
- [x] One agent, five modes, mode shown on each reply
- [x] Tool loop with 30 typed tools (search_case_file (the same plain search as More), open_page (one-tap card to any page), change_setting (everyday settings only), run_job (brief, rule re-check, snapshot), search_web (free search), find_contacts, list_plan, complete_task, update_event, search_web via provider, fetch_official_page, verify_claim, find_offices, get_race_card, add_contact, log_interaction, queue_follow_up, create_task, reschedule_plan, add_deadline, log_money, draft_document, score_speech, start_mock_room, update_platform, save_to_vault, add_event, record_lesson, record_decision, update_profile, save_brief_item)
- [x] Approval levels: does without asking / asks first / never; autonomy dial for routine kinds; SEND/POST/FILE/SIGN/SPEND fixed
- [x] Voice input confirms changes to money, deadlines and contacts
- [x] Verification gates: official source, freshness, cross-check, label; grounding gate with one repair pass
- [x] Injection defence: fetched pages quoted as data, instructions flagged
- [x] Case-file brief per request; cited tags ([T#], [POS#], [S#])
- [x] Monthly spend cap with meter, retry, fallback model, offline queue
- [x] Audit log of every tool call and approval (insert-only)

## Free AI
- [x] Seven services: Gemini, Groq, OpenRouter free models, Cerebras, Mistral free (no card); Claude, OpenAI (paid)
- [x] Guided key flow: pick, numbered steps, Open the key page, Paste key from clipboard, Connect (lists models, picks one, tests)
- [x] Key detection from prefix and cleaning of pasted text; wrong-service paste switches selection
- [x] One encrypted key per service; connected services list with today's use, Make main, Re-test, Remove
- [x] Failover across services; short wait on free-tier "slow down"; daily-cap resting until tomorrow
- [x] Free only mode; free calls never count against the spend cap
- [x] Keyless search_web tool for services without built-in search
- [x] Lighter requests for small-context free models
- [x] Home prompt to connect a free AI when none is set

## Modules
- [x] Compass: values interview, three issues, story bank (60s/3m/10m) with 3-draft cap and lock, voice samples, positions with counter-argument and sources, one-page platform, consistency check
- [x] Ground: offices with sources and labels, body explainers, race cards, race scoring with working, target/stretch/fallback, win number calculator
- [x] Briefing: daily background digest with one optional action per item, weekly long read
- [x] Field: event calendar outside work hours, pre-event card, voice debrief that files contacts and follow-ups, board and commission finder
- [x] People: contact book, two-way contact count, follow-up queue, kitchen cabinet seats, relationship map, validators pipeline, press list, contact page with history and promises
- [x] Gym: daily scored speaking reps (clarity, pace, fillers, length), record-to-listen, mock rooms (door, reporter, forum, donor, interview, debate) with coach score, flashcards, skill ladder
- [x] Studio: drafts in your voice, fact-check pass, unverified claims block approval, crisis cooling period, versions, share after approval, AI disclosure reminder
- [x] Vault: deadline wall with official links, rules with limits and phone, money ledger with limit warnings and CSV export, encrypted file store, notes, disclaimer helper, self-vetting
- [x] Campaign HQ (stage 7): election countdown, launch checklist, call-time mode, door mode with tallies and support rate, volunteers, turnout plan
- [x] Approvals screen
- [x] Case file browser with delete and audit log

## Background and safety
- [x] Hourly worker: snapshot, playbook seeding, stage advance, rescoring, deadline alerts (14/7/2), follow-up and promise reminders, mentor requirement
- [x] Daily brief worker, rule re-check worker (flags changed pages), offline queue worker
- [x] Notifications: separate channels for deadlines, brief and follow-ups; quiet hours for work and sleep
- [x] Encrypted DB (SQLCipher), keys in Keystore-backed storage, app lock with PIN or biometric and auto-lock; masked secret fields, blank recent-apps preview when locked, lock-screen notifications show headings only
- [x] FLAG_SECURE on Vault and vault documents; panic wipe with double confirm
- [x] Encrypted export/import (also from setup's first screen); a snapshot before every import; daily snapshots, seven kept, restore screen
- [x] Releases signed with one fixed key, checked by fingerprint, so updates install over the last APK
- [x] Crash reports on device with copy; visible network log
- [x] Combined pages with tabs: What's on locally (meetings, briefing), Your voice (who you are, drafts), Your town (offices, town quiz), Records (vault, case file), Your plan (plan, weekly review, run or wait)
- [x] Calm theme (light default, dark for night), text scaling, 48dp tap targets, screen-reader labels, collapsible Settings sections, grouped More menu

## Added in cycle 1, phase 6 (50 rounds)
- [x] Works fully without AI: the Chief answers by finding the right page and card in plain code; town quiz, values interview, templates, prep cards, claim finder and gap list are built from the case file
- [x] Case-file search (More), week-ahead panel and Sunday notification, check-ins panel, duplicate-contact merge, delete contact or office
- [x] Event reminders outside quiet hours; notifications open the right screen; notifications-off and crash notices on Today
- [x] Money: raised, spent and cash on hand (in-kind counted as raised, not cash), limit checks, CSV
- [x] Deadlines added by hand, marked done, shown as passed; stale approvals expire; old chat, usage and brief items pruned on a schedule; backup reminder
- [x] Backups include settings (never keys or PIN), are verified after export, imports are all-or-nothing; restores keep an undo copy
- [x] Rule re-check ignores clock and copyright noise, so only real page changes are flagged
- [x] Locale-aware dates and times; phone-formatted times parse
- [x] Calmer screens: smaller deadline words, three-column money totals, one highlighted empty cabinet seat
