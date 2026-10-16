# Booking PRD

Monday's build sells seats to one event at a time, takes a mock payment, and refunds what the customer actually paid. The words are in `CONTEXT.md`. The behavior is in `spec.md`.

Java, Spring Boot, React, and PostgreSQL. About 10–12 hours. Correct and small.

## Customer

A customer signs in with email and password. The caps apply per account.

A customer can search events by name, open an event, and see the seat map by section, with the price and whether each seat is free, held, blocked, or a ticket. The map shows those seats when the customer opens the event, and again after one of their own actions returns. It does not update by itself. A customer can hold seats, check out, see the order, cancel some tickets or all of them, and join the waitlist.

## Admin

An admin creates events, price tiers, and promo codes only through authenticated admin API endpoints. Starter data supplies the venue, the sections, and the seats.

An admin can block a seat, release a block, cancel an event, reschedule an event, and issue an exceptional refund. Each of these is an override with a reason and an audit entry.

Sales and occupancy is one authenticated admin read for one event. No screen and no chart. It returns how many seats are free, held, blocked, and tickets, how many tickets were sold, and the sum of what customers paid.

## In the build

- Sign-in, the seat map, holds, checkout, the order, cancel, and the waitlist.
- The mock payment, including unknown and a status lookup.
- The booking state machine and the refund state machine.
- Reschedule, with tickets kept and a 14-day full refund that includes the service fee.
- The four admin overrides above, plus the admin create endpoints and the one sales read.

## Out of the build

- Screens for creating a venue, a section, a seat, an event, a price tier, or a promo code. Starter data covers the venue, and Sachin allowed authenticated admin API endpoints for events, price tiers, and promo codes.
- A live interview change. That happens after the code exists.

## What the design has to defend

1. A payment row is committed before the mock call. Unknown is reconciled by status lookup. A succeeded payment gets a ticket.
2. Checkout is the submit. The payment row is written in that request. An abandoned page is only a hold.
3. A request that asks for more than the remaining 6 or 12 writes no holds. A second request from the same customer waits, then is refused when the first hold used the last spot.
4. A seat is free, held, blocked, or a ticket. A refund leaves the seat a ticket until it succeeds. Only a customer cancel or a reschedule refund then frees it.
5. Refund windows are 168 and 48 hours before the event instant. Each ticket stores its discount and fee shares. A customer cancel never returns the service fee, except inside the 336 hours after a reschedule.
