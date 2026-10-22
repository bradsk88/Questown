---
title: 'seeking_work' leaver's workable-block scan is empty even though the job board is registered and has requests
status: ready-for-agent
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 (`/tmp/dev-server-7.log`):

- 19:20:55: > Gatherer was sitting around doing nothing.. I tried adding a
  wheat seeds request. Waiting to see if they wake up.
- 19:21:43: > Their status says they're looking for a job board. but there is
  one, its registered, and it has requests...

The user also noted (19:20:55) that the wheat-seeds request did NOT wake the
gatherer — correct, requests only pull in the job whose product matches; a
gatherer's products are tree loot, not seeds.

## Evidence chain (from `/tmp/dev-server-7.log`, single town, flag @ 77,69,-77)

- Villager `2dc23236` worked fine on `gatherer:gather` through 19:06:50
  (BOP deposit). The gate room/mat was visible at 19:02:06: the WHY dump for
  `gatherer:gather` shows `job_blocks={… 74, 69, -87=true}`, `rooms={[75,-87]}`,
  ~50 state-0 spots, status COLLECTING_SUPPLIES.
- From 19:07:44 for ~15 min the villager cycled
  `seeking_work → downtime → dining` every ~50s, never re-claiming a concrete
  work. Every `[WHY] job=gatherer:seeking_work` dump:
  `current=NO_JOBSITE`, and the decisive gate
  "town has workable blocks for ANY WorkState{Stage: 0,
  RoomsWithWorkableBlocks=[RoomsContainWorkState=false because no spots
  found]}" with **`spots={} rooms={} job_blocks={}`** — an entirely empty
  scan.
- Meanwhile the town was healthy: 6 rooms registered, store_room @ [90,-71],
  kitchen_small quest completed 19:11:34, top-need batch posted 19:14:12.
- At 19:22:55 the villager re-acquired `gatherer:gather` and immediately
  progressed COLLECTING_SUPPLIES → GOING_TO_JOB → WAITING_FOR_TIMED_STATE →
  EXTRACTING_PRODUCT (19:24:48) — the world had a workable job site all
  along; only the SEEKING_WORK computation saw an empty set.

## Root-cause candidate (verified against the code, not yet proven in the room layer)

`WorkSeekerJob` (jobs/declarative) declares its WorkLocation on the
`SpecialQuests.JOB_BOARD` room, and its status override checks
`town.getPossibleWork().getFor(getId()).isEmpty()`. The empty `rooms={}` in
the dump means the seeker's room scan found NO rooms at all — not even the
gate room that `gather` saw minutes earlier — so the failing layer is the
room-set computation for the seeker job (room population / recipe match for
the JOB_BOARD room, or the stage-0 state check vs the board's
JOBS_AVAILABLE state-1 block state at `ServerJobsRegistry:601`), not the
status decision itself (`JobStatusesTest` covers the decision mapping).

## Acceptance criteria

- A leaver in `seeking_work` in a town whose board is registered and has
  requests finds the board room and claims a board job (no 15-minute
  seeking_work/downtime loop).
- The `[WHY]` dump for a seeker with a registered board shows a populated
  `rooms=`/`job_blocks=`, or names the concrete failing predicate.

## Notes

Instrument the room-population layer next (`roomsWithWorkableStatefulBlocks`
/ the seeker's WorkLocation predicate), per the questown-status-why loop.
Also note the adjacent world fact: at load (19:01:56) the basics-batch
reward was refused — "Refusing to apply reward more than once:
MCRewardList{…AddBasicsBatch[job_board+store_room]}" — so the board in this
world was NOT placed by that reward (it came from the crafting tab); make
sure the board-room registration path is identical for both origins.

## Audit (2026-10-22)

No fix commit for the empty seeker scan (`git log --grep seeking` shows board-at-rest 8a628fc7 and fallbacks, not the room-set layer). Needs a repro: a held run with a gatherer cycling seeking_work while a registered board has requests; compare the seeker's `rooms={}` with the board room.
