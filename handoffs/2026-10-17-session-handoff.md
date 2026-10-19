# Handoff: Stitch customer site route

Date: Saturday 17 October 2026, afternoon ET.
Repo: `/Users/krishna/ticket-booking`. Origin: `https://github.com/KrishnaPatel1552/ticket-booking`.

## Where to start

The route is decided and sliced. Start with [Sign in](https://github.com/KrishnaPatel1552/ticket-booking/issues/40). It is the only ticket with nothing blocking it.

Read the decision instead of reconstructing it: [What the Stitch screens may change](https://github.com/KrishnaPatel1552/ticket-booking/issues/39). The visual source is the Stitch export at `/Users/krishna/Downloads/stitch_harbor_hall_ticket_booking`, including `harbor_hall_venue_system/DESIGN.md`. That folder is outside the repo.

Language is `CONTEXT.md`. Behavior is `NOTES.md`. `PRODUCT.md` is the written personality. The decision set that personality aside for this rebuild: the screens use the harbor look.

## What this session did

- Closed the discarded map [Customer site redesign](https://github.com/KrishnaPatel1552/ticket-booking/issues/33), and with it [Event page prototype](https://github.com/KrishnaPatel1552/ticket-booking/issues/36) and [Rebuild the customer site](https://github.com/KrishnaPatel1552/ticket-booking/issues/37). Do not continue them.
- Charted [Customer site from the Stitch screens](https://github.com/KrishnaPatel1552/ticket-booking/issues/38), resolved its only ticket, and closed the map. Nothing on it is open.
- [Keyboard pattern for the seat map](https://github.com/KrishnaPatel1552/ticket-booking/issues/34) is reused by the seat-map ticket. [What the order rail shows](https://github.com/KrishnaPatel1552/ticket-booking/issues/35) stays closed and is not the layout.
- No spec issue was published. The agreed test seam is the customer site, driven the way a customer uses it, with the existing API client stubbed. The Java API tests stay as they are.
- Published seven ready-for-agent tickets. Blocking edges are native GitHub dependencies.

The frontier and the chain:

1. [Sign in](https://github.com/KrishnaPatel1552/ticket-booking/issues/40) — start here.
2. [Search events](https://github.com/KrishnaPatel1552/ticket-booking/issues/41) — blocked by Sign in.
3. [Hold seats from the seat map](https://github.com/KrishnaPatel1552/ticket-booking/issues/42) — blocked by Search events.
4. [Review the order](https://github.com/KrishnaPatel1552/ticket-booking/issues/43) — blocked by Hold seats from the seat map.
5. [Check out](https://github.com/KrishnaPatel1552/ticket-booking/issues/44) — blocked by Review the order.
6. [Cancel tickets](https://github.com/KrishnaPatel1552/ticket-booking/issues/45) — blocked by Check out.
7. [Join the waitlist and take an offered hold](https://github.com/KrishnaPatel1552/ticket-booking/issues/46) — blocked by Hold seats from the seat map.

No customer-site code was changed. The API was not changed.

## Git state

- Branch `ticket-booking`, HEAD `8838352`, one commit ahead of `origin/ticket-booking`. That commit was not made in this session, and it was not pushed.
- Untracked at the time of this handoff: `PRODUCT.md`, `frontend/src/prototype/`, `handoffs/2026-10-16-session-handoff-3.md`, and this file. None of them were committed.

## Suggested skills

- `tdd` — [Sign in](https://github.com/KrishnaPatel1552/ticket-booking/issues/40) asks for a customer-site test with the API client stubbed. Read `~/.agents/skills/tdd/SKILL.md` and write that test before the screen.
- Do not call wayfinder, to-spec, or to-tickets to restate this route. The map is closed and the tickets are published.
