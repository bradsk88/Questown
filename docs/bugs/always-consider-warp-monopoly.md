# Bug: `always_consider` Jobs Monopolizing Warp Selection

The following are notes written by the claude agent. Read them through completely, then read the human-written note at
the bottom of this doc.

## Symptom
During `/qt test cook 10000 destroy`, no `cooked_beef` is produced. The cook villager spends all warp cycles on auxiliary jobs (`stock_fuel`, `stock_ingredients`, `fuel`, `extract`) and never runs `cook:beef`.

## Architecture (Job Selection During Warp)

Three layers filter which sub-job a villager works on:

### Layer 1: `TownPossibleWork.getWorkPercentPossible()`
Precomputes a scored list of viable sub-jobs for each root job. Jobs with score > threshold make it into the "preselected" list.

- Checks `canFit` (time-of-day) — **uses real server time**
- Checks `getHighestPossibleState()` — `ALWAYS_CONSIDER` bypasses room/supply scoring here (returns maxState)
- Result: `Prepared for cook: [stock_ingredients, stock_fuel, fuel, extract]` — beef excluded

### Layer 2: `TownVillagerData.chooseFromList()`
Picks a job from the preselected list, filtering by `canFitInDay` and `canSatisfy` (request matching).

- Original code: `always_consider` jobs returned **immediately** (bypassing `canSatisfy`)
- `requestedResults` from job board — often empty (no player requests)

### Layer 3: `TownFlagState.getRandomFinishableWork()`
Orchestrates selection with fallbacks:
1. `chooseFromList()` on preselected jobs
2. Buffer (100 ticks) to avoid thrashing
3. `getPreferredWork()` on all preferred jobs
4. Fallback: any preselected job passing `canFit` - This is to handle if the town has now requests. The villager will, after waiting logn enough, "do their own thing".

### Layer 4: `PostDowntimeWarper.resolveJob()`
Caches selected job while villager has items (mid-cycle). Re-evaluates when inventory is empty.

## Root Causes Identified

### Cause 1: `canFit` in TownPossibleWork uses wrong time
`getWorkPercentPossible()` calls `ServerJobsRegistry.canFit(null, j.getId(), Util.getDayTime(t.getServerLevel()))` — this is the **real** server time. During warp, `PostDowntimeWarper` uses `VIRTUAL_MORNING_TICK = 1000` for its own `canFit` checks. So beef passes `canFit` in the warper but fails it in the preselection, getting score 0 and excluded from the preselected list entirely.

**Fix**: Remove or bypass `canFit` in `getWorkPercentPossible()`. It's redundant with the `canFit` check in `chooseFromList()`, which uses the correct time context. The preselection should focus on supply/room availability, not time-of-day.

### Cause 2: `always_consider` immediate return in `chooseFromList`
Original code at line 47-48:
```java
if (canAlwaysStart.test(p)) {
    return p;  // No canSatisfy check
}
```
Returns immediately for `always_consider` jobs, bypassing request matching. With 4 `always_consider` sub-jobs shuffled before 1 beef, beef almost never gets selected.

**Fix**: `always_consider` should bypass `canFitInDay` but still go through `canSatisfy`:
```java
if (!canAlwaysStart.test(p) && !canFitInDay.test(p)) {
    continue;
}
```

### Cause 3: Empty `requestedResults` breaks `canSatisfy` loop
When the job board has no requests (`requestedResults` is empty), the `canSatisfy` loop body never executes. No job is ever returned from `chooseFromList`, regardless of eligibility.

**Fix**: When `requestedResults` is empty, return the first eligible job (any that passes `canFit` or `canAlwaysStart`). There's nothing to match against, so all eligible jobs should be selectable.

### Cause 4: Auxiliary jobs monopolize via re-evaluation frequency
Even after fixes 1-3, auxiliary `always_consider` jobs (stock_fuel, stock_ingredients) complete fast (pick up item, deposit), triggering frequent re-evaluations. Each re-evaluation shuffles all eligible jobs. With 4 auxiliary jobs vs 1 beef, beef has ~20% chance per re-evaluation and only ~3 re-evaluations per warp. ~51% chance of never selecting beef.

**Potential fix (not yet implemented)**: `PostDowntimeWarper` should prioritize the assigned job (`fallbackJobID`). After an auxiliary job completes a cycle, switch back to the assigned job rather than random selection. This ensures auxiliary jobs support the main job rather than replacing it.

## `ALWAYS_CONSIDER` Intent
From `SpecialRules.java:79-82`:
> By default, Questown will only allow villagers to start a job if there is an abundance of supplies available. This rule causes the job to ignore that so villagers can always start the associated job.

It means "bypass supply abundance checks" — NOT "always select this job first." The two places it's checked should reflect this:
1. `TownPossibleWork.getHighestPossibleState()` — correctly bypasses room/state scoring
2. `TownVillagerData.chooseFromList()` — should bypass `canFitInDay` only, not `canSatisfy`

## Test Observations

### Run 1 (no fixes)
- `Prepared for cook: []` — all jobs scored 0 (canFit fails for all)
- `always_consider` jobs bypassed `canFit` in `chooseFromList` but beef was never in the list

### Run 2 (canFit bypass for ALWAYS_CONSIDER in TownPossibleWork + chooseFromList fix)
- `Prepared for cook: [stock_ingredients, stock_fuel, fuel, extract]` — beef still excluded (not ALWAYS_CONSIDER)
- Empty `requestedResults` meant `canSatisfy` loop never matched anything

### Run 3 (removed canFit from TownPossibleWork entirely + empty requests fix)
- `Prepared for cook: [stock_ingredients, stock_fuel, fuel, beef, extract]` — beef included
- But `stock_fuel` won every shuffle, monopolizing all warp cycles (Cause 4)

## Files Involved
- `src/main/java/ca/bradj/questown/town/TownPossibleWork.java` — preselection scoring
- `src/main/java/ca/bradj/questown/town/TownVillagerData.java` — `chooseFromList()`
- `src/main/java/ca/bradj/questown/town/entity/TownFlagState.java` — `getRandomFinishableWork()`
- `src/main/java/ca/bradj/questown/town/PostDowntimeWarper.java` — warp job caching/resolution
- `src/main/java/ca/bradj/questown/jobs/declarative/TimeWarpWorldInteraction.java` — debug logging (cleanup)

# Warp-specific findings (2026-02-15)

## Bugs found during warp cook investigation

(The changes from this investigation were reverted because we ultimately did not fix the problem of "no cooked food produced", so we're starting over)

### 1. `postInsertHook` discards state advancement (FIXED)
**File:** `AbstractWorldInteraction.java` line 568
**Bug:** `postInsertHook(getTown(inputs), rules, ...)` passes the **original** unmodified state instead of `ctx` (the state after ingredient insertion). This means the `insert_into_slot_0` hook operates on stale state, discarding the `processingState` advancement (e.g., `state:1` → `state:2` gets lost).
**Fix:** Change to `postInsertHook(ctx, rules, ...)`.
**Impact:** Affects both realtime and warp. In realtime, this may be masked by the furnace doing the actual work. In warp, it's fatal — the job state never advances past the insertion step.

### 2. `canFit` in preselection uses real server time (FIXED)
**File:** `TownPossibleWork.java` line 201
**Bug:** `getWorkPercentPossible()` calls `ServerJobsRegistry.canFit()` with real server time. During warp, `PostDowntimeWarper` uses `VIRTUAL_MORNING_TICK=1000`. Mismatch causes time-sensitive jobs to get score 0 and be excluded from the preselected list.
**Fix:** Remove the `canFit` check from preselection. Time filtering is handled downstream.

### 3. Cook result generator returns `minecraft:air` (Likely a flawed agent assumption)
**File:** `ResourceJobLoader.java` `makeCookJobs()`
**Bug:** `makeCookJobs` calls `worldWorkInt(obj, cooldownTicks)` which reads the template JSON's `"result": {"item": "minecraft:air"}`. The template uses `air` as a placeholder (realtime cooking uses the actual furnace to produce cooked items). But warp relies on the result generator to produce the output since there's no real furnace at `fakePos`.
**Attempted fix:** Created `smeltingResultWorkInt()` that looks up the smelting recipe to produce the correct cooked item. **However**, the extraction often runs under a *different* sub-job's warper (see #4), so the correct result generator isn't always active.

### 4. Warp extraction runs under wrong sub-job's warper (NOT YET FIXED - Use farmer as a reference for utilizing REAL positions)
**Root cause:** All cook sub-jobs share the same `fakePos` work block state. When `cook:beef` advances `processingState` to `maxState` (ready for extraction), the warp handler only processes **one status per tick**. On the next tick, `PostDowntimeWarper` cycles to a different sub-job (e.g., `cook:stock_ingredients`). That job's warper sees `processingState >= maxState` and does extraction with the **wrong result generator**.
**Attempted fix:** Added immediate extraction in `DeclarativeJobs.warper()` — after the handler advances state to maxState, immediately run the EXTRACTING_PRODUCT handler in the same tick. **This needs more testing** — unclear if the result generator is correctly invoked, or if other state (villager items, etc.) is consistent for the follow-up extraction.

## Architecture observations for warp cook path

- **Realtime cook flow:** villager → insert beef into real furnace → furnace smelts (Minecraft ticks) → villager extracts cooked_beef from furnace. The result generator is irrelevant; the furnace does the work.
- **Warp cook flow (intended):** villager → `insert_into_slot_0` hook → `furnace_smelt_warp` global rule advances furnace → `take_from_slot_2` hook extracts. But all of these operate on real-world `BlockPos`, and warp uses `fakePos` with no real furnace.
- **Warp cook flow (actual):** The `insert_into_slot_0` hook silently fails (no furnace at fakePos). The `furnace_smelt_warp` rule calls `advanceProcessing(pos, ticks)` on work block positions, but these may also be fake/wrong. Extraction falls through to the result generator, which returns `air` (template default).
- **What needs to happen:** Either (a) the warp needs to use real furnace positions instead of fakePos, or (b) the result generator for cook jobs needs to produce the smelted output directly, AND extraction must happen under the correct job's warper (the one that inserted the ingredient).

# Human Notes

## Special Rule: "Always Consider"

The purpose of "always consider" was introduced for jobs that (essentially) have an empty "work_states" value in their
JSON definition. For example, cook_extract should always be able to pull cooked meat from a furnace if it is in a QT 
room. They don't need any supplies to do this job, so the traditional logic in TownPossibleWork.getHighestPossibleState
would exclude those jobs (because it scores based on availability of supplies). Another bug (where work would not start 
if the work_states were empty) prompted us to require a tool (stick) for the extract job. So it might work without the
always_start rule. Subsequently, we might also determine that stock_fuel and stock_ingredients do not require the 
"always consider" special rule. This might make our lives easier.

## Intention behind shuffling

In real-time gameplay, after completing a single "cycle" for a job, the villager drops that job title and tries to start
another one. We use shuffle to create an "equal opportunity" for the next job that they will start. In the ideal case,
if the villager is capable of 5 different jobs, they will cycle through each one before returning to try the first one 
again (cycling in random order to create an organic feel in-game). 

In practice, this often looks like:
- Cook:Insert_Fuel -> E.g. successful!
- Cook:Extract_beef -> No cooked beef yet. Wait for a bit... Nope, still no cooked beef. Try the next job!
- Cook:Stock_ingredients -> Sure, I see some raw beef in a non-kitchen chest, let me move it to where it will be useful!
- Cook:Stock_fuel -> But, there's no fuel in town. Wait for a bit... Nope, still no fuel. Try the next job!
- Cook:Extract_beef -> No cooked beef yet. Wait for a bit... Nope, still no cooked beef. Try the next job!
- Cook:Insert_Ingredients -> Ah, there's some beef in the kitchen chest, let's take that to the furnace.
- (Minecraft) I"m smelting now!
- Cook:Insert_Fuel -> No fuel in town. Wait a bit. Still not fuel in town.
- (Minecraft) I'm done smelting!
- Cook:Insert_Ingredients -> No ingredients to be moved around. Wait a bit.. Still none. On to the next.
- Cook:Extract_beef -> There's cooked beef in the furnace, I'll grab it and put it in a chest.

Because of the "waiting" logic, every jobs gets a fairly decent chance. 

I want the time warp to replicate this. However, given how quickly the warp moves (everything happens within a single 
tick) it may be acceptable to simple cycle through every job in order, assuming the result is comparable.

Remember that a single warp tick might "unlock" the ability for a job to be possible on a subsequent warp tick.

## Cook's "multi-job" flow
Because the cook interacts with real-world ticking block entities (a nascent concept for questown, so it bends some of 
the architecture but still works well in realtime) a cook's work is done via multiple jobs.

Cook:Insert_ingredients (places an ingredient in a furnace - Job done, "takes" its "air" result and moves to next job)
Cook:Insert_fuel (basically the same, just uses a different item tag and its special rule inserts via a different slot)
Cook:Extract (if the minecraft ticked entity has finished smelting, this job takes the result from the slot)

The intent is to get the same sort of result as the baker, who inserts coal and bread, waits for a timer, and then 
extracts bread (all as one job) - but eliminating the downside of the bread_oven_block, which players are not allowed
to interact with, in favor of a block that players and villagers can interact with - potentially collaboratively.