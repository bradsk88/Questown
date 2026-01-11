# Warp Debugging Session Notes

## Problem
Time warp is not producing items for crafter villagers (sticks/bowls) even though ingredients are available.

## Root Causes Identified and Fixed

### 1. Day/Night Time Check Rejecting Jobs During Warp
- **Issue**: `TownPossibleWork.getWorkPercentPossible()` and `TownFlagBlockEntity.getRandomFinishableWork()` were using actual server time to check if jobs can fit in the day
- **Fix**: Removed `canFit()` check from `TownPossibleWork` (line 182), and `PostDowntimeWarper` now uses a virtual morning time (`VIRTUAL_MORNING_TICK = 1000`)

### 2. Non-Downtime Villagers Getting 0 Warp Ticks
- **Issue**: When a villager's current job (e.g., `crafter/planks`) couldn't be completed, `ImportantTicks.forVillager()` returned 0 ticks
- **Fix**: Added fallback to dynamic resolution in `ImportantTicks.forVillager()` when `getRandomFinishableWork()` returns null

### 3. PreferredBuffer Throttle Blocking Warp
- **Issue**: The `preferredBuffer < 100` check in `getRandomFinishableWork()` was returning null too early during warp
- **Fix**: Modified logic to allow proceeding if `preferredBuffer + ticksElapsed >= 100`, and `PostDowntimeWarper` passes `Math.max(ticksPassed, 1000)`

### 4. Game Freeze During Warp
- **Issue**: `PostDowntimeWarper.warp()` was calling `work.recomputeNow()` on every single warp tick (expensive operation)
- **Fix**: Added `hasRecomputed` flag to only recompute once per warp session

### 5. Wrong Job Being Selected (CURRENT ISSUE - may be fixed)
- **Issue**: `getRandomFinishableWork()` was iterating `getPreferredWorkIds()` which includes ALL jobs for the root, selecting `planks` (no supplies) instead of `stick`/`bowl` (have supplies)
- **Fix**: Changed to iterate `startableWork.getFor(currentJob)` which is the preselected list filtered for jobs with available supplies

## Files Modified
1. `PostDowntimeWarper.java` - Virtual morning time, hasRecomputed flag, larger ticksElapsed
2. `ImportantTicks.java` - Fallback to dynamic resolution, guard against 0 duration
3. `TownFlagBlockEntity.java` - Buffer check fix, iterate preselected jobs, debug logging
4. `TownPossibleWork.java` - Removed canFit() check from precomputation
5. `ImportantTicksTest.java` - Updated test for new expected behavior

## Next Steps to Try
1. **Test the latest fix** - The last change (iterating preselected jobs instead of all preferred work IDs) should select `stick` or `bowl` instead of `planks`
2. **Check the warp log** - Look for:
   - `[getRandomFinishableWork] Iterating preselected jobs: [bowl, stick]`
   - `[getRandomFinishableWork] END: found=JobID{rootId='crafter', jobId='stick'}` (or bowl)
   - `Computed N important ticks for job crafter/stick (dynamic=false)`
3. **If still not working**, check if the warper for `stick`/`bowl` is actually executing work cycles (COLLECTING_SUPPLIES, EXTRACTING_PRODUCT, DROPPING_LOOT)
4. **Clean up debug logging** after fix is confirmed working

## Key Insight
The warp system needs to select jobs that:
1. Can fit in the day (time check) - handled by virtual morning time
2. Have available supplies (ingredients/tools) - handled by preselected list
3. Match work requests OR are acceptable unrequested work - handled by preferredBuffer bypass

The preselected list (`startableWork.getFor()`) already filters for #2, so we should use it for the final fallback selection.
