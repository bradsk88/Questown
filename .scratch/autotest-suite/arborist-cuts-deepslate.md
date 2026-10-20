---
title: 'arborist/cut_trees: the warp arborist yields deepslate, not oak logs'
status: ready-for-agent
origin: 2026-10-20 sharded full-suite runs (autotest-par run-2 and run-3)
---

In both failing runs of `arborist/cut_trees` / `[full_cycle]` today the warp result was
`After: {minecraft:deepslate=5, minecraft:wooden_axe=1}` and `oak_log: expected >= 4/5, got 0`.
So the arborist harvested five deepslate blocks where the tree's logs should be. Deepslate is
deep-underground terrain, so the harvested positions are probably not the grown tree's (a tree
that failed to grow, or a position computed below the arena floor).

This is the long-standing arborist flake on the known-failures list; the deepslate detail is new.
It may be seed/terrain dependent like `farmer-warp-loses-container-on-some-seeds.md` (resolved
by clearing the arena to the natural surface, 8535e746). Next: grab the world seed from the
failing run's `level.dat` (tools/autotest-parallel.sh dirs: build/autotest-par/run-<i>/world)
and reproduce with `level-seed=` as in that ticket.

Seeds of the two failing shard worlds (each shard runs many scenarios in one world, so the seed
alone may not reproduce it — run the same shard set): run-2 `7974589906024771991`, run-3
`-4963735958793904313` (shards of `--shard 4` after 8535e746).
