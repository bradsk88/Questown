# unseededMat equivalence test — remaining defect map (2026-10-19 session)

Status: **RED by design (spec-test), now mapped.** `TickerAdvancerEquivalenceTest.gatherer_notool_unseededMat_shouldStillStartAndExtract`
fails; 61/62 siblings green. This test is a spec for the 855 raw-food soft-lock.

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

## Next step (one bisection, ~30 min)

Make `TestTickerDependencies.runPreTickHook` actually refresh/serve a fresh
`roomsNeedingInput` per tick (drop the cache, or recompute in the hook). If green → blocker #2;
if still red → blocker #1 (the TODO container node needs the test stands to expose the leaver's
held items as a container, or the node needs finalizing).

## Guardrails

- Do NOT commit the 3 intermixed files (JobsClean, ResourceJobLoader, RoomsStatusLogic) — they carry
  uncommitted Gate A/B hunks (see the 2026-10-19 session: "no other session" — it is the dev's own).
- 61/62 siblings (incl. seeded freshMat + notool extract variants) prove the Oct-19 probe strip is
  behavior-neutral for this ticker.
