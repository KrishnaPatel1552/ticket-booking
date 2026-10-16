# Copart SDET take-home — current state

Updated: Wednesday 14 October 2026, afternoon ET.

The route is the map [Ticket booking take-home route](.scratch/take-home-route/map.md). This file is the brief. Decisions live on that map's tickets, not here.

No code has been written. The brief says not to write code until after the design reply.

## What this is

Sachin Fegade (Engineering Manager QA, Copart) sent a take-home for the **SDET Internship**. It is the basis of a later interview. Scope is intentionally large and underspecified. How the work is approached matters as much as what gets built.

Stack they want: **Java (Spring Boot), React, PostgreSQL**. If that stack is a problem, say so in today's reply. Resume is strongest in Python, SQL, and data pipelines, with Java listed and a PostgreSQL/FastAPI project. Spring Boot and React are not shown as recent production work.

## Deadlines

| When | What |
| --- | --- |
| Today, Wed 14 Oct 2026, end of day | Email questions and design note (Step 1). No code yet. |
| Thu 15 Oct 2026 | Copart answers questions in one reply. |
| Mon 19 Oct 2026 | Code on GitHub, then email the link (Step 2). |
| After submission | ~45 min interview: explain design and trade-offs, then make a live change they choose. |

Effort target for the code: **10–12 hours**. A smaller scope done correctly beats a wide shallow build.

## Process note to resolve in the same email

The thread also has a **first-round Teams interview at 2:00 PM ET on Friday 16 October** (Java and Spring coding), scheduled before this take-home arrived. The take-home email says the interview will be scheduled on Monday after the code is in. Ask which conversation is the Friday call, and whether the take-home interview replaces or follows that round.

Private GitHub is fine. The invite target in the PDF is the placeholder `[GITHUB USERNAME]` — confirm the real handle before inviting.

## Product they asked for

A ticket booking system for live events (concerts, theatre, sports): a customer site and an admin back office.

Customers:

- Browse and search events
- See a venue seat map by section, with live availability and price
- Select seats, hold them temporarily, check out (mock payment is fine)
- View bookings, cancel fully or partially, and get refunds
- Join a waitlist for sold-out events

Admins:

- Manage venues, sections, seats, events, price tiers, and promo codes
- Block or release seats, cancel or reschedule an event, issue exceptional refunds
- See sales and occupancy reports and a full audit trail

Stated rules (explicitly not the full set):

- A seat is never sold twice. A hold lasts 10 minutes.
- An order is limited to 6 tickets. A customer is limited to 12 tickets per event.
- Price depends on tier, early-bird dates, and group size. Promo codes have their own limits.
- Refund amount depends on how close the event is. A cancelled event refunds everything.
- Every admin override needs a reason and is audited.

Gaps and collisions between rules are part of the exercise. Finding them is what the questions are for.

## Step 1 reply (due today)

Email, before any code:

1. Clarifying questions, ranked by how much each answer would change the design
2. Assumptions for anything they do not answer
3. A one-page design note: data model, booking and refund state machines, and how double-selling is prevented
4. What will be cut or deferred, and why

Question quality is graded. They care that the questions show where the rules collide.

## Step 2 submission (due Monday)

- GitHub link emailed to Sachin (private repo is fine)
- Real commit history; they read it
- Tests of the hard parts against a **real PostgreSQL** database, including **concurrent attempts on the same seat**. Mock-only tests do not count for those
- README with exact run steps
- `NOTES.md`: decisions, ambiguities and how they were resolved, known gaps, what would come next, and an honest list of AI tools used and where

AI use is allowed if it is disclosed and the work can be defended. Undisclosed use counts against the submission more than disclosed use would.

## What they will judge

- Questions and assumptions that show rule collisions
- Correctness under concurrency and failure
- Domain model and state machines
- Honest scoping and trade-offs
- Ability to explain the code and change it live

## Status

| Item | State |
| --- | --- |
| Brief read | Done |
| Resume read | Done |
| Clarifying questions drafted | Not started |
| Assumptions drafted | Not started |
| One-page design note | Not started |
| Scope cuts chosen | Not started |
| Email sent | Not sent |
| Code, tests, README, NOTES.md | Not started (blocked until after today's reply) |

## Next action

Draft the Step 1 email: ranked questions, assumptions, one-page design, and explicit cuts. Send it today. Do not start the Spring Boot / React repo until that reply is out.
