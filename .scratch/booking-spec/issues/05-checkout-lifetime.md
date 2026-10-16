> Canonical ticket: https://github.com/KrishnaPatel1552/copart-prep/issues/13
> Closed as #13. This file is the archive. The resolution comment on the issue is the answer.

# Checkout lifetime and an unknown payment

Label: wayfinder:grilling
Type: grilling
Status: resolved
Blocked by: 02

## Question

What starts checkout, how long can checkout last at most, and what happens to the seat if checkout never finishes or the payment result is unknown?

Sachin accepted the Step 1 idea in principle and pointed it here. Once checkout has started on a hold, another customer cannot take the seat until checkout finishes. A hold still lasts 10 minutes. A waitlist hold lasts 15 minutes. That difference is already locked.

The payment record, the mock results, and an unknown payment come from [Payment recorded before the attempt](02-payment-before-attempt.md). Do not reopen them. While a payment is pending, unknown, or succeeded and the order has no ticket, the seats stay with that customer. The hold's 10 minutes cannot release them. Unknown finishes only when the status lookup returns success or decline.

Decide:

- The action that starts checkout.
- The longest checkout can last before a payment row exists, and how that time relates to the hold's 10 minutes.
- What happens to the seat when the customer never finishes checkout and no payment row exists.

Use the domain-modeling skill. Checkout is not in the glossary yet. Write the term into `CONTEXT.md` if this ticket resolves it.

## Answer

Krishna accepted checkout as the submit. The glossary term is [checkout](../../../CONTEXT.md). An unknown payment stays on [Payment recorded before the attempt](02-payment-before-attempt.md): the seats stay until the status lookup returns success or decline.

**Start.** Checkout starts when the customer submits that order for payment. That same request inserts the payment row before the mock call.

**Before a payment row exists.** There is no separate timer. If the customer never submits, the hold ends on its own clock, 10 minutes, or 15 for a waitlist hold, and the seats become free. Checkout cannot start after that clock has run out. If the request dies before the payment row commits, no one was charged, and the same hold clock still releases the seats.
