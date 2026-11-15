# Feature inventory

Checked each phase. Controls may move or merge; a function is only removed when phase 4 judged it useless, with the reason in DECISIONS.md.

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
- [x] Tool loop with 26 typed tools (search_web (free search), find_contacts, list_plan, complete_task, update_event, search_web via provider, fetch_official_page, verify_claim, find_offices, get_race_card, add_contact, log_interaction, queue_follow_up, create_task, reschedule_plan, add_deadline, log_money, draft_document, score_speech, start_mock_room, update_platform, save_to_vault, add_event, record_lesson, record_decision, update_profile, save_brief_item)
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
- [x] Encrypted DB (SQLCipher), keys in Keystore-backed storage, app lock with PIN or biometric and auto-lock
- [x] FLAG_SECURE on Vault and vault documents; panic wipe with double confirm
- [x] Encrypted export/import; daily snapshots, seven kept, restore screen
- [x] Crash reports on device with copy; visible network log
- [x] Dark and light themes, text scaling, large tap targets, screen-reader labels on key controls
