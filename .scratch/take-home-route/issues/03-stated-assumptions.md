# Assumptions we will design against

Type: grilling
Status: resolved
Blocked by: 02

## Question

For everything we ask and they may not answer, what assumption do we write into today's email and then design against?

Every assumption must name the question it covers, the choice we will proceed with, and what we will do if Thursday's reply contradicts it. Prefer the assumption that keeps the Monday build smaller and the invariant easier to test. Do not assume a rule they already stated. Do not settle a term that Canonical language for the booking domain left provisional unless this ticket is explicitly choosing the assumption that makes it provisional.

The answer is the assumption list, in the same order as the questions, ready to paste into the email.

## Answer

Krishna accepted these assumptions. Friday's interview is a question only. There is no assumption for it. One promo code per order is our choice. An early-bird window is not a promo code.

Paste this list.

1. **Hold and block.** The one already on the seat stays. A block cannot remove a hold. A hold cannot be placed on a blocked seat. The second action fails. If you pick a winner, we will follow that before building holds and blocks.

2. **Ten minutes during checkout.** Once checkout has started on that hold, another customer cannot take the seat until checkout finishes. If the 10 minutes already ran out before checkout starts, the seat is free. If the seat should free itself during payment, we will change expiry before building checkout.

3. **Caps.** A hold counts toward both limits: 6 tickets on the order, and 12 tickets for that customer at that event. Cancelling tickets gives those spots back right away. If a hold should not count, or a cancel should not give a spot back, we will change this before building the limits.

4. **Price.** Inside the early-bird window, the starting price is the early-bird price. Outside it, the starting price is the price tier. The group rate applies to that number. Then one promo code applies. A second promo code on the same order is rejected. A promo code that is already used up is skipped, and the price without it stands. The early-bird window is not a promo code. If you want a different order, we will change it before building prices.

5. **Refunds.** More than 7 days before the event: the full ticket price comes back. From 7 days down to 24 hours: half. Under 24 hours: nothing. An event cancellation returns the full price of every ticket. A partial cancel returns money only for the tickets cancelled, at the rate above. A reschedule keeps existing tickets valid, ends holds, and a later cancel uses these same windows against the new date. If you give different windows or a different reschedule rule, we will replace these before building refunds.

6. **Waitlist.** A waitlist entry is a place in line for the event, not a claim on a specific seat. When a ticket is cancelled, the next customer gets a hold, and the 6 and 12 limits still apply. If they cannot take it, the next customer is offered. If a waitlist entry should name a specific seat, or skip the limits, we will change that before building the waitlist.

7. **Same customer.** An email address is enough. The same email is the same customer. There is no sign-in. If a customer must sign in, we will add that before enforcing the 12-ticket limit.

8. **Section.** A section is a permanent part of the venue, shared by every event there. A hold or a ticket is for one seat at one event, so the same seat can be sold again for a different event. If each event needs its own copy of the section, we will copy seats per event before building the venue.
