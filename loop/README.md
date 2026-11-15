# Running the improvement loop

Each session continues from `LOOP_STATE.json` (see CLAUDE.md, "Infinite improvement routine").

1. `git fetch origin && git checkout -B loop origin/main` (start from what's on main).
2. Stop at once if a file named `STOP` exists in the repo root.
3. Per iteration: make one real change (or record "no change"), then
   `loop/iter.sh <action> "<one-line change>"`. It checks the action matches the state, advances the counters,
   adds the CHANGELOG line and commits locally.
4. Every few iterations, and always at a phase end: `git push -f origin HEAD:loop`, then `loop/ci.sh`
   (waits for the CI run on that commit; prints errors if red). Fix forward up to 10 iterations, else revert
   to `last_green_commit`.
5. Green at a phase end (or when the session is about to end): `git push origin HEAD:main`, and record the
   commit as `last_green_commit` in LOOP_STATE.json.
6. End of a cycle (phase 6 done): bump `VERSION`, write `RELEASE_NOTES.md`, push to main. The Release workflow
   tags, signs with the fixed key, smoke-tests and publishes. `WF=Release loop/ci.sh` watches it.
7. Patch releases between cycles only for a defect that breaks a main path for the owner.

Never: weaken section 8, approval levels or verification gates; change `signing/`; push red; use any API key
found in chat history or files.
