# Ballot 0.2.3

**Fix: "not a supported model".** Google retires and renames Gemini models often, and not every model is on the free tier. Ballot used to try one or two names and give up.

- **Connect** now tries up to eight models, best first (the newest Flash models, then Google's "latest" names), and keeps the first one that answers.
- If a model you were using is retired later, the Chief switches to a working one by itself and remembers it.
- If nothing answers, Ballot shows Google's own message so you can see why.

Already connected? Open More → Settings → AI services and tap **Re-test** on Gemini.
