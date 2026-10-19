# Handoff: Customer hold journey is specified, built, and checked

Date: Sunday 18 October 2026, afternoon ET.
Repo: `/Users/krishna/ticket-booking`. Origin: `https://github.com/KrishnaPatel1552/ticket-booking`.

## Where to start

Issue #51 is implemented and checked in the browser. Do not re-specify it or rebuild it. The next session should start from whatever the customer asks next. Closing #51, or opening a pull request for `2e04673`, only happens if they ask.

The morning file, [Sachin check, then a blank customer site](2026-10-18-session-handoff.md), is an earlier session. The spec request came from [Spec the hold journey before more screens](2026-10-17-session-handoff-2.md).

## What this session did

- Turned that 17 October handoff into GitHub issue [#51](https://github.com/KrishnaPatel1552/ticket-booking/issues/51), label `ready-for-agent`. The issue is the spec. Do not copy it here.
- The customer agreed the six screen decisions, the customer-site tests plus the existing hold HTTP tests, and two limit sentences. Other refusals stay "Those seats could not be held."
- They later accepted two behaviors the review had flagged: a mixed request that is over the limit and also names a taken or blocked seat uses the limit reason, and a generic refusal whose seat is no longer free can drop that seat from the selection.
- Implemented the conflict body and the sentences on `main` as `2e04673`. The Hold screen, Change seats, Release hold, Search events, and the map offer were already on HEAD. Frontend tests: 51 passed. `mvn test` passed.
- Walked the journey in the browser on a new local event, Hold Journey Oct18. Hold, Change seats, search and return, checkout of the existing hold, the order-limit sentence, Release hold, a taken seat with the generic sentence, and the event-limit sentence all matched #51. The 11 tickets used for the event-limit check were created through the API, then the sentence was read on the page.
- A reload of the site returns to sign-in. Sign-in is memory on the page. After signing in again and opening the event, the hold was still on the server and the map offer came back. That is not a change requested for #51.
- `npx tsc -p frontend/tsconfig.app.json --noEmit` fails only in untracked `frontend/src/prototype/styles/StylePrototype.tsx`, which imports three variant files that are not there. Tracked `frontend/src/App.tsx` and `frontend/src/api.ts` are not in that error list. Do not point `frontend/src/main.tsx` at the prototype. The morning handoff records that this blanks the site.

## Git state

- Branch `main`, ahead of `origin/main` by 1. HEAD `2e04673`. Not pushed.
- #51 is still open.
- Untracked, and not part of that commit: `frontend/src/prototype/`, `PRODUCT.md`, `MISSION.md`, `RESOURCES.md`, `TEACHING.md`, `assets/`, `.agents/`, `lessons/`, `reference/`, `scripts/repro-held-seat-paths.mjs`, `skills-lock.json`, and the handoff files that are not on `main`, including this file.
- Nothing was listening on 8080 or 5173 when this handoff was written. The local database still has the Hold Journey Oct18 event from the browser check. Seed accounts are in `src/main/java/ticketbooking/starter/StarterData.java`. Do not copy passwords into a new note.

## Suggested skills

- `pr` — `~/.agents/skills/pr/SKILL.md`. Call it only if the customer asks for a pull request of `2e04673`.
- `brainstorming` — `~/.agents/skills/brainstorming/SKILL.md`. Call it before any new behavior, including a sign-in that survives reload. Do not treat that reload as part of #51.
- `code-review` — `~/.agents/skills/code-review/SKILL.md`. Already run on the uncommitted limit diff before `2e04673`. Call it again only for a later change.
- Do not use `diagnosing-bugs` to re-open Change seats. That reload is already on `main`.
