---
title: Fix the WarpWorldAccess.chopTreeInMemory infinite recursion (StackOverflowError on warp tree-chops)
status: ready-for-agent
created: 2026-10-10
---

## Context

Found by the full 57-scenario autotest suite run on 2026-10-10 (night-cleanup
session; see `.scratch/night-cleanup-2026-10-10.md`). The suite ran clean
through test 18/57 (36 PASS verdicts, 0 FAILs), then the recursion bug took
out the arborist scenarios and finally **crashed the server** at test 19/57
(`organizer/fetch`).

**Not a regression** from `aec8a7f6` (the warp work-block assignment change) —
that touched container-block assignment only; the tree-chop path is untouched
this session. Pre-existing bug.

## Evidence

- `run/logs/latest.log` (2026-10-10 06:42:50 and 06:43:02):
  `Time warp raised exception` → `java.lang.StackOverflowError`, stack
  self-recursing at `WarpWorldAccess.chopTreeInMemory(WarpWorldAccess.java:263)`
  through `resolveBlockState` → `level.getBlockState` (chunk loads inside the
  recursion).
- `run/crash-reports/crash-2026-10-10_06.43.02-server.txt` — the third
  occurrence propagated out of the warp's try/catch and killed the whole
  server (test 19/57 organizer/fetch).
- Tests 16 (`arborist/cut_trees`) and 18 (`arborist/cut_trees [full_cycle]`)
  died via the "Warp returned null state" path **without a [PASS]/[FAIL]
  verdict line** (see Notes).

## Mechanism (verified against the stack trace)

`BlockPos.betweenClosed` returns an iterator that **mutates one shared
`BlockPos`** as it advances. `adjacent.immutable()` is a **no-op on a mutable
`BlockPos`** (it returns `this`), so the *same object* is stored as the key in
both the `visited` set and the `blockStates` map, then its coordinates change
under it. Subsequent hash lookups miss → the "removed-in-memory" block is
still visible via the `level.getBlockState` fallback (the real world is
unmodified by a warp snapshot) → the block gets re-cut and re-recursive forever
→ stack overflow.

The real-world `MinecraftWorldAccess.chopTreeRecursive` (line ~122) has the
same `immutable()`-no-op smell — it passes the live iterator object into the
recursive call — but survives because it actually removes blocks from the
world, so re-visits find air. Same treatment owed as a hygiene fix.

## Fix (spec)

- In `WarpWorldAccess.chopTreeInMemory`: materialize the position before it is
  used as a key — `BlockPos pos = new BlockPos(adjacent);` (a real copy) — and
  use that for `visited.contains` / `visited.add` / `blockStates.put` /
  `resolveBlockState` / the recursive call. (~4 lines.)
- In `MinecraftWorldAccess.chopTreeRecursive`: pass a copy into the recursive
  call (no `visited` set there; the smell is the shared mutable object).
- Verification is the held autotest, not a unit test: a chop needs block states,
  and the `WarpWorldAccess` unit-test constructor (level=null) returns null
  from `resolveBlockState`, so the recursion is unreachable in a plain unit
  test. If a seam makes a unit pin possible, add one; otherwise note it as
  harness-gated per the test doctrine.

## Acceptance criteria

- `arborist/cut_trees`, `arborist/plant_sapling`, `arborist/cut_trees
  [full_cycle]`, and `organizer/fetch` each PASS **with verdict lines** in a
  held autotest run.
- No `Time warp raised exception` / `StackOverflowError` in that run.
- The full 57-scenario suite completes (or at minimum runs past test 19/57
  without crashing the server).
- `:test` green modulo the 6 known `todo_…` harness-gated markers;
  `build -x test` green.

## Notes

- **Related, minor:** `TestExecutor.runWarp` marks "Warp returned null state"
  as an error and moves to `Phase.DONE` with **no [PASS]/[FAIL] verdict line**
  for the scenario — a crashed scenario looks like a hang and the final tally
  under-counts. Consider emitting a `[FAIL]` verdict on the warp-null/error
  path so the summary is honest.
