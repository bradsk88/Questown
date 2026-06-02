---
title: "fix: Make WarpWorldAccess.useItemOnBlock truly in-memory"
type: fix
status: active
date: 2026-06-02
origin: docs/special-rules-decoupling.md (Phase 4 "Migrate Remaining Rules")
---

# fix: Make WarpWorldAccess.useItemOnBlock truly in-memory

## Overview

`WarpWorldAccess` snapshots world state and defers every mutation to a dirty-set
that is committed by `applyTo(ServerLevel)` **only if the warp succeeds**. One
method violates that contract: `useItemOnBlock` (the planting / bone-meal seam)
punches straight through to the live `ServerLevel` via `Item.useOn(...)` during
warp simulation. This plan replaces that punch-through with an in-memory
implementation that uses the same snapshot/dirty-set primitives as every other
`WarpWorldAccess` method.

This is **Target 1** of the "finish the decoupling task" session. **Target 2**
(un-archiving the arborist by warp-completing the tree rules) is captured
separately in `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`
and is explicitly out of scope here.

---

## Problem Frame

`WarpWorldAccess.useItemOnBlock` (`src/main/java/ca/bradj/questown/world/WarpWorldAccess.java`,
lines ~249-256) builds a `UseOnContext` against the private `level` and calls
`item.getItem().useOn(...)`, mutating the real world immediately. Two concrete
defects follow from this:

1. **Leak on failed warp.** Writes are supposed to be deferred and committed only
   when `result.state() != null` (`TownFlagState` lines ~224-225). A planting
   write happens immediately, so if warp aborts after a plant, the crop is in the
   world but town-state rolled back — uncommitted drift.
2. **Plant-then-no-grow parity bug.** A crop planted during warp writes to the
   live world but **not** to the `blockStates` snapshot. The snapshot for that
   position was taken at construction (the workspot + `above()` are in
   `roomPositions`), so the later `GrowCropsWarpRule` reads the stale pre-plant
   snapshot and the crop does not grow that warp.

The rule that drives this seam, `UseLastInsertedItemOnBlockSpecialRule`, is marked
`QTNativeRule` (Tier 1 = "fully warpable/testable"), so the marker is currently
dishonest — and the method is untestable because the unit-test constructor sets
`level == null` (any test would NPE).

### Live consumers (scope anchor)

`ResourceJobLoader` loads only `questown_jobs`. The only **live** consumers of
`use_last_inserted_item_on_block` are:

- `src/main/resources/data/questown/questown_jobs/farmer_wheat_plant.json` —
  workspot `minecraft:farmland`, `require_air_above`; plants wheat seeds.
- `src/main/resources/data/questown/questown_jobs/farmer_global_bone.json` —
  workspot `#minecraft:crops` with `age<7`; applies bone meal.

The third consumer, `questown_job_archive/arborist_plant_tree.json` (sapling
placement), is **archived/not loaded** and belongs to Target 2.

---

## Requirements Trace

- R1. `WarpWorldAccess.useItemOnBlock` performs **no** mutation of the real
  `ServerLevel`; all effects go through the in-memory snapshot + dirty-set.
- R2. A seed `BlockItem` places its block's default state at `pos.above()`
  (crop default = age 0), recorded in `blockStates` and `dirtyBlocks`, so
  `GrowCropsWarpRule` finds it in the same warp and `applyTo` commits it.
- R3. Bone meal advances the `"age"` property of the crop **at `pos`** by a
  deterministic **+3**, capped at `getMaxBlockIntProperty("age")`, marked dirty.
- R4. An unsupported item returns `false`, logs an error, and writes nothing —
  no NPE under the `level == null` test constructor.
- R5. The method is unit-testable without a `ServerLevel`, making
  `UseLastInsertedItemOnBlockSpecialRule`'s `QTNativeRule` (Tier 1) claim honest.
- R6. `MinecraftWorldAccess.useItemOnBlock` (realtime) is unchanged.

---

## Scope Boundaries

- Realtime planting/bone-meal (`MinecraftWorldAccess`) is not changed.
- No new randomness is introduced into warp (bone meal is deterministic +3).
- `canSurvive` / placement-validity simulation is **not** reproduced — the job
  definition already gates planting (farmland workspot + `require_air_above`),
  so warp trusts that gate.
- No change to `chopTree` (already in-memory) or `CheckTreePlantable` (Tier 2).

### Deferred to Follow-Up Work

- Un-archiving the arborist / warp-complete tree rules (sapling placement during
  warp, plantability reconciliation, leaf-path clearing, arborist autotest):
  `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`.

---

## Context & Research

### Relevant Code and Patterns

- `src/main/java/ca/bradj/questown/world/WarpWorldAccess.java` — target method
  `useItemOnBlock`; in-memory primitives already present: `resolveBlockState`,
  `findIntProperty`, `setBlockIntProperty`, `blockStates.put` + `dirtyBlocks.add`,
  `removeBlock`, `chopTree` (the in-memory template to mirror).
- `src/main/java/ca/bradj/questown/jobs/special/GrowCropsWarpRule.java` —
  `growBy()` / `isGrowableCrop()` show the exact age-advance pattern (read `age`,
  read max `age`, `min(age+n, max)`, write back). Bone meal reuses this shape.
- `src/main/java/ca/bradj/questown/world/MinecraftWorldAccess.java` — the realtime
  `useItemOnBlock` (unchanged) defines the behavior warp must match positionally:
  `BlockHitResult` with `Direction.UP` → `BlockItem` placement at `pos.above()`;
  bone meal targets the clicked `pos`.
- `src/test/java/ca/bradj/questown/world/WarpWorldAccessTest.java` — existing
  in-memory unit tests using the map-based constructor (`level == null`); new
  tests follow the `chopTree_*` / `setBlockIntProperty_*` style.

### Institutional Learnings

- `gatherer-warp-loot-flaky` (memory) — random ceilings make warp autotests
  flake. Motivates the deterministic +3 bone-meal choice over a real 2–5 roll.
- "Warp vs Realtime parity" (memory) — warp reimplementations of MC mechanics are
  a recurring divergence source. Mitigated here: bone meal reuses the existing
  age primitive and planting uses the item's own default block state, so the new
  divergence surface is ~nil.
- `questown_job_archive/README.md` — jobs are archived for insufficient warp
  support; confirms the sapling case is intentionally not live (→ Target 2).

### External References

- None. Internal refactor with strong local patterns; no external research run.

---

## Key Technical Decisions

- **Dispatch on item type, no real-world fallback.** `BoneMealItem` → bone-meal
  path; `BlockItem` → plant path; everything else → `false` + log. The old
  `level`-backed `useOn` fallback is removed entirely. Rationale: kills the
  punch-through (R1, R4); archived sapling items become a safe no-op instead of a
  leak.
- **Plant at `pos.above()` using the item's default block state.** Mirrors
  realtime `BlockItem` placement on a `Direction.UP` hit; generic across crop
  types (wheat/beetroot/etc.); crop default state is age 0. (R2)
- **Bone meal = deterministic +3, capped at max.** Realtime is random 2–5
  (expected value 3.5 → floor 3). Deterministic avoids new autotest flakiness and
  reuses the `GrowCropsWarpRule.growBy` age primitive. (R3) *(see grill decision)*
- **No ADR.** The decision is ~15 lines and easily reversible (fails the
  hard-to-reverse bar). Context lives in a code comment + the doc update.

---

## Open Questions

### Resolved During Planning

- A2 (run real `useOn` against a capturing scratch level) vs A1 (hand-roll
  in-memory): **A1.** A2 is intractable — `VoidLevel` can't capture writes and
  isn't a `Level`; bone meal's `useOn` casts to `ServerLevel`. With only two live
  crop operations, A1's divergence risk is negligible.
- Bone-meal increment: **deterministic +3** (confirmed with user).
- Crop position: **`pos.above()`** for planting, **`pos`** for bone meal
  (confirmed against job defs + `GrowCropsWarpRule` read positions).

### Deferred to Implementation

- Exact item-dispatch predicate (`instanceof BoneMealItem` vs `item.is(BONE_MEAL)`):
  pick during implementation; both are guarded by the crop `age` check.
- Whether to extract a shared `advanceAge(pos, n)` helper used by both this method
  and `GrowCropsWarpRule` — decide once the code is in front of you; do not
  over-abstract for two call sites.

---

## Implementation Units

- [ ] U1. **Reimplement `WarpWorldAccess.useItemOnBlock` in-memory (with tests)**

**Goal:** Replace the real-world punch-through with in-memory dispatch so warp
planting/bone-meal use the snapshot/dirty-set, fixing the leak and parity bugs and
making the method unit-testable.

**Requirements:** R1, R2, R3, R4, R5

**Dependencies:** None

**Files:**
- Modify: `src/main/java/ca/bradj/questown/world/WarpWorldAccess.java`
- Test: `src/test/java/ca/bradj/questown/world/WarpWorldAccessTest.java`

**Approach:**
- Rewrite `useItemOnBlock(ItemStack item, BlockPos pos)`:
  - Bone meal → if `pos` has an `"age"` below its max: advance by `min(age+3, max)`
    via the existing int-property primitive; `dirtyBlocks.add(pos)`; return `true`.
    Otherwise return `false`.
  - `BlockItem` → place `getBlock().defaultBlockState()` at `pos.above()`;
    `blockStates.put(pos.above(), state)` + `dirtyBlocks.add(pos.above())`;
    return `true`.
  - else → `QT.JOB_LOGGER.error(...)`; return `false`; no world write.
- Remove the `UseOnContext` / `BlockHitResult` / `Vec3` imports if now unused.
- Add a short comment explaining why warp planting is hand-rolled rather than
  delegating to `useOn` (VoidLevel can't capture + isn't a `Level`; bone meal
  casts to `ServerLevel`).

**Execution note:** Test-first — write the failing in-memory tests against the
`level == null` constructor first; they currently can't even run because the old
method NPEs without a live level.

**Patterns to follow:**
- `WarpWorldAccess.chopTree` / `removeBlock` for snapshot + dirty mutation style.
- `GrowCropsWarpRule.growBy` / `isGrowableCrop` for the age read/cap/write shape.

**Test scenarios:**
- Happy path (plant): seed `BlockItem` (e.g. `wheat_seeds`) on a farmland pos →
  `pos.above()` holds the crop block at age 0; `dirtyBlocks` contains `pos.above()`;
  returns `true`.
- Happy path (bone meal): bone meal on a crop at `pos` with age 2 (max 7) →
  age becomes 5; `dirtyBlocks` contains `pos`; returns `true`.
- Edge case (bone-meal cap): bone meal on age 6 (max 7) → age clamps to 7, not 9;
  returns `true`.
- Edge case (bone-meal on maxed crop): age already at max → no change; returns
  `false` (or `true` with no-op — assert chosen contract and that no over-grow
  occurs).
- Error path (bone meal on non-crop): `pos` has no `"age"` property → returns
  `false`; `dirtyBlocks` unchanged.
- Error path (unsupported item): a non-`BlockItem`, non-bone-meal item (e.g.
  `Items.STICK`) → returns `false`; **no NPE** despite `level == null`; nothing
  marked dirty. (Proves the punch-through is gone — R4.)
- Integration (plant-then-grow parity): after planting at `pos.above()`, calling
  `GrowCropsWarpRule`-style growth via `setBlockIntProperty`/`getBlockIntProperty`
  on `pos.above()` reflects the planted crop (snapshot is authoritative). Covers
  the parity-bug fix.

**Verification:**
- New tests pass; `./gradlew compileJava` clean.
- `useItemOnBlock` contains no reference to `level`, `UseOnContext`, or `useOn`.

---

- [ ] U2. **Update the decoupling doc to reflect reality**

**Goal:** Record that live planting/bone-meal are now in-memory and point the
tree-rule remainder at the Target 2 plan, so the doc stops being stale.

**Requirements:** R5 (honesty of the tier story)

**Dependencies:** U1

**Files:**
- Modify: `docs/special-rules-decoupling.md`

**Approach:**
- Phase 4 item 1 area: note that `use_last_inserted_item_on_block` is now fully
  in-memory in `WarpWorldAccess` for the live crop operations (plant + bone meal).
- Phase 4 item 2 (Tree rules): correct the stale claim — `chopTree` is already
  in-memory; sapling placement + plantability are deferred and tracked in
  `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`.
- Optionally annotate the consolidated interface notes for `useItemOnBlock`.

**Test scenarios:**
- Test expectation: none — documentation-only change.

**Verification:**
- Doc no longer states `useItemOnBlock` "still delegates to the real world"; the
  arborist remainder links to the Target 2 plan.

---

## System-Wide Impact

- **Interaction graph:** `useItemOnBlock` is called from
  `UseLastInsertedItemOnBlockSpecialRule.beforeExtract`, which runs in both
  realtime and warp. Only the warp implementation changes; realtime is untouched.
- **State lifecycle risks:** The fix *removes* a partial-write/leak risk by
  routing through the deferred `applyTo` commit. Confirm planted crops land in
  `dirtyBlocks` so `applyTo` writes them back.
- **API surface parity:** `MinecraftWorldAccess.useItemOnBlock` intentionally
  keeps using real `useOn` — the two implementations now differ in mechanism but
  must agree in observable result for the two live crop operations (asserted by
  position/age tests).
- **Unchanged invariants:** `chopTree`, `CheckTreePlantable`, container/furnace
  methods, and all realtime behavior are unchanged.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Warp planting diverges from realtime (wrong block/position/age) | Tests assert `pos.above()` + age 0 for planting and `pos` + capped age for bone meal; both mirror realtime `useOn` semantics |
| A future non-crop `BlockItem` flows through and gets placed at `pos.above()` | Live job defs restrict ingredients to seeds; behavior still mirrors realtime `BlockItem` placement; documented as accepted |
| Deterministic +3 under/over-credits vs realtime random 2–5 | Accepted, documented; effect is marginal because `GrowCropsWarpRule` also advances age over a warp |

---

## Sources & References

- **Origin document:** `docs/special-rules-decoupling.md` (Phase 4)
- Related code: `src/main/java/ca/bradj/questown/world/WarpWorldAccess.java`,
  `src/main/java/ca/bradj/questown/jobs/special/GrowCropsWarpRule.java`,
  `src/main/java/ca/bradj/questown/town/entity/TownFlagState.java`
- Follow-up plan: `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`
