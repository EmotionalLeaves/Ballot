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
