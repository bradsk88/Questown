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

## Investigation (2026-10-24, from the saved log; static read, nothing run)

- **Not a supply-match failure.** 315 of the run's `[WHY]` supply checks on the chest at (4, 11)
  matched the sword predicate (`Pair[a=...Ingredient2Predicate...]`); the 684 `No match` ones are
  checks for other needs. The passing overnight run shows the same mix (426 no-match, 78 match).
  So the hunter knew the chest held its tool and never took it: the take needs it to arrive.
- **The chest was in a south-row building.** `VillageGrowthExecutor.buildOneBuilding`: corner
  (3, 64, 10), walls x 3-7 / z 10-14, door in the south wall at (5, 64, 14), chest at the interior
  north-west corner (4, 64, 11). South-row doors face away from the flag; in the passing run the
  storeroom chest was in the north row (-8, -15), whose south doors face the flag.
- **The walk target should be (4, 64, 12).** `TownContainers` -> `InteractionSpots.find`: door
  side first; `doorDirectionFromCenter` gives SOUTH (back X wall z 10 < door z 14); (4, 12) is a
  standable interior cell; `ProductionJob.navTargetForContainer` pins y to the room floor.
- **Doors are openable.** `VisitorMobEntity.registerGoals` adds `OpenDoorGoal`, and
  `VillagerPathNavigation` sets `setCanOpenDoors(true)` (the `TestBlueprintRegistry:2435` comment
  "Townies cannot open wooden doors" is stale on that point).
- **The positions don't fit "door impassable".** The hunter mostly stood at (8, 64, 15) (150 of
  the observes), about 5 blocks from (4, 12). The closest reachable cells outside, if the
  doorway were blocked, are (4, 64, 9) and (5, 64, 15), about 3 blocks. So either the walk target
  is not (4, 12) (the container's `room` may be another registered room), or the path gives up
  short for another reason.

## Next

Instrument the `[STALL:collecting]` line (`CollectingStallTrace`) with the villager's position,
its walk target / nav target, the path's end node and `isDone`, and the supply target's room
(door and walls). Then repro: the stall needs the storeroom chest in a south-row building; check
how the driver picks that building and pin it there, so the repro does not wait on chance.
Related: `.scratch/playtest-2026-10-21/hunters-stuck-collecting-after-sleep.md` (stuck in
COLLECTING_SUPPLIES with swords in the chest; a different trigger, unreproduced).
