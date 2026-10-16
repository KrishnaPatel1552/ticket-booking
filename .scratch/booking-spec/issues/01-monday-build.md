> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/9
> Closed as #9. This file is the archive. The resolution comment on the issue is the answer.

# What Monday's build includes

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by:

## Question

For a build of about 10–12 hours, what is in Monday's submission, and what is left out with a reason Krishna can later copy into `NOTES.md`?

Sachin already locked these, so this ticket does not reopen them. Customers sign in with email and password, and the caps apply per account. Starter data is enough for venues, sections, and seats. An admin must be able to create events, price tiers, and promo codes, on screens or through authenticated admin API endpoints. A waitlist entry is a place in line. When a seat frees up, the next customer gets a hold that lasts 15 minutes, and the caps still apply. A hold and a block do not interrupt each other. Holds count toward the 6 and 12 limits, and a cancel frees those spots. The price order is early-bird or price tier, then group rate, then one promo code. A section is a permanent part of the venue.

Sachin left two pieces optional. Reschedule: if it is in, bookings are kept and each customer gets a full-refund window of 14 days from the announcement, fee included. If it is out, the spec says why. The per-event sales and occupancy summary: it may stay small. If it is out, the spec says why.

Decide all of the following:

- Reschedule is in, or out with a reason.
- The summary is in, or out with a reason.
- An admin creates events, price tiers, and promo codes on screens, or only through authenticated admin API endpoints.
- Which customer paths are in: search, the seat map, hold, checkout, seeing an order, cancel, and the waitlist.
- Which admin paths are in: block, release a block, event cancellation, and an exceptional refund.

Do not design the payment record, checkout lifetime, caps under concurrency, the clock, the cents split, or the state machines. Those are other tickets. If reschedule or the summary is left out, do not open a follow-up ticket for its behavior.

## Answer

Krishna kept reschedule in the build and asked for it in the design. The sales and occupancy summary is in. Admin creation is API only. The customer paths and the four admin paths are all in.

**In the build**

- Reschedule. Tickets stay on the order. Each customer gets 14 days from the announcement to take a full refund, and that refund includes the fee. What a reschedule does to a hold, a waitlist entry, and the refund bands after the new date is [What a reschedule does](08-reschedule.md).
- Sales and occupancy, as one authenticated admin read for one event. No screen and no chart. It returns how many seats are free, held, blocked, and tickets, how many tickets were sold, and the sum of what customers paid.
- An admin creates events, price tiers, and promo codes only through authenticated admin API endpoints. Starter data supplies the venue, the sections, and the seats.
- A customer can sign in with email and password, search events by name, open an event, see the seat map by section with the price and whether each seat is free, held, blocked, or a ticket, hold seats, check out, see the order, cancel some tickets or all of them, and join the waitlist.
- An admin can block a seat, release a block, cancel an event, and issue an exceptional refund. Each of these is an override with a reason and an audit entry.

**Out of the build**

- Screens for creating a venue, a section, a seat, an event, a price tier, or a promo code.

Reason Krishna can copy into `NOTES.md` for the missing setup screens: starter data covers the venue, and Sachin allowed authenticated admin API endpoints for events, price tiers, and promo codes.
