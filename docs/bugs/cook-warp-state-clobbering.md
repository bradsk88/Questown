# Bug: Cook sub-jobs clobbering each other's processingState during warp

## Status: Fixed (2026-02-15)

## Symptom
During time warp, cook villagers produce far fewer cooked items than expected. The furnace
often ends up with wrong items in wrong slots, or items never get inserted at all.

## Root Cause: Single-step-per-tick + shared processingState

All cook sub-jobs (`cook/fuel`, `cook/beef`, `cook/extract`) share the same furnace `BlockPos`
for `MCTownState.workStates` tracking. Each sub-job's warper runs only ONE step per warp tick
(e.g., COLLECTING_SUPPLIES), then returns. On the next warp tick, `PostDowntimeWarper` cycles
to a different sub-job, which sees the previous job's leftover `processingState` and misinterprets
it as its own.

Example sequence showing the clobber:
1. `cook/beef` runs: COLLECTING_SUPPLIES advances `processingState` to 1
2. Next tick: `cook/fuel` runs, sees `processingState=1`, interprets as fuel's state:1
3. Fuel's state:1 rule is `insert_into_slot_1` -- inserts coal (correct for fuel, wrong timing)
4. `cook/beef`'s state:1 (`insert_into_slot_0`) never runs -- beef never enters the furnace

## Fix: Loop each cook sub-job to completion

**File:** `PostDowntimeWarper.java`

When the resolved job has `rootId` "cook", run its warper in a loop (max 6 iterations) instead
of a single call. The loop breaks early when the warper returns the same state (no progress).

```java
if ("cook".equals(resolvedJob.rootId())) {
    return runToCompletion(jobWarper, level, liveState, currentTick, ticksPassed, villagerNum);
}
```

The limit of 6 covers the full cycle: collect supplies (1) + state:0 (1) + state:1 (1) +
auto-extraction/reset (1) + drop loot (1) + buffer (1). The extraction step already resets
`processingState` to 0 via `getResetFunc()` -> `State.fresh()`, so the next sub-job starts clean.

Per warp tick for a cook villager, the flow becomes:
1. `SmeltFurnaceWarpRule.onWarpTick()` fires -- advances furnace smelting
2. `PostDowntimeWarper.warp()` resolves the next cook sub-job (e.g., `cook/fuel`)
3. Loop runs fuel to completion: collect coal -> state:0 -> state:1 (insert into slot 1) -> reset
4. Next warp tick: resolves `cook/beef`, loops to completion: collect beef -> insert slot 0 -> reset
5. `furnace_smelt_warp` advances smelting (beef + coal now in furnace)
6. Eventually: `cook/extract` loops: take from slot 2 -> drop to containers -> reset

## Secondary bug uncovered: TakeFromSlotSpecialRule null context

**File:** `TakeFromSlotSpecialRule.java`

`TakeFromSlotSpecialRule.beforeExtract()` calls `super.beforeExtract(ctxInput, event)`, which
returns `null` (the base class `JobPhaseModifier.beforeExtract` default). It then passes that
`null` as the context to `tryGiveItem(null, ...)`, creating `Inputs(null, level, uuid)` and
causing an NPE in `ProductionTimeWarper.getHeldItems`.

This bug was latent -- before the loop fix, state clobbering prevented `cook/extract` from ever
reaching the extraction phase during warp. With the loop fix, extraction actually fires and hits it.

Fix: fall back to `ctxInput` when `super.beforeExtract()` returns null.

## Test results

`/qt test cook 10000 destroy` produces cooked beef in the chest (3 observed in testing). The test
command itself reports FAIL because its checker compares MCTownState snapshots, which don't account
for items deposited into containers during warp. The checker needs a separate fix to diff container
contents.

## Files changed
- `src/main/java/ca/bradj/questown/town/PostDowntimeWarper.java` -- `runToCompletion` helper + cook branch
- `src/main/java/ca/bradj/questown/integration/TakeFromSlotSpecialRule.java` -- null context guard
