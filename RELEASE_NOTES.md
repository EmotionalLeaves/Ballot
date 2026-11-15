# Ballot 0.3.3

**Fix: Gemini error 400 "Invalid value at tools[0].function_declarations…".** Some of the Chief's tools have a field called "type" (the type of draft, event or file). When Ballot translated the tools into Gemini's format, it mistook those fields for the type label itself and sent "STRING" where Gemini expected a description of the field. Gemini rejected the whole request. Fixed, with a test that checks every tool converts cleanly.


**Fix: updates now install over the last version.** Until now every release was signed with a different key, so Android refused to install one over another. From 0.3.3 on, every release uses one fixed key and installs over the last one, with your data kept.

**One-time move from 0.3.2 or earlier (this release only):**
1. In your current Ballot: More → Settings → Backup. Type a passphrase (8+ characters) and tap Export. Save the file somewhere you can find it, such as Downloads.
2. Uninstall the old Ballot.
3. Install this APK and open it.
4. More → Settings → Backup. Type the same passphrase, tap Import and pick the file.
5. Add your AI key again. Keys are not put in backups.

After this, future updates install straight over the top.
