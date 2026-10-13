# world/ — world access for warps and realtime

## `chopTree` flood fill is iterative, not recursive

Both `WarpWorldAccess.chopTreeInMemory` and
`MinecraftWorldAccess.chopTreeRecursive` flood-fill the connected region of
the trunk's `matchBlock` (a 1-block cut, drops added per block) using an
explicit `ArrayDeque<BlockPos>` work stack, **not** recursion.

Why: the fill is unbounded by design — `WarpWorldAccess.resolveBlockState`
falls back to the live `ServerLevel` for positions outside the in-memory
snapshot (chunk generation included), so the reachable log region in an
accumulated world can be tens of thousands of blocks. One JVM stack frame per
block overflowed the ~1 MB thread stack in the 57-scenario autotest suite
(2026-10-10: depth 3300+ / 29,482 cuts before the `StackOverflowError`
killed the server; see `.scratch/warp-tree-chop/chop-treememory-infinite-recursion.md`).
The iterative form has the identical cut set (same window, same match, same
`blockStates.put(AIR)` + `dirtyBlocks` + drops) — only traversal order
changes.

The warp variant marks **every popped position** visited (resolved at most
once); the realtime variant keeps the "re-examined blocks must still match"
property instead — a cut block is AIR, so stale work-stack entries are no-ops.

Pinned by `WarpWorldAccessTest.chopTree_cutsLargeConnectedRegion_withoutStackOverflow`
(5000-log line; red on the recursive variant, green on the iterative one).

## `WarpWorldAccess` test constructor with `fallbackStateProvider`

The 5-arg package-private test constructor
(`Map, Map, recipeResolver, fuelTimeProvider, Function<BlockPos,BlockState>`)
stands in for the real world when `level == null`: `resolveBlockState`
resolves snapshot → level → fallback provider in that order. This is what
makes the flood fill unit-testable at scale (the 4-arg form resolves unknown
positions to null, making large-region fills unreachable in plain unit
tests). Tests must bootstrap `SharedConstants.tryDetectVersion()` +
`Bootstrap.bootStrap()` first (registry-bound `Blocks`/`ItemStack` usage).

## Warp chunk reads must be presence-gated (2026-10-13 full-suite hang)

`WarpWorldAccess.resolveBlockState` fell back to `level.getBlockState(pos)`
for positions outside the in-memory snapshot. For a chunk that is
generated but EVICTED from the chunk cache, that goes through the chunk
system's managed block on the main thread, which waits for the chunk
system the main thread itself is blocking - a deadlock the watchdog kills
after `max-tick-time` (600 s here). The full-suite run hit it at test
16/58 (arborist/cut_trees: the tree-chop flood fill read terrain beyond
the snapshot). The lookup is now `resolveLevelBlockState`:
`ServerChunkCache.hasChunk(x, z)` (never blocks; the 1.20.6 equivalent of
the old `getChunkIfPresent` - there is no `getChunkIfPresent` on
`Level`/`ServerLevel` on this branch) and an unloaded chunk resolves to
null = the region boundary, instead of generating the chunk from the
warp tick. Pinned by `WarpWorldAccessChunkBoundaryTest` (red-verified
against the pre-fix unconditional read).
