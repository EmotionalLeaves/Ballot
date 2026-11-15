# Decisions

Each design choice and its reason. Reversing one needs a stronger, logged reason.

- **2026-11-14 · App name.** The outline's working title was MANDATE; the repo is named Ballot, so the app is Ballot and the robot stays the Chief.
- **2026-11-14 · One Gradle module.** The outline suggests a module per feature. One `app` module with feature packages builds faster in CI and has fewer moving parts while the app is young. Splitting is a candidate for `overhaul_system_architecture`.
- **2026-11-14 · Screens observe DAO flows directly.** The database is the single source of truth; a ViewModel per screen would only copy it. Chief requests run in the app-wide scope (`askChief`) so they survive leaving a screen.
- **2026-11-14 · Non-streaming replies.** Tool loops with server-side web search are simpler and more reliable without streaming. A busy bar shows progress. Streaming is a later improvement.
- **2026-11-14 · Default provider Claude.** It has a built-in web search tool, which the Researcher mode depends on. OpenAI-compatible endpoints work for everything except web search.
- **2026-11-14 · The app never sends.** "Asks first" items open your own messaging or share sheet after approval; you press send. Matches "no bulk sending from the app".
- **2026-11-14 · Grounding gate with one repair pass.** An answer that states an election rule without a stored source gets one rewrite attempt; if it still fails it is replaced with a plain "confirm with the election office".
- **2026-11-14 · Official domains.** .gov, .mil and the locality form of .us count as official. City sites on other domains can be added in Settings.
- **2026-11-14 · Signing fallback.** Creating repository secrets was not possible from the build session, so releases sign with a key kept in the Actions cache until the four `BALLOT_*` secrets are set.
- **2026-11-14 · Stages 3 to 5 cannot be skipped.** The open-seat fast lane brings the race decision forward from stage 3 on, but never skips a stage's exit test.
- **2026-11-14 · Free AI first.** The owner asked for free AI and easy keys. Free services are listed first and Google Gemini is recommended: it needs only a Google account and no card. Claude stays available for the strongest research.
- **2026-11-14 · Models are discovered, not hard-coded.** Free catalogues change monthly, so Connect asks the service for its model list and picks by preference, falling back to a preset default.
- **2026-11-14 · Everything but Claude uses the OpenAI chat format.** Gemini, Groq, OpenRouter, Cerebras and Mistral all offer OpenAI-compatible endpoints, so one client covers them.
- **2026-11-14 · Keyless search via DuckDuckGo.** Free services have no built-in web search. Results are passed as untrusted leads and are not stored as sources, so the grounding gate still requires a fetched page.
- **2026-11-14 · No schema change for per-service usage.** Usage records store "service:model" in the existing model column, so no migration was needed.
- **2026-11-15 · Patch releases for broken free paths.** Releases normally wait for the end of a cycle, but a defect that breaks the recommended free service (Gemini multi-step tool calls) ships as a patch release as soon as CI is green.
- **2026-11-15 · Gemini uses its native API.** Google AI Studio now issues AQ. auth keys, which the OpenAI-compatible Gemini endpoint rejects. Ballot calls generateContent directly with the x-goog-api-key header, which accepts both AQ. and AIza keys. This reverses the earlier 'everything but Claude uses the OpenAI format' decision for Gemini, for a stronger reason: the new keys do not work there.
