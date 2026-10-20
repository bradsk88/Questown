---
title: 'Autotest: "Town flag failed to initialize (timeout after 200 ticks)" for a server''s first tests'
status: needs-triage
origin: 2026-10-20 (02:46 single run; 16:4x shard 3 of a --shard 4 run)
---

A server's first scenarios can fail before running: `Placed town flag at 0, 64, 0` then
`Town flag failed to initialize (timeout after 200 ticks)`. On 2026-10-20 shard 3 lost its first
four scenarios (cook/simple_furnace_food, baker/bread, smelter/process_ore, explorer/explore)
this way; test 5 on initialized normally. The server was far behind ("Can't keep up ... 38727
ticks behind") at the time, with 3 other shards running. Seen once before, in a single-server
run at 02:46 (before the arena flatten change 8535e746), so not caused by it — but clearing to
the natural surface can mean more blocks to destroy on a hilly seed.

World seed of the failing shard: -9116547651420931824. Next: run shard 3's list serially with
that seed; if the flag init is load- or terrain-dependent, a longer init timeout or waiting on the
flag's chunk/BE may be the fix.
