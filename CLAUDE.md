# Questown Project Guidelines

## Naming Conventions

- `MAX_DOWNTIME_TICKS` is actually "downtime ticks" (the number of ticks a villager stays in downtime). "Max" refers to player override capability via work requests, not an upper bound.
- Avoid generic names like "DynamicWarper" - prefer descriptive names that explain what the class does (e.g., "PostDowntimeWarper" for a warper that handles post-downtime job resolution).

## Current Work: Time Warp Fix (as of 2026-01-10)

### Status: UNTESTED FIX PENDING

The latest fix needs in-game testing. Run `./gradlew runClient`, load a world with a crafter villager, put saplings in a container, and sleep to trigger time warp. Check if sticks are produced.

### What Was Fixed

1. **Day/night rejection** - Warp now uses virtual morning time instead of actual game time
2. **Zero ticks for non-downtime villagers** - Falls back to dynamic resolution
3. **PreferredBuffer throttle** - Fixed to allow warp to proceed
4. **Game freeze** - Only recompute job possibilities once per warp session
5. **Wrong job selection** (latest, untested) - Now iterates preselected jobs (with supplies) instead of all preferred work IDs

### Key Files

- `PostDowntimeWarper.java` - New class for dynamic job resolution during warp
- `ImportantTicks.java` - Generates warp ticks, falls back to dynamic resolution
- `TownFlagBlockEntity.java` - `getRandomFinishableWork()` with debug logging
- `TownPossibleWork.java` - Removed time-of-day filter from precomputation

### Debug Logging

Debug logging is currently enabled in `TownFlagBlockEntity.getRandomFinishableWork()`. Look for:
- `[getRandomFinishableWork] START/END` - Traces job selection
- `[getRandomFinishableWork] Iterating preselected jobs: [bowl, stick]` - Should see this
- Should select `stick` or `bowl`, NOT `planks`

### If Still Not Working

1. Check if `getRandomFinishableWork` is returning `stick` or `bowl` (not `planks`)
2. If correct job selected, check if the warper is executing work cycles
3. See `docs/warp-debugging-notes.md` for full debugging session notes

### After Fix Confirmed

1. Remove debug logging from `TownFlagBlockEntity.getRandomFinishableWork()`
2. Test Arborist (World Modifier) scenario per `docs/warp-scenarios.md`
3. Squash commits
