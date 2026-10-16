> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/10
> Closed as #10. This file is the archive. The resolution comment on the issue is the answer.

# Payment recorded before the attempt

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by:

## Question

How is a payment recorded before it is attempted, and how is a payment with no ticket found and resolved?

Sachin rejected this outcome from the Step 1 design note. The mock payment succeeds. The program stops before it writes the ticket. The hold ends. Another customer gets that seat. The first customer paid and has no ticket.

The mock must be able to return success, decline, timeout, and unknown, selectable by test input. It must also offer a status lookup for an unknown outcome.

Decide:

- The payment record, and the moment it is written relative to the mock call.
- What each mock result does to that record.
- How a payment with no ticket is found.
- How that payment is finished so the customer gets the ticket or the money comes back, and a second customer cannot take the seat while the payment is open.

Use the domain-modeling skill. Write any resolved term into `CONTEXT.md`. The word payment is not in the glossary yet.

Leave checkout's start, its maximum duration, and the seat when checkout never finishes to [Checkout lifetime and an unknown payment](05-checkout-lifetime.md).

## Answer

Krishna accepted the payment record, the reconciliation rule, and the rule that a succeeded payment gets a ticket. The glossary term is [payment](../../../CONTEXT.md).

**The record.** One payment belongs to one order. The system inserts that row and commits it before it calls the mock. The row holds the amount in cents and a status: pending, unknown, succeeded, or declined. The mock is called at most once for that payment. A later check uses the status lookup.

**Mock results.** Success sets the payment to succeeded. Decline sets it to declined. Timeout and unknown both set it to unknown. Sachin required unknown as a mock result, and he required the lookup to resolve it. Unknown is not a finished payment. A finished payment is succeeded or declined.

**While the payment is pending, unknown, or succeeded and the order has no ticket.** Another customer cannot hold or buy those seats. The order does not get a ticket yet, and the seats are not released. The hold's 10 minutes cannot release them. The lookup runs until it returns success or decline. Success writes the ticket and does not take the money again. Decline releases the seats, and the order has no ticket. The money comes back only when the lookup says the payment was declined.

**How a payment with no ticket is found.** A query finds a payment that is succeeded and whose order has no ticket, and a payment that is still pending or unknown. That query is the reconciliation.
