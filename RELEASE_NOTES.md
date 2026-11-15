# Ballot 0.3.3

**Fix: Gemini error 400 "Invalid value at tools[0].function_declarations…".** Some of the Chief's tools have a field called "type" (the type of draft, event or file). When Ballot translated the tools into Gemini's format, it mistook those fields for the type label itself and sent "STRING" where Gemini expected a description of the field. Gemini rejected the whole request. Fixed, with a test that checks every tool converts cleanly.

Installs over your current version; your case file, contacts, tasks and keys are kept.
