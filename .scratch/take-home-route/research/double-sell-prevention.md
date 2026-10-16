# Double-sell prevention

Read against PostgreSQL 18, the version served by the documentation at the time of this note (`/docs/current/` resolves to 18). Every claim below is from those pages. This note does not prescribe application code. It says what the design note can commit to, which of the usual mechanisms do not actually carry the invariant, how the chosen mechanism behaves in the three required cases, and the failure it still does not cover.

## Decision

Commit to one mechanism, in two parts that do different jobs:

1. A **unique partial index** on the columns that identify the seat for that event, covering the row both while it is a hold and while it is a sale, and not covering a hold that has been released.
2. A **row lock** on that row for the checkout that turns the hold into a sale, and for the write that releases an expired hold. The lock is `SELECT … FOR UPDATE`, or the `UPDATE` / `DELETE` itself, which locks the row. The checkout runs at **Read Committed**, which is PostgreSQL’s default, because that is the level at which a waiter rechecks the row it was blocked on.

The hold and the sale are the same row. The sale is an update of the hold, not a second insert after payment. The ten minutes are a timestamp on that committed row. They are not a lock held open for ten minutes.

The index is what still rejects a second active row after either transaction commits or the checkout crashes. The row lock is what stops a second checkout from updating that same row onto a different buyer, and what stops an expiry write from releasing the row while checkout is in flight.

## What the index is

A unique constraint makes the listed columns unique among all rows of that table. Adding one creates a unique B-tree index, and that index is what enforces it. A restriction that covers only some rows cannot be written as a unique constraint; it has to be a unique partial index. ([5.5.3. Unique Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html), [11.6. Unique Indexes](https://www.postgresql.org/docs/18/indexes-unique.html))

A partial unique index enforces uniqueness only among rows that satisfy its predicate. The documentation’s own example is one successful row per subject and target, with any number of unsuccessful rows left unconstrained. ([11.8. Partial Indexes](https://www.postgresql.org/docs/18/indexes-partial.html), Example 11.3)

That is the shape of this invariant. The predicate includes the hold and the sale. A released hold does not satisfy it, so the expired row can stay in the table and a later hold can exist. A sold row keeps satisfying it, so the seat cannot be inserted again. Uniqueness is only the columns in the index, so those columns have to be the seat for that event; a seat column alone would treat two events as one seat, and an event column alone would treat two seats as one. ([11.6. Unique Indexes](https://www.postgresql.org/docs/18/indexes-unique.html), [5.5.3. Unique Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html))

Nulls are not equal in a unique index unless `NULLS NOT DISTINCT` is set, so duplicate rows are allowed when a constrained column is null. The indexed columns have to be `NOT NULL`. ([5.5.3. Unique Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html))

The index is the immediate kind. A deferrable unique constraint inserts first and postpones the check; its preliminary probe is allowed to report possible duplicates without waiting, and the real check happens later. Non-deferrable uniqueness is checked immediately. A partial unique index is not a deferrable table constraint. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html), [SET CONSTRAINTS](https://www.postgresql.org/docs/18/sql-set-constraints.html))

A `CHECK` expression cannot carry this invariant. PostgreSQL does not support check constraints that look at other rows, and it tells you to use `UNIQUE`, `EXCLUDE`, or `FOREIGN KEY` for cross-row restrictions. ([5.5.1. Check Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html))

## What the row lock adds

The unique index does not stop an update that keeps the same key. Because of MVCC, an update deletes the old row version and inserts a new one, and an update that does not change the indexed key is explicitly allowed. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html))

Two checkouts that both `UPDATE` the same hold onto a buyer are that case. The second statement blocks on the row lock taken by the first `UPDATE`, `DELETE`, or `SELECT … FOR UPDATE`. At Read Committed, when the first transaction commits, the waiter applies its command to the updated row and re-evaluates its `WHERE`. If that condition requires the row to still be this checkout’s hold, it no longer matches a row that has become a sale, and the second write changes nothing. ([13.2.1. Read Committed Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), [13.3.2. Row-Level Locks](https://www.postgresql.org/docs/18/explicit-locking.html))

`FOR UPDATE` locks the rows the `SELECT` actually returned. Other transactions’ `UPDATE`, `DELETE`, and locking `SELECT`s of those rows wait until this transaction ends. An ordinary `UPDATE` that does not change a key column takes `FOR NO KEY UPDATE`, which conflicts with another such update and with `FOR UPDATE`. The lock is released at the end of the transaction. ([13.3.2. Row-Level Locks](https://www.postgresql.org/docs/18/explicit-locking.html))

`SELECT … FOR UPDATE` does not by itself keep the row sold. It blocks conflicting commands only until the locking transaction ends. After that, a blocked transaction continues unless the row was actually updated while the lock was held. The sale has to be that update. A later transaction can still update the row; the lock is gone. ([13.4.2. Enforcing Consistency with Explicit Blocking Locks](https://www.postgresql.org/docs/18/applevel-consistency.html))

Inserts of a second row are the index’s job, not the row lock’s. `INSERT` into a table with no unique index is not blocked by concurrent activity. A table with a unique index can block when another session locks or modifies a row with the same key; the wait is the uniqueness check. ([INSERT](https://www.postgresql.org/docs/18/sql-insert.html), [63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html))

Do not use `ON CONFLICT DO UPDATE` to assign the seat. That clause updates the existing conflicting row. It is an allowed way to change the row the unique index is protecting. `ON CONFLICT DO NOTHING` only reports that the index already has the key. Exclusion constraints cannot be the arbiter of `ON CONFLICT DO UPDATE`. ([INSERT](https://www.postgresql.org/docs/18/sql-insert.html), ON CONFLICT Clause)

The ten-minute hold must already be a committed row. Holding a transaction open across user input is a documented way to stall every waiter and to invite deadlocks. The row lock belongs only around the checkout decision and the expiry write. ([13.3.4. Deadlocks](https://www.postgresql.org/docs/18/explicit-locking.html))

## The three cases

### Two concurrent checkouts of the same seat

If both try to insert an active row, the unique index allows only one of those keys in any snapshot. The second inserter waits if the first transaction has not committed. If the first commits and leaves the key in place, the second is a uniqueness violation. If the first rolls back, the second proceeds. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html), [INSERT](https://www.postgresql.org/docs/18/sql-insert.html))

If both try to update one existing hold, the key does not change, so the index will not reject the second write. The row lock orders them. The second waits, then its `WHERE` is rechecked against the sold row and must not match. One sale is committed. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html), [13.2.1. Read Committed Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html))

An unlocked `SELECT` is not that decision. At Read Committed each statement sees its own snapshot, and two statements in one transaction can see different data. A business rule checked on an unlocked read can be stale before the write. ([13.2.1. Read Committed Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), [13.4. Data Consistency Checks at the Application Level](https://www.postgresql.org/docs/18/applevel-consistency.html))

At Repeatable Read or Serializable, a waiter that finds the row changed since its snapshot began is aborted with a serialization failure instead of rechecking `WHERE`. That also fails the second checkout rather than committing a second sale. The design note should describe the Read Committed recheck, because that is the default, and should treat a serialization failure as a failed checkout rather than as a retry that ignores the sold row. ([13.2.2. Repeatable Read Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), [13.5. Serialization Failure Handling](https://www.postgresql.org/docs/18/mvcc-serialization-failure-handling.html))

These waits happen inside PostgreSQL between real sessions. A test that never opens two sessions against this database does not exercise them.

### A hold expiring while another checkout is in flight

`now()` and `CURRENT_TIMESTAMP` return the start time of the current transaction and do not move for the rest of it. A checkout transaction that started before the deadline still sees a time before the deadline on every later statement. A concurrent expiry transaction has its own start time. The clock does not order them. ([9.9.5. Current Date/Time](https://www.postgresql.org/docs/18/functions-datetime.html))

The checkout locks the hold row before it decides. The expiry `UPDATE` or `DELETE` of that same row waits until the checkout transaction ends. ([13.3.2. Row-Level Locks](https://www.postgresql.org/docs/18/explicit-locking.html))

If checkout commits the sale, the row still satisfies the partial index, so the key is still taken. The expiry command, once unblocked, is rechecked against that new version. Its `WHERE` has to require a hold. A condition that names only `expires_at` still matches a sold row that kept the old timestamp, and Read Committed will apply the expiry write to that updated row. ([11.8. Partial Indexes](https://www.postgresql.org/docs/18/indexes-partial.html), [13.2.1. Read Committed Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html))

A second checkout inserting another active row, while this row is still a hold or already a sale, conflicts with the unique index. An update deletes the old row version before inserting the new one. If that old version is the key still in the index and the expiry transaction has not committed, the inserter waits for that transaction to end and then checks again. A committed expiry leaves the row outside the predicate, so the index no longer contains it. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html), [11.8. Partial Indexes](https://www.postgresql.org/docs/18/indexes-partial.html))

### A crash after payment succeeds, before the sale is recorded

The hold was committed before payment. A completed transaction is written to permanent storage before success is reported, and that record survives a later crash. `COMMIT` is the point at which the changes become durable. The default `synchronous_commit` of `on` waits for the local WAL flush before reporting success. The hold table has to be an ordinary logged table; an unlogged table is not crash-safe. ([3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html), [COMMIT](https://www.postgresql.org/docs/18/sql-commit.html), [19.5.1. Settings](https://www.postgresql.org/docs/18/runtime-config-wal.html), [28.3. Write-Ahead Logging (WAL)](https://www.postgresql.org/docs/18/wal-intro.html), [14.5. Non-Durable Settings](https://www.postgresql.org/docs/18/non-durability.html))

The sale update had not committed. If a failure prevents a transaction from completing, none of its steps affect the database. The row lock is released at the end of the transaction. What remains is the committed hold, still in the unique partial index, so another active row for that seat is still a duplicate key. ([3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html), [13.3.2. Row-Level Locks](https://www.postgresql.org/docs/18/explicit-locking.html), [11.8. Partial Indexes](https://www.postgresql.org/docs/18/indexes-partial.html))

After a crash, recovery replays WAL from the latest checkpoint, and a committed transaction is already in that log. The sale does not have to be reconstructed from the crashed session: the hold is the committed row that occupies the key. ([28.3. Write-Ahead Logging (WAL)](https://www.postgresql.org/docs/18/wal-intro.html), [28.5. WAL Configuration](https://www.postgresql.org/docs/18/wal-configuration.html), [3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html))

## Mechanisms that do not provide this invariant

### Isolation level, including Serializable

Read Committed is the default. At that level a serialization anomaly is possible, and a second updater of the same row will write the new version when its `WHERE` still matches. Repeatable Read is snapshot isolation. The documentation says attempts to enforce business rules at that level are not likely to work without explicit locks. ([13.2.1. Read Committed Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), [13.2.2. Repeatable Read Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), Table 13.1 on [13.2. Transaction Isolation](https://www.postgresql.org/docs/18/transaction-iso.html))

Serializable makes the committed serializable transactions equivalent to some serial order, or it aborts one of them. That guarantee holds only when every write, and every read that needs a consistent view, uses Serializable. Any other isolation level can subvert it. The protection does not extend to hot standby or logical replicas. Predicate locks do not block, they depend on the plan chosen for the query, and they can be coarsened to a whole relation when memory is short. The application must retry the whole transaction on SQLSTATE `40001`. PostgreSQL will not retry it. Data read in the transaction is not valid until that transaction commits. ([13.2.3. Serializable Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), [13.4.1. Enforcing Consistency with Serializable Transactions](https://www.postgresql.org/docs/18/applevel-consistency.html), [13.5. Serialization Failure Handling](https://www.postgresql.org/docs/18/mvcc-serialization-failure-handling.html))

None of that declares “one active row for this seat.” A transaction that is wrong when run alone is still wrong when run serially. An incomplete sale transaction still affects nothing after a failure. Serializable sessions can also raise unique violations even after they checked that the key was absent. The index remains the constraint that defines the key. ([13.2.3. Serializable Isolation Level](https://www.postgresql.org/docs/18/transaction-iso.html), [3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html))

### `SELECT … FOR UPDATE` alone

The lock covers returned rows, and only until the transaction ends. It does not leave a durable “sold” fact. After commit, a later transaction can update the row. A crash ends the transaction, so the lock is gone with it, and an uncommitted sale update is discarded. Two inserts do not lock one shared row merely because each ran a locking read that returned nothing: the lock is on rows retrieved. The commands it is documented to block are `UPDATE`, `DELETE`, and the locking forms of `SELECT`. ([13.3.2. Row-Level Locks](https://www.postgresql.org/docs/18/explicit-locking.html), [13.4.2. Enforcing Consistency with Explicit Blocking Locks](https://www.postgresql.org/docs/18/applevel-consistency.html), [3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html))

### A unique constraint that does not include the hold

A unique index on a sale row that is inserted only after payment does not occupy the key during the crash in case 3, because that insert never committed. A unique constraint on every row, including released holds, also forbids a later hold if the old row is kept. The partial index is what makes the hold and the sale share one key and lets a released hold stop occupying it. ([5.5.3. Unique Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html), [11.8. Partial Indexes](https://www.postgresql.org/docs/18/indexes-partial.html), [3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html))

A unique index alone still does not stop two updates of that one row. That is why the row lock is part of the same mechanism. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html))

### Exclusion constraints, including a ten-minute range

If every operator in an exclusion constraint is equality, the result is equivalent to a unique constraint, and an ordinary unique constraint is faster. There is little point in using a B-tree exclusion constraint for that. In practice an exclusion constraint is a GiST or SP-GiST index. ([CREATE TABLE](https://www.postgresql.org/docs/18/sql-createtable.html), `EXCLUDE`; [5.5.6. Exclusion Constraints](https://www.postgresql.org/docs/18/ddl-constraints.html))

`UNIQUE (… WITHOUT OVERLAPS)` is not that equality constraint. It is enforced as an exclusion constraint: the other columns may be duplicated as long as the range column does not overlap. A hold stored as a range that ends after ten minutes therefore stops conflicting when that range ends, including with a checkout that has not committed a sale and with a crash that never wrote one. The invariant we need is equality of one active seat, not non-overlapping time ranges. The documented concurrency rule for “wait for the other transaction, then recheck” is written for unique indexes, not for exclusion indexes. ([CREATE TABLE](https://www.postgresql.org/docs/18/sql-createtable.html), `UNIQUE` / `WITHOUT OVERLAPS`; [63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html))

### Advisory locks

Advisory locks are not enforced. The database does not require anyone to take them. Transaction-level ones are released at the end of the transaction. Session-level ones last until unlock or session end, and they survive a rollback of the transaction that took them. They are cleaned up at the end of the session. They are not a row, so a crash that prevents the sale transaction from completing leaves no sale and no lock. Another session that never takes the lock can sell the seat. ([13.3.5. Advisory Locks](https://www.postgresql.org/docs/18/explicit-locking.html), [3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html))

## The failure it does not cover

A payment that succeeds outside PostgreSQL is not a step of the database transaction. Atomicity covers the transaction’s own updates: a failure cancels those, and a commit keeps those. It does not record, and it does not undo, a charge the database never stored. ([3.4. Transactions](https://www.postgresql.org/docs/18/tutorial-transactions.html))

Across the crash, the seat is still held, so it is not sold twice. The hole is the next step. Nothing in the committed data says a payment succeeded. When the hold is later released because its ten minutes have passed, the row leaves the partial index, and a different checkout can commit a sale of that seat. The first payment is still captured, and PostgreSQL will not stop the second sale on account of it.

The same limit applies to any later `UPDATE` that keeps the indexed key and changes the buyer, or that moves a sold row out of the partial predicate. The index forbids a second active row. It does not freeze the columns of the one row it already has. ([63.5. Index Uniqueness Checks](https://www.postgresql.org/docs/18/index-unique-checks.html), [11.8. Partial Indexes](https://www.postgresql.org/docs/18/indexes-partial.html), [13.4.2. Enforcing Consistency with Explicit Blocking Locks](https://www.postgresql.org/docs/18/applevel-consistency.html))

One configuration footgun belongs next to that hole. With `synchronous_commit` off, a commit the client was already told had succeeded can disappear in a crash, and the database looks as if that transaction had aborted. The durability used above is the default, `on`. ([19.5.1. Settings](https://www.postgresql.org/docs/18/runtime-config-wal.html))

## Claims that are not in the PostgreSQL docs

- No page read for this note says, in those words, that crash recovery rolls back a transaction that never committed. The crash case above uses the transaction chapter’s rule that a failure preventing completion leaves none of the steps in effect, the `COMMIT` page’s rule that durability applies once the transaction has committed, and the WAL chapters’ description of redoing logged committed work.
- No page read here states that an exclusion-constraint insert waits on an uncommitted conflicting row the way a unique index does. That wait is cited only for unique indexes.
- Payment is not a PostgreSQL feature. The docs do not mention a payment provider, and they do not say a captured charge survives a crash.
