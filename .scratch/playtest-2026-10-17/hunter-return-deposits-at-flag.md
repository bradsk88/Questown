---
title: 'Hungry hunter ate its raw catch although the chest held non-raw food (dining fallback after failing to reach the chest)'
status: fixed
origin: 2026-10-17 playtest round 3 — user chat 06:05:25
---

"One thing I notice is that they brought home both loot and a BOP, and it seems like
deposited EVERYTHING via the flag's position. When they SHOULD have deposited their
non-BOP items by walking to the chest."

## Log (run/logs/debug.log, villager 9d507196)

- Job → `hunter:bop_depositing` → `Villager removed questown:block_of_progress from
  their inventory [stone_sword, feather, mutton, block_of_progress, air, air]` →
  `Flag now contains 4 BOPs`. Only the BOP is removed at the flag per the log.
- Then `hunter:dining_no_table` → `hunter:dining_raw_food` — the raw mutton is a
  dining candidate, so it may have been EATEN (not deposited).
- 06:05:48 `[WHY] job=hunter:dining_raw_food IDLE → DROPPING_LOOT` — the first loot
  drop, from the dining job; no log line names the container it dropped into.

## Needs (observer)

Where did the feather end up — in a chest, in the flag, or still on the villager?
That settles whether this is a deposit-route bug or the dining job eating the mutton
and dropping the rest from wherever it stands.

## Resolution (user, 06:0x)

"It did show up in the chest." The deposit route is correct: the BOP went to the
flag (`hunter:bop_depositing`), the rest to a chest via DROPPING_LOOT. The missing
mutton was eaten: a hungry villager with no cooked food falls back to raw food
(`CONTEXT.md` "Dining", the `dining_raw_food` variant, mood `ATE_RAW_FOOD −10`).
Not a deposit bug. Open design question if the player finds "the hunter eats the
raw-food quest's catch" surprising — not raised yet.

## Reopened (user, 06:08:21)

"There was non-raw food in the chest. So, no. They should not have eaten their raw loot."

Log chain (run/logs/debug.log): `hunter:dining_no_table` sat in COLLECTING_SUPPLIES with
`Located supplies at Position{x=105, z=-60}` repeating many times a second but no take →
06:05:48 `Reached max ticks for hunter:dining_no_table. Falling back to
hunter:dining_raw_food` → raw eating. So the raw meal is the fallback firing because the
villager could not take food from the chest at (105,-60) — the same chest-access failure
as the hunting COLLECTING_SUPPLIES hang (hunter-collecting-supplies-after-hunt.md) and
the bed/wall nav bug. On reload the villager stood at (105.6, 71, -59.9): the chest's
column, two blocks above the floor (69) — possibly on top of the chest/wall.

Fix order: chest access first (this is a symptom); then decide whether a failed
no-table dining should fall back to raw food while non-raw food sits in a reachable
container.

## Correction (06:07–06:08) — not chest access; the dining job's take

- 06:07:28 `dining_raw_food` max-ticks → `seeking_work`; 06:07:52 gave up → `hunter:sword`;
  06:07:53 `TOOK stone_sword` from the same chest on the first try. So the villager CAN
  reach and use the chest (105,-60): the standing-on-the-chest theory is refuted.
- `DinerNoTableWork` wants `#questown:villager_food` (as the eat-state tool and the
  consume ingredient); the chest held 11 carrot + 2 apple (`Villager is taking
  minecraft:carrot from …` dump). The no-table diner located the supplies continuously
  and never took any; raw dining took a red_mushroom; the hunt took its sword at once.
- **Lead:** the suite's deterministic known-reds `eating/dine_at_time` (fullness −16%)
  and `eating/eat_direct` (fullness −12%) — 3/3 full-suite runs — may be this same
  failure (diner sees food it never takes). Next step: run `eating/eat_direct` with the
  supplies probe and compare its take path to the hunter's tool take.

## Observation (user, 06:10:15) — revises the correction above

"They are standing on top of the chest. Currently they have the 'can't find my way
there' balloon." So the 06:07:53 sword take does NOT refute the standing position —
it shows the take works when the villager is beside the chest. The failure mode is the
villager ending up ON TOP of the supply chest: from there it is neither "at" the chest
for the take check nor able to path to a cell beside it (CANT_REACH). Both the dining
stall and the earlier hunting stall started from this chest; the reload position
(105.6, 71, -59.9) is on top of it. Next: read the supply walk-target selection (is the
target the chest's own cell/top?) and the take-distance check from feet one block above.

## Root cause + fix (2026-10-17)

Two compounding causes, both fixed (commit below):
1. The chest's walk target was inside the wall: `TownContainers` stepped one cell toward
   the door unchecked; for this store room that cell is the north wall (door (106,-61),
   chest (105,-60), only free interior cell (106,-60)). Now `InteractionSpots.find` with
   `WalkTargets.isStandable` picks (106,-60). `InteractionSpotsTest` pins the room.
2. Open doors were impassable (the 2026-10-15 negative door malus; vanilla treats
   malus < 0 as blocked), so routes went over walls / onto beds and chests. Reverted.
