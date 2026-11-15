# Ballot: instructions for the builder

Ballot is a native Android app (Kotlin, Compose, Room + SQLCipher, WorkManager) for one user: a first-time candidate. The robot inside is "the Chief". The product spec is the outline the owner wrote; this file holds what a new session needs to keep building.

## Ground rules

- Builds happen only in GitHub Actions. `ci.yml` runs on every push (lint, unit + Robolectric tests, compile). `release.yml` runs when `VERSION` changes on main (it creates the `v<VERSION>` tag itself) (gates, signed APK, emulator smoke test, GitHub Release).
- Single source of truth is the database. Screens observe DAO flows; actions write through `CaseFile` or the tools.
- Deterministic core (`core/`): scoring, campaign math, race model, stages, deadlines, verification and approval levels run in plain code with JVM tests. The model explains them and never computes them.
- Every schema change bumps `BallotDatabase.VERSION`, adds a `Migration` to `BallotDatabase.MIGRATIONS` and a test. A change that cannot migrate is dropped.
- Section 8 guardrails (law, honesty, privacy), the approval levels and the verification gates are never weakened.

## Infinite improvement routine

Runs on a loop with no end condition and no questions asked. One cycle is 312 iterations across six phases, then it starts again at phase 1.

```json
{
  "execution_mode": "autonomous",
  "human_interaction": "disabled",
  "termination_condition": "none",
  "routine": [
    {"phase": 1, "name": "overall_checks", "tasks": [
      {"action": "system_check_and_update", "iterations": 50},
      {"action": "system_check_and_update", "iterations": 10},
      {"action": "system_check_and_update", "iterations": 1}]},
    {"phase": 2, "name": "interface_simplification", "tasks": [
      {"action": "reduce_menus_and_buttons", "iterations": 5},
      {"action": "establish_single_access_point", "iterations": 5}]},
    {"phase": 3, "name": "professional_polish", "tasks": [
      {"action": "reduce_menus_and_buttons", "iterations": 1},
      {"action": "apply_professional_formatting", "iterations": 20}]},
    {"phase": 4, "name": "practical_validation", "tasks": [
      {"action": "test_core_functionality", "iterations": 20},
      {"action": "verify_goal_completion", "iterations": 20}]},
    {"phase": 5, "name": "intuitive_redesign", "tasks": [
      {"action": "combine_screens", "iterations": 10},
      {"action": "remove_excess_navigation_elements", "iterations": 10},
      {"action": "apply_intuitive_layout", "iterations": 10}]},
    {"phase": 6, "name": "bold_overhaul", "tasks": [
      {"action": "overhaul_system_architecture", "iterations": 50},
      {"action": "apply_bold_aesthetics", "iterations": 50},
      {"action": "force_professional_standards", "iterations": 50}]}
  ],
  "loop_behavior": "infinite_repeat"
}
```

### What each action means here

| Action | Each iteration does |
| --- | --- |
| `system_check_and_update` | Finds and fixes one defect: a crash, a failing test, a broken flow, an outdated dependency, a stale rule source |
| `reduce_menus_and_buttons` | Removes or merges one control that the Chief or the Home card can replace |
| `establish_single_access_point` | Moves one more function behind the Chief, so everything is reachable by asking |
| `apply_professional_formatting` | Fixes one screen's spacing, type, alignment, copy or empty state against the design language |
| `test_core_functionality` | Runs one end-to-end flow as a new user and repairs whatever fails |
| `verify_goal_completion` | Checks one feature against "does this move a real person closer to office" and strengthens or removes it |
| `combine_screens` | Merges two screens that serve one job |
| `remove_excess_navigation_elements` | Deletes one tab, drawer, back-stack hop or duplicate route |
| `apply_intuitive_layout` | Reorders one screen so the next step is obvious without reading |
| `overhaul_system_architecture` | Rebuilds one weak subsystem properly, with migration and tests |
| `apply_bold_aesthetics` | Pushes one screen further toward the calm, clear look (see DECISIONS.md, 2026-11-15) without adding clutter |
| `force_professional_standards` | Brings one area up to release quality: accessibility, performance, error handling, test coverage |

### Push policy

| Event | What happens |
| --- | --- |
| Every iteration | Local commit only, one-line message. No push, no build |
| End of each phase | Local checks run (see below). If green, one push of the whole phase |
| End of each cycle | Version bump, release notes, one tagged release APK |
| Red at a phase end | No push. Fix forward up to 10 iterations, then revert to the last green commit and move on |
| Session about to end | Push whatever is green, so no work is stranded |

### Rules that keep an endless loop safe

- Never lose data. Every schema change ships with a tested migration.
- Never push red. Only commits that pass the quality gates leave the machine.
- Never weaken the guardrails (approval levels, verification gates, section 8).
- Never remove a capability by accident. `FEATURES.md` is checked each phase. Controls can go; functions cannot unless phase 4 judged them useless and logged why.
- Simplify toward the Chief, with a floor of Home, Chief and one overflow.
- No churn: an iteration that finds nothing worth changing records "no change".
- No oscillation: `DECISIONS.md` records each design choice; reversing one needs a stronger logged reason.
- Human interaction is disabled for the build loop only. Inside the app, the Chief still asks before it sends, posts, files, signs or spends.

### Resuming

- `LOOP_STATE.json` holds cycle, phase, task and iteration counters, updated with every commit.
- `CHANGELOG.md` gets one line per iteration. `DECISIONS.md` holds the design log. `FEATURES.md` holds the inventory.
- A new session reads `LOOP_STATE.json` and continues from the exact iteration.
- A file named `STOP` in the repo root stops the loop. Nothing else does.
- `loop/README.md` has the step-by-step for a session; `loop/iter.sh` records an iteration, `loop/ci.sh` waits for CI.
- Releases are signed with the fixed key in `signing/`. Never change it (see `signing/README.md`).

## Local checks without Android tooling

A sandbox with no Android SDK can still compile and test the pure-Kotlin core with the Kotlin compiler that ships inside Gradle (`/opt/gradle-*/lib/kotlin-compiler-embeddable-*.jar`): compile `app/src/main/java/**/core/*.kt` plus `app/src/test/**/CoreTest.kt` and run them with JUnit. Everything else is verified by CI on push; read the run's logs through the GitHub API.
