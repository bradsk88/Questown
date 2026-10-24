---
title: 'village/growth: the first hunter never fetches a sword (stalled at roster 1)'
status: needs-triage
origin: 2026-10-24 growth run 23:00 (log: growth-hunter-sword-fetch-stall-2026-10-24.log.gz)
---

The run timed out at 240000 ticks at roster 1. The only open quest was the onboarding raw-food
LOOT quest (`questown:villager_simple_furnace_food_raw`), which only the hunter's own extraction
closes.

- The hunter (a8e3f094) entered `hunter:sword` 32 times. Every `[WHY] job=hunter:sword` transition
  was `-> COLLECTING_SUPPLIES` (105 of 105). No trip started, and there were no `[LOOTTRACE] extract`
  lines.
- The storeroom (container at x 4, z 11) held 4 stone swords and cooked cod throughout (the supply
  check names the sword tool and those items).
- Each attempt ended `hunter:sword -> downtime`, between eating loops
  (`dining_no_table -> dining_raw_food`).
- The `[WHY]` dump lists one workable spot, at (1, 64, 0) beside the flag; stages 1 and 2 had none.

The same beat passed in the run before it (22:19, same code apart from the declined-upgrade fix,
which never ran here: no discards) and in the overnight runs. So it is intermittent. Cause not
established: the next read is the hunter's path to the chest (whether it reaches x 4, z 11) and
why the fetch gives up.
