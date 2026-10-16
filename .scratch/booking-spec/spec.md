# Booking spec

The behavior for Monday's build. The words are in `CONTEXT.md`. Scope is in `prd.md`.

## Price

Inside the early-bird window, the starting price is the early-bird price for that seat's price tier. Outside it, the starting price is the price tier. The group rate applies to that number. Then one promo code applies. A second promo code on the same order is rejected.

A promo code has a global limit and a per-customer limit. One use is consumed when the order first receives its tickets, which is when the payment succeeds. A customer cancel does not restore it. An exceptional refund does not restore it. A reschedule does not restore it, including when the customer takes the 14-day refund. An event cancellation does, when that override is recorded.

An exclusive promo code cannot be combined with early-bird or group pricing. On that order the starting price is the price tier, the group discount is zero, and the promo code applies.

A promo code that is already used up is skipped, and the price without it stands.

The group rate and the service fee are stored amounts. This spec does not pick the numbers.

## Caps

The limits are 6 tickets on the order and 12 tickets for that customer at that event. A hold counts. A cancel frees the spot when the ticket comes off the order.

A request names the seats to hold. The most it can add is the smaller of two remaining counts: 6 minus the holds and tickets already on that order, and 12 minus the holds and tickets that customer already has for that event. If the request names more than that, none of those holds are written.

Two requests at the same moment lock one row for that customer and that event. The first request to get the lock runs the check. The other waits, runs the same check, and is refused when the first hold used the last spot. It does not keep a hold.

## Seat

A seat at an event is free, held, blocked, or a ticket. One seat has one hold or one ticket.

A hold lasts 10 minutes and is not a sale. A waitlist hold lasts 15 minutes. A hold stays past its clock while a payment for that order is pending, unknown, or succeeded and the order has no ticket yet.

A hold and a block do not interrupt each other. The request that locks the seat first stays. The other fails.

## Checkout and payment

Checkout starts when the customer submits that order for payment. That same request inserts one payment row for that order and commits it before the mock call. The row holds the amount in cents and a status: pending, unknown, succeeded, or declined. The mock is called at most once. A later check uses the status lookup.

If any seat in the submit is no longer that customer's live hold, the submit fails and writes no payment row. Checkout cannot start after the hold clock has run out. If the customer never submits, the hold ends on its clock and the seat becomes free. If the request dies before the payment row commits, no one was charged, and the hold clock still releases the seat.

Success sets the payment to succeeded and writes the ticket. It does not take the money again. Decline sets it to declined, releases the seats, and the order has no ticket. Timeout and unknown both set it to unknown. A finished payment is succeeded or declined.

While the payment is pending, unknown, or succeeded and the order has no ticket, another customer cannot hold or buy those seats. The lookup runs until it returns success or decline. The money comes back only when the lookup says the payment was declined.

A query finds a payment that is succeeded and whose order has no ticket, and a payment that is still pending or unknown. That query is the reconciliation.

## Booking moves

- Free becomes held when the caps allow it.
- Held becomes free when its clock runs out and no payment row exists, when a reschedule ends a bare hold, or when the payment finishes as declined.
- Checkout leaves the seat held.
- Succeeded, including a lookup that returns success, moves held to a ticket.
- Unknown leaves the seat held until that lookup.
- An admin blocks a free seat, and releases a block back to free.
- A ticket does not become free here. That is a refund, below.

## Clock and whole cents

Each event has a start instant in the venue's time zone. A refund window is elapsed time before that instant. At exactly 168 hours the refund is 100%. After that, through exactly 48 hours, it is 50%. Under 48 hours it is 0%. A 25-hour local day does not stretch either window, and a 23-hour day does not shrink it.

When the order is confirmed, each ticket stores four cent amounts that already sum to the order: starting price, its share of the group discount, its share of the promo discount, and its share of the service fee. A discount is split in proportion to each ticket's price before that discount. The fee is split in equal shares. Any leftover cent goes to the lowest ticket id. A later cancel uses those stored shares. A fee of 100 cents on three tickets is 34, 33, and 33, with 34 on the lowest ticket id.

A ticket's net is its starting price minus its group share minus its promo share. A customer cancel refunds the band applied to that net. The fee share is not included, even when every ticket on the order is cancelled. Half of an odd net rounds up, so 1001 cents at 50% returns 501 cents.

The service fee is a flat amount on the order. A customer cancel does not return it. An event cancellation or an exceptional refund can return it, as below.

## Refund

A refund is pending, unknown, succeeded, or failed. The row is committed before the mock call. Success, decline, timeout, and unknown work as they do for a payment. Unknown finishes only by status lookup.

While the refund is pending or unknown, the ticket stays on the order, the seat stays a ticket, and the money has not come back. Another customer cannot take the seat.

Succeeded returns the money.

- A customer cancel or a reschedule refund then takes those tickets off the order, and the seat becomes free. The next waitlist entry is offered. If that customer cannot take it, the next entry is offered.
- An event cancellation takes the tickets off and does not put the seats back on sale. The waitlist entries end. Each ticket's full net is returned, plus every fee share, including a fee share kept on an earlier customer cancel. The net is not paid twice.
- An exceptional refund returns only the cents the admin named, with a reason. The bands do not apply. The tickets stay, and the seat does not become free.

Failed leaves the ticket, the seat, and the money as they were. The repair is a new refund for the same cents, the amount from the failed attempt, not a band recomputed later.

## Reschedule

Tickets stay on the order. A hold with no payment row ends, and that seat becomes free. Nothing is refunded for a hold. A payment that is already pending, unknown, or succeeded without a ticket keeps the seats until it finishes. Tickets that then exist stay on the order for the new date.

A waitlist entry stays, in the same place in line.

The 14 days are 336 elapsed hours from the moment the reschedule override is recorded. At exactly 336 hours the customer can still take the full refund. During that window, cancelling a ticket returns that ticket's full net plus that ticket's fee share, and this replaces the usual bands. A fee share already kept on an earlier customer cancel stays kept. After 336 hours, a customer cancel uses the usual bands against the new event instant, and the fee stays kept.

## Waitlist

A waitlist entry is a place in line for the event, not a claim on a seat. When a seat becomes free and the event still happens, the next entry gets a hold that lasts 15 minutes, and the caps still apply. If that customer cannot take it, the next entry is offered.

## Admin read

The sales and occupancy read for one event returns how many seats are free, held, blocked, and tickets, how many tickets were sold, and the sum of what customers paid.

## Seat map

The seat map shows each seat's price and state when the customer opens the event, and again after one of their own actions returns. It does not update by itself while the page is open. Another customer's hold or ticket appears the next time this customer opens the event or completes an action.
