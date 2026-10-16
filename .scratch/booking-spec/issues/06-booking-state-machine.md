# Booking state machine

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by: 05, 08

## Question

What is the booking state machine, including every allowed move Sachin said the interview will ask about?

The Step 1 note already says a seat at an event is free, held, blocked, or a ticket. A hold and a block do not interrupt each other. The second action fails. Checkout, the hold expiry, and an unknown payment are decided in [Checkout lifetime and an unknown payment](05-checkout-lifetime.md). Use that answer. Do not reopen it. What a reschedule does to a hold is decided in [What a reschedule does](08-reschedule.md). Use that answer. Do not reopen it. When a hold is allowed is decided in [Caps when one customer acts twice](03-caps-under-concurrency.md). Use that answer. Do not reopen it.

Decide the states and the allowed moves among them. Include:

- A free seat becomes a hold, when the caps allow it.
- A hold becomes a ticket.
- A hold ends and the seat is free.
- Checkout starts, checkout never finishes, and the payment result is unknown.
- An admin blocks a free seat, and an admin releases a block.
- A hold and a block on the same seat. The second action fails.

Do not design the refund machine, a failed refund, or the moment a cancelled ticket's seat becomes free. That is [Refund state machine](07-refund-state-machine.md).

## Answer

Krishna accepted four seat states and the lock that orders a simultaneous hold and block. No new glossary term. A cancelled ticket's seat becoming free stays on [Refund state machine](07-refund-state-machine.md).

**States.** A seat at an event is free, held, blocked, or a ticket. Pending, unknown, and succeeded-without-a-ticket are payment statuses. While any of those is open, the seat stays held.

**Moves.**

- Free becomes held when the caps allow it.
- Held becomes free when its clock runs out and no payment row exists, or when a reschedule ends a bare hold, or when the payment finishes as declined.
- Checkout on a live hold inserts the payment row and leaves the seat held. If any seat in that submit is no longer that customer's live hold, the submit fails and writes no payment row.
- Succeeded, including a lookup that returns success, moves held to a ticket.
- Unknown leaves the seat held until that lookup.
- An admin blocks a free seat, and releases a block back to free.
- A hold and a block on the same seat do not change it. The request that locks the seat first stays. The other fails.
- A ticket does not become free in this machine.
