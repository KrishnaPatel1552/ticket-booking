# Handoff: Booking spec for Monday

Date: Friday 16 October 2026, late afternoon ET.
Repo: `/Users/krishna/copart-prep` on `main`.
No application code has been written.

## Where to start

Read the map first. It is the route. Do not restate its decisions in a new doc.

- Map: `.scratch/booking-spec/map.md`
- Glossary: `CONTEXT.md`
- Sachin's reply: `Gmail- Manager's reply.pdf`
- Earlier route, already closed: `.scratch/take-home-route/map.md`

The next ticket is the first open, unblocked, unclaimed child. That is [Clock and whole cents](.scratch/booking-spec/issues/04-clock-and-cents.md). Claim it before any work. Resolve one ticket per session.

## What the next session is for

Finish the open tickets on [Booking spec for Monday](.scratch/booking-spec/map.md). When no ticket remains, write `.scratch/booking-spec/prd.md` and `.scratch/booking-spec/spec.md` from the ticket answers. Those two files are the destination. Do not start the build.

## Tickets

Resolved. The answer is on the ticket. The map has the one-line gist.

- [What Monday's build includes](.scratch/booking-spec/issues/01-monday-build.md)
- [Payment recorded before the attempt](.scratch/booking-spec/issues/02-payment-before-attempt.md)
- [Caps when one customer acts twice](.scratch/booking-spec/issues/03-caps-under-concurrency.md)

Open now. Nothing is claimed.

- [Clock and whole cents](.scratch/booking-spec/issues/04-clock-and-cents.md) — take this first
- [Checkout lifetime and an unknown payment](.scratch/booking-spec/issues/05-checkout-lifetime.md) — its blocker is resolved. The unknown-payment rule is already closed on the payment ticket. This ticket decides what starts checkout, how long checkout can last before a payment row exists, and a checkout that never finishes.

Waiting.

- [What a reschedule does](.scratch/booking-spec/issues/08-reschedule.md) — waits on Clock and whole cents
- [Booking state machine](.scratch/booking-spec/issues/06-booking-state-machine.md) — waits on checkout and reschedule
- [Refund state machine](.scratch/booking-spec/issues/07-refund-state-machine.md) — waits on Clock and whole cents and reschedule

## Still fog

Screen flows, and whether the seat map updates by itself or shows the current seats when the customer opens the event and after each action. Leave this until a resolved ticket makes the question sharp.

## Out of scope

The build, tests, README, `NOTES.md`, and the GitHub push. Krishna writes the documents Sachin asked for, from the spec. Interview practice. The Friday 16 October 2:00 PM ET call. Sachin schedules the interview after the code is submitted.

## Glossary touched this session

`CONTEXT.md` now has **payment**, and **hold** stays past 10 minutes while a payment for that order is unresolved. Keep one ubiquitous language. Do not swap synonyms.

## Suggested skills

- `wayfinder` — claim a ticket, grill it, record the answer on the ticket, add one line to the map. One ticket per session.
- `grilling` — decisions are made with Krishna. Recommend an answer, then wait.
- `domain-modeling` — write a resolved term into `CONTEXT.md` when the ticket resolves it. Keep the file a glossary.
- `wait-what` — Krishna asked for a simpler explanation when a pitch was too dense.

Tracker rules: `.agents/skills/setup-matt-pocock-skills/issue-tracker-local.md` is not in this repo. The local rules used here are: map at `.scratch/booking-spec/map.md`, tickets at `.scratch/booking-spec/issues/NN-slug.md`, `Status: claimed` before work, `Status: resolved` plus an `## Answer` when done, `Blocked by:` numbers. A ticket is unblocked when every ticket it lists is resolved.
