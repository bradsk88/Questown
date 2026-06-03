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
  placement, as vanilla does; feature resolved from the live `registryAccess`).
- **Resolution (2026-06-03):** The earlier "the test server's world type lacks worldgen feature
  support" hypothesis was **wrong**, and was based on a flawed control: the sapling was cleared
  only in the *snapshot*, so the real-`ServerLevel`/`VoidLevel` controls still saw an
  `oak_sapling` at the trunk base (not "free") and failed trivially. `TreeFeature` does **no**
  heightmap relocation in 1.19.2 — it places at `pos` directly. The real cause of `place()`
  returning false is `getMaxFreeTreeHeight < treeHeight`: a block in the tree's footprint wasn't
  free (air/leaves/logs). In the headless suite the obstructions were (1) **prior-scenario
  residue** (cook/smelter cobblestone walls) surviving because the old `flatten` only cleared a
  `±7 × 5`-high box while the arborist farm extends to offset +10 and a tree needs ~9 vertical,
  and (2) **crowded saplings** — three spaced 2 apart, inside each other's foliage radius (a
  sapling block isn't "free"), so none could grow. Fix: jobs-track `flatten` now clears the full
  build volume + tree headroom (`TestArenaPreparer.buildVolume`), and `full_cycle` seeds one
  centered sapling. The worldgen path itself was correct all along.
- **Deterministic growth (2026-06-03):** `growTreeAt`/`canTreeGrowAt` previously passed
  `level.random` into `TreeFeature.place`, making the grown trunk's height (oak: 4–6 logs)
  non-deterministic — a warp-determinism violation (cf. bone-meal +3) and a test-flake source.
  Both paths now seed the RNG by block position (`TreeFeatureResolver.seededFor(pos.asLong())`),
  so a sapling at a given position always grows the same tree. The warp loop fells one trunk per
  `full_cycle`, so the assertion is `oak_log >= 5`: the starter column alone is 4, and the
  deterministic grown oak is a fixed 5, making the grow→chop signal robust rather than 1-in-3 flaky.
