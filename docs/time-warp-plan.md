# Time Warp Implementation Plan

Optimized for visible in-game progress. After each step, run `/qt warp` to observe changes.

## Architecture Principle: Code Sharing

**MCTownStateWorldInteraction** (warp) and **RealtimeWorldInteraction** should share as much code as possible.

- Both extend `AbstractWorldInteraction`
- When common logic emerges in `MCTownStateWorldInteraction`, pull it up to the abstract class
- Benefits:
  1. Ensures warp and realtime use identical logic
  2. Allows realtime behavior to be covered by unit tests (more resilient)
  3. Reduces code duplication and maintenance burden

## Completed

### Testing
- ✅ Handler dispatch tests - `DeclarativeJobWarpingTest.java`
- ✅ Multi-warp scenario tests (state accumulation, timers)
- ✅ Work progression tests (State's 10x scaling)

### Simulation Accuracy
- ✅ Timer/walk time in computeWarpTicks - `DeclarativeJobs.java:399`

## Remaining (in order)

### Step 1: Enable Warpers
- ✅ Re-enable warpers - `ServerJobsRegistry.java:460`
  - Added `warper()` getter to `Work.java`
  - Updated `getWarper()` to accept `BlockPos townFlagPos`
- **Observe**: Warp command does something (likely broken behavior)

### Step 2: Detect Workable Blocks
- ✅ `roomsWithWorkableStatefulBlocks()` :440
- **Fixed**: Now correctly reports workable blocks based on processing state

### Step 3: Detect Supplies
- ✅ `hasSuppliesV2()` :445
- **Fixed**: Now checks if current state actually needs ingredients/tools before
  returning true. Previously always returned true, causing crashes at states
  that don't require supplies.

### Step 4: Verify Basic Cycle
- ✅ Test full cycle: collect → work → extract → drop loot
- **Fixed**: `getEveningStatus()` in `ProductionStatuses.java` now checks for
  `roomsWithCompletedProduct()` before returning RELAXING, enabling extraction
  during evening time.
- **Tests**: Added `ProductionStatusesTest.java` with 7 tests covering evening
  status detection including extraction scenario.

### Step 5: Downtime During Warp
- ✅ **NOT NEEDED** - Downtime is already accounted for in `totalDuration` calculation
- **Tested**: Adding explicit downtime simulation caused under-production (12 bowls vs 15 real-time)
- **Reason**: The `totalDuration` includes real-world overhead (walking, pathfinding, etc.) which
  implicitly accounts for rest periods. Real-time 10,000 ticks with 2 rest periods = 15 bowls,
  and warp should match this without additional downtime penalties.
- **Conclusion**: Warp production should match real-time production; no additional downtime logic needed

### Step 6: Job Switching During Warp
- ✅ Allow villagers to switch between jobs of same root (e.g., bowl ↔ stick for crafter)
- ✅ Job switching should be random (same as realtime behavior)
- **Fixed**: Two changes enable job switching:
  1. `TownFlagState.java`: Always use `PostDowntimeWarper` for dynamic job resolution
  2. `TownFlagBlockEntity.java`: Shuffle preselected jobs in `getRandomFinishableWork()`
- **Observe**: With planks + saplings in chest, villager produces BOTH bowls AND sticks

#### Known Bug: Supply Drop on Job Switch
- **Issue**: Villagers may drop supplies if job switches mid-collection
- **Example**: Collect sapling for stick → job switches to bowl → sapling dropped to chest
- **Expected**: Villager should complete the simple task (make stick) before switching
- **Root cause**: Job is re-evaluated on every warp tick, not at cycle boundaries
- **Future fix**: Only allow job switching after completing current cycle (after DROPPING_LOOT)

### Step 7: Item Recovery
- ✅ Track inserted items in MCTownState
  - Added `insertedItems` map to MCTownState (villagerIndex -> workBlockPos -> items)
  - `withInsertedItem()`, `getTotalInsertedItemsCount()`, `getInsertedItemsCount()`, `withInsertedItemsCleared()`
- ✅ `timesInserted()` - returns count from MCTownState tracking
- ✅ Item insertion tracking in `postInsertHook()` - records items when inserted during warp
- ✅ Add recovery logic to NO_SUPPLIES handler
  - `AbstractDeclarativeJobWarper`: Changed NO_SUPPLIES handler from NULL_HENDLAR to `recoverInsertedItems`
  - `AbstractStateInteraction`: Added `simulateRecoverInsertedItems()` abstract method
  - `MCTownStateWorldInteraction`: Implements recovery by clearing tracking and depositing items
- **Implementation approach**: Full recovery logic (tracks items inserted, recovers on NO_SUPPLIES)
- **How it works**: When NO_SUPPLIES is encountered during warp, any previously inserted items are
  recovered (deposited back to containers or given to villager) so they aren't lost.

### Step 8: Non-Supply World Containers
- ⬜ Handle world containers - `InsertIntoSlotSpecialRule.java:35`
- **Observe**: E.g. Furnace contains inserted item after warp step
- **Note**: This is a special rule for jobs like "cook" who actually 
            use real world blocks (e.g. furnace) in a way that the 
            player can see and interact with. For the sake of warp,
            we may only need to ensure that items inserted into such
            blocks on the final step of the warp. Rather than interacting 
            with the real world container during the warp itself.

### Step 10: All Jobs
- **Observe**: All currently implemented jobs should function correctly
- **Note**: Many jobs utilize special rules. So this may require additional
            work to ensure those special rules are compatible with warp.

## Not Needed
- ~~Store villager statuses~~ - computed on-the-fly

## Related Docs
- `time-warp-logic.txt` - Algorithm details
- `work-loop.md` - tryWorking() flow
