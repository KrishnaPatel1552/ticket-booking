# Double-sell prevention for the design note

Type: research
Status: resolved
Blocked by:

## Question

What mechanism should the one-page design note commit to so that one seat cannot be sold twice, given a hold that lasts 10 minutes and tests that must run against a real PostgreSQL database, including concurrent attempts on the same seat?

The note needs the mechanism and why it holds under two concurrent checkouts, a hold expiring while another checkout is in flight, and a crash after payment succeeds but before the sale is recorded. It does not need application code.

Primary sources only: PostgreSQL documentation for the isolation level, row lock, unique or exclusion constraint, and advisory lock we would actually rely on. Record the choice, the failure it does not cover, and the citation for each claim.

Write the findings to `.scratch/take-home-route/research/double-sell-prevention.md`. Do not check out another branch in this working tree.

## Answer

The design note should commit to one mechanism: a unique partial index on the seat for that event, covering the same row while it is a hold and while it is a sale, plus a row lock (`SELECT … FOR UPDATE`, or the `UPDATE` that locks the row) around checkout and around the write that releases an expired hold. Checkout uses Read Committed, the PostgreSQL default, so a transaction that waits on that lock rechecks the row it was blocked on. The ten minutes are a timestamp on the committed hold, not a lock held open for ten minutes. The sale is an update of that hold, so the key stays occupied across the transition.

Two concurrent checkouts cannot both leave an active row. A second insert waits on the unique index and then fails if the first row committed. A second update of the same hold does not change the key, so the index would allow it; the row lock orders the two updates, and the loser’s `WHERE` no longer matches a row that has become a sale. A hold expiring during checkout is the same lock: expiry waits, then its `WHERE` is rechecked and must still require a hold, while the sold row remains in the partial index. `now()` does not move inside a transaction, so the clock is not what orders those two sessions.

A crash after payment and before the sale is recorded drops the uncommitted sale update and its row lock. The hold was already committed, on a logged table, with `synchronous_commit` left at its default, so it is still the unique active row. The seat is not sold twice.

That does not cover a payment PostgreSQL never stored. Once the hold is released because its ten minutes have passed, the row leaves the index and another checkout can commit a sale. The index also does not freeze the buyer on the one row it already has; a later update that keeps the same key can change that row. Findings: `.scratch/take-home-route/research/double-sell-prevention.md`.
