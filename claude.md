# Questown

A Minecraft mod implementing a villager job system.

## Design Philosophies

### Abstraction-First Generics

The codebase uses heavily parameterized generic types to decouple domain logic from Minecraft-specific code. Classes like `Hendlar`, `Inpoots`, and `AbstractWorldInteraction` work with abstract type parameters (`TOWN`, `POS`, `LEVEL`, `ITEM`) rather than concrete Minecraft types.

**Why**: Enables pure, testable domain logic. Unit tests run without Minecraft dependencies. Real MC types (`MCTownItem`, `MCHeldItem`) implement generic interfaces.

### Declarative Job Definitions

Jobs are defined as data structures rather than imperative code. A job declares:
- Ingredients required at each state
- Tools required at each state
- Work units required at each state
- Time required at each state
- Special rules that modify behavior

**Why**: Jobs become data-driven. Complex behavior emerges from combining requirements and rules rather than writing procedural logic.

### Explicit State Machines

Jobs are fundamentally state machines using `ProductionStatus`:
```
Idle -> Go to job site -> Collect supplies -> Work -> Extract product -> Drop loot -> Idle
```

**Why**: Explicit state management makes job logic predictable and debuggable. Each state represents a meaningful milestone.

### Hook and Rules System

Extensibility through hooks (`PreInitHook`, `PostExtractHook`, etc.) and special rules (`CLAIM_SPOT`, `HUNGER_FILL`, `SHARED_WORK_STATUS`). Rules are string-referenced and dispatched through hooks.

**Why**: Allows behavior customization without modifying core logic. External mods can register new rules.

### Layered World Interactions

Hierarchical interaction handlers separate concerns:
- `AbstractWorldInteraction` - Pure domain logic
- `AbstractItemWI` - Item insertion/extraction
- `AbstractWorkWI` - Work progression
- `RealtimeWorldInteraction` - Concrete MC implementation

**Why**: Each handler manages one aspect of job execution and can be tested independently.

### Composition Over Inheritance

Complex jobs are built by composing simple, tested components rather than deep inheritance hierarchies. Validation logic lives in check objects (`ItemWorkChecks`), allowing easy swapping and testing.

### Job Rooms vs Supply Sources

Job rooms and supply sources are distinct concepts:
- **Job rooms**: Where work is performed (e.g., town gate for gatherer). Use `getRoomsForJob(dj)`.
- **Supply sources**: Where ingredients/tools are fetched from (any room with containers, e.g., store room). Use `getAllRooms()`.

In `TownPossibleWork`, when checking ingredient availability, search ALL rooms - villagers fetch supplies from any town container, not just their job site.

## Code Style

### Line Length

- Hard limit: 120 characters
- Soft limit: 80 characters (prefer when possible)

### Clean Code Principles

- Small, focused methods with single responsibilities
- Meaningful names that reveal intent
- Minimal comments (code should be self-documenting)
- Extract complex conditionals into well-named methods
- Avoid deep nesting

### Null Handling

- Prefer annotating parameters with `@NotNull` over adding defensive null checks
- When a null value appears unexpectedly, investigate the root cause rather than adding guards
- Defensive null checks can mask bugs; understanding why null occurred is more valuable

## Time Warp System

Time warp simulates the passage of game time, processing all job work that would
normally occur during that period. This allows fast-forwarding hours or days of
work without waiting for real-time ticks.

### How It Works

1. **Command Entry**: `/qt warp <pos> <ticks>` triggers warping on a Town Flag
2. **Orchestration**: `TownFlagState.advanceTime()` coordinates the process
3. **Important Ticks**: Rather than processing every tick, the system identifies
   key moments where state transitions occur
4. **Handler Dispatch**: Each `ProductionStatus` maps to a `Hendlar` that
   simulates the appropriate action (working, extracting, dropping loot, etc.)
5. **State Update**: Work block timers, villager inventories, job progress, 
   and even real minecraft blocks (e.g. crop growth and harvest) are all 
   updated as if the work actually occurred

### Key Classes

| Class | Purpose |
|-------|---------|
| `AbstractDeclarativeJobWarper` | Core warping logic and state machine |
| `TownFlagState` | Orchestrates the warp process for all villagers |
| `ProductionTimeWarper` | Simulates inventory operations (drop, collect) |
| `Hendlar` | Handler interface for each ProductionStatus |
| `HendlarInpoots` | Input parameters for handlers |

### Warp Algorithm

```
For each villager:
  1. Get current work block state (or initialize fresh)
  2. Determine ProductionStatus based on signals
  3. Execute the handler for that status
  4. Reduce work block timer by ticks passed
  5. Return modified state
```

### Design Notes

- Uses heuristic block positions since actual block data isn't persisted
- Timer reduction is clamped to zero (never negative)
- Null states initialize to `State.fresh()`
- Handler map is extensible via `staticInitialize()`

### ProductionStatus Handlers

Every `ProductionStatus` must have a corresponding handler in
`AbstractDeclarativeJobWarper.staticInitialize()`. When adding a new status,
register it with a `Hendlar` (use `NULL_HENDLAR` if no action needed during warp).

**Current null-action statuses** (villager idles during warp):
- `RELAXING`, `WAITING_FOR_TIMED_STATE`, `NO_SPACE`, `GOING_TO_JOB`
- `NO_SUPPLIES`, `IDLE`, `NO_JOBSITE`, `NO_WORK_POSSIBLE`

### NO_WORK_POSSIBLE Status

Returned by `WorkSeekerJob.getStateComputer()` when:
1. Time is MORNING, NOON, or UNDEFINED
2. `town.getStartableWork().getFor(jobId).isEmpty()` - no startable work exists

This occurs when a villager is at the job board seeking work but the town has
no available jobs matching their skills. During time warp, this status does
nothing (uses `NULL_HENDLAR`).

### ImportantTicks and Null JobIDs

`ImportantTicks.forVillager()` calls `getRandomFinishableWork()` which may return
`null` when no finishable work is available (e.g., no requested work, not enough
time left in day). When null is returned, the method returns early with only
downtime ticks collected so far - no work progression occurs during the warp.

## Status Detection Architecture

The `productionRoutine` function determines what a villager should
do next. It uses a lazy conditional dependency (LZCD) tree to
evaluate conditions in priority order.

### Structure

```
productionRoutine(...)
  │
  ├─ Early exit: If WAITING and timer still active → stay WAITING
  │
  ├─ usualRoutine(...) ← Core decision tree
  │     │
  │     └─ LZCD Tree (evaluated top-to-bottom, first match wins)
  │
  └─ Late override: If idle/null/no_supplies AND timer active → WAITING
```

### LZCD Decision Tree (Priority Order)

Each node has: conditions that must be true, a status to return,
and a fallback node if conditions aren't met.

```
1. Work without items (extraction)
   ├─ Conditions: prioritizeExtraction, not already going to job
   └─ Calls: job.tryChoosingItemlessWork()

2. Use held items (insert ingredients)
   ├─ Conditions: hasWorkItems, hasWorkableBlocks
   └─ Calls: job.tryUsingSupplies()

3. Work in different room without items
   ├─ Conditions: prioritizeExtraction, hasWorkableBlocks
   └─ Calls: job.tryChoosingItemlessWork()

4. Drop loot (hands full)
   ├─ Conditions: inventoryFull, townHasSpace
   └─ Returns: DROPPING_LOOT

5. No space (hands full, nowhere to put items)
   ├─ Conditions: inventoryFull
   └─ Returns: NO_SPACE

6. Drop loot (has non-work items)
   ├─ Conditions: hasNonWorkItems, townHasSpace
   └─ Returns: DROPPING_LOOT

7. Collect supplies
   ├─ Conditions: townHasSupplies
   └─ Returns: COLLECTING_SUPPLIES

8. Drop loot (no supplies available)
   ├─ Conditions: hasNonWorkItems, hasWorkableBlocks, townHasSpace
   └─ Returns: DROPPING_LOOT

9. Drop loot (no work possible)
   ├─ Conditions: hasAnyItems, townHasSpace
   └─ Returns: DROPPING_LOOT

10. Wait for timer
    ├─ Conditions: timerActive
    └─ Returns: WAITING_FOR_TIMED_STATE

11. Extract results
    ├─ Conditions: not going to jobsite
    └─ Calls: job.tryChoosingItemlessWork()

12. No jobsite (town has supplies but nowhere to use them)
    ├─ Conditions: townHasSupplies, inventoryEmpty
    └─ Returns: NO_JOBSITE

13. No space (holding items)
    ├─ Conditions: hasAnyItems
    └─ Returns: NO_SPACE

14. No jobsite (nothing to do)
    ├─ Conditions: inventoryEmpty, noSupplies, noWorkableBlocks
    └─ Returns: NO_JOBSITE

15. Fallback
    └─ Returns: NO_SUPPLIES
```

### Key Behaviors

**COLLECTING_SUPPLIES takes priority over WAITING**: If supplies
are available, villager collects them even during time-based work.
WAITING only triggers when there's truly nothing else to do.

**Location-aware work selection**: `tryUsingSupplies()` checks if
the villager is already at a room needing work. If so, work there.
Otherwise, return GOING_TO_JOB.

**Preference ordering**: Jobs define `getAllWorkStatesSortedByPreference()`
to control which work states are attempted first when multiple
options exist.

## Testing Principles

### Check Existing Coverage First

Before writing new tests, search for existing test files that may already cover
the behavior. Use grep/glob to find related test classes.

### Clear Test Boundaries

Each test class should have a clear, non-overlapping responsibility:
- `StatusesProductionRoutineTest` - status detection logic
- `DeclarativeJobWarpingTest` - warp-specific behavior (timers, handler dispatch)

### Test the Layer, Not Through It

Test the unique behavior of each layer. Don't re-test dependencies:
- Warp tests assume status detection works (tested in `StatusesProductionRoutineTest`)
- Warp tests focus on: timer mechanics, null handling, handler dispatch

### When Fixtures Are Broken, Question Test Expectations

If test infrastructure has bugs (e.g., a mock ignoring its inputs),
tests written against it may encode incorrect expectations. After
fixing a broken fixture, don't assume failing tests are correct -
they may have been written against the broken behavior.

**Action**: When tests fail after a fixture fix, ask whether the
implementation or the test expectations are correct. Don't assume
tests are authoritative.

### Smart Fixtures Prevent Inconsistency

When test fixtures have related fields that must stay in sync,
derive one from the other rather than requiring manual consistency.

Example: `TestJobTown` derives `roomsWithWorkableStatefulBlocks()`
from `roomsNeedingIngredientsByState` - if rooms need input, they
automatically become workable blocks. This prevents tests from
accidentally creating impossible game states.

## Current Work: Time Warp Revival

**Status**: In Progress (WIP commit on branch `1.19.2-0.0.11-alpha.1`)

**Plan**: See `docs/time-warp-plan.md` for the full task list.

**Quick Summary**: The time warp feature simulates game time passage for
villager jobs. Core infrastructure exists but simulation methods in
`MCTownStateWorldInteraction` are stubbed out.

**Approach**: Option B (bottom-up TDD) - Build simulation methods with
tests first. Ensures each piece works before integration.

**Completed**:
- [x] Handler dispatch tests for all statuses (NO_SPACE, NO_JOBSITE, WAITING,
      GOING_TO_JOB, NO_SUPPLIES, RELAXING, work states 0-9)
- [x] Multi-warp scenario tests (timer accumulation, drop loot, collect supplies)
- [x] Work progression tests (work reduces, clamps to zero, multiple warps)
- [x] Steps 1-4 of warp implementation (warpers enabled, workable blocks, supplies,
      basic cycle with extraction)
- [x] Fixed `hasSuppliesV2()` to check if state needs ingredients/tools
- [x] Fixed `getEveningStatus()` to extract products before relaxing
- [x] Added `ProductionStatusesTest.java` with evening status tests

**Next Steps** (TDD order):
1. Implement remaining stub methods in `MCTownStateWorldInteraction.java`:
   - `tryGrabbingInsertedSupplies()` (line 359)
   - `timesInserted()` (line 365)
2. Step 5: Item Recovery - track inserted items, add recovery logic
3. Step 6: World Containers - handle actual chest contents
4. Step 7: Polish - MutableEntityInvStateProvider, load/save work states