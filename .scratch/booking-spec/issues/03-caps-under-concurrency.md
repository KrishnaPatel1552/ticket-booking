# Caps when one customer acts twice

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by:

## Question

How do the 6 and 12 limits stay correct when the same customer holds two different seats at the same moment?

A lock on one seat row does not lock the other seat. Each request can read the same ticket count and allow one more hold. The order can end above 6 tickets. The customer can end above 12 tickets for that event.

Sachin already locked the counting rule. A hold counts toward both limits. A cancel frees those spots. The caps apply per account, because the customer signs in.

The earlier double-sell note is an input, not the answer: [Double-sell prevention](../../take-home-route/research/double-sell-prevention.md). That note keeps one active hold or ticket per seat per event. This ticket is the cap across two seats.

Decide the rule that makes one of the two holds fail, for the order limit and for the customer limit. Say what the second request observes.

## Answer

Krishna accepted a whole-request check and first come, first served. The second request waits, then runs the same check.

A request names the seats to hold. The most it can add is the smaller of two remaining counts. One count is 6 minus the holds and tickets already on that order. The other is 12 minus the holds and tickets that customer already has for that event. A hold counts. A cancel frees the spot.

If the request names more seats than that remaining count, none of those holds are written. The customer sends a smaller request. With 11 already counting toward 12, a request for 2 seats writes nothing. A request for 1 seat can write a hold.

Two requests at the same moment lock one row for that customer and that event with `SELECT … FOR UPDATE`. The first request to insert that row wins it. The other waits on that row. The first request to get the lock runs the check. Seat 14 still fits, so that hold is written. The second request waits, runs the same check, and is refused when the first hold used the last spot. The customer sees the refusal. The second request does not keep a hold. The seat lock from the double-sell note still keeps one hold or ticket on one seat.
