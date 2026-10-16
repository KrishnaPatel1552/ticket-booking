> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/16
> Closed as #16. This file is the archive. The resolution comment on the issue is the answer.

# Refund state machine

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by: 04, 08

## Question

What is the refund state machine, including a refund that fails, a refund whose result is unknown, and the moment a cancelled seat can be taken again?

The bands, the clock, the whole-cent split, and the fee come from [Clock and whole cents](04-clock-and-cents.md). Use that answer. Do not reopen it. What a reschedule does, including the 14-day full refund with the fee included, comes from [What a reschedule does](08-reschedule.md). Use that answer. Do not reopen it.

A customer cancel, an event cancellation, a reschedule refund, and an exceptional refund are different. An exceptional refund is an override: the admin names the amount and the reason, and an audit entry records it. When a seat frees and the event has a waitlist, the next customer gets a hold that lasts 15 minutes, and the caps still apply. If that customer cannot take it, the Step 1 assumption is that the next waitlist entry is offered. Sachin did not contradict that assumption.

Decide:

- The states of a refund, and the allowed moves.
- What is true while the refund result is unknown: the ticket, the seat, and the money.
- What happens when the refund fails, and what the repair is.
- The moment the seat becomes free for a new hold or for the next waitlist entry.
- How a customer cancel, an event cancellation, a reschedule refund, and an exceptional refund differ in that machine.

## Answer

Krishna accepted the refund states, the seat staying a ticket until success, and which successes free a seat. The glossary term [refund](../../../CONTEXT.md) stays money returned, separate from a cancel.

**States.** A refund is pending, unknown, succeeded, or failed. The row is committed before the mock call. Success, decline, timeout, and unknown work as they do for a payment. Unknown finishes only by status lookup.

**While pending or unknown.** The ticket stays on the order, the seat stays a ticket, and the money has not come back. Another customer cannot take the seat.

**Succeeded.** The money is returned. A customer cancel or a reschedule refund then takes those tickets off the order, and the seat becomes free. The next waitlist entry is offered under the existing rule. An event cancellation takes the tickets off and does not put the seats back on sale, and the waitlist entries end. An exceptional refund returns only the cents the admin named. The tickets stay, and the seat does not become free.

**Failed.** The ticket, the seat, and the money stay as they were. The repair is a new refund for the same cents, the amount from the failed attempt, not a band recomputed later.
