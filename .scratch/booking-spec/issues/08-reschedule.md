> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/14
> Closed as #14. This file is the archive. The resolution comment on the issue is the answer.

# What a reschedule does

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by: 04

## Question

What does a reschedule do to a hold, a waitlist entry, and the refund bands after the new date?

[What Monday's build includes](01-monday-build.md) kept reschedule in the build. Sachin already locked two rules. Tickets stay on the order. Each customer gets 14 days from the announcement to take a full refund, and that refund includes the fee.

The clock, the usual refund bands, the whole-cent split, and the fee come from [Clock and whole cents](04-clock-and-cents.md). Use that answer. Do not reopen it.

Decide:

- What an announcement is, and when the 14 days start.
- What happens to a hold on that event.
- What happens to a waitlist entry on that event.
- How the 14-day full refund, fee included, sits next to the usual bands once the event has a new date.
- What a customer cancel does after those 14 days.

Leave the refund states, a failed refund, and an unknown refund to [Refund state machine](07-refund-state-machine.md). That ticket must use this answer.

## Answer

Krishna accepted the 14-day window, the hold rule, the waitlist, and the promo use. The announcement is the reschedule. No new glossary term.

**Announcement.** The 14 days are 336 elapsed hours from the moment the reschedule override is recorded. At exactly 336 hours the customer can still take the full refund. During that window, cancelling a ticket returns that ticket's full net plus that ticket's fee share, and this replaces the usual bands. A fee share already kept on an earlier customer cancel stays kept. After 336 hours, a customer cancel uses the usual bands against the new event instant, and the fee stays kept.

**Holds.** A hold with no payment row ends when the event is rescheduled, and that seat becomes free. Nothing is refunded. A payment that is already pending, unknown, or succeeded without a ticket keeps the seats under the payment rule until it finishes. Tickets that then exist stay on the order for the new date.

**Waitlist.** A waitlist entry stays on the event, in the same place in line. A seat that becomes free is offered under the existing waitlist rule.

**Promo use.** A reschedule does not restore a promo use, including when the customer takes the 14-day refund. An event cancellation is still the only restore.
