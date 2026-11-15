# Ballot

A personal chief of staff for one first-time candidate. The robot inside is **the Chief**: it researches, drafts, schedules, tracks and reminds, and you do the human part: show up, speak, decide, shake hands.

Native Android (Kotlin, Jetpack Compose). Built entirely by GitHub Actions, so it can be made, updated and installed from a phone.

## Install

1. Open the latest release on the repo's **Releases** page.
2. Download `Ballot-<version>.apk` and open it. Allow installs from your browser if Android asks.
3. Later releases install over the top and keep your data.

## What's inside

- **Home**: the single most useful thing to do now, with a three-line brief and a "too much" button.
- **The Chief**: chat with voice input. Five modes (Strategist, Researcher, Writer, Coach, Clerk). Every fact cites a source; anything sent, posted, filed, signed or spent waits for your tap.
- **Path**: eight stages from "find your reason" to "serve or regroup", each with an exit test.
- **Compass, Ground, Briefing, Field, People, Gym, Studio, Vault, Campaign HQ**: the nine modules from the outline.

## Privacy

Everything stays on the phone in an encrypted database (SQLCipher, key in the Android Keystore). Network calls go only to your AI provider and to pages being fetched, and are listed in Settings. No analytics, ads or trackers. Encrypted backups are started by you.

## Build

- Every push runs the quality gates (lint, unit tests, agent evaluation set, Robolectric UI tests, compile).
- Changing `VERSION` on main (or pushing a `v*` tag) runs the gates, builds a signed release APK, installs it on an emulator for a smoke test, then publishes a GitHub Release.

### Signing

Releases are signed with the key in these repository secrets, when present: `BALLOT_KEYSTORE_B64`, `BALLOT_KEYSTORE_PASSWORD`, `BALLOT_KEY_ALIAS`, `BALLOT_KEY_PASSWORD`. Until they are set, releases are signed with a fallback key kept in the Actions cache so updates still install in place (the cache is dropped after 7 days without a release). Switching keys later means uninstalling once, so export a backup first.

## The improvement loop

See `CLAUDE.md`, `LOOP_STATE.json`, `CHANGELOG.md`, `DECISIONS.md` and `FEATURES.md`. Add a file named `STOP` to the repo root to stop the loop.
