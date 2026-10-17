---
title: 'Hungry gatherer stands at the chest taking and putting back a carrot in a loop'
status: needs-triage
origin: 2026-10-17 playtest round 4 — user chat 06:26:55 (dev-server-6, build 2c9f5880)
---

"Gatherer is standing at chest inserting and removing a carrot constantly."

## Log (run/logs/debug.log, villager 90826535 / "9082...81b")

- `[PROBE:supplies]` take counts 06:2x: **898× gatherer:dining_no_table carrot**,
  317× gatherer:gather carrot, 133× hunter:dining_no_table carrot.
- The loop pair, same second: `Villager 9082...81b is putting minecraft:carrot in
  BlockPos{x=105, y=69, z=-60}` → `Villager is taking minecraft:carrot from …` →
  `[PROBE:supplies] job=gatherer:dining_no_table TOOK carrot`, then
  `[WHY] job=gatherer:dining_no_table COLLECTING_SUPPLIES → …`.
- Same chest as the chest-walk-target fix (105,-60); this run already had that fix
  (2c9f5880), so the take now succeeds — and the diner then deposits it straight back.

## Hypothesis (unverified)

`dining_no_table` takes the carrot as its eat-state TOOL (DinerNoTableWork:
TOOLS_REQUIRED_AT_STATES NEED_EAT = #villager_food), then a drop/deposit path
(DROPPING_LOOT or the "put item" container step) returns it because the job's next
state can't start (no jobsite? flag-side eat spot), so it re-collects. Check which
state transition puts it back — the earlier 309-carrot gatherer:gather run (05:45) is
the same signature. Possibly the same family as eating/eat_direct + dine_at_time known
reds.

## Code read (2026-10-17, continued) — not yet a diagnosis

- The put-back is `Jobs.tryDropLoot` (DROPPING_LOOT): it drops every UNLOCKED item from
  `getItemsForDrop()` (= the whole journal). Locks come only from the player's slot locks
  (`ProductionJob` container listener), and `ProductionJournal.hasAnyLootToDrop` = any
  unlocked non-empty item. So a job keeps its "tool" (the diner's carrot, food listed as
  the NEED_EAT tool in `DinerNoTableWork`) only if the status logic never puts it into
  DROPPING_LOOT while holding it. Next: capture the `[WHY]` sequence for one take→put
  cycle to find which transition sends a freshly-supplied diner to DROPPING_LOOT.
- Not reproduced by village/day1 (day1-3/day1-4 logs: ≤39 takes per job, cooked food
  available). The playtest loop (898×, raw carrots) needs its conditions: the narrow
  store room and/or carrot-only food. A day1 variant seeding only carrots would test the
  second.
