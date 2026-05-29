# Bug: `F2_chest_spawn_after_campfire` — sunset-chest flag bit never set

## Status: Open (discovered 2026-05-29)

## Symptom

Clean-world full autotest run produced:

```
[autotest] chicken-arc:F2_chest_spawn_after_campfire [PASS] final beat state: expected=SUNSET_AND_MAP actual=SUNSET_AND_MAP
[autotest] chicken-arc:F2_chest_spawn_after_campfire [FAIL] flag bit chicken-sunset-chest-spawned: expected=true actual=false
[autotest] chicken-arc:F2_chest_spawn_after_campfire [PASS] chicken spawned: live count=1 (expected >= 1)
```

The arc advances to `SUNSET_AND_MAP` (correct), the helper chicken is alive (correct), but the persistent flag bit `chicken-sunset-chest-spawned` is never flipped to `true`. The chest spawn step never completes.

Reproduces in **chicken-only** isolation AND in the full clean-world run — so it is not pollution from other tests. Real regression.

## Reproduction

```sh
# Chicken-only (fastest)
./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=chicken

# Full suite
./gradlew runServer -Dquestown.autotest=true
```

## Likely Cause

Recent commit `85aca1b7 feat(chicken): SUNSET beat night-phase + sleep observation; wander; predator immunity` touched the sunset-arc goals. Suspect interaction between the new `HelperChickenWanderNearFlagGoal` and the existing `HelperChickenSunsetChestGoal`, OR a regression in the `readFlag` null-handling change inside `HelperChickenFollowNearFlagGoal`.

## First Places to Look

1. **`HelperChickenSunsetChestGoal.java:74-82`** — `canUse()` gate, especially the `findNearestPlayerForFlag()` call. If this returns null in the test environment, the goal never starts.
2. **`HelperChickenSunsetChestGoal.java:209` and `:245`** — these are the two call sites that set the flag bit (`persistChestSpawned()`). If we never reach `spawnChest()`, the bit stays false.
3. **`HelperChickenFollowNearFlagGoal.java:115-125`** — `readFlag()` now returns `null` (was `ChickenBeatState.FORFEIT`); confirm `isArcActive` handles null correctly for the SUNSET_AND_MAP phase-1 window.
4. **Goal-selector priority interaction.** `HelperChickenSunsetChestGoal` is priority 2; `FollowNearFlagGoal` + `WanderNearFlagGoal` are priority 3. Priority 2 should always preempt 3, but verify `canUse` isn't false-negative for SunsetChestGoal during phase 1.

## Suggested Diagnostic

Add temporary `QT.JOB_LOGGER.info` to `HelperChickenSunsetChestGoal.canUse()` and `spawnChest()` to confirm which gate fails, then re-run the chicken-only autotest.

## Impact

Bar-C blocker for the onboarding sunset branch. Players in the sunset-arc scenario never get their starter chest, soft-locking the onboarding flow.

## Related

- `docs/bugs/eating-dine-at-time-fullness.md` — the other bar-C blocker from the same clean-world run.
- Commit `85aca1b7` — prime suspect for the regression.
