> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/8
> Closed as #8. This file is the archive. The resolution comment on the issue is the answer.

# One-page design note

Type: grilling
Status: resolved
Blocked by: 01, 04, 05

## Question

What goes on the one page: the data model, the booking state machine, the refund state machine, and how a seat cannot be sold twice?

Use only canonical terms. Treat Assumptions we will design against as the rules, not as facts Copart confirmed. Omit anything What today's reply cuts or defers removed. The double-sell section states the mechanism chosen in Double-sell prevention for the design note, including the expiry and crash cases that mechanism covers.

One page means short enough to paste into the email as the design section, not a second spec. The answer is that section, ready to paste.

## Answer

Krishna accepted this page.

**Data model.** A venue has sections. A section has seats. Each seat has a price tier. An event uses that venue's seats, so the same seat can be sold for a different event. An event has an early-bird window. During that window, the early-bird price for the tier is the starting price. Outside it, the starting price is the price tier. A customer is an email address. A hold is one seat at one event for one email, with an expiry time. An order is one email and one event, with at most one promo code. A ticket is one seat on an order. A block is an admin removal of one seat at one event. A waitlist entry is one email's place in line for one event. An override is an admin action with a reason. An audit entry is the record of that override.

**Booking.** A seat at an event is free, held, blocked, or a ticket. A free seat can be held when the caps allow it: 6 tickets on the order, 12 for that email at that event, counting holds. A hold becomes a ticket when checkout commits. If the 10 minutes run out before checkout starts, the seat is free again. Once checkout has started, another customer cannot take the seat until checkout finishes. A free seat can be blocked. A blocked seat can be released. A hold and a block do not interrupt each other. The second action fails. When a ticket is cancelled and the event is sold out, the next waitlist entry gets a hold if the caps allow it.

**Refund.** More than 7 days before the event, the full ticket price comes back. From 7 days down to 24 hours, half. Under 24 hours, nothing. A partial cancel returns money only for the tickets cancelled. An event cancellation returns the full price of every ticket. An exceptional refund is an override: the admin names the amount and the reason, and an audit entry records it. Reschedule is not in this build.

**One seat, one sale.** PostgreSQL allows one active hold or ticket per seat per event, using a unique partial index. Checkout and expiry lock that row. The 10 minutes are a timestamp on the hold, not a lock held open for 10 minutes. Two checkouts cannot both commit. If the hold expires during checkout, expiry waits, then sees the ticket and stops. A crash after mock payment and before the ticket is saved leaves the hold in place, so the seat is not sold twice. A charge that succeeded outside the database is not in PostgreSQL. After that hold expires, another customer can buy the seat.
