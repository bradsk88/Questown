# Bug: `F2_chest_spawn_after_campfire` — sunset-chest flag bit never set

## Status: Resolved 2026-05-30 — autotest-verified 2026-05-30

Verify before beta cut: `./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=chicken` — `F2_chest_spawn_after_campfire` must report `[PASS] flag bit chicken-sunset-chest-spawned: expected=true actual=true`. In-game: enter the SUNSET_AND_MAP beat (light the campfire with the bound wand); the helper chicken should walk a few blocks, peck, and spawn a chest with a filled map + wooden axe.

## Symptom

Clean-world full autotest run produced:

```
[autotest] chicken-arc:F2_chest_spawn_after_campfire [PASS] final beat state: expected=SUNSET_AND_MAP actual=SUNSET_AND_MAP
[autotest] chicken-arc:F2_chest_spawn_after_campfire [FAIL] flag bit chicken-sunset-chest-spawned: expected=true actual=false
[autotest] chicken-arc:F2_chest_spawn_after_campfire [PASS] chicken spawned: live count=1 (expected >= 1)
```

The arc advanced to `SUNSET_AND_MAP`, the chicken was alive, but the chest spawn step never completed.

## Root Cause

Two compounding defects in `HelperChickenSunsetChestGoal`, both about the chicken never reaching its peck target:

1. **Chest target inherited the player's Y, not the ground.** `pickChestPos` built candidate cells at `playerBase.offset(dx, 0, dz)` — i.e. the player's feet Y. When the player stands one block above the chicken's ground (in the autotest the fake player is teleported onto the flag block; in-game, on stairs / a block / mid-jump), the target sat at y=65 while the chicken walked the floor at y=64. The chest also would have spawned floating, since `findChestPlacementPos` checked only replaceability, not a solid floor below.

2. **Arrival threshold was tighter than the pathfinder's stopping accuracy.** The path follower (`createPath(target, 1)`, accuracy 1) stops roughly a block-and-a-half short of the target centre — measured `distSqr ≈ 2.4` in the flat arena — but `ARRIVAL_DISTANCE_SQR` was `2.0`. So `distSqr` never dropped to `≤ 2.0`, `peckAndSpawn()` never fired, and `tickPathing` re-pathed to the same unreachable centre every tick. The stuck-teleport fallback needs 600 ticks; the scenario only runs 200.

(The earlier suspicion of a wander-goal vs sunset-goal priority conflict from commit `85aca1b7` was wrong — `HelperChickenSunsetChestGoal` is priority 2 and correctly preempts the priority-3 wander/follow goals. The only change that commit made to the sunset goal was the map-filling, unrelated to this stall.)

## Fix

In `HelperChickenSunsetChestGoal`:

- `pickChestPos` now places candidate cells on the chicken's ground Y (`chickenAt.getY()`), keeping the player's X/Z as the horizontal centre. The chest lands on a floor the chicken can reach and stand on.
- New `hasArrived(distSqr, navDone)` (pure, unit-tested): arrival is `distSqr <= ARRIVAL_DISTANCE_SQR` OR (`navDone && distSqr <= NAV_SETTLE_ARRIVAL_SQR`, 9.0). Once the pathfinder can get no closer, the settled distance counts as arrival; the looser cap still rejects a fully-failed path that left the chicken far away (the stuck-teleport handles that).

## Tests

- `HelperChickenSunsetChestGoalTest.pickChestPos_picksAtChickenGroundY_notPlayerY_regressionForSunsetStall`
- `HelperChickenSunsetChestGoalTest.hasArrived_pathfinderSettledShort_isTrue_regressionForSunsetStall` (+ tight-radius / still-moving / nav-done-but-far cases)
- End-to-end: autotest `F2_chest_spawn_after_campfire`.

## Related

- `docs/bugs/eating-dine-at-time-fullness.md` — the other bar-C blocker from the same clean-world run (still open).
