---
title: 'ERROR "In slot N, expected carrot but state had air" when the town chunk reloads'
status: needs-triage
origin: 2026-10-17 playtest round 5 — dev-server-8 log 18:12:48 (build 3ed88de2)
---

The player walked >100 blocks away (flag stopped ticking 18:04:57 / 18:08:42), then came back.
On the villager's reload (`entity-deserializer` thread, `TownVillagerMobs` loading the stored
town state):

```
[entity-deserializer/ERROR] In slot 5, expected MCTownItem{quantity=1, item=minecraft:carrot} but state had MCTownItem{quantity=0, item=minecraft:air}. This is a bug and may cause items to be lost.
[entity-deserializer/ERROR] In slot 6, expected ... carrot ... but state had ... air ...
```

Source: `TownStateSerializer.checkItems` — the live chest (-664,63,-305) holds carrots in slots 5
and 6 that the stored town state (the warp snapshot) does not. The carrots exist in the world
(the "expected" side is the live chest); the question is whether anything later writes the stale
state back over the chest. The gatherer had put carrots into that chest at 18:09:34 / 18:09:44
(dining_raw_food DROPPING_LOOT) — after the last state store?

## Next

Find when `NBT_TOWN_STATE` is stored relative to villager chest deposits while the flag is
not ticking; check whether a load with a mismatch overwrites the live chest. Likely
pre-existing (not touched this session).

## Second data point (dev-server-9, build fe1b7238, 19:41:55–19:42:24)

```
19:41:55 Town flag at (25,67,65) stopped ticking because: Closest player is 10041 blocks away
19:41:55 [PROBE:supplies] job=gatherer:gather TOOK carrot (totalTakes=4)      <- same tick
19:42:24 [ERROR] In slot 0, expected 16x carrot but state had 17x carrot
```

The gatherer took a carrot on the very tick the flag stopped ticking, so the stored town state
(the warp snapshot) kept the pre-take 17 while the chest has 16. Points at the snapshot being
taken before the villager's same-tick container change, rather than at anything lost — the
"expected" (live chest) side is correct both times. Question stays: does a later warp/settle
write the stale 17 back (item duplication) or the stale air back (item loss)?
