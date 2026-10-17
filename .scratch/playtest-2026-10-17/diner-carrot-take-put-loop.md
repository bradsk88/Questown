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
