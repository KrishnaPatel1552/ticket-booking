# Teaching notes

Scratchpad for the interview course. This is not the graded `NOTES.md`.

## Preferences
- Krishna asked to prepare for every question Sachin can ask, from basic to advanced.
- Before answer practice, explain what the code already does, with a diagram and a worked example.
- Teach in that order, one lesson at a time. Do not dump the whole bank into one file.
- On 18 Oct 2026 Krishna asked for the Copart PDF in easy language, the way a chat explanation sounds, split into parts if needed. Spoken answers should be short enough to say out loud.

## Sequence
0. What is already built, walked through as Ada at Harbor Lights. Lesson `lessons/0002-what-we-built.html`. Interview answers wait until this can be retold.
1. Four seat states, and the moves among them. Lesson `lessons/0001-seat-states.html`.
2. When a hold ends: 10 minutes, 15 for a waitlist hold, and checkout as the submit.
3. Caps of 6 and 12 under two requests at once.
4. One active hold or ticket: the partial unique index.
5. Payment row before the mock; unknown finishes only by lookup.
6. Price order and splitting leftover cents.
7. Refund bands, the fee, and the reschedule window.
8. Promo uses, and what an event cancellation restores.
9. Waitlist offer.
10. Cuts and trade-offs, in the words of `NOTES.md`.
11. What the PostgreSQL tests actually prove.
12. Rehearse a live change.
13. Java, Spring, and SQL questions if he leaves the domain and asks the intern to code.

Lessons 1–3 are the basic walkthrough. 4–8 are the failure cases. 9–13 are the advanced defense and the live change.

## Not yet shown
No answer has been recalled from memory yet. The PDF is now split into parts 3–8. Start with lesson 3, the five questions he named. Do the quiz out loud before opening the next part.

## PDF parts
3. `lessons/0003-five-defenses.html` — questions 1–5, the ones he asked to defend.
4. `lessons/0004-system-and-database.html` — questions 6–15.
5. `lessons/0005-edge-cases.html` — questions 16–25.
6. `lessons/0006-how-we-tested.html` — questions 26–35.
7. `lessons/0007-what-you-did.html` — questions 36–45.
8. `lessons/0008-live-coding.html` — the six practice changes. Explain them. Do not change product rules unless he asks live.
