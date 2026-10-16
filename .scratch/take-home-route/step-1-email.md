# Step 1 email

To: Sachin.Fegade@copart.com
Subject: Re: Take-home exercise: ticket booking system

Hi Sachin,

Thank you for the exercise. Java, Spring Boot, React, and PostgreSQL work for me. Below are my questions, ranked by how much each answer would change the design, the assumptions I will use for anything unanswered, a one-page design note, and what I will leave out of Monday's build.

I used Cursor to help me organize this reply. I can walk through every decision in it.

## 1. Questions

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

## 2. Assumptions

I will design against these if a question above is unanswered. If your reply contradicts one, I will change that part before building it. I am not assuming an answer for Friday's interview.

1. **Hold and block.** The one already on the seat stays. A block cannot remove a hold. A hold cannot be placed on a blocked seat. The second action fails.

2. **Ten minutes during checkout.** Once checkout has started on that hold, another customer cannot take the seat until checkout finishes. If the 10 minutes already ran out before checkout starts, the seat is free.

3. **Caps.** A hold counts toward both limits: 6 tickets on the order, and 12 tickets for that customer at that event. Cancelling tickets gives those spots back right away.

4. **Price.** Inside the early-bird window, the starting price is the early-bird price. Outside it, the starting price is the price tier. The group rate applies to that number. Then one promo code applies. A second promo code on the same order is rejected. A promo code that is already used up is skipped, and the price without it stands. The early-bird window is not a promo code.

5. **Refunds.** More than 7 days before the event: the full ticket price comes back. From 7 days down to 24 hours: half. Under 24 hours: nothing. An event cancellation returns the full price of every ticket. A partial cancel returns money only for the tickets cancelled, at the rate above. A reschedule keeps existing tickets valid, ends holds, and a later cancel uses these same windows against the new date.

6. **Waitlist.** A waitlist entry is a place in line for the event, not a claim on a specific seat. When a ticket is cancelled, the next customer gets a hold, and the 6 and 12 limits still apply. If they cannot take it, the next customer is offered.

7. **Same customer.** An email address is enough. The same email is the same customer. There is no sign-in.

8. **Section.** A section is a permanent part of the venue, shared by every event there. A hold or a ticket is for one seat at one event, so the same seat can be sold again for a different event.

## 3. Design note

**Data model.** A venue has sections. A section has seats. Each seat has a price tier. An event uses that venue's seats, so the same seat can be sold for a different event. An event has an early-bird window. During that window, the early-bird price for the tier is the starting price. Outside it, the starting price is the price tier. A customer is an email address. A hold is one seat at one event for one email, with an expiry time. An order is one email and one event, with at most one promo code. A ticket is one seat on an order. A block is an admin removal of one seat at one event. A waitlist entry is one email's place in line for one event. An override is an admin action with a reason. An audit entry is the record of that override.

**Booking.** A seat at an event is free, held, blocked, or a ticket. A free seat can be held when the caps allow it: 6 tickets on the order, 12 for that email at that event, counting holds. A hold becomes a ticket when checkout commits. If the 10 minutes run out before checkout starts, the seat is free again. Once checkout has started, another customer cannot take the seat until checkout finishes. A free seat can be blocked. A blocked seat can be released. A hold and a block do not interrupt each other. The second action fails. When a ticket is cancelled and the event is sold out, the next waitlist entry gets a hold if the caps allow it.

**Refund.** More than 7 days before the event, the full ticket price comes back. From 7 days down to 24 hours, half. Under 24 hours, nothing. A partial cancel returns money only for the tickets cancelled. An event cancellation returns the full price of every ticket. An exceptional refund is an override: the admin names the amount and the reason, and an audit entry records it. Reschedule is not in this build.

**One seat, one sale.** PostgreSQL allows one active hold or ticket per seat per event, using a unique partial index. Checkout and expiry lock that row. The 10 minutes are a timestamp on the hold, not a lock held open for 10 minutes. Two checkouts cannot both commit. If the hold expires during checkout, expiry waits, then sees the ticket and stops. A crash after mock payment and before the ticket is saved leaves the hold in place, so the seat is not sold twice. A charge that succeeded outside the database is not in PostgreSQL. After that hold expires, another customer can buy the seat.

## 4. What I will cut or defer

**In Monday's build**

- A customer can search events by name, open an event, see a seat map by section, see which seats are free, and see the price.
- A customer can hold seats, check out with a mock payment, see the order, cancel some tickets or all of them, and get the refund from the assumption.
- The waitlist is in the build under the assumption above. If your reply changes that, I will change the waitlist before building it.
- An admin can block a seat, release a block, cancel an event, and issue an exceptional refund. Each action has a reason and an audit entry.
- A seat is never sold twice. Tests will include two checkouts of the same seat at once, against a real PostgreSQL database.
- The customer is an email address.

**Not in Monday's build**

- Reschedule. The assumption is above. Event cancellation already refunds every ticket.
- Sales and occupancy reports. The seat map shows which seats are free, held, blocked, or sold.
- Sign-in, accounts, and passwords.
- Admin screens to add venues, sections, seats, events, price tiers, and promo codes, unless you say those screens are in scope. Until then, that data is starter data.

Best regards,
Krishna Patel
