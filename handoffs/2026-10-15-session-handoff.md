# Handoff: Copart ticket-booking take-home

Date: Thursday 15 October 2026, afternoon ET.
Repo: `/Users/krishna/copart-prep` on `main`.
No application code has been written.

## Where to start

Read the map first. It is the route. Do not restate its decisions in a new doc.

- Map: `.scratch/take-home-route/map.md`
- Glossary: `CONTEXT.md`
- Brief (not the decisions): `CURRENT_STATE.md`
- Email draft, not confirmed sent: `.scratch/take-home-route/step-1-email.md`

Every child ticket under `.scratch/take-home-route/issues/` is resolved. There is no frontier ticket.

## What this session decided

The destination is a Step 1 reply, then a wait for Copart's Thursday reply, then locked design decisions, and only then implementation. The map ends when those decisions are recorded. Building the system is out of scope on the map.

Closed tickets, in the order a new session should trust them:

- Canonical language for the booking domain
- Ranked questions for today's reply (ten questions; admin setup was added later)
- Assumptions we will design against (no assumption for the Friday interview)
- Double-sell prevention for the design note (findings in `.scratch/take-home-route/research/double-sell-prevention.md`)
- What today's reply cuts or defers
- One-page design note

Standing rule: one ubiquitous language. Use `CONTEXT.md`. Do not swap synonyms.

## Unfinished at handoff

The user has not said the email was sent. Step 1 was due end of day Wednesday 14 October 2026. Copart said they would answer in one reply on Thursday 15 October 2026.

One proposed email edit was explained and not accepted. Do not add it unless the user says yes. The sentence was: the seat map shows the current seats when the customer opens the event and after each action; a map that updates by itself is deferred. The user asked for a simpler explanation and then ended the session. The draft file does not contain that sentence.

Stack questions for Copart are not needed. They required Java, Spring Boot, React, and PostgreSQL. The email says that stack is fine. Two build constraints were stated for later, not as new tickets: the 10 minutes are decided in PostgreSQL, and the seat map loads current rows rather than updating by itself.

## When Copart's reply exists

That reply is the fog on the map ("Not yet specified"). Do not start Spring Boot, React, schema, or tests until a new session folds the reply into the language, the assumptions, and Monday's scope, and the user accepts those decisions.

The Friday 16 October 2026, 2:00 PM ET Teams interview is a question in the email. There is no assumption for it. What to do about Friday depends on their answer.

Monday 19 October 2026 is the code deadline. Effort target is 10–12 hours. Tests of the hard parts must use a real PostgreSQL database, including concurrent attempts on the same seat. `NOTES.md` must disclose AI tools. This session used Cursor.

## Suggested skills

Call these before continuing the route:

- `wayfinder` — the map is the tracker. Local markdown rules are in the wayfinder skill and in `setup-matt-pocock-skills` (`issue-tracker-local.md`). Claim a ticket before working it. Resolve one ticket per session.
- `grilling` — decisions are made with the user, with a recommended answer, then a wait.
- `domain-modeling` — write resolved terms into `CONTEXT.md` immediately. Keep it a glossary.
- `wait-what` — the user asked for simpler wording when a pitch was too dense. Use it before a long design explanation.

Do not call implementation skills until the Thursday reply has been folded in and the user says to build.
