# Ballot 0.3.2

Follow-up to the Connect fix, so nothing waits forever:

- **Stop button** on the Chief's "working" bar cancels a long request.
- Each AI service gets **75 seconds** per answer before Ballot moves on to your next service.
- Gemini Connect skips models that can't chat (robotics, video, transcription, agents), so it reaches a working one sooner.
