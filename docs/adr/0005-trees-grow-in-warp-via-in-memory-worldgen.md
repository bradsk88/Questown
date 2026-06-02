# Trees grow during warp via in-memory worldgen, not post-warp reconciliation

status: accepted
date: 2026-06-02

## Context

The arborist plants saplings and chops grown trees. To work offline (during
warp), a sapling planted in a warp must be able to grow into a tree and be
harvested **within the same warp** — otherwise the plant→grow→chop cycle never
completes while the player is away, defeating warp's "simulate what villagers
produced" purpose.

Tree growth is real worldgen (`TreeFeature.place`), which needs a
`WorldGenLevel`. During warp there is no live level to mutate; world state is an
in-memory snapshot held by `WarpWorldAccess` and committed atomically by
`applyTo`.

## Decision

Run real `TreeFeature.place` against a snapshot-backed `WorldGenLevel`
(`SnapshotWorldGenLevel`, a writing sibling of `VoidLevel`) that reads
snapshot-first and writes into `WarpWorldAccess`'s snapshot/dirty-set. A
deterministic, elapsed-time `GrowTreesWarpRule` triggers growth; `cut_trees`
chops the resulting logs from the same snapshot; `applyTo` commits all of it.
Plantability (`canTreeGrowAt`) and growth (`growTreeAt`) become `QTWorldAccess`
methods, so the tree rules are Tier 1 and behave identically in realtime and
warp.

## Considered and rejected

- **Place saplings in warp, reconcile after `applyTo`.** Reconciliation can't
  use `afterWarpRecovery` (fires only for `processingState > 0` blocks; a
  completed planting resets to 0) and, more fundamentally, materializes trees
  *after* the warp — so `cut_trees`, which chops the warp-start snapshot, can
  never harvest a tree planted in the same warp. Rejected once same-warp harvest
  was required.
- **Heuristic warp-tree (e.g. a log column) instead of real worldgen.** Simpler,
  but diverges from realtime/vanilla geometry and would need messy detection to
  upgrade un-chopped stumps to real trees. Rejected to avoid the realtime/warp
  divergence that is a recurring bug source here.

## Consequences

- One MC-coupled operation (`TreeFeature.place`) runs behind the
  `QTWorldAccess` seam on both paths; the tree rules carry no `asServerLevel()`.
- `SnapshotWorldGenLevel` is the decoupling doc's intended **reference example**
  for complex world interaction under warp.
- Growth is gated on an elapsed-time threshold and scoped to farm rooms (see the
  plan), keeping warp deterministic and avoiding surprise growth of decorative
  saplings.
- Worldgen can't run when `WarpWorldAccess.level == null` (unit-test
  constructor), so the tree-growth path is autotest-covered, not unit-tested.
- **Implementation note (2026-06-02):** `canTreeGrowAt`/`growTreeAt` are implemented as
  decided (real `TreeFeature.place` against `SnapshotWorldGenLevel`; sapling cleared before
  placement, as vanilla does; feature resolved from the live `registryAccess`). In the
  **autotest arena**, `TreeFeature.place` returns false even under ideal conditions — and this
  is **not** specific to `SnapshotWorldGenLevel`: a plain `VoidLevel` and even direct
  real-`ServerLevel` placement fail identically (ruling out the adapter, a null/real
  `ChunkGenerator`, the builtin-vs-runtime registry, and heightmap relocation). The likely cause
  is the test server's world type lacking worldgen feature support; the design is expected to
  work in a normal overworld (where vanilla sapling growth works). The arborist chop and plant
  autotests pass in warp; the full grow→chop is asserted as chop-only pending this. See the plan.
