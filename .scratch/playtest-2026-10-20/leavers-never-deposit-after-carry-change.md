---
title: 'Hunters/miners never return their loot since the carry-output change (e425b8e5)'
status: resolved
origin: 2026-10-20 playtest — user: "my miner and hunter were stuck working and not returning"
---

## Facts

- Playtest logs: before e425b8e5 (dev-server-15) the hunter cycles hunt → seeking_work/dining/
  downtime. After it (dev-server-16..18) hunters and the miner only go `sword -> sword` /
  `coal -> coal` every 2–4 min.
- Realtime hunter autotest (loot_on_extract_accumulate) with probes: the hunt finishes holding the
  meat (`[PROBE:wrapup] hunter:sword status=EXTRACTING_PRODUCT holds=true`), the carry-on branch
  keeps it in the job, and it never deposits (0 walked drops, 0 board deposits).

## Cause

e425b8e5: a finished cycle holding output carries on (wrappingUp=false) so the job's own status
walks the output to storage. For the cook the next status is more extraction, then
DROPPING_LOOT — works. For a leaver the next status is a new trip: in `JobStatuses` the
"use items" branch (has work items: the sword/pickaxe) comes before "drop loot from non-full
hands before starting more work", so it never drops. Before e425b8e5 the handoff's board deposit
(`depositCarriedWorkOutput`, an instant move to the nearest chest) emptied its hands.

## Options (design call)

1. Carry on only when the next step is more extraction at the same job site (the cook); otherwise
   hand off as before (leavers' loot goes back to the instant board deposit). Small, safe.
2. Make "drop loot from non-full hands before starting more work" outrank "use items" in the
   status tree, so every job walks its output home before starting a new cycle. Matches the
   ruling ("carrying output by hand is how it should work") but touches every job's ordering —
   needs the full suite + growth.

## Resolved (2026-10-20)

Dev chose option 2. JobStatuses: "drop loot before starting a new cycle" now outranks "use items". Realtime hunter: 5 walked drops, 0 board deposits. See jobs/COMPLEX.md.
