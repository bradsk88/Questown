---
title: Fix the WarpWorldAccess.chopTreeInMemory StackOverflowError (unbounded recursion depth on large connected log regions)
status: closed
closed: 2026-10-10
created: 2026-10-10
---

## Resolved (2026-10-10)

Shipped as the iterative-flood-fill fix: `WarpWorldAccess.chopTreeInMemory`
and `MinecraftWorldAccess.chopTreeRecursive` now use an explicit
`ArrayDeque<BlockPos>` work stack instead of per-block recursion. Pinned by
`WarpWorldAccessTest.chopTree_cutsLargeConnectedRegion_withoutStackOverflow`
(5000-log line; verified red on the recursive variant, green on the
iterative one). The 57-scenario autotest gate ran to completion with
**0 StackOverflow** and no server crash (182 PASS / 29 FAIL — all FAILs are
pre-existing expectation failures tracked in `arborist-not-depositing-logs.md`,
not this bug). Mechanism + test seam documented in
`src/main/java/ca/bradj/questown/world/COMPLEX.md`.

## Context

Found by the full 57-scenario autotest suite run on 2026-10-10 (night-cleanup
session; see `.scratch/night-cleanup-2026-10-10.md`). The suite ran clean
through the first ~17 scenarios, then the tree-chop recursion took out the
arborist scenarios and **crashed the server**. Test index varies with world
seed ordering: first run died at test 18/57 (`arborist/cut_trees
[full_cycle]`, crash report `crash-2026-10-10_06.43.02-server.txt`); the
repro run died at test 16/57 (base `arborist/cut_trees`, crash report
`crash-2026-10-10_18.08.50-server.txt`).

**Not a regression** from `aec8a7f6` (the warp work-block assignment change) —
that touched container-block assignment only; the tree-chop path is untouched.
Pre-existing bug.

## Evidence

- Crash reports above: `java.lang.StackOverflowError` with **~1000
  self-recursive `chopTreeInMemory` frames**; the innermost frame was inside
  `resolveBlockState` → `level.getBlockState` → `getChunk` →
  `ChunkMap.schedule` / `DistanceManager.addTicket` — i.e. **chunk
  generation/registration needed the last sliver of stack on top of the
  ~1000 already-consumed chop frames**.
- Instrumented repro (`[chopdbg]` depth/cuts logging, since removed): one
  `chopTree` call in the crashed run reached **depth 3300+ / 29,482 cuts**
  before the SOE. The cut region sat at **y=4..7** and spanned roughly
  x∈[-156,77], z∈[-61,216] — hundreds of blocks from the y=64 arena.
- The region is **not a natural forest**: the flood's own footprint is what
  makes it — the fallback `level.getBlockState(pos)` generates chunks on
  demand as the fill wanders (the SOE stack's chunk-scheduling frames prove
  live chunk generation mid-flood), and in-memory cuts (`blockStates` +
  `dirtyBlocks`) feed the next fill in the same warp.

## Mechanism (corrected)

The flood fill is **recursive: one JVM stack frame per cut block**. In the
accumulated suite world the connected `matchBlock` region reachable from the
trunk is tens of thousands of blocks wide (the fill is not bounded to the
room/arena — `resolveBlockState` falls back to the live `ServerLevel` for
positions outside the snapshot, and the level happily generates chunks), so
recursion depth reaches the 1 MB thread-stack budget and the SOE lands
wherever the innermost frame happens to need a little extra stack (here:
chunk scheduling).

**First diagnosis (refuted 2026-10-10): the "moving-key" theory.** It claimed
`adjacent.immutable()` on the `betweenClosed` cursor is a no-op, so shared
mutable keys broke `visited`/`blockStates` and caused infinite re-cutting.
Refuted empirically:

- `MutableBlockPos.immutable()` is `new BlockPos(this)` (verified in
  bytecode) — a fresh **frozen copy**; `BlockPos.immutable()` is a true
  no-op because `BlockPos` is already immutable. Keys are stable.
- L-shape / 605-log box / 400-log snake probe tests: exact cut counts, zero
  re-cuts, `visited=true` on every re-encounter. The guards are sound.

The realtime twin `MinecraftWorldAccess.chopTreeRecursive` has the same
unbounded-recursion shape (its dedupe is "the block is now AIR", which is
sound — the SOE risk is purely the frame-per-block depth).

## Fix (implemented)

- `WarpWorldAccess.chopTreeInMemory`: converted to an **iterative flood fill**
  with an explicit `ArrayDeque<BlockPos>` work stack; each position is
  resolved and cut at most once (`visited` marks on pop). Same cut set as the
  recursion, same window (`center±1 x/z, +0/+1 y`), no stack growth.
- `MinecraftWorldAccess.chopTreeRecursive`: same iterative treatment (kept the
  "re-examined blocks must still match" property — a cut block is AIR, so a
  stale work-stack entry is a no-op).
- Regression test:
  `WarpWorldAccessTest.chopTree_cutsLargeConnectedRegion_withoutStackOverflow`
  — 5000-block OAK_LOG line supplied through the
  `fallbackStateProvider` test seam (standing in for the real world beyond
  the snapshot). Verified **red** on the recursive variant
  (`failures=1`, `StackOverflowError`) and **green** on the iterative one.
- A `fallbackStateProvider` test-constructor seam (5-arg
  `WarpWorldAccess(Map, Map, fn, int, Function<BlockPos,BlockState>)`) was
  added this session for exactly this pin — previously `resolveBlockState`
  returned null for unknown positions when `level == null`, making the flood
  untestable in a plain unit test.

## Verification results (2026-10-10, gate passed)

- Full 57-scenario suite (`/tmp/chop-suite-verify.log`): **0 StackOverflow,
  server never crashed, all 57 tests ran to completion** (182 PASS / 29
  FAIL — the FAILs are pre-existing expectation failures, tracked in
  `arborist-not-depositing-logs.md`, not crashes).
- `arborist/plant_sapling` PASS; both `cut_trees` variants now complete with
  verdicts (FAIL on the pre-existing deposit gap, not a crash).
- `WarpWorldAccessTest`: 34/34 incl. the new
  `chopTree_cutsLargeConnectedRegion_withoutStackOverflow` (red-verified on
  the recursive variant).

## Acceptance criteria

- `arborist/cut_trees`, `arborist/plant_sapling`, `arborist/cut_trees
  [full_cycle]`, and `organizer/fetch` each PASS **with verdict lines** in a
  full-suite autotest run.
- No `Time warp raised exception` / `StackOverflowError` in that run.
- The full 57-scenario suite completes without crashing the server.
- `:test` green modulo the known `todo_…` harness-gated markers;
  `build -x test` green.

## Notes

- **Related, minor:** `TestExecutor.runWarp` marks "Warp returned null state"
  as an error and moves to `Phase.DONE` with **no [PASS]/[FAIL] verdict line**
  for the scenario — a crashed scenario looks like a hang and the final tally
  under-counts. Consider emitting a `[FAIL]` verdict on the warp-null/error
  path so the summary is honest.
- Open question (not blocking this fix): whether the flood should be bounded
  to the room/arena instead of following the log type through the live
  terrain. The current fix makes the unbounded fill *safe*; bounding it would
  also stop a `cut_trees` warp from cutting real terrain logs outside the
  arena. Decided after the suite gate.
