# Booking spec for Monday

Label: wayfinder:map

## Destination

A short PRD and a spec that a build can follow without a new product question. They record Sachin's locked rules and the answers to the five points he will ask Krishna to defend. The files are `.scratch/booking-spec/prd.md` and `.scratch/booking-spec/spec.md`. The map ends when those two files exist. It does not include the build. Both files are written.

## Notes

- Domain is the ticket-booking take-home after Sachin Fegade's reply of 15 October 2026. The reply is `Gmail- Manager's reply.pdf`. The earlier route is [Ticket booking take-home route](../take-home-route/map.md). Do not restate that map's decisions here.
- Standing rule: one ubiquitous language. Sessions that touch language use the domain-modeling skill and write resolved terms into `CONTEXT.md` immediately. A term stays provisional until the ticket that owns it is resolved.
- Every session consults grilling and domain-modeling. Use wait-what when a pitch is too dense.
- Stack is Java, Spring Boot, React, and PostgreSQL. Monday's build is about 10–12 hours. Correct and small beats wide and shallow.
- Krishna writes `NOTES.md`, the README, and the GitHub delivery. Those documents follow this spec. They are not tickets on this map.
- Claim a ticket before working it. Resolve one ticket per session.

## Decisions so far

- [What Monday's build includes](issues/01-monday-build.md) — reschedule is in; sales and occupancy is one admin read for one event; events, price tiers, and promo codes are created through authenticated admin API endpoints
- [Payment recorded before the attempt](issues/02-payment-before-attempt.md) — one payment row is committed before the mock call; unknown is reconciled by status lookup; a succeeded payment gets a ticket
- [Caps when one customer acts twice](issues/03-caps-under-concurrency.md) — a request that asks for more than the remaining 6 or 12 writes no holds; the second request waits on one customer-and-event row and is refused
- [Clock and whole cents](issues/04-clock-and-cents.md) — windows are 168 and 48 hours before the event instant; each ticket stores its discount and fee shares; a customer cancel never returns the fee
- [Checkout lifetime and an unknown payment](issues/05-checkout-lifetime.md) — checkout is the submit, and the payment row is written in that request; an abandoned page is only a hold
- [What a reschedule does](issues/08-reschedule.md) — 336 hours from the reschedule returns the fee share; a bare hold ends; the waitlist stays; a promo use stays consumed
- [Booking state machine](issues/06-booking-state-machine.md) — a seat is free, held, blocked, or a ticket; a simultaneous hold and block are settled by whichever request locks the seat first
- [Refund state machine](issues/07-refund-state-machine.md) — the seat stays a ticket until the refund succeeds; only a customer cancel or a reschedule refund then frees it
- Seat map — shows the current seats when the customer opens the event and after each of their actions; it does not update by itself

## Out of scope

- The build itself: Spring Boot services, React UI, PostgreSQL schema and migrations, concurrency tests, README, `NOTES.md`, and the GitHub push. Krishna writes the Sachin documents from the spec. The map ends when the PRD and the spec exist.
- Practicing the live interview change. That happens after the code exists.
- The Friday 16 October 2:00 PM ET Teams call. Sachin schedules the interview after the code is submitted.
