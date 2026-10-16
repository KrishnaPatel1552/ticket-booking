# Ticket booking take-home route

## Destination

A Step 1 reply Krishna can send today (ranked questions, assumptions, one-page design note, cuts), written in one ubiquitous language, followed by a locked set of design decisions once Copart replies on Thursday. The map ends when those decisions are recorded and implementation is allowed to start. It does not include building the system.

## Notes

- Standing rule: one ubiquitous language for this project. The same terms appear in the email, the design note, `CONTEXT.md`, and later code. Do not swap in synonyms. Sessions that touch language use the domain-modeling skill and write resolved terms into `CONTEXT.md` immediately. A term whose boundary is still a question to Copart stays provisional until that answer lands.
- Product rules Copart has not answered stay assumptions in the reply. Do not treat them as settled, and do not start implementation until the post-reply decisions are closed.
- Every session consults grilling and domain-modeling.
- Stack they asked for is Java, Spring Boot, React, and PostgreSQL. Flag it in the reply only if we decide it is a problem.
- Monday's build is scoped to roughly 10–12 hours. Correct and small beats wide and shallow.
- AI use is allowed only if it is disclosed and the work can be defended.

## Decisions so far

- [Double-sell prevention for the design note](.scratch/take-home-route/issues/04-double-sell-prevention.md) — one active hold or sale per seat, enforced by a unique partial index and a row lock
- [Canonical language for the booking domain](.scratch/take-home-route/issues/01-canonical-language.md) — order, ticket, seat, hold, and block are the sale words; who wins when a hold and a block want the same seat stays a question for Copart
- [Ranked questions for today's reply](.scratch/take-home-route/issues/02-ranked-questions.md) — ten questions; hold-versus-block is first, and admin setup was added from the cut list
- [Assumptions we will design against](.scratch/take-home-route/issues/03-stated-assumptions.md) — second action fails on a taken seat; holds count toward the caps; one promo code after a single starting price; no assumption for Friday
- [What today's reply cuts or defers](.scratch/take-home-route/issues/05-cuts.md) — search and waitlist stay; reschedule, reports, and sign-in are out; admin setup is asked, with starter data if unanswered
- [One-page design note](.scratch/take-home-route/issues/06-design-note.md) — seat is free, held, blocked, or a ticket; one active hold or ticket per seat per event

## Not yet specified

- After Thursday's reply arrives: which assumptions survive, which terms firm up, and which booking, refund, pricing, and scope decisions that reply unlocks. This stays fog until the reply exists; it is the gate in front of implementation.
- How the Friday 16 October 2:00 PM ET Teams interview sits next to this take-home. The question belongs in today's email; what we do about the answer depends on what they say.

## Out of scope

- Implementing the booking system: Spring Boot services, React UI, PostgreSQL schema and migrations, concurrency tests, README, `NOTES.md`, and the GitHub push. The map ends when the decisions those artifacts must follow are locked.
- Practicing the live interview change. That happens after the code exists.
