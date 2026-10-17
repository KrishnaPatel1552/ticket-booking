> Canonical ticket: https://github.com/KrishnaPatel1552/ticket-booking/issues/5
> Closed as #5. This file is the archive. The resolution comment on the issue is the answer.

# Ranked questions for today's reply

Type: grilling
Status: resolved
Blocked by: 01

## Question

What clarifying questions do we email today, ranked by how much each answer would change the design?

Each question must aim at a place where the stated rules collide or where a missing rule would fork the model. High fidelity means a concrete scenario, the two or three outcomes that scenario could take, and why those outcomes are different designs. Rank by design impact, not by curiosity.

Use only the canonical language from Canonical language for the booking domain. Cover at least the collisions already visible in the brief: hold versus sold, the 6-per-order and 12-per-customer caps, price composed from tier plus early-bird plus group size plus promo, refund amount versus event cancellation, partial cancel, waitlist, admin override, and reschedule. Include this one by name: when a customer hold and an admin block want the same seat, who wins? Include the process clash already in the thread: a Teams interview is on the calendar for Friday 16 October at 2:00 PM ET, while this email schedules the take-home interview after Monday's code.

Do not write the assumptions, the design note, or the cut list here. Those are later tickets. The answer is the ordered question list, ready to paste into the email.

## Answer

Krishna kept the recommended questions, moved the customer-identity question to just after the waitlist, and left the other order as 1, 2, 3, 4, 5, 6, then section, then the Friday interview. Question 10 was added when the cut list was decided: ask whether admin setup screens are in Monday's build. Paste this list.

1. **Hold and block on the same seat.** A customer has a hold. An admin blocks that seat before the 10 minutes are up. Or the seat is already blocked, and a customer tries to hold it. Who wins? If the admin wins, a hold can disappear during checkout. If the customer wins, a block waits until the hold ends or becomes a ticket. If neither can interrupt the other, the second action fails.

2. **The 10 minutes end during checkout.** The customer is paying when the hold reaches 10 minutes. Does the seat stay with them until payment finishes, or can another customer take it? If the hold stays through payment, expiry waits. If it does not, payment can succeed for a seat that is no longer theirs.

3. **What the caps count.** The limits are 6 tickets per order and 12 tickets per customer for one event. A customer already has 10 tickets, then holds 3 more seats. Does the hold fail because 13 is over 12, or do holds not count until they become tickets? After they cancel 2 tickets, can they buy 2 more? This decides whether the cap is checked at hold time, at payment, or both.

4. **How the price is built.** A seat has a price tier, the event is inside an early-bird window, the order is large enough for a group rate, and the customer enters a promo code. Do these stack, or does one replace the others, and in what order? A promo code can also be at its own limit. This decides whether the price is one number on the seat or a stack of rules with a winner.

5. **Refund amount, partial cancel, and a cancelled event.** A customer cancels 2 of 4 tickets the day before the event. How much money comes back? If an admin cancels the whole event, every ticket is refunded, including tickets that would have received a smaller refund under the normal rule. What are the time windows and the amounts? A reschedule is separate: do existing tickets stay valid, do holds end, and can the customer take a refund instead?

6. **Waitlist.** The event has no seat left. A customer adds a waitlist entry. Is that a place in line, or a claim on a specific seat? When a ticket is cancelled, does the next customer get a hold, and do the 6 and 12 caps still apply? This decides whether a waitlist entry is a place in line or another way to take a seat.

7. **Same customer.** A customer may have 12 tickets for one event. Do they sign in, or is an email address enough to know it is the same person?

8. **Section reused or copied.** Is a section a permanent part of the venue, shared by every event there, or set up again for each event? This decides whether seats belong to the venue or to the event.

9. **Friday's interview.** A Teams interview is on the calendar for Friday 16 October at 2:00 PM ET. This email says the take-home interview is scheduled after Monday's code. Which conversation is Friday, and does the take-home interview replace that round or follow it?

10. **Admin setup.** Should an admin be able to add venues, sections, seats, events, price tiers, and promo codes in this build? The other admin actions are block, release, event cancellation, and exceptional refund. If setup screens are out of scope, starter data is enough for the venue and the prices.
