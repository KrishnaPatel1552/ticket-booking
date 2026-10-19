# Notes

These notes record the design decisions, the five points from the 15 October reply, the ambiguities encountered and how the code resolves them, what was cut and why, what would come next, and the AI tools used. Domain terms match `CONTEXT.md`: customer, admin, venue, section, seat, event, hold, block, order, checkout, payment, ticket, waitlist entry, cancel, refund, event cancellation, reschedule, price tier, early-bird, group rate, promo code, service fee, override, and audit entry.

---

## Five Points from the 15 October Reply

### 1. Payment recorded before the attempt, and resolving a payment with no ticket

A charge that succeeded outside the database while the server crashed before writing the ticket must never leave a customer having paid with no ticket while another customer buys the seat.

- **Recording before the attempt:** `Checkout.submit` is not a single database transaction. First, `RecordPendingPayment.record` runs in its own `@Transactional` method: it verifies every submitted seat is still that customer's live hold, locks any attached promo code, computes and stores the four whole-cent shares on each `event_seat` row, closes the order to new holds (`open_for_holds = false`), and inserts and flushes a `payment` row with status `PENDING` before returning. Only after that transaction commits does `Checkout` call `paymentGateway.pay`.
- **Applying the result:** `FinishPayment.apply` runs in a second transaction. `SUCCESS` sets the payment to `SUCCEEDED` and promotes the order's holds to `TICKET`s. `DECLINE` sets the payment to `DECLINED`, deactivates the holds (`active = false`), and offers freed seats to the waitlist if the event is sold out. `TIMEOUT` and `UNKNOWN` set the payment to `UNKNOWN` and keep the seats held.
- **Why the seat cannot be taken after a crash:** If the process crashes after the gateway charges the customer and before `FinishPayment` commits, the `PENDING` payment row and the active `HOLD` rows are already committed in PostgreSQL. `ExpiredHoldRelease` (`EventSeatRepository.lockExpiredHolds`) skips any hold whose order has a non-declined (`PENDING`, `UNKNOWN`, or `SUCCEEDED`) payment row, so the 10-minute expiry never gives the seat away.
- **Finding and resolving a payment with no ticket:** `PaymentRepository.findNeedingReconcile` finds any payment on the order that is `PENDING`, `UNKNOWN`, or `SUCCEEDED` while the order still has holds instead of tickets. Opening the order with a status lookup (`GET /api/orders/{id}?lookup=true`) or calling `POST /api/orders/{id}/reconcile` runs `ReconcilePayment`, which queries the gateway status lookup without charging again:
  - Lookup `SUCCESS` sets the payment to `SUCCEEDED` and writes the `TICKET` rows.
  - Lookup `DECLINE` sets the payment to `DECLINED` and releases the seats.
  - Lookup `TIMEOUT` or `UNKNOWN` leaves the payment `UNKNOWN` and keeps the seats held.

### 2. Checkout in progress: start, duration, and unknown outcomes

- **What starts checkout:** Checkout starts when the customer submits the held order (`POST /api/orders/{id}/checkout`) and `RecordPendingPayment.record` commits the `PENDING` payment row. An open browser screen by itself is only a hold; if the customer abandons the page before submitting, no payment row exists and the hold expires on its 10-minute clock (15 minutes for a waitlist hold). Checkout cannot start after `held_until` has passed (`!hold.getHeldUntil().isAfter(now)` fails with `409 Conflict` and writes no payment row).
- **How long checkout can last:** The 10-minute (or 15-minute) clock only governs how long the customer has to start checkout. Once the `PENDING` payment row is committed, there is no second timer and no maximum duration while the payment is `PENDING` or `UNKNOWN`. Expiring a seat while a charge is unresolved would risk selling the seat twice if the charge later settles as succeeded.
- **What happens if checkout never finishes or returns unknown:** The seat remains `HELD` for that order until a status lookup (via opening the order with lookup or calling reconcile) resolves the payment to `SUCCEEDED` (moving the seat to `TICKET`) or `DECLINED` (releasing the seat back to `FREE` or offering the waitlist).

### 3. Caps of 6 and 12 under concurrency

A customer is limited to **6 tickets per order** and **12 tickets per customer per event**, counting both active holds and active tickets. Cancelling tickets sets `active = false` on those rows and frees those cap spots once the refund succeeds.

- **Why locking seat rows is not enough:** Two concurrent requests from the same customer for *different* seats (for example, holding `A1` in one request and `A2` in another when the customer already has 11 seats at the event) lock disjoint `seat` rows. Under `READ COMMITTED`, both would count 11 active seats and both would insert, exceeding the 12-seat cap.
- **How the code serializes same-customer requests:** Before counting active holds and tickets, `HoldSeats` calls `lockCustomerEvent(customerId, eventId)`, which executes:
  1. `INSERT INTO customer_event (customer_id, event_id) VALUES (?, ?) ON CONFLICT DO NOTHING`
  2. `SELECT customer_id FROM customer_event WHERE customer_id = ? AND event_id = ? FOR UPDATE`
- The first request acquires the row lock on `customer_event`, checks the remaining capacity (`min(6 - onOrder, 12 - atEvent)`), locks the physical `seat` rows in ascending `seat_id` order, and inserts the holds. The second request blocks on `SELECT ... FOR UPDATE`, wakes up after the first commits, sees the updated count in its fresh `READ COMMITTED` snapshot, and fails with `409 Conflict` (`order-limit` or `event-limit`), writing no holds. A request that asks for more seats than the remaining allowance writes zero holds rather than a partial subset.

### 4. Booking and refund state machines

#### Booking (seat) state machine

A seat at an event is in one of four states: **`FREE`**, **`HELD`**, **`BLOCKED`**, or **`TICKET`**. (`PENDING` and `UNKNOWN` are payment statuses, not seat states; while a payment is unresolved, the seat remains `HELD`.)

- **`FREE` -> `HELD`:** A customer holds free seats (`held_until = now + 10m`) when the 6 and 12 caps allow it, or the next eligible waitlist entry receives an automatic hold (`held_until = now + 15m`) when a seat frees on a sold-out event.
- **`FREE` -> `BLOCKED`:** An admin blocks a free seat with a non-blank reason, inserting an active `seat_block` row and an `audit_entry`.
- **`BLOCKED` -> `FREE` (or `HELD` via waitlist):** An admin releases an active block with a reason. If the event still happens and has a waiting entry that fits the caps, the freed seat is immediately offered as a 15-minute `HELD` seat; otherwise it is `FREE`.
- **Hold vs. block collision:** A hold and a block never interrupt each other. Both `HoldSeats` and `BlockSeat` lock the target `seat` rows in ascending id order (`SeatRowLock.lockInIdOrder`). Whichever transaction locks the seat first sees no competing claim and commits; the second sees the active hold or block and fails with `409 Conflict`.
- **`HELD` -> `HELD` (checkout started / payment unknown):** Committing a `PENDING` payment or receiving `TIMEOUT` / `UNKNOWN` from the payment mock keeps the seat `HELD` past its 10-minute clock until lookup resolves the payment.
- **`HELD` -> `FREE` (or `HELD` via waitlist):**
  - The 10-minute (or 15-minute) hold clock runs out while no non-declined payment row exists, and an entry point (`SeatMapQuery`, `HoldSeats`, `BlockSeat`, `JoinWaitlist`, or `SalesOccupancy`) runs `ExpiredHoldRelease`.
  - The customer explicitly releases their own bare hold (`POST /api/orders/{id}/release`).
  - The payment finishes as `DECLINED` (directly or via lookup).
  - An admin reschedules the event, which ends bare holds that have no payment row.
  - Whenever a hold ends on a live event with a waitlist, `OfferWaitlist` offers the freed seat to the next eligible customer in line.
- **`HELD` -> `TICKET`:** Payment succeeds (directly from the mock or via a later status lookup). The existing `event_seat` row is updated in place from `kind = 'HOLD'` to `kind = 'TICKET'` with `held_until = null`.
- **`TICKET` -> `FREE` (or `HELD` via waitlist):** A customer cancel refund or reschedule refund reaches `SUCCEEDED`.
- **`TICKET` -> `BLOCKED` (off sale):** An admin cancels the event (`cancelled_at` is set) and the event-cancellation refund succeeds. Unsold seats on a cancelled event are shown as `BLOCKED` and cannot be held.

#### Refund state machine

A `refund` row has four states: **`PENDING`**, **`UNKNOWN`**, **`SUCCEEDED`**, or **`FAILED`**, and a kind: `CUSTOMER_CANCEL`, `EVENT_CANCELLATION`, or `EXCEPTIONAL`. Like payments, every refund commits a `PENDING` row in its own transaction before calling `refundGateway.refund`.

- **`PENDING` -> `SUCCEEDED`:** The refund mock returns `SUCCESS`.
- **`PENDING` -> `FAILED`:** The refund mock returns `DECLINE`.
- **`PENDING` -> `UNKNOWN`:** The refund mock returns `TIMEOUT` or `UNKNOWN`.
- **`UNKNOWN` -> `SUCCEEDED` | `FAILED` | `UNKNOWN`:** Unknown finishes only by status lookup (`GET /api/orders/{id}?lookup=true` or `POST /api/orders/{id}/refunds/reconcile`).
- **While `PENDING` or `UNKNOWN`:** The tickets remain active on the order (`active = true`, `kind = 'TICKET'`), the seat remains a `TICKET`, and no other customer can hold or buy the seat.
- **When `FAILED`:** The tickets remain on the order, the seat stays a `TICKET`, and the money is not returned. Retrying a cancel on those same tickets repairs the failed refund at the exact `amount_cents` already stored on the failed refund row rather than recomputing the time band at the later timestamp.
- **When a cancelled seat becomes available again (`SUCCEEDED`):**
  - **Customer cancel (`CUSTOMER_CANCEL`, including within the 14-day reschedule window):** Only when the refund reaches `SUCCEEDED` are the covered `event_seat` rows set to `active = false`. At that moment the seat becomes `FREE` (or is immediately offered as a 15-minute `HELD` seat to the next eligible waitlist entry).
  - **Event cancellation (`EVENT_CANCELLATION`):** When the refund reaches `SUCCEEDED`, the tickets are taken off the order (`active = false`), the waitlist is ended, and the seats stay off sale (`BLOCKED`).
  - **Exceptional refund (`EXCEPTIONAL`):** Returns the admin-specified cent amount and records an audit entry, while leaving the tickets active on the order; the seat stays a `TICKET`.

### 5. Time zones, daylight saving, and whole-cent shares

- **Time zones and daylight saving:** Each event stores `starts_at` (`TIMESTAMPTZ` / `Instant`) and the venue's IANA `time_zone`. Refund windows are measured as elapsed hours (`Duration.between(now, event.getStartsAt())`) between UTC instants rather than local calendar days:
  - **100% of ticket net:** at `>= 168` elapsed hours (`7 * 24` hours) before `starts_at`.
  - **50% of ticket net:** at `< 168` hours down to `>= 48` elapsed hours before `starts_at`. Half of an odd net rounds up in the customer's favor (`(net + 1) / 2`), so a 1001-cent net refunds 501 cents.
  - **0%:** under `48` elapsed hours before `starts_at`.
  - **Reschedule window:** `<= 336` elapsed hours (`14 * 24` hours) from `rescheduled_at` returns 100% of the ticket's net plus its service fee share, replacing the normal bands. After 336 elapsed hours, cancellations use the normal bands against the new `starts_at` and keep the fee.
  - Because the server compares instants in elapsed hours, a 25-hour local fall-back Sunday or a 23-hour spring-forward Sunday does not stretch or shrink the 168-hour, 48-hour, or 336-hour windows.
- **Price order and splitting whole cents across tickets:**
  - The starting price for each seat is the tier's `early_bird_price_cents` when `now` is inside `[early_bird_starts_at, early_bird_ends_at)` and that price is present; otherwise it is `price_cents`.
  - The group rate (`group_rate_amount_cents`, capped at the ticket subtotal) applies once when the order meets `group_rate_threshold`.
  - One promo code (`amount_cents`) then applies to the remaining ticket subtotal. A second promo code on the same order is rejected. An `exclusive` promo code uses the regular tier `price_cents`, sets the group discount to zero, and applies the promo amount.
  - The flat `service_fee_cents` is added to the order after discounts and is never reduced by discounts.
  - When `RecordPendingPayment` prepares the payment, `CentShares.split` divides the group discount (weighted by each seat's starting price), the promo discount (weighted by each seat's price after the group share), and the service fee (weighted equally across tickets) into whole cents. Each ticket first receives its integer floor share `(totalAmount * weight) / totalWeight`, and any remainder cents are assigned one cent at a time to the tickets in ascending `seat_id` / `event_seat` order (for example, a 100-cent fee across 3 tickets is stored as `34`, `33`, and `33`).
  - Each `event_seat` row stores `starting_price_cents`, `group_share_cents`, `promo_share_cents`, and `fee_share_cents`. Those shares sum to the exact `payment.amount_cents`. A partial cancellation later reads the stored shares on the cancelled ticket rows (`net = starting_price_cents - group_share_cents - promo_share_cents`) and never re-divides the order.

---

## Other Design Decisions

- **Data model and single active seat (`one_active_event_seat`):** Sections, seats, and price tiers belong permanently to the `venue`, so the same physical seats are reused across events. Rather than moving rows between separate hold and ticket tables, `event_seat` stores both (`kind` is `HOLD` or `TICKET`) with an `active` boolean. PostgreSQL enforces `CREATE UNIQUE INDEX one_active_event_seat ON event_seat (event_id, seat_id) WHERE active`. Two concurrent holds on the same seat for the same event cannot both commit; the loser fails on the unique index (or after waiting on `SeatRowLock`), while released holds and cancelled tickets stay in the table with `active = false` for history and sales reporting.
- **Promo code consumption and restoration:** Promo limits (`global_limit` and `per_customer_limit`) are checked under a `PESSIMISTIC_WRITE` lock on the `promo_code` row when `RecordPendingPayment` runs. `PaymentRepository.countTaken` and `countTakenByCustomer` count payments with status `PENDING`, `UNKNOWN`, or `SUCCEEDED` on events where `cancelled_at IS NULL`. A hold alone does not consume a promo code, and a `DECLINED` payment releases the taken use. A customer cancel, reschedule, or exceptional refund does not restore a promo use; an admin event cancellation sets `cancelled_at` on the event, which immediately restores those promo uses.
- **Waitlist ordering, cap checks, and expiry rotation:** A customer can join the waitlist only when the event has no free seats, is not cancelled, and the customer is not already at the 12-ticket event cap. Waitlist entries are offered in FIFO order (`id ASC`), skipping any entry whose customer cannot take the seat under the 12-ticket cap. When a 15-minute waitlist hold expires without payment, `OfferWaitlist` rotates past the customer whose offer just lapsed before wrapping around, so one unresponsive customer at the front of the line does not repeatedly capture the same freed seat. Fulfilling a waitlist hold at payment sets `ended_at` on that `waitlist_entry` (`V19__waitlist_reentry_index.sql`), allowing the customer to rejoin later if needed.
- **Test clock:** Production uses `SystemAppClock`. Under `@ActiveProfiles("test")`, `TestClockController` exposes `POST /api/test/clock` so HTTP integration tests can advance time across hold expiries, early-bird boundaries, refund windows, and daylight-saving transitions deterministically.

---

## Ambiguities and How the Code Resolved Them

- **Early-bird window boundaries:** The early-bird window is closed at the start and open at the end (`[early_bird_starts_at, early_bird_ends_at)`). At the exact start instant, the early-bird price applies; at the exact end instant, the regular price tier applies.
- **Exhausted promo code at checkout:** If an attached promo code has reached its global or per-customer limit by the time checkout locks the code row, checkout skips the promo code (and restores early-bird and group pricing if the skipped code was marked `exclusive`) rather than failing the payment.
- **Exceptional refund leaves tickets active:** An admin exceptional refund names an exact cent amount (up to the remaining unrefunded amount on the order) and a required reason. When it succeeds, the customer keeps their tickets and seats.
- **Event cancellation while a customer refund is in flight:** When an admin cancels an event, `EventCancellation` first reconciles any `PENDING` or `UNKNOWN` customer-cancel refund on that event. If that customer refund succeeds, the event cancellation refunds only the kept `fee_share_cents` for that ticket so the ticket net is never refunded twice; if the earlier customer refund fails, the event cancellation refunds the full ticket net plus the fee share.
- **Customer hold release with an unresolved payment:** If a customer asks to release a hold (`POST /api/orders/{id}/release`) while a payment for that order is `PENDING` or `UNKNOWN`, `ReleaseHold` runs the payment status lookup first. If the lookup reports `SUCCESS`, the hold becomes a `TICKET` and is not released; only if no payment succeeded does the release free the seats.

---

## Known Gaps (What Was Left Out, and Why)

The target was a 10–12 hour build where concurrency, state transitions, and cent math are solid and tested against real PostgreSQL. The following were left out deliberately:

- **No admin UI or setup screens for venues, sections, seats, events, price tiers, or promo codes:** Starter data seeds the venue (`Harbor Hall`), sections (`Orchestra` and `Balcony`), seats (`A1`–`A12`, `B1`–`B8`), price tiers, and initial event (`Harbor Lights`). Admin creation of events, price tiers, and promo codes, as well as seat blocks, block releases, event cancellations, reschedules, exceptional refunds, sales summaries, and audit reads, are implemented as authenticated `/api/admin/**` endpoints (with `scripts/admin.mjs` and `scripts/admin/*.json` for CLI execution), as permitted by the 15 October reply.
- **No visual charts for sales and occupancy:** `GET /api/admin/events/{eventId}/sales` returns the per-event summary as JSON (`freeSeats`, `heldSeats`, `blockedSeats`, `tickets`, `ticketsSold`, and `paidCents`). Refunds are not subtracted from `paidCents`, and `ticketsSold` counts seats that have reached `TICKET` for that event, including tickets later cancelled.
- **No push/polling live seat map:** The customer site loads the seat map when an event is opened and refreshes it after each customer action. It does not poll on an interval or open a WebSocket/SSE stream.
- **No background scheduler for hold expiry or payment/refund reconciliation:** Expired bare holds are released on demand when an event's seat map, hold, block, waitlist, or sales endpoint is called. Unresolved (`PENDING` / `UNKNOWN`) payments and refunds finish when the order is opened with a lookup or when the reconcile endpoint is called.
- **No real payment processor or tax calculation:** Payment and refund outcomes (`success`, `decline`, `timeout`, `unknown`) and subsequent lookup outcomes are caller-scripted so every failure mode can be driven by HTTP tests and from the UI.
- **Fixed-cent discounts only:** Group rates and promo codes are stored as whole-cent amounts rather than percentages, and at most one promo code can be attached to an order.

---

## What Would Come Next

1. **Background scheduler:** A scheduled worker to release expired holds proactively (so waitlist offers trigger even when nobody opens the event) and to poll the gateway lookup for `PENDING` / `UNKNOWN` payments and refunds with exponential backoff.
2. **Admin back-office UI:** Screens over the existing `/api/admin/**` endpoints for creating events, price tiers, and promo codes, managing seat blocks and event overrides, and viewing the sales/occupancy summary and audit trail.
3. **Live seat-map push updates:** Server-Sent Events or WebSockets pushing seat state transitions (`free`, `held`, `blocked`, `ticket`) while a customer has the seat map open.
4. **Orders list across events:** A dedicated customer bookings page listing orders across all events (currently, opening an event loads all of that customer's paid orders and any active hold for that event).
5. **Percentage discounts and tax:** Supporting percentage-based group/promo discounts using the same `CentShares` proportional floor-plus-lowest-seat-id remainder rule.

---

## AI Tools Used

- **Cursor (Coding Agent):**
  - **Step 1 preparation:** Used to help organize the clarifying questions, stated assumptions, scope cuts, and one-page design note for the 14 October email, and to break the 15 October reply into concrete specifications for the state machines, locking rules, and cent-splitting rules.
  - **Backend implementation (`src/main/java`, `src/main/resources/db/migration`):** Generated the Spring Boot controllers, domain use-case services (`HoldSeats`, `Checkout`, `RecordPendingPayment`, `FinishPayment`, `CancelTickets`, `RecordPendingRefund`, `FinishRefund`, `EventCancellation`, `RecordReschedule`, `IssueExceptionalRefund`, `OfferWaitlist`, `CentShares`, `RefundBand`, etc.), JPA entities/repositories, and Flyway SQL migrations (`V1` through `V19`).
  - **PostgreSQL integration tests (`src/test/java/ticketbooking`):** Generated the 11 HTTP test suites (146 `@Test` methods) exercising concurrency locks (`PausingHoldGate`, `PausingPaymentAttemptGate`, `PausingRefundAttemptGate`, `PausingPromoCodeLockGate`), double-sell prevention, cap boundaries, refund bands, DST transitions, waitlist rotation, and gateway reconciliation against `ticket_booking_test`.
  - **Customer frontend and CLI scripts (`frontend/`, `scripts/`):** Generated the React/TypeScript customer site, the 8 Vitest frontend test suites (63 tests), the admin CLI runner (`scripts/admin.mjs`), and drafted `README.md`, `CONTEXT.md`, and `NOTES.md`.
- **Google Stitch:** Used to generate the initial visual layout reference for the customer-facing Harbor Hall screens before wiring the React components to the API.
