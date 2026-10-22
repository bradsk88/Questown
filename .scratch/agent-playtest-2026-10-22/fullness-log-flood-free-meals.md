---
title: 'A diner that gives up on its meal eats every tick for free (the "Fullness changed from 4990 to 5000" flood)'
status: needs-triage
origin: 2026-10-22 agent playtest (worktree build/wt, 21-villager cascade world)
---

# What happened

`Fullness changed from 4990 to 5000` (SimpleVillagerHandle.fillHunger, logged only when the
value changes) flooded the log in bursts: 03:12:48-03:13:57 (500 lines), 04:15-04:17, 04:23
(185 in one minute). Traced on the 04:23 burst, gatherers 5f1c4d5b and f65982f3:

```
04:23:05.073 Job changed to gatherer:dining_no_table for 5f1c4d5b...
04:23:05.591 [PROBE:supplies] job=gatherer:dining_no_table TOOK cooked_mutton   (the only food taken)
04:23:16.646 gatherer gave up waiting for ingredients after 201 ticks (job: dining_no_table)
04:23:16 ... 04:23:58  "Effect has been applied to 5f1c...: uncomfortable_eating / are_raw_food"
                       ~18/s; 501 are_raw_food + 514 uncomfortable_eating in the window
                       (and 501 + 501 for f65982f3); each flag tick's hunger decrement
                       (5000 -> 4990) is refilled at once, hence the INFO line.
04:23:41.337 gatherer gave up waiting for different work after 501 ticks (dining_no_table)
04:23:41.337 Job changed to gatherer:dining_raw_food   (the loop carries on)
04:23:58.936 Job changed to gatherer:seeking_work      (loop ends)
```

No "No result items during extraction" debug line in the window, so it is not the normal extract.

# Why (code)

`JobLogic.tick`, the give-up branch (introduced in 0cf38a5c, "WIP: Introducing blacksmith"):

- `noSuppliesTicks > maxNoSupplyTicks` -> "gave up waiting for ingredients" ->
  `grabbingInsertedSupplies = true`; once `tryGrabbingInsertedSupplies()` succeeds ->
  `grabbedInsertedSupplies = true`.
- Then **every tick**: `worldBeforeTick.tryExtractWithNoItem(); worldBeforeTick.changeToNextJob();
  ticksWithoutNextJob++; return;` and the flag is never cleared on this job.
- `AbstractWorldInteraction.tryExtractWithNoItem` -> `postExtractHook(inputs, town, null)` runs
  the job's EXTRACTING_PRODUCT special rules. For the diners those are the meal:
  `DinerNoTableWork` HUNGER_FILL + APPLY_UNCOMFORTABLE_EATING; `DinerRawFoodWork`
  HUNGER_FILL_HALF + APPLY_UNCOMFORTABLE_EATING + APPLY_ATE_RAW_FOOD.
- `changeToNextJob` (board) doesn't move the townie (`Prepared for gatherer: []`, no work), so the
  branch repeats until the separate "gave up waiting for different work after 501 ticks" fallback
  swaps the job, which here was another diner job, so it went round again.

Effect: one cooked mutton bought ~40 s of meals every tick; a diner that gave up eats anyway
(free food), the mood effects are re-applied hundreds of times, and the log floods.

Not settled: why the no-table diner waited 201 ticks for ingredients while holding the mutton
(the insert never happened), and why are_raw_food shows during the dining_no_table window
(its rules don't list it).

# For the dev (judge)

Fix shape, any of: run `tryExtractWithNoItem` once on giving up (not every tick); skip the
extract hooks for a give-up that inserted nothing (no meal without food); and/or clear
`grabbedInsertedSupplies` once the hand-off has been asked for.
