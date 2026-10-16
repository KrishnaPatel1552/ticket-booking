# What today's reply cuts or defers

Type: grilling
Status: resolved
Blocked by: 03

## Question

What do we explicitly cut or defer in today's email, and why?

Monday is roughly 10–12 hours. The reply must say what the build will not do, in the canonical language, so the one-page design note does not describe a system we have already dropped.

Judge each customer and admin capability from the brief: keep, defer, or cut. A deferral needs the reason (time, depends on an unanswered rule, or weakens the concurrency proof if attempted). The seat-never-sold-twice invariant, the hold, checkout, and a real PostgreSQL concurrency test stay in. Volume is not a reason to keep something.

The answer is the cut list, ready to paste into the email.

## Answer

Krishna kept the core build and the search box, deferred reschedule and reports, cut sign-in, kept the waitlist unless Thursday changes the assumption, and asked that admin setup be a question in the email. The search box filters events by name. If question 10 is unanswered, Monday uses starter data for venues, sections, seats, events, price tiers, and promo codes.

Paste this list.

**In Monday's build**

- A customer can search events by name, open an event, see a seat map by section, see which seats are free, and see the price.
- A customer can hold seats, check out with a mock payment, see the order, cancel some tickets or all of them, and get the refund from the assumption.
- The waitlist is in the build under the assumption: a place in line, the next customer gets a hold, and the caps still apply. If Thursday's reply changes that, we change the waitlist before building it.
- An admin can block a seat, release a block, cancel an event, and issue an exceptional refund. Each action has a reason and an audit entry.
- A seat is never sold twice. Tests include two checkouts of the same seat at once, against a real PostgreSQL database.
- The customer is an email address.

**Not in Monday's build**

- Reschedule. The assumption stays in the email. Event cancellation already refunds every ticket.
- Sales and occupancy reports. The seat map shows which seats are free, held, blocked, or sold.
- Sign-in, accounts, and passwords.
- Admin screens to add venues, sections, seats, events, price tiers, and promo codes, unless you say those screens are in scope. Until then, that data is starter data. This is question 10 in the email.
