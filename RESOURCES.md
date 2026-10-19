# Interview resources

## Knowledge

- [Booking state machine (closed spec)](.scratch/booking-spec/issues/06-booking-state-machine.md)
  The moves Sachin said the interview will ask about. Use for: any question about what a seat is, and which move is allowed.
- [`NOTES.md`](NOTES.md)
  The decisions the code carries, the ambiguities, and the cuts. Use for: "why did you do it this way?"
- [`CONTEXT.md`](CONTEXT.md)
  The words to use. Use for: keeping hold, block, checkout, and ticket distinct.
- [PostgreSQL: Partial Indexes](https://www.postgresql.org/docs/current/indexes-partial.html)
  Official description of a unique index that covers only the rows matching a predicate. Example 11.3 is the same shape as `one_active_event_seat`. Use for: why a released hold does not block a later hold.
- [PostgreSQL: Explicit Locking](https://www.postgresql.org/docs/current/explicit-locking.html)
  `SELECT … FOR UPDATE` and how row locks interact with transactions. Use for: the customer-event lock around the 6 and 12 caps.
- [Spring Framework: Transaction management](https://docs.spring.io/spring-framework/reference/data-access/transaction.html)
  Where a transaction starts and ends. Use for: why the payment row is committed before the mock runs.

## Wisdom (Communities)

- [Ministry of Testing — The Club](https://club.ministryoftesting.com/)
  Moderated testing community. Use for: saying a design out loud to people who hire and interview testers, including the thread on how a useful SDET interview checks a claimed strength and then an uncertain area.
- [A framework for "how would you test X"](https://club.ministryoftesting.com/t/a-framework-for-how-would-you-test-x-questions/87575)
  A short community thread on ordering a test answer: scope, happy path, boundaries, failures, then what you would run first. Use for: if he asks how you would test a change.

## Gaps

- The local PDF “Copart SDET Internship Manager Interview Questions” was read on 18 Oct 2026. It flags five topics he asked to defend, then lists predicted questions. Lessons `0003` through `0008` follow that split. The closed tickets and the code still win if a predicted question and the code disagree.
- No high-trust public source describes this specific Copart interview. Do not invent questions from generic SDET lists and present them as his.
