# Time Warp: Mid-Work Scenario (Gatherer)

## Problem Statement

When a gatherer villager is "out gathering" (status = `WAITING_FOR_TIMED_STATE`) when warp begins, the time warp needs to:
1. Wait for the timer to expire (simulate passage of time)
2. Drain the timer down to 0
3. Complete their current gathering trip (extract loot)
4. Drop loot to containers
5. Start new cycles with proper food consumption (1 food per trip)

## Key Files

- `AbstractDeclarativeJobWarper.java` - Core warp logic, handles status transitions
- `DeclarativeJobs.java` - Creates warper, passes `initialStatus` from villager journal
- `DeclarativeJobWarpingTest.java` - Unit tests for warp scenarios

## The Core Challenge

The villager's **journal status** (`WAITING_FOR_TIMED_STATE`) never updates during warp. Each warp tick reads the same status from the journal. This makes it hard to distinguish:
- **First extraction**: Villager returning from gathering trip, needs to extract loot
- **New cycle start**: Villager dropped items, should collect supplies (food) for next trip

## Attempted Solutions

### Approach 1: Check `hasNonSupplyItems`
```java
if (initialStatus.isWaitingForTimers() && !villagerHasItems) {
    // Force extraction
}
```
**Problem**: After dropping items, `villagerHasItems` is false again, so extraction is forced repeatedly instead of going to `COLLECTING_SUPPLIES`.

### Approach 2: Check `processingState != maxState`
```java
if (initialStatus.isWaitingForTimers() && !villagerHasItems && processingState != maxState) {
    // Force extraction, then set processingState = maxState
}
```
**Problem**: `DROPPING_LOOT` resets `processingState` to 0, triggering forced extraction again.

### Approach 3: Check computed status for COLLECTING_SUPPLIES
```java
// Compute normal status first
ProductionStatus computedStatus = statusProvider.computeStatus(...);

// Only force extraction if computed status is NOT COLLECTING_SUPPLIES
boolean wouldCollectSupplies = computedStatus != null && computedStatus.isCollectingSupplies();
if (initialStatus.isWaitingForTimers() && !villagerHasItems && !wouldCollectSupplies) {
    // Force extraction
} else if (computedStatus != null) {
    status = computedStatus;
}
```
**Problem**: After `COLLECTING_SUPPLIES`, villager has food but `hasNonSupplyItems()` returns false (food is a supply item). Since computed status is `state:0` (not COLLECTING_SUPPLIES), extraction is forced again, skipping food consumption.

### Approach 4: Also check for supply items (CURRENT SOLUTION)
```java
boolean villagerHasNonSupplyItems = inventory.hasNonSupplyItems();
boolean villagerHasSupplyItems = inventory.getSupplyItemStatus().values().stream()
        .anyMatch(supplyStatus -> supplyStatus == SupplyItemStatus.HAS_ITEM);

boolean wouldCollectSupplies = computedStatus != null && computedStatus.isCollectingSupplies();
boolean needsFirstExtraction = initialStatus != null
        && initialStatus.isWaitingForTimers()
        && !villagerHasNonSupplyItems
        && !villagerHasSupplyItems  // NEW: also check for supplies (food)
        && !wouldCollectSupplies;
```
**Status**: WORKING. After `COLLECTING_SUPPLIES`, villager has food so `villagerHasSupplyItems` is true, preventing forced extraction. The normal flow proceeds to `state:0` which consumes the food.

**Note**: This is a workaround. The ideal flow for `WAITING_FOR_TIMED_STATE` would be:
1. Wait for the timer to expire (create a fake tick representing that future time)
2. Drain the timer down to 0
3. Then extract loot

Currently we skip directly to extraction. This works but could be improved to better match real-time behavior.

## Gatherer Job Structure

From `gatherer_unmapped_notool_med.json` (gather_half_day):
```json
"work_states": [
  { "ingredients": "#questown:villager_food", "quantity": 1 },  // State 0: "pack lunch"
  { "work": 1 },                                                  // State 1: activates timer
  { "time": 4000 }                                                // State 2: 4000 tick wait
]
```

Normal cycle:
1. `COLLECTING_SUPPLIES` - Take food from container to inventory
2. `state:0` - "Pack lunch" - take food to the flag (destroys food item)
3. `state:1` - Technical requirement - doing 1 "work" activates the timer for next state
4. `WAITING_FOR_TIMED_STATE` - Wait 4000 ticks (villager is "out gathering")
5. `EXTRACTING_PRODUCT` - Generate "gathered" loot
6. `DROPPING_LOOT` - Put loot in containers
7. Back to `COLLECTING_SUPPLIES` (or DOWNTIME if due/overdue)

## Warp Handlers

From `AbstractDeclarativeJobWarper.staticInitialize()`:
- `COLLECTING_SUPPLIES` → `collectSupplies` (takes item from container to inventory)
- `state:0`, `state:1`, etc. → `tryWorkingProduction` (processes work states)
- `EXTRACTING_PRODUCT` → `tryWorking` (extracts loot)
- `DROPPING_LOOT` → `dropLoot` (puts items in containers)
- `WAITING_FOR_TIMED_STATE` → `NULL_HENDLAR` (does nothing)

## Key Observations from Logs

1. **processingState resets after DROPPING_LOOT**:
   ```
   tick=298526 EXTRACTING_PRODUCT workState={processingState=3...}
   tick=298527 DROPPING_LOOT workState={processingState=0...}
   ```

2. **Double COLLECTING_SUPPLIES causes double food consumption**:
   ```
   tick=294757 COLLECTING_SUPPLIES  <-- Takes food #1
   tick=294761 COLLECTING_SUPPLIES  <-- Takes food #2 (should not happen!)
   ```

3. **Correct cycle shows state:0, state:1, then extraction**:
   ```
   tick=301443 COLLECTING_SUPPLIES  <-- Take food from chest
   tick=301444 state:0              <-- "Pack lunch" (destroys food)
   tick=301445 state:1              <-- Do work (activates timer)
   tick=301446 EXTRACTING_PRODUCT   <-- Timer skipped in warp, get loot
   tick=301447 DROPPING_LOOT        <-- Put loot in chest
   tick=301448 COLLECTING_SUPPLIES  <-- Start next cycle
   tick=305444 state:0              <-- Large tick jump = timer simulation
   ```

4. **Tick jumps represent timer simulation**: The warp system generates "important ticks"
   at intervals (e.g., 301448 → 305444) rather than processing every single tick. The ~4000
   tick jump represents the gatherer's timer being simulated.

## Tests Added

In `DeclarativeJobWarpingTest.java`:
- `whenStartingInWaitingForTimedState_shouldTransitionToExtractingProduct`
- `whenStartingInWaitingForTimedState_shouldNotCallOtherHandlers`
- `whenStartingInWaitingForTimedState_stateReflectsExtraction`
- `whenStartingInWaitingForTimedState_withItems_shouldNotForceExtraction`
- `whenStartingInWaitingForTimedState_withoutItems_shouldForceExtraction`
- `whenStartingInWaitingForTimedState_afterExtractionAlreadyDone_shouldNotForceAgain`
- `whenStartingInWaitingForTimedState_gathererStyle_shouldCompleteGathering`

## Resolution

All issues resolved as of 2026-01-11:

1. ~~**Update failing test**~~ - DONE. Updated `whenStartingInWaitingForTimedState_afterExtractionAlreadyDone_shouldNotForceAgain` to use a status provider that returns `COLLECTING_SUPPLIES`.

2. ~~**Test in-game**~~ - DONE. Food consumption verified correct (52 → 49 carrots = 3 food for 3 trips).

3. **Edge cases considered**:
   - `computedStatus` null: Falls back to IDLE, no forced extraction if villager has items
   - Villager has food but not loot: `villagerHasSupplyItems` prevents forced extraction
   - Multiple villagers: Each villager warps independently with their own journal status

## Related Files

- `ProductionStatus.java` - Status enum with `isWaitingForTimers()`, `isCollectingSupplies()`
- `ProductionTimeWarper.java` - `simulateCollectSupplies()`, `simulateExtractProduct()`
- `TownFlagState.java` - Warp loop at lines 218-224
- `ImportantTicks.java` - Generates warp tick schedule
