---
title: 'BOP deposit stall + BOP duplication: foreign work-state record blocks the flag gate'
status: ready-for-agent
---

2026-10-14: FIXED `23afa0c5` (foreign-state normalization in `rooms()` + `findJobSite`, idempotent BOP hand-out; `DeclarativeJobsRoomsForeignStateTest`).

# BOP deposit stall + BOP duplication

Captured 2026-10-14 (server-19, fresh-world playtest, in-game chat):

- 04:17:58: "My villager isn't depositing their BOP"
- 04:18:18: "In fact, they are standing beside a chest and dupicating their BOP"

## Symptoms (server log, villager 5de9ec1b, gatherer)

1. Loop every ~10s: `seeking_work → bop_depositing`, status gate `NO_JOBSITE`
   then `NO_SPACE` — never deposits.
2. `[LOOTTRACE] tryGiveItem: item=questown:block_of_progress` fires on every
   loop iteration (34 BOPs granted 04:16–04:20) — the "duplication".

## Root cause (two defects, one chain)

**Defect 1 — soft-lock (gate reads a foreign work-state).** The flag block is a
shared work location with ONE per-owner state record, but different works number
states differently: `bop_depositing` uses 0–2, `DowntimeWork` uses 0–9 (both
located at `SpecialQuests.TOWN_FLAG`). At 04:13:05 the gatherer entered
downtime at the flag; at 04:13:55 the cycle was interrupted (→ seeking_work) and
the record was left at state **3**. `bop_depositing`'s gate
(`DeclarativeJobs.rooms` → `RoomsWithWorkableStatefulBlocks`) only matches spots
at state 0 or 1; it read the raw 3 → "no spots found" → NO_JOBSITE/NO_SPACE
forever. The supply-loop sibling
(`DeclarativeJobTicker.computeRoomsNeedingInput`) already normalizes
`processingState > maxState → State.fresh()`; the gate path and `findJobSite`
did not — the "sibling sites must agree" trap the code comment warns about.

**Defect 2 — duplication (non-idempotent BOP hand-out).** The latch
(`hasBlockOfProgress`) clears only at deposit (`ClearBOPSpecialRule`). With the
deposit stuck, `WorkSeekerJob` re-enters
`TownFlagBlockEntity.changeJobForVisitorFromBoard` on every flag visit; each
entry handed out a fresh BOP (`tryGiveItem` REMOVE_FROM_WORLD), violating the
documented "one townie holds at most one BOP" invariant
(`town/entity/COMPLEX.md` "Earn → latch → assign").

## Fix (implemented, full suite 1212/0)

- `DeclarativeJobs.rooms()` and `DeclarativeJobs.findJobSite`: a record with
  `processingState > maxState` is a foreign work's numbering — read as fresh,
  matching the supply-loop guard. Pinned by
  `DeclarativeJobsRoomsForeignStateTest` (red-verified: the foreign-state case
  fails on the pre-fix gate; the in-range and null cases pin the existing
  behaviour so the normalization can't over-fire).
- `changeJobForVisitorFromBoard`: hand out the BOP only when the townie is not
  already carrying one (scan the job inventory for `BLOCK_OF_PROGRESS`). The
  deposit routing is unchanged.

Note: the world already contains the duplicated BOPs in the stuck villager's
inventory + whatever was dropped; they are harmless economy (depositable on
later cycles / pickable), no cleanup required.
