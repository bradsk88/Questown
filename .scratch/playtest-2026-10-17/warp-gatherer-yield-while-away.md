---
title: 'Catch-up warp: base gatherer produced ~29 items in one morning away'
status: needs-triage
origin: 2026-10-17 playtest round 5 — user chat 18:22:23 (dev-server-8, build 3ed88de2)
---

"Does the base level gather produce a full six items of loot? I'm surprised they filed up an entire
chest while I was away overnight. They shoudln't have been producing overnight"

## Facts (dev-server-8 log)

- The warp only simulates daytime (`TownFlagState.daytimeTicks`, dayTime 0–12000 of each day).
  Flag stopped ticking ~18:08 (evening, ~61000); resumed 18:20:51 at ~80296 (dayTime 8296), so
  the warp simulated ~8300 ticks (dawn → noon). No night work.
- At 18:20:51: 6× `No loot table found for jobs/gatherer_notool_full/minecraft/forest` (the
  full-day table; `gatherer/gather_full_day` has `attempts: 6`, `time: 6000`), and **29**
  `[LOOTTRACE] extract` lines, each 1 item: Feather 5, Wheat Seeds 4, Sugar Cane 4, Brown
  Mushroom 4, Apple 4, Red Mushroom 3, Egg 3, Stick 2.
- Tiers: `gather` 2 attempts / 2000 t; `gather_half_day` 4 / 4000 t; `gather_full_day` 6 / 6000 t.

## Open question

8300 daytime ticks fit at most ~1–2 full-day trips (6–12 items), not 29 — unless the warp runs
several trips or several tiers. Check `PostDowntimeWarper` / `advanceTime` trip count for a
`time`-gated leaver job vs realtime (a warp-parity scenario for `gatherer/gather_full_day`), and
whether a fresh day-2 gatherer should be on the full-day tier at all (design: base level).
