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
