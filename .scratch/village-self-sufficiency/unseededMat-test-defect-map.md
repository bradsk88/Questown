# unseededMat equivalence test — defect map (2026-10-19 session)

Status: **GREEN — both blockers resolved; 62/62 in TickerAdvancerEquivalenceTest, jobs+town sweep green except deliberate `todo_*` markers.**

## Bisection results (2026-10-21 session)

**Blocker #2 (stale rni cache) — RULED OUT.** Made `TestTickerDependencies.runPreTickHook` refresh
`cachedRoomsNeedingInput` per tick (recomputed via `RoomsStatusLogic.compute`, mirroring the
production guards) and `getCachedRoomsNeedingInput()` recompute per serve (cache dropped). Test still
red ⇒ not the cache. (Code confirms it was structurally a no-op: `SpecialRulesRegistry`'s appliers
never use `roomsReplacer` — grep the `JobPhaseModifier` subclasses — and `DeclarativeJobTicker.tick()`
recomputes rniot fresh per call anyway.) Kept the fresh-serve change as a faithful mirror of
production's hook contract.

**Blocker #1 (`containers=[]` at the "TODO" gate) — CONFIRMED + FIXED.** The gate labeled "TODO"
is `TownHasSupplies`'s own placeholder `describe()` (`TownHasSupplies.java:42 return "TODO"; // TODO?`),
not an unfinished node. It scans room containers via `ContainersClean.get` → `toBlock(...).asContainer()
()/asChest()`. The stand returned `null` for everything, AND `TestRoomMatch`'s room contained only the
job block — which `ContainersClean.get` skips for job-block positions — so `containers=[]` was
structural, not a data accident. Fixed by exposing the leaver's held items (live
`inventory.getItems()` view) as a room block: `TestRoomMatch.defaultRoom` now adds
`TestTickerDependencies.HELD_ITEMS_STORE_POS (3,3)`; `toBlock` returns a read-only `ContainerTarget`
there mirroring production leavers who arrive carrying their `initial_request` items.

**Spec-test assertion rework (was measuring the wrong moment).** With blocker #1 fixed the leaver runs
its FULL lifecycle in 6000 ticks: food consumed, loot extracted — and a completed leaver job resets the
mat (so end-state is 0, exactly like the green `freshMat` sibling's "leaveState0"). The old single
6000-tick `jobBlockState() > 0` assertion was therefore wrong for a completed job. Test now runs two
phases: 200 ticks → assert state>0 (proves start with null mat state); then 5800 more → assert
`productExtracted()` + "loot" in inventory (proves completion+extraction).

## What was already fixed (in-flight working tree, uncommitted — Gate A/B, "Fix A")

## What was already fixed (in-flight working tree, uncommitted — Gate A/B, "Fix A")

- `DeclarativeJobTicker.computeRoomsNeedingVillagerInput` bsFn: null → `State.fresh()` (with the whoever-seeds-first comment).
- `RoomsStatusLogic` ×2: `State.freshIfNull(...)`.
- `JobsClean` L305-337 spotStatuses builder: `State.freshAtState(0)` for null.
- `TownWorkStatusStore.defaultStateFactory`: welcome mat seeds `welcomeMatSeedState()` unconditionally
  (comment: dev directive 2026-10-07, "left ~half the leavers' per-owner ledgers WITHOUT the mat").
- `State.freshIfNull` / `freshAtState` / `welcomeMatSeedState` exist in `town/workstatus/State.java`.

**Verified working by the failing test's own dump:** `spots={mat=0}`, `Stage: 0 = true`
(town has workable spot with state at (0,0)) — the selection/view layer is null-tolerant now.

## Failing leaf (from the [WHY] gate dump, transition IDLE → state:0)

```
gate[not going to jobsite]=<?>=null (cannot be pre-computed)   ← null fallback path
gate[inventory full]=false / town has storage space=true / hasWorkItems=true ...
gate[TODO=false] (No matches found for <itemPred> in any containers)
    containers = []            ← TownHasSupplies → ContainersClean.get(supplyRooms, false) → []
    room needs = {0=T, 1=F, 2=F}
else →
gate[hasAnyItems]=true ... gate[town has active timer]=false ...
```

Two candidate blockers (both state-independent — i.e. NOT the null→fresh reads, which are proven OK):

1. **`containers = []`**: `TownHasSupplies.populate()` → `ContainersClean.get(rooms.get(), false)`
   over `TickTownProvider.getSupplyRooms()` (from `deps::getRoomsForSupplyCheck`). In the test stands:
   `TestTickerDependencies.toBlock(...)` returns `asChest()=null, asContainer()=null` always →
   `addIfChest/addIfContainer` both false → containers always []. The TODO-labeled gate then rejects.
   (The TODO node is a placeholder by name — its author left it unfinalized.)
2. **Stale cache**: `TestTickerDependencies.cachedRoomsNeedingInput` + `cacheRoomsNeedingInput(...)`
   (L40/249) and `runPreTickHook` = NO-OP ("special rules not tested yet", L245). In production the
   pre-tick hook is where the rni cache gets refreshed/invalidated; the test never invalidates →
   the first (null-state) computed map sticks for the whole 6000-tick run.

## Tried this session (did not flip the test, kept — see git)

`TestWorkStatusHandle.defaultStateFactory` now mirrors production: seeds
`State.welcomeMatSeedState()` at `IntegrationTestWorld.DEFAULT_WORKSPOT_POS` instead of `null`.
Still RED ⇒ the blocker is not seeding; it is one of the two candidates above.

## Verification

- `./gradlew :test --tests 'ca.bradj.questown.jobs.declarative.TickerAdvancerEquivalenceTest'` →
  tests=62 failures=0 (was 61/62 until the blocker-#1 stand fix + assertion rework).
- `--tests 'ca.bradj.questown.jobs.*' --tests 'ca.bradj.questown.town.*'` → only reds are the
deliberate `todo_*` registry-bound markers (`ServerJobsRegistryCanFitTest.todo_…`,
`WorkStatusStoreMatSeedTest.todo_…`), per the "red is default until Forge harness" rule.
- Held-run re-sweep of `village/self_sufficiency` still owed before declaring the 855 soft-lock closed in
  the real world (unit spec is green; live proof pending).

## Guardrails

- Do NOT commit the 3 intermixed files (JobsClean, ResourceJobLoader, RoomsStatusLogic) — they carry
  uncommitted Gate A/B hunks (see the 2026-10-19 session: "no other session" — it is the dev's own).
- 61/62 siblings (incl. seeded freshMat + notool extract variants) prove the Oct-19 probe strip is
  behavior-neutral for this ticker.
