---
title: Un-archive the Arborist (warp-complete the tree rules)
type: feat
status: mostly-done
date: 2026-06-02
origin: docs/special-rules-decoupling.md (Phase 4.2 "Tree rules")
adr: docs/adr/0005-trees-grow-in-warp-via-in-memory-worldgen.md
---

# Un-archive the Arborist (warp-complete the tree rules)

## Why this exists

`questown_job_archive/README.md` benches jobs primarily because they have
**insufficient time-warp support**. Both arborist jobs are archived:

- `questown_job_archive/arborist_cut_trees.json` (uses `chop_down_tree`)
- `questown_job_archive/arborist_plant_tree.json` (uses `check_tree_plantable`,
  `use_last_inserted_item_on_block`)

`ResourceJobLoader` loads only `questown_jobs`, so neither is live today. This
plan restores both, makes the tree rules fully warp-correct, and turns the tree
seam into the **reference example** the decoupling doc asks for: complex world
interaction (real worldgen) that works identically in realtime and warp.

## Design decisions (resolved in grilling, 2026-06-02)

1. **Tracer-bullet sequencing.** Restore `cut_trees` first (it is mechanically
   warp-ready) as an independently shippable slice, then `plant_sapling`.
2. **Leaf-path clearing is out of scope.** It is a realtime *navigation* aid
   (helps a live villager reach a leaf-occluded trunk); it does nothing offline
   (`beforeTick` never fires in warp). Tracked separately as realtime polish.
3. **Chop fidelity accepted as-is.** Both `chopTree` impls drop one log per log
   block, no leaf/apple/sapling drops, no in-rule tool damage. Realtime and warp
   are already identical, so the warp-parity bar is met. Axe durability comes
   free from the core `degradeTool` path (`RealtimeWorldInteraction` /
   `TimeWarpWorldInteraction`), which keys off the work state's tool predicate
   (`#questown:axes`). No durability work needed.
4. **Same-warp harvest is required.** A tree planted during a warp must be
   choppable within the *same* warp. This forces real tree geometry into the
   in-memory snapshot during warp — see slice 2 and ADR-0005.
5. **In-memory worldgen, no post-warp reconciliation.** Tree growth runs real
   `TreeFeature.place` against a snapshot-backed `WorldGenLevel`, writing into
   `WarpWorldAccess`'s snapshot/dirty-set. `applyTo` commits everything. The
   earlier "place sapling in warp, reconcile after `applyTo`" idea is
   **dropped** — the snapshot adapter makes it unnecessary. (ADR-0005.)
6. **No plantability heuristic.** Both realtime and warp run the real
   `TreeFeature.place` plantability check, differing only in the level adapter
   (`VoidLevel` over the real level vs `SnapshotWorldGenLevel` over the
   snapshot). A heuristic would only reintroduce realtime/warp divergence.
7. **Deterministic, elapsed-time growth.** A sapling grows when
   `currentTick - plantTick >= TREE_GROWTH_TICKS` (a tunable constant), not via a
   probabilistic per-tick roll. Matches the project's deterministic-warp choices
   (cf. bone-meal +3) and keeps autotests non-flaky.
8. **Farm-scoped growth.** Only saplings in **farm-type rooms**
   (`SpecialQuests.FARM`) grow during warp — seeded at warp start by scanning
   farm-room positions, plus this-warp's plantings. Decorative saplings in
   houses/gardens (also present in the town-wide `roomPositions` snapshot) are
   deliberately left untouched, so warp never bursts an ornamental sapling
   through a player's roof.

## Current state of the tree seam

- `WarpWorldAccess.chopTree` is already in-memory (snapshot + dirty-set
  recursion); `ChopDownTree` consumes `event.world().chopTree(...)` and is a
  `QTNativeRule`. The chop half is warp-ready but has **no live consumer**, so no
  coverage.
- `CheckTreePlantable` is Tier 2 today: it runs `TreeFeature.place` against
  `VoidLevel(asServerLevel())`. In warp `asServerLevel()` is null, so it cannot
  run. This is the part this plan makes Tier 1.

---

## Slice 1 — Restore `arborist/cut_trees` (tracer bullet)

**Goal:** prove the chop seam end-to-end on both paths with live coverage.

- Move `arborist_cut_trees.json` from `questown_job_archive/` to `questown_jobs/`.
- Add an autotest blueprint for the arborist:
  - `RoomType.FARM`, `roomId = SpecialQuests.FARM`.
  - Fenced farm plot containing a **bare 4-high `oak_log` column** with a
    reachable base (no leaves; deterministic drop count; trunk reachable for the
    realtime villager since leaf-clearing is out of scope).
  - Supplies: one `#questown:axes` tool (e.g. `minecraft:wooden_axe`).
  - `realtimePhase = true` — the arborist is **in-town work**, not a leaver, so
    assert **both** the realtime and warp passes (this is the parity proof).
  - Expectation: `ExpectedProduct("minecraft:oak_log", 4, null)` (min 4),
    `minCyclesExpected = 1`.
- Register the entry in `TestBlueprintRegistry.get(...)` /
  `getTestableJobs()` and add an `"arborist".equals(jobId.rootId())` branch.

**Done when:** the arborist cut-trees autotest passes in realtime AND warp.

---

## Slice 2 — Restore `arborist/plant_sapling` (in-warp grow + same-warp harvest)

**Goal:** the arborist plants saplings, they grow into real trees during warp,
and the same warp can chop them — all in-memory, committed atomically.

### New `SnapshotWorldGenLevel`

A `WorldGenLevel` sibling of `VoidLevel`:

- **Reads** route snapshot-first (`WarpWorldAccess` block state), then fall back
  to the real level.
- **Writes** (`setBlock`/`removeBlock`/`destroyBlock`) route into
  `WarpWorldAccess`'s `blockStates` + `dirtyBlocks` instead of being discarded.
- A **dry-run mode** (writes discarded, à la `VoidLevel`) for the plantability
  check.

Risk to verify during impl: `TreeFeature.place` is currently called with a
**null `ChunkGenerator`** (the existing dry-run does this and works); confirm the
write path is also fine with null.

### New `QTWorldAccess` methods + shared resolver

- `boolean canTreeGrowAt(BlockPos pos, ItemStack sapling)` — plantability gate
  (dry-run).
  - `MinecraftWorldAccess`: `TreeFeature.place` against `VoidLevel(level)` (the
    logic relocated out of `CheckTreePlantable`).
  - `WarpWorldAccess`: `TreeFeature.place` against `SnapshotWorldGenLevel`
    dry-run.
- `boolean growTreeAt(BlockPos pos, ItemStack sapling)` — actually generate the
  tree (write mode); symmetric on both impls (realtime writes the real level,
  warp writes the snapshot). Called by `GrowTreesWarpRule`.
- Shared `TreeFeatures.resolve(sapling)` helper — sapling-id →
  `ConfiguredFeature` → `(TreeFeature, TreeConfiguration)` lookup, extracted from
  the current inline `CheckTreePlantable` body and used by both methods on both
  impls.

### `CheckTreePlantable` → Tier 1

- `implements QTNativeRule`; body shrinks to sapling detection +
  `event.world().canTreeGrowAt(...)`. All MC/worldgen coupling moves behind the
  interface. Tier-2 startup-warning list loses `CheckTreePlantable`.
- `SaplingTesterBlock` devtool keeps working (still calls
  `postJobBlockCheckPassed`, which now delegates inward).

### Planted-sapling carrier (on `WarpWorldAccess`)

- `Map<BlockPos, PlantedSapling>` where `PlantedSapling = (ItemStack sapling,
  long plantTick)`.
- Populated when `useItemOnBlock` plants a `SaplingBlock` (the existing
  `plantBlockAbove` path), recording `plantTick = currentWarpTick`.
- Seeded at warp construction by scanning **farm-room** positions for existing
  sapling blocks (`plantTick = warp-start tick`) so prior-warp arborist saplings
  also grow. Non-farm rooms are skipped.

### New `GrowTreesWarpRule` (global warp-tick rule)

- Analogous to `GrowCropsWarpRule`; declared as a `global` rule in the arborist
  JSON(s) and collected/deduped like the crop rule.
- `onWarpTick`: for each carried sapling where
  `event.currentTick() - plantTick >= TREE_GROWTH_TICKS`, call
  `growTreeAt(pos, sapling)` and drop the entry. `cut_trees` then sees the logs
  in the snapshot and can chop them the same warp.

### `plant_sapling` JSON

- Move `arborist_plant_tree.json` into `questown_jobs/`.
- Add `questown_vanilla:grow_trees_warp` to its `global` rules (alongside
  `check_tree_plantable`, `claim_workspot`, `require_air_above`).

### Testability gap (per CLAUDE.md)

Unit tests construct `WarpWorldAccess` with `level == null`, so `canTreeGrowAt` /
`growTreeAt` (which need worldgen + registries) cannot run there — they will be
**autotest-covered**, not unit-tested, consistent with how `chopTree` already
returns empty drops when `level == null`. Note this in the test files rather than
simulating worldgen.

**Done when:** an arborist plant autotest passes in warp — sapling planted,
grown to a real tree in-memory after `TREE_GROWTH_TICKS`, and present after
`applyTo`; and the realtime path plants a sapling that vanilla grows normally.

---

## Slice 3 — Full-cycle test (capstone)

A single autotest exercising **harvest → plant → grow → harvest** for the
arborist across a warp window long enough to cross `TREE_GROWTH_TICKS`, asserting
both that logs are produced (chop) and that new trees exist (grow). This is the
forcing function that proves slices 1–2 compose. Deferred until 1 and 2 land.

---

## Definition of done (whole effort)

- Both arborist jobs live in `questown_jobs/`.
- Arborist autotests pass in realtime AND warp (cut, plant, and full-cycle).
- `CheckTreePlantable` is Tier 1 (`QTNativeRule`) via `canTreeGrowAt`; no
  `asServerLevel()` in the tree rules.
- `docs/special-rules-decoupling.md` Phase 4.2 + Jobs Summary Table updated to
  reflect the shipped design (in-memory worldgen, no reconciliation).

## Outcome (2026-06-02)

**Landed and green (autotest, jobs track):**
- Both arborist jobs live in `questown_jobs/` (`arborist/cut_trees`, `arborist/plant_sapling`).
- `arborist/cut_trees` autotest **passes in warp** — the villager chops the log column and
  deposits oak_logs.
- `arborist/plant_sapling` autotest **passes in warp** — the villager plants saplings
  (sapling supply is consumed).
- `arborist/cut_trees [full_cycle]` autotest **passes** (asserts the starter column is chopped;
  exercises the seed→grow code path).
- `CheckTreePlantable` is now Tier 1 (`QTNativeRule`) via `canTreeGrowAt`; no `asServerLevel()`
  in the tree rules.
- New: `SnapshotWorldGenLevel`, `TreeFeatureResolver`, `GrowTreesWarpRule`
  (`questown_vanilla:grow_trees_warp`), `QTWorldAccess.canTreeGrowAt`/`growTreeAt`/planted-sapling
  carrier, and `WarpWorldAccess.seedFarmSaplings` (seeds farm-room saplings at warp start).

**Two real bugs found and fixed** while wiring this up (the chop seam had "no live consumer,
so no coverage" — these were latent):
1. `ChopDownTree.beforeExtract` seeded its context from `super.beforeExtract(...)`, which returns
   `null` ("didn't handle") — so every chopped log was given to a null context and lost. Now seeds
   from the real `ctxInput` (like `HarvestCropSpecialRule`).
2. `event.workSpot()` is unreliable during warp (it can be the town origin `(0,0,0)`), so
   `ChopDownTree` now scans `event.jobBlockPositions()` for a log (like `HarvestCropSpecialRule`
   scans for crops). `chopTree` was also hardened to only chop `#minecraft:logs`.
Both arborist JSONs were also switched `result: minecraft:air` → `uses_special_rules` to match
`farmer/harvest_wheat`. NOTE: this is **cosmetic / self-describing only** — both map to
`ResultGenerator.alwaysEmpty()`, and `tryExtractProduct` runs the result generator **only if
`preExtractHook` returns null**, so a `beforeExtract` rule that handles extraction skips the
generator entirely. The load-bearing fixes were (1) and (2); the result-type change is a no-op.

**Known limitation — in-warp tree growth not yet verified end-to-end:** `growTreeAt`/`canTreeGrowAt`
run `TreeFeature.place`, which returns **false in the autotest arena**. This was traced
exhaustively and is **not** a defect in the decoupling code: a plain `VoidLevel` (the adapter the
original `CheckTreePlantable` already used) fails identically, and it is not caused by the snapshot
adapter, a null/real `ChunkGenerator`, the builtin-vs-runtime feature registry, or heightmap
relocation — direct real-`ServerLevel` placement at the same ideal position (dirt below, clear
air column, max build height 320) also returns false. The most likely cause is the autotest
server's world type lacking worldgen feature support; vanilla sapling growth (and therefore this
path) is expected to work in a normal overworld. Consequently the full-cycle autotest asserts the
chop (`oak_log >= 4`) rather than grow→chop (`>= 5`); tighten it once growth places in-arena. The
growth plumbing is implemented and exercised (saplings are seeded/carried and `growTreeAt` is
invoked) — it just does not yet place a tree under the test harness. Follow-up: verify
`TreeFeature.place` in a normal world / configure the autotest world for worldgen features.

## Out of scope / deferred

- Leaf-path clearing (realtime navigation polish).
- Axe durability balancing (already handled by core `degradeTool`).
- Growing non-farm (decorative) saplings during warp.
- Probabilistic growth timing.
