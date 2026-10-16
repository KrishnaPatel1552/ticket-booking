> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/4
> Closed as #4. This file is the archive. The resolution comment on the issue is the answer.

# Canonical language for the booking domain

Type: grilling
Status: resolved
Blocked by:

## Question

Which words are canonical for this project, and which words from the brief do we refuse as synonyms?

The language has to be tight enough that today's email, the one-page design note, and later code cannot drift. Resolve the terms the brief already forces into view: the person who books, the performance they book for, the place, the section, the seat, the temporary claim, the purchase, the line on that purchase, the waitlist, the price rule, the early-bird window, the group-size rule, the promo, the refund, the admin action that breaks a normal rule, and the record of that action.

Where two brief words might mean the same thing or two different things (booking vs order, hold vs reservation, cancel vs refund, customer vs account), pick one and say what the other must not mean.

Mark a term provisional when its boundary is something we are about to ask Copart. Do not invent a boundary they still have to answer.

Record resolved terms in `CONTEXT.md` using the domain-modeling format. Leave this ticket's answer as the decision, with the glossary file as the asset.

## Answer

Krishna accepted the recommended words. The glossary is [CONTEXT.md](../../../CONTEXT.md).

**Order** is the purchase. Booking is not a domain noun. **Seat** is a place in the venue. **Ticket** is that seat once it is on an order, and the caps count tickets. **Hold** is the customer's 10-minute claim. **Block** is the admin taking a seat off sale. **Customer** and **Admin** are the two people. **Event**, **Venue**, and **Section** are the show, the building, and a group of seats. **Cancel** releases tickets. **Refund** is the money. **Event cancellation** and **Reschedule** are separate. Price words are **Price tier**, **Early-bird**, **Group rate**, and **Promo code**. A **Waitlist entry** is one customer's request. An **Override** breaks a normal rule and carries a reason. An **Audit entry** is the permanent record of that override.

These boundaries stay open, and they are questions for Copart rather than definitions:

- When a customer hold and an admin block want the same seat, who wins? Krishna asked for this explicitly. [Ranked questions for today's reply](02-ranked-questions.md) must include it.
- Whether a customer must sign in.
- Whether a section is reused for every event in the venue, or set up again for each event.
- How the refund amount changes as the event gets closer, and what a reschedule does to holds and tickets people already have.
- What each price rule attaches to, and which one wins when several apply.
- Whether a waitlist entry is a place in line or an offer of a specific seat.
