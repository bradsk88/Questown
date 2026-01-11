# Time Warp Implementation Plan

Optimized for visible in-game progress. After each step, run `/qt warp` to observe changes.

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

### Step 5: Item Recovery
- ⬜ Track inserted items in MCTownState
- ⬜ `timesInserted()` :365
- ⬜ `tryGrabbingInsertedSupplies()` :359
- ⬜ Add recovery logic to NO_SUPPLIES handler
- **Observe**: Villagers who can't proceed recover items, move to other work

### Step 6: World Containers
- ⬜ Handle world containers - `InsertIntoSlotSpecialRule.java:35`
- **Observe**: Chest contents reflect warped changes

### Step 7: Polish
- ⬜ MutableEntityInvStateProvider - inventory tracking
- ⬜ Load/save work states - `TownStateSerializer.java:88`
- **Observe**: Warp state persists across save/load

## Not Needed
- ~~Store villager statuses~~ - computed on-the-fly

## Related Docs
- `time-warp-logic.txt` - Algorithm details
- `work-loop.md` - tryWorking() flow
