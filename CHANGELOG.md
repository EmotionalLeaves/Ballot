# Changelog

One line per loop iteration, newest last.

## 0.1.0

- Build: first full build of all six rounds (spine, ground, presence, voice, campaign, hardening).

## Loop

- c1 p1 t1 i1 `system_check_and_update`: Weekly counts now stop at the week's end, so review history shows real per-week scores; stage exits passed now count
- c1 p1 t1 i2 `system_check_and_update`: Daily brief builds after the chosen time instead of five hours early, and never during work or sleep
- c1 p1 t1 i3 `system_check_and_update`: Add the missing proactive trigger: a weekly score that drops two weeks running creates a shrink-the-plan task and one notification
- c1 p1 t1 i4 `system_check_and_update`: Fact check uses the freshness window from Settings instead of a fixed 30 days
- c1 p1 t1 i5 `system_check_and_update`: Busy indicator counts requests in flight, so a second Chief request no longer clears it early
- c1 p1 t1 i6 `system_check_and_update`: Screen readers now hear tappable panels as buttons and chips as selected or not; chips grow to 48dp tap targets
- c1 p1 t1 i7 `system_check_and_update`: Chief replies that span several tool steps are joined with a paragraph break instead of running words together
- c1 p1 t1 i8 `system_check_and_update`: Requests queued offline in an earlier session now run when the app next starts with a connection
- c1 p1 t1 i9 `system_check_and_update`: Opening a vault file decrypts off the main thread and can no longer crash the app; a bad recording no longer crashes playback
- c1 p1 t1 i10 `system_check_and_update`: Win numbers on race cards and in HQ use your safety margin from Settings instead of a fixed 10%

## Free AI rounds (requested 2026-11-14)

- Round 1: seven AI services built in (Gemini, Groq, OpenRouter free, Cerebras, Mistral free; Claude, OpenAI paid), one key per service, key detection from the pasted text, live model lists, free calls cost nothing, failover across your services with a short wait on free-tier rate limits, "free only" mode.
- Round 2: guided free-key flow in setup and Settings: pick a service, numbered steps, Open the key page, Paste key from the clipboard (Ballot spots which service a key belongs to), Connect picks a working model and tests it. Settings lists connected services with today's use, Make main, Re-test, Remove. Home shows "Connect a free AI" until one is set up.
- Round 3: free web search for every service without a built-in one (all the free ones): a keyless search_web tool reads DuckDuckGo results as leads, and the Chief must fetch the official page before citing anything.
- Round 4: free-tier resilience: a service that says "no more today" rests until tomorrow and requests go straight to your next service; Settings shows it. Small-context free models get a lighter request (core tools, shorter brief, clipped results).
- Round 5: tests for key detection, model picking, search parsing, Retry-After and daily caps; README, feature list and decision log updated; version 0.2.0.
- c1 p1 t1 i11 `system_check_and_update`: Approved items now report their result in the Chief's thread, so research or tracking you approve later is not lost to the conversation
- c1 p1 t1 i12 `system_check_and_update`: Two-way contact count on People refreshes after every logged conversation, not only when someone is added
- c1 p1 t1 i13 `system_check_and_update`: Rewriting the go or no-go memo no longer piles up duplicate get-to-yes tasks
- c1 p1 t1 i14 `system_check_and_update`: Weekly review saves once, so a double tap no longer writes duplicate lessons
- c1 p1 t1 i15 `system_check_and_update`: App lock switch explains that a PIN is needed instead of silently doing nothing; short PINs say why they were refused
- c1 p1 t1 i16 `system_check_and_update`: Door math no longer double-counts the safety margin and uses your real weeks to election day and doors per hour
- c1 p1 t1 i17 `system_check_and_update`: Call-time script fills in your own reason from your story and the suggested ask instead of placeholders
- c1 p1 t1 i18 `system_check_and_update`: Spend panel says plainly that free services never count and when Free only guarantees nothing is spent
- c1 p1 t1 i19 `system_check_and_update`: A re-fetched page only counts as cross-checked when its content matches the earlier fetch; a changed page no longer earns Verified
- c1 p1 t1 i20 `system_check_and_update`: The Chief can now look people up in your contact book (find_contacts), so suggestions about who to ask come from your real network
- c1 p1 t1 i21 `system_check_and_update`: The Chief can read the whole plan and mark tasks done or events attended when you tell it, so saying 'I went to the meeting' updates the record and the exit test
- c1 p1 t1 i22 `system_check_and_update`: Gemini tool calls keep working across steps: Ballot echoes back Gemini's thought signature and names tool results, which newer Gemini models require
- c1 p1 t1 i23 `system_check_and_update`: Clearing the Chief conversation asks first and says that everything filed stays in the case file
- Fix (owner report): new AQ. Gemini keys did not work; Gemini now uses its native API. Release 0.2.2.
- c1 p1 t1 i24 `system_check_and_update`: When the Chief has no AI service it now offers a one-tap card straight to AI services in Settings
- c1 p1 t1 i25 `system_check_and_update`: Get-a-key flow shows the chosen service and its steps on one screen; the full list of services opens only when you ask
- c1 p1 t1 i26 `system_check_and_update`: Adding an event takes two taps: pick a day chip and a time chip, with typing kept as a fallback
- c1 p1 t1 i27 `system_check_and_update`: The weekly review now actually arrives: a task and one reminder on the day you choose in Settings (Sunday by default)
- c1 p1 t1 i28 `system_check_and_update`: Briefing shows whether today's brief is waiting, building or failed, instead of asking you to come back later
- c1 p1 t1 i29 `system_check_and_update`: The daily brief no longer sends a 'brief is ready' notification when the Chief actually hit an error or found nothing
- c1 p1 t1 i30 `system_check_and_update`: A day's brief made only of seat or decision items no longer gets rebuilt every six hours
- c1 p1 t1 i31 `system_check_and_update`: Re-test on a connected AI service now shows whether it worked and, if not, why
- c1 p1 t1 i32 `system_check_and_update`: A service that passes a re-test is taken off today's resting list straight away
- c1 p1 t1 i33 `system_check_and_update`: Text size slider saves once when you let go instead of on every pixel of the drag
- c1 p1 t1 i34 `system_check_and_update`: App lock slows guessing: after five wrong PINs each try waits 30 seconds
- Fix (owner report): "not a supported model" on Gemini; Connect tries up to eight models best-first and the Chief self-heals to a working model. Release 0.2.3.
- Redesign (owner request): calm theme, simpler Today, standard navigation bar, grouped More, collapsible Settings, compact Plan. Release 0.3.0.
- c1 p1 t1 i35 `system_check_and_update`: Status bar icons follow Ballot's theme so they stay visible on the light screen; app opens on paper instead of a black flash; icon recoloured to the calm teal
- c1 p1 t1 i36 `system_check_and_update`: Bottom bar no longer leaves a double gap above the phone's navigation area
- c1 p1 t1 i37 `system_check_and_update`: Gemini gets enough output room for its thinking plus the answer, so replies no longer come back empty; a cut-off reply is retried
- c1 p1 t1 i38 `system_check_and_update`: Free reasoning models (gpt-oss on Groq and Cerebras, OpenRouter free) get room to think and answer; an empty cut-off reply moves to the next service
- c1 p1 t1 i39 `system_check_and_update`: The Chief knows what is still waiting for your approval, so it never tells you something was sent or filed when it wasn't
- c1 p1 t1 i40 `system_check_and_update`: A service added in Settings appears in the connected list straight away instead of after leaving the screen
- Fix (owner report): Connect stuck on "Connecting…"; cancellable requests, 25 s per try, 90 s cap, progress and Cancel. Release 0.3.1.
- c1 p1 t1 i41 `system_check_and_update`: A long Chief request can be stopped: the working bar has a Stop button that cancels the request and its network calls
- c1 p1 t1 i42 `system_check_and_update`: A Chief request gives each AI service 75 seconds before moving to the next, so one slow free tier can't stall a reply for minutes
- c1 p1 t1 i43 `system_check_and_update`: Gemini Connect skips robotics, video, transcription and agent models that can't chat, so it reaches a working model sooner
- Fix (owner report): Gemini 400 on tool declarations; fields named "type" were converted as type names. Release 0.3.3.
- Fix (owner report): releases were signed with a different key each time, so updates could not install over the last APK. Fixed signing key in signing/, fingerprint gate in release.yml. One-time backup move from 0.3.2.
- c1 p1 t1 i44 `system_check_and_update`: Test that every tool's required fields are declared and its name suits Gemini, so no tool can trip a 400 again
- c1 p1 t1 i45 `system_check_and_update`: PINs, the backup passphrase and typed AI keys are masked and kept out of the keyboard's learned words
- c1 p1 t1 i46 `system_check_and_update`: Importing a backup snapshots the current case file first, so an import can be undone
- c1 p1 t1 i47 `system_check_and_update`: Setup's first screen can restore a backup file, so a reinstall or new phone skips the eight setup steps
- c1 p1 t1 i48 `system_check_and_update`: verify_claim checks for source_ids up front like its schema says, and a test keeps every tool's checks matched to its schema
- c1 p1 t1 i49 `system_check_and_update`: Tool ids written as S12, [T4] or #7 are read correctly, and a null required field counts as missing instead of crashing the tool
- c1 p1 t1 i50 `system_check_and_update`: Rule re-checks skip error pages, so a 404 or a server outage no longer raises a false 'rule page changed' alert or replaces the source
- c1 p1 t2 i1 `system_check_and_update`: Fetched pages record the address that answered after redirects, so a .gov link redirecting to another site is no longer marked official
- c1 p1 t2 i2 `system_check_and_update`: Notifications show only their heading on the lock screen; the brief text and contact names stay hidden until the phone is unlocked
- c1 p1 t2 i3 `system_check_and_update`: With the app lock on, the recent-apps preview is blank (Android 13+), so the case file can't be read from the app switcher
- c1 p1 t2 i4 `system_check_and_update`: When Gemini garbles a tool call and sends back nothing, the Chief asks again once instead of showing an empty answer
- c1 p1 t2 i5 `system_check_and_update`: OpenAI-style services that leave tool-call ids empty, say 'stop' while calling tools, or answer with a refusal field now work with the Chief
- c1 p1 t2 i6 `system_check_and_update`: Background briefs and queued retries no longer re-queue themselves, so a failed brief can't turn up later as a stray chat message or delete a real queued request
- c1 p1 t2 i7 `system_check_and_update`: A queued request that fails three times now tells you in the chat instead of vanishing
- c1 p1 t2 i8 `system_check_and_update`: Web page fetches and free search give up after 30 seconds instead of 150, so one slow site can't stall a Chief answer
- c1 p1 t2 i9 `system_check_and_update`: http-only town and county sites can be read when https fails, but a page that didn't arrive over https never counts as official
- c1 p1 t2 i10 `system_check_and_update`: An http-only site gets a plain 'no secure version, open it in a browser or call' message; the http fallback is dropped because cleartext stays off app-wide
