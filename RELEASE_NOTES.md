# Ballot 0.3.4

Installs straight over 0.3.3 and keeps everything.

**Fixes**
- When Gemini garbles a step and sends back nothing, the Chief asks again instead of showing an empty answer.
- Free services that send tool calls in a loose format (empty ids, "stop" while calling tools) now work with the Chief.
- Tool ids written as S12, [T4] or #7 are read correctly.
- A slow website gives up after 30 seconds, so one page can't stall an answer.
- Rule re-checks ignore error pages, so an outage no longer raises a false "rule page changed" alert.
- A .gov link that redirects to another site is no longer marked official.
- A queued request that fails three times tells you in the chat instead of vanishing.

**Moving and restoring**
- Setup's first screen has "I have a Ballot backup file", so a reinstall or new phone skips the eight setup steps.
- Importing a backup snapshots what was there first, so an import can be undone.

**Privacy**
- PINs, the backup passphrase and typed AI keys are masked and kept out of the keyboard's learned words.
- With the app lock on, the recent-apps preview is blank (Android 13+), and lock-screen notifications show only their heading.
