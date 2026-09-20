---
title: Villagers must not produce loot during night / sleep time-warp
status: needs-triage
closed: 2026-09-20
created: 2026-09-06
---

## Context

Observed in a held village/growth run (2026-09-06): every time the player
slept at the campfire (a time warp to morning), the flag broadcast
"While you were away (1 day): 2 apple, 6 birch log, 2 feather, ..." — the
gatherer produced a full batch of loot overnight. The driving agent started
using sleep as a *production shortcut* (`time set <night>` + campfire sleep
whenever the town needed items), which works but is wrong: villagers should
be sleeping at night, not working.

Expected behavior (per the dev): assuming the player is in town, all
production happens during the day. A sleep/time-warp must not yield
production.

The warp-production path is `PostDowntimeWarper` (jobs "catch up" on warp
ticks) and the loot summary is `TownFlagState.sendWarpSummary` (deltas of
`snapshotContainerItems` across the warp, broadcast when
`timeSinceWake >= 12000`). The summary text "While you were away" suggests
the original intent was player-absence catch-up, but it fires for an
in-town player who merely slept.

## Acceptance criteria

- An autotest (e.g. `village/no_night_production` or a `perf/`-style
  scenario) that: sets up a town with a working gatherer, keeps the player
  in town, sleeps through the night, and asserts container contents are
  unchanged by the warp (no new items).
- Same scenario with production observed during the following *day* as a
  control, so the test fails for the right reason (night production) and
  not because the job is broken.
- After the fix, "While you were away" either does not fire for an in-town
  sleep, or fires only for genuine player absence (define which in the
  fix).

## Notes

- Fixed 2026-09-20: `TownFlagState.tick` gates natural warps on wall-clock
  continuity (an in-town time jump, sleep or `/time set`, produces nothing)
  and warps absence only for its daytime ticks (`daytimeTicks`).
  `gatherer/axe [sleep_jump]` now asserts a non-positive delta and the warp
  category passes 4/4; verified live in a held run (no "While you were
  away" after sleep or `/time set`). Close after review.
- Repro from the held run: `time set 85000`, `tp Dev -2 64 1`,
  `_qtdev clickheld -3 64 0` (campfire sleep) → `SleepFinishedTimeEvent`
  → broadcast "While you were away (1 day): ..." with a full loot batch.
- The agent's take-away that sleep = free production is a design smell the
  test should make impossible.
- Related: `PostDowntimeWarperNoOpGuardTest` already exists for a warp
  no-op guard; check it when placing the new test.
