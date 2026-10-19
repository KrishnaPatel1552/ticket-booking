# Mission: Defend the booking system in Sachin's interview

## Why
Sachin Fegade is interviewing Krishna for the Copart SDET internship. The ~45 minute conversation is an explanation of this ticket-booking take-home, the trade-offs, and a live change he chooses. The goal is to answer from the locked rules and the code, out loud, from a basic walkthrough through the failure cases.

## Success looks like
- Answer a basic "what happens to this seat?" question without looking, using free, held, blocked, and ticket.
- Explain the hard cases he locked: caps, the hold clock, an unknown payment, refunds, and a single active seat.
- Say what was left out, and why, without apologizing for a cut that was deliberate.
- Make a small live change in the existing code and say which rule it must still satisfy.

## Constraints
- One short lesson at a time. Storage comes from recalling an answer, not from reading a list of every question.
- Use the words already in `CONTEXT.md` and `NOTES.md`.
- `NOTES.md` is the document Sachin grades. Never append teaching notes to it. Preferences for this course go in `TEACHING.md`.
- The code and the closed spec tickets are the authority when they disagree with memory.

## Out of scope
- New product behavior. Interview practice does not change the booking rules.
- Generic algorithm puzzles that are not how this interview was described.
