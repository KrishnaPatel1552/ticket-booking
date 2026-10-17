> Canonical ticket: https://github.com/KrishnaPatel1552/ticket-booking/issues/12
> Closed as #12. This file is the archive. The resolution comment on the issue is the answer.

# Clock and whole cents

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by:

## Question

Which clock decides the refund windows, what a daylight-saving change does to those windows, and how discounts and the fee split across tickets in whole cents?

Refund bands Sachin set: 100% at 7 or more days before the event, 50% from 48 hours to 7 days, and 0% under 48 hours. The refund is what the customer actually paid for that ticket, after discounts.

A service fee is a flat fee per order. A customer cancel does not return it. An event cancellation or an admin refund does return it.

The price order is already locked: early-bird or the price tier, then the group rate, then one promo code. A promo code has a global limit and a per-customer limit, and it is consumed when the booking is confirmed. A customer cancel does not restore a use. An event cancellation does. A code can be exclusive, which means it cannot be combined with early-bird or group pricing.

Sachin asked for the group discount, the promo discount, and the fee to be split across tickets in whole cents, so a partial cancel refunds exactly what was paid. He also said a customer cancel does not return the fee. Both rules have to work together.

Decide:

- The clock, and the rule when a local day has 25 hours.
- How the group discount, the promo discount, and the fee split in whole cents.
- What a customer cancel refunds for one ticket, including that ticket's share of the fee.
- What an event cancellation and an admin refund return, including the fee.
- When a promo use is consumed and when it is restored.

Use the domain-modeling skill. Write any resolved term into `CONTEXT.md`. Service fee is not in the glossary yet.

Leave the refund states, a failed refund, and an unknown refund to [Refund state machine](07-refund-state-machine.md).

## Answer

Krishna accepted the clock, the stored cent shares, the customer-cancel amount, the event-cancellation and exceptional-refund amounts, and the promo use. The glossary term is [service fee](../../../CONTEXT.md).

**Clock.** Each event has a start instant in the venue's time zone. A refund window is elapsed time before that instant: 168 hours, and 48 hours. At exactly 168 hours the refund is 100%. After that, through exactly 48 hours, it is 50%. Under 48 hours it is 0%. A 25-hour local day does not stretch either window, and a 23-hour day does not shrink it. The server compares now with that instant.

**Whole cents.** When the order is confirmed, each ticket stores four cent amounts that already sum to the order: starting price, its share of the group discount, its share of the promo discount, and its share of the service fee. A discount is split in proportion to each ticket's price before that discount. The fee is split in equal shares. Any leftover cent goes to the lowest ticket id. A later cancel uses those stored shares and does not divide again. A fee of 100 cents on three tickets is 34, 33, and 33, with 34 on the lowest ticket id.

**Customer cancel.** The customer gets the band applied to that ticket's net: starting price minus its group share minus its promo share. The fee share is not included, even when every ticket on the order is cancelled. Half of an odd net rounds up, so 1001 cents at 50% returns 501 cents.

**Event cancellation and an exceptional refund.** An event cancellation returns each ticket's full net, plus every fee share, including tickets the customer already cancelled. The net is not paid twice. The fee share still comes back. An exceptional refund is the cents the admin names, with a reason. The bands do not apply. That amount may include the fee because the admin typed it.

**Promo use.** One use is consumed when the order first receives its tickets, which is when the payment succeeds. A customer cancel of some or all tickets does not restore it. An exceptional refund does not restore it. An event cancellation does. What a reschedule does to that use stays on [What a reschedule does](08-reschedule.md).
