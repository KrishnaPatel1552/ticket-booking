# Ticket booking

The language for a system that sells seats to live events. These words are the ones the email, the design note, and the code all use.

## Language

### People

**Customer**:
The person who holds seats, places orders, cancels tickets, and joins a waitlist.
_Avoid_: User, account, buyer

**Admin**:
The staff person who can break a normal rule.
_Avoid_: User, agent, operator

### Place and event

**Venue**:
The building where events happen.
_Avoid_: Location, stadium

**Section**:
A named group of seats in a venue.
_Avoid_: Zone, block

**Seat**:
One sellable place in a section.
_Avoid_: Ticket, chair

**Event**:
One scheduled happening at a venue, such as a concert, a play, or a game.
_Avoid_: Show, performance

### Sale

**Hold**:
A customer's temporary exclusive claim on a seat. It lasts 10 minutes and is not a sale. It stays past those 10 minutes while a payment for that order is unresolved.
_Avoid_: Reservation, lock, block

**Block**:
An admin removal of a seat from sale.
_Avoid_: Hold, freeze, lock

**Order**:
The purchase a customer checks out, views, and cancels.
_Avoid_: Booking, cart, purchase

**Checkout**:
The customer's submission of an order for payment.
_Avoid_: Purchase, cart

**Payment**:
The money attempt for one order.
_Avoid_: Charge, transaction, ticket

**Ticket**:
One seat on an order. The limits of 6 per order and 12 per customer per event count tickets.
_Avoid_: Seat, booking

**Waitlist entry**:
One customer's request for an event that has no seat left to sell.
_Avoid_: Queue, standby

**Cancel**:
Taking one or more tickets off an order. It may be some of them or all of them.
_Avoid_: Refund

**Refund**:
Money returned to a customer for tickets on an order.
_Avoid_: Cancel

**Event cancellation**:
The decision that an event will not happen. Every ticket is refunded.
_Avoid_: Cancel, reschedule

**Reschedule**:
Moving an event to a different date.
_Avoid_: Event cancellation

### Price

**Price tier**:
A named price level on a seat.
_Avoid_: Zone, class

**Early-bird**:
A date window on an event. During that window, the early-bird price for a seat's price tier is the starting price. It is not a promo code.
_Avoid_: Promo code, discount

**Group rate**:
A price change based on how many tickets are on the order.
_Avoid_: Discount, bulk

**Promo code**:
A code the customer enters that changes the price. It is not an early-bird window.
_Avoid_: Early-bird, coupon, discount, voucher

**Service fee**:
A flat amount on an order, apart from the price of the tickets.
_Avoid_: Charge, tax, surcharge

### Admin record

**Override**:
An admin action that breaks a normal rule and carries a reason. The kinds named so far are a block, releasing a block, an event cancellation, a reschedule, and an exceptional refund.
_Avoid_: Fix, exception

**Audit entry**:
The permanent record of an override.
_Avoid_: Log, history
