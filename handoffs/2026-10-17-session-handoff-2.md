# Handoff: Spec the hold journey before more screens

Date: Saturday 17 October 2026, evening ET.
Repo: `/Users/krishna/ticket-booking`. Origin: `https://github.com/KrishnaPatel1552/ticket-booking`.

## Where to start

Do not implement. The next session builds a correct spec for the customer hold journey, then verifies that spec against Sachin's locked rules and against the walkthrough. A plan comes only after that spec is agreed.

The customer said the current design was not made properly. They want to hold seats, release a hold by hand, and, when seats are already held, either check out or return to search. Those three wishes are not in Sachin's reply. Treat them as proposals to specify, not as tickets to code.

## What this session did

- Read `handoffs/2026-10-17-walkthrough-notes.md`. On Harbor Lights, Hold seats works and the Hold screen has Check out. Change seats did not release the seats. Leaving that screen, or reloading, dropped the order from the page while the seats stayed held. There was no Check out for a hold already on the map, and no way back to search. A full book of 12 seats failed with "Those seats could not be held." and did not name the cap.
- Reproduced that on `http://127.0.0.1:5173` with `scripts/repro-held-seat-paths.mjs` (untracked). After Change seats the button said free while the server still said held. After a reload the button and the server both said held, and Check out, release, and search were absent.
- Compared that with Sachin's reply and the written spec. He locked a 10-minute hold, caps of 6 and 12, checkout as the submit of that order, decline releasing the seats, and a 15-minute waitlist offer. He did not lock a customer release, a Check out control for a hold already on the map, or a control back to search. An abandoned page is only a hold: `.scratch/booking-spec/prd.md` and `.scratch/booking-spec/issues/05-checkout-lifetime.md`.
- Pushed one fix that stays inside those rules. Change seats reloads the seat map, so a held seat stays held. Commit `1e5dfaf` on [PR #47](https://github.com/KrishnaPatel1552/ticket-booking/pull/47). Frontend tests: 44 passed. Live check: after Change seats the button and the server both said held, and the map had no Check out.
- Did not add a release, a way back to an existing hold, a way back to search, or a cap message that names 6 or 12.

## Spec to verify

Read these before writing a new rule:

- Sachin's reply, restated in `.scratch/booking-spec/issues/01-monday-build.md`, `05-checkout-lifetime.md`, and `06-booking-state-machine.md`. The PDFs are outside the repo: `Gmail- Manager's reply.pdf` and the 14 October take-home brief.
- `.scratch/booking-spec/spec.md` under Seat, Checkout and payment, Booking moves, Waitlist, and Seat map.
- `NOTES.md`, especially "No live-updating seat map" and "What was left out".
- Screen tickets that are not Sachin's words: GitHub #41 through #46. #43 says Change seats returns to the map so a new selection can be held. It does not say the old hold is released. #41 has no My Orders. #42 locked the sentence "Those seats could not be held."
- The walkthrough: `handoffs/2026-10-17-walkthrough-notes.md`.

Scenarios the spec must answer, including the ones Sachin left open:

| Scenario | What the written spec says now |
|---|---|
| Hold free seats | 10 minutes. Caps apply. The map reloads after the customer's own action. |
| Change seats | Return to the map. The hold remains. As of `1e5dfaf` the map shows those seats held. |
| Release a hold by hand | Not specified. A hold ends on its clock, on decline, or when a reschedule ends a bare hold. "Release" in the brief is an admin releasing a block. |
| Check out a hold that already exists | Checkout is the submit of that order, and it fails if the seat is no longer that customer's live hold. The order screen is memory on the page that just held. A waitlist offer is the exception: opening the event can restore "Held for you". |
| Leave for search and come back | Search is required. A control that leaves the event is not specified. Nothing sets `openEventId` back to null in `frontend/src/App.tsx`. |
| Reload | Not specified. The hold is server state. The order screen is not. |
| Expiry | 10 minutes, or 15 for a waitlist hold. Opening the event releases an expired hold with no payment row. There is no scheduler. |
| Decline | Releases the seats and writes no ticket. |
| Caps of 6 and 12 | The whole request is refused. The page does not say which cap. |
| Waitlist offer | Next customer gets a 15-minute hold. The map links to that order. A hold the customer made themselves has no such link. |

## Git state

- Branch `stitch-customer-site`, tracking `origin/stitch-customer-site`, HEAD `1e5dfaf`, even with the remote. [PR #47](https://github.com/KrishnaPatel1552/ticket-booking/pull/47) is open against `ticket-booking`.
- Untracked, and not part of that commit: `PRODUCT.md`, `frontend/src/prototype/`, `handoffs/2026-10-16-session-handoff-3.md`, `handoffs/2026-10-17-session-handoff.md`, `handoffs/2026-10-17-walkthrough-notes.md`, `scripts/` (includes the repro), `.agents/`, `skills-lock.json`, and this file.
- The customer site was on port 5173 and the API on 8080 during the repro. Seed accounts are in `src/main/java/ticketbooking/starter/StarterData.java`. Do not copy passwords into a new note.

## Suggested skills

- `brainstorming` — `~/.agents/skills/brainstorming/SKILL.md`. Use it before any new behavior. Present the design and get approval. Do not implement in that session.
- `grilling` — `~/.agents/skills/grilling/SKILL.md`. Use it to verify the spec. Walk the scenario table above until each open row has one answer.
- `domain-modeling` — `~/.agents/skills/domain-modeling/SKILL.md`, only if a new term has to be written down. `CONTEXT.md` already defines hold, order, checkout, and ticket.
- `writing-plans` — only after the spec is agreed. Not at the start of the session.
- Do not use `diagnosing-bugs` to re-open the Change seats bug. That reload is on `1e5dfaf`.
