# Ballot 0.3.1

**Fix: "Connecting…" that never finished.**

- Connect first checks your key, then shows which model it's trying ("Trying gemini-… 2 of 8").
- Each try gets 25 seconds and the whole connect gives up after 90, with a clear message.
- A **Cancel** button stops it at any time.
- If your key is fine but Google is just slow or busy, Ballot saves the key anyway and the Chief retries when you ask something.
- Under the hood, every network request can now be cancelled, so a slow service can't freeze any screen.

Also in this release: status bar icons stay visible on the light theme, no black flash on start, a new teal app icon, no double gap under the bottom bar, more room for Gemini and free reasoning models so answers don't come back empty, and the Chief knows what is still waiting for your approval.
