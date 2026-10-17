# Handoff: GitHub tracker and closed maps

Date: Friday 16 October 2026, evening ET.
Repo: `main` on origin `https://github.com/KrishnaPatel1552/ticket-booking` (private).

## Where to start

Both wayfinder maps are closed. Do not reopen them to restate decisions. Read the spec route first.

- Map: [Booking spec for Monday](https://github.com/KrishnaPatel1552/ticket-booking/issues/2)
- Earlier route, also closed: [Ticket booking take-home route](https://github.com/KrishnaPatel1552/ticket-booking/issues/1)
- Destination files: `.scratch/booking-spec/prd.md` and `.scratch/booking-spec/spec.md`
- Glossary: `CONTEXT.md`
- Tracker rules: `AGENTS.md` and `docs/agents/`

Local `.scratch/*/map.md` files are pointers. The markdown under `.scratch/*/issues/` is an archive. GitHub is the issue tracker.

No application code has been written. The build was out of scope of both maps. Confirm with Krishna before starting it.

## What this session did

- Installed all 38 skills from `mattpocock/skills` globally for Cursor at `~/.agents/skills/`.
- Ran setup. Issue tracker is GitHub. Triage labels are the five defaults. Domain docs are single-context (`GLOSSARY.md` and `docs/adr/` at the root, created lazily).
- Created the private GitHub repo and pushed `main`.
- Published both local maps as closed parent issues. Child tickets are closed. The question is the issue body. The answer is the resolution comment. Blocking edges are GitHub issue dependencies.
- The seat-map decision had no local ticket. It is [Whether the seat map updates by itself](https://github.com/KrishnaPatel1552/ticket-booking/issues/17).

## Left out of git

The resume PDF and the two Gmail PDFs are gitignored (`/*.pdf`) and were not pushed. They remain in the working tree.

`wontfix` already existed on the repo. The other triage labels and the `wayfinder:*` labels were created.

## Suggested skills

- `ask-matt` — route the next session before picking a flow.
- `implement` and `tdd` — only if Krishna starts the Monday build. Drive tests at the seams in the spec.
- `domain-modeling` — keep `CONTEXT.md` as the glossary if a term changes.
- `grilling` — use if a product question appears. Do not reopen a closed map ticket to answer it.
- `handoff` — write the next handoff to `handoffs/`, not only the temp directory.
