# Ballot 0.2.2

**New Google keys now work.** Google AI Studio has switched to a new key format that starts with `AQ.` instead of `AIza`. Those keys are refused by the compatibility route Ballot used before, so Ballot now talks to Gemini through Google's own API. Both old and new keys work.

- Ballot recognises `AQ.` keys as Gemini keys when you paste them.
- When Google rejects a key, Ballot says why in plain words and what to do (for example: delete the key in AI Studio and create a new one).
- Gemini's daily limit is detected, so Ballot switches to your other free service until tomorrow.

To connect: More → Settings → AI services → Add a service → Google Gemini → Paste key → Connect.
