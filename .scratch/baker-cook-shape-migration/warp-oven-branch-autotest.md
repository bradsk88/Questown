---
title: Autotest coverage for the warp-lane bread-oven branch
status: resolved
created: 2026-10-10
resolved: 2026-10-10
---

**RESOLVED 2026-10-10 (same day).** No new scenario was owed: the existing
`baker/bread` held autotest IS the end-to-end warp-lane oven test. It failed on its
first held run (2026-10-10 05:55 — the warp work-block assignment tested the slot-gated
`isJobBlock` predicate, so `baker/extract` could never be assigned on an empty oven and
the warp's baked bread was never extracted) and PASSed on 06:19 after the assignment
moved to the block-identity check (`Work.isJobBlockAt` → `shouldInitializeWorkState`;
`PostDowntimeWarper.findAssignedWorkBlock` round-robins matches). PASS verdict:
`After {bread=4, wheat=26, coal=10}`, `minecraft:bread expected >= 1, got 4`. See
docs/plans/2026-10-10-002 "Post-plan follow-up", `town/COMPLEX.md` "Post-downtime warp
work-block assignment", and `jobs/COMPLEX.md` "Warp-lane work-block seam".

## Context

The 2026-10-10 cook-shape migration (`docs/plans/2026-10-10-002-feat-baker-cook-shape-migration-plan.md`)
gave `WarpWorldAccess.advanceProcessing` a bread-oven branch, selected by
`blockStates.get(pos).getBlock() instanceof BreadOvenBlock` and driving the
shared `BreadOvenBake` state machine against the in-memory slot snapshot.

**What is unit-tested:**

- `BreadOvenBakeTest` — the bake arithmetic both lanes share (1 wheat + 1
  coal -> 1 bread over 1000 ticks, output stacking, partial-progress reset,
  cross-call progress, takeBread).
- `BakerEquivalenceTest` — ticker-vs-advancer equivalence + real completion
  for `baker/bread`, `baker/fuel`, `baker/extract` (the slot rules; the warp
  branch is NOT exercised — the equivalence framework's world is
  `TestWorldAccess`, not `WarpWorldAccess`).
- `WarpWorldAccessTest.oven_warpBranch_requiresRegistryBoundBlock_cannotReachInUnitTest`
  pins the gap: a `BreadOvenBlock` cannot be constructed in a unit test
  (registry-bound `Block.<init>` post-bootstrap).

**What is NOT yet verified:** the full warp lane end-to-end for a baker room
— warp actually advancing the oven through `SmeltFurnaceWarpRule`, writing
bread back to the real oven BE via `applyTo`, and `baker/extract` picking it
up. That is a held-run / autotest question (see `questown-held-run` and
`TestBlueprintRegistry.bakerBlueprint` — the fixture is unchanged: oven +
chest + wheat + coal).

## Suggested shape

Add a `baker/oven_warp` autotest scenario (or extend an existing held-run
scenario that changes a villager to baker): after `warpTime`, assert the
oven BE slot 2 holds bread, slots 0/1 are consumed, and the baker townie
extracts. Revisit whenever the QTWorldAccess seam
(`docs/special-rules-decoupling.md`) lands — that may make the branch
unit-testable and this issue redundant.
