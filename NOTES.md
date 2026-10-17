# Notes

These notes are for a reader grading the decisions. The behavior is the code on `integration/monday-booking`, checked against the HTTP tests. The words are the ones in `CONTEXT.md`: customer, admin, venue, section, seat, event, hold, block, order, checkout, payment, ticket, waitlist entry, cancel, refund, event cancellation, reschedule, price tier, early-bird, group rate, promo code, service fee, override, and audit entry.

## Decisions the code carries

A payment row is inserted and committed before the mock is called. `Checkout` is not a transaction. `RecordPendingPayment.record` is, and it flushes the pending row before it returns. The mock runs after that return, so a crash after the commit still leaves the payment. A refund uses the same split. `CancelTickets`, the event-cancellation refund, and the exceptional refund each commit a pending refund row, then call the mock.

Unknown finishes only by a status lookup. Timeout and unknown from the payment mock both store `unknown`. They do not write a ticket and they do not release the seat. A later lookup that returns success writes the ticket without taking the money again. A lookup that returns decline sets the payment to declined and releases the seats. A lookup that returns timeout or unknown leaves the payment unknown. Opening the order with a lookup, and the reconcile request, are what run that lookup. A refund follows the same pattern, except a decline from the refund mock stores `failed`. There is no background scheduler.

One seat at one event has one active hold or one ticket. The hold and the ticket are one row. Becoming a ticket updates that row. PostgreSQL enforces this with the unique partial index `one_active_event_seat` on `(event_id, seat_id)` where the row is active. A second hold or a second ticket on that seat fails, and the request keeps no new hold. The same seat can be sold for a different event. A released hold leaves the index.

A hold request is capped at 6 tickets on the order and 12 tickets for that customer at that event, counting holds and tickets. The request may add only the smaller of the two remaining counts. A request that names more seats than that remaining count writes no holds. Two hold requests from the same customer at the same event take one lock first: the code inserts a `customer_event` row and then selects it `for update`. The first request to get the lock runs the cap check. The other waits, runs the same check, and writes nothing when the first hold used the last spot. The transaction is Read Committed. The 10 minutes are a timestamp on the committed hold, not a lock held open for that time. A customer hold is still held at 9 minutes 59 seconds and is free when the event is opened at 10 minutes. A hold that already has a payment row stays.

The price is early-bird, then the group rate, then one promo code. Inside the early-bird window the starting price is the early-bird price for that seat's price tier. Outside it, the starting price is the price tier. The group rate is a stored cent amount, applied once when the order has enough tickets, and it cannot exceed the ticket total. Then one promo code takes a fixed cent amount off what remains of the tickets. A second promo code on the same order is rejected. An exclusive promo code uses the price tier as the starting price and a group discount of zero, then applies. The service fee is added after those discounts and is not reduced by them. The amounts in starter data are a group-rate threshold of 4, a group rate of 500 cents, and a service fee of 100 cents. Those numbers are starter data. The rule is the order in which they combine.

When the payment succeeds, each ticket stores its starting price, its share of the group discount, its share of the promo discount, and its share of the service fee. A discount is split in proportion to each ticket's price before that discount. The service fee is split in equal shares. Any leftover cent goes to the lowest ticket id, so a fee of 100 cents on three tickets is 34, 33, and 33. Those four shares already sum to the order. A later cancel uses the stored shares and does not divide them again.

Refund windows are elapsed time before the event instant, compared as a duration of hours. At exactly 168 hours the customer cancel returns 100 percent of that ticket's net. After that, through exactly 48 hours, it returns half the net. Half of an odd net rounds up. Under 48 hours it returns nothing. A customer cancel never returns the fee share in those bands. The server compares instants. A 25-hour local day does not stretch the window.

A reschedule records the override, and the special refund lasts 336 elapsed hours from that instant. At exactly 336 hours a customer cancel still returns the full net plus that ticket's fee share, and that amount replaces the usual bands. After 336 hours the usual bands use the new event instant, and the fee stays kept. A fee share already kept on an earlier customer cancel stays kept. A reschedule does not recompute a refund row that was already committed.

An event cancellation does not put seats back on sale. The seat map shows a seat with no active hold or ticket as blocked once the event is cancelled, and a hold on a cancelled event is refused. The cancellation ends the waitlist. It restores promo uses: a use counts only while the payment is pending, unknown, or succeeded and the event is not cancelled, so setting the cancellation instant drops those payments out of the taken count. A customer cancel and a reschedule do not restore a promo use. A succeeded customer cancel takes those tickets off the order and the seats can be held again. A reschedule keeps the tickets, ends a hold that has no payment, and can offer that freed seat. Neither one clears the taken use.

A promo use is consumed when the payment succeeds and the order first receives its tickets. A hold does not consume it. A declined payment does not consume it. A pending or unknown payment counts as taken, under a lock on the promo code, before the payment amount is committed.

Opening the event releases an expired hold that has no payment row, and then offers the next waitlist entry. A waitlist hold lasts 15 minutes. Production uses the system clock. Tests move that clock through `POST /api/test/clock`, which exists only on the test profile.

Each override requires a reason and writes an audit entry. The overrides are a block, releasing a block, an event cancellation, a reschedule, and an exceptional refund.

## Ambiguities and how the code resolved them

The early-bird window is closed at the start and open at the end. The start instant uses the early-bird price. The end instant uses the price tier. One second before the end is still early-bird, and one second before the start is the price tier. If the early-bird price or either endpoint is missing, the starting price is the price tier.

A used-up promo code is skipped instead of failing checkout. If the global limit or the per-customer limit is already taken, checkout stores the price without that code and still completes. The same skip applies to a used-up exclusive code: early-bird and the group rate then stand.

An exceptional refund leaves the tickets in place. The admin names the cents and a reason. Success marks that refund succeeded and does not take the tickets off the order. The seat stays a ticket. While the refund is pending or unknown, the tickets stay as well.

A failed customer-cancel refund for the same tickets is repaired at the cents already stored on that failed refund. The band is not recomputed. If a customer refund is still pending or unknown when the event is cancelled, that refund is looked up first. A success then returns only the kept fee share for a ticket already taken off. A failure lets the event cancellation refund the full net plus the fee share.

## What was left out, and why

The build is one API and one customer site. Correct behavior was the point of the work. These were left out on purpose.

- No screens for creating a venue, a section, a seat, an event, a price tier, or a promo code. An admin creates an event, a price tier, and a promo code with authenticated API calls. Starter data supplies the venue, the sections, and the seats.
- No admin site. A block, releasing a block, an event cancellation, a reschedule, and an exceptional refund are authenticated API calls. Each one requires a reason and writes an audit entry. A customer is refused.
- No sales chart. One authenticated read returns, for one event, how many seats are free, held, blocked, and tickets, how many tickets were sold, and the sum of succeeded payment amounts. Refunds are not subtracted. Tickets sold counts tickets that have been on an order for that event, including tickets later taken off. There is no screen.
- No live-updating seat map. The site loads the map when the customer opens the event and again after one of their own actions returns. It does not poll and it does not receive pushes.
- No real card charge. The caller scripts success, decline, timeout, or unknown, and scripts the later status lookup.
- No tax.
- No second promo code on an order, and no percent promo code or percent group rate.
- No background scheduler for expiry or for finishing an unknown payment or refund. Opening the event releases an expired hold that has no payment row. A person, or a test, finishes an unknown result by asking for the status lookup.

## What would come next

The next product work is the cuts above, in roughly this order: screens for the admin creates, then a sales screen, then a seat map that updates while the page is open, then a real card payment and tax. A percent promo code, or a second code on one order, would be a new pricing rule, not a small screen. A scheduler would release an expired hold and finish an unknown payment or refund without waiting for someone to open the event or call reconcile.

The written spec also reserves one live change for after this code exists. That change is not in this build.

## Tools

Cursor coding agents implemented the application, the tests, and these two documents from the written spec. No person typed that implementation by hand. This session's agent wrote `README.md` and `NOTES.md` after reading the code and running it. Earlier Cursor coding agents wrote the application and the tests. The documents do not add a tool that was not used.
