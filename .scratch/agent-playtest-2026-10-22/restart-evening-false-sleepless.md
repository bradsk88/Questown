---
title: 'A restart in the evening marks bedded townies bedless (false SLEEPLESS next morning)'
status: closed
closed: 2026-10-22
origin: 2026-10-22 agent playtest
---

# What happened

Server stopped at nightfall (01:19), restarted (01:21). The hunter had a registered Bedroom
(2 beds, completed quest at 01:09) and the previous nightfall had logged `Nightfall: 0 of 1`.
On the flag's first tick after the player joined:

```
01:21:25.121 Sending tutorial toast to Dev: tutorial.campfire_ready...
01:21:25.125 Nightfall: 1 of 1 townies have no bed for the night
...
01:24:53.078 Villager 23c4...be1 was bedless last night; SLEEPLESS applied until tick 89996
```

The next real nightfall (01:29:40) logged `0 of 1` again.

# Why

`BedlessLedger.captureEdge` treats the first observed EVENING tick after load as the edge
(documented: "a restart mid-evening captures on the first observed tick (best effort)", pinned
by `BedlessLedgerTest`). On that tick `TownVillagerSleepModule.hasBed` has no assignment yet
(the bed supply comes from recipe-matched rooms that the restarted flag has not re-matched), so
every townie counts as bedless.

# For the dev (judge)

The best-effort capture was a stated v1 trade-off for timing, but it now produces a debuff for
a townie that had a bed. Options: skip the capture when `lastSignals` is null (a restart
mid-evening then records nobody), or defer the capture until the room scan has run once.
Not changed autonomously because `BedlessLedgerTest` pins the current rule.

# Second repro (2026-10-22 02:44): not only evening

Server stopped at night with both townies in the big bedroom (2 beds, completed quest), restarted
still at night. First flag tick after join:

```
02:44:12.539 Dev joined the game
02:44:12.794 Nightfall: 2 of 2 townies have no bed for the night
02:45:03.341 Villager d543...708 was bedless last night; SLEEPLESS applied until tick 156752
02:45:03.341 Villager 23c4...be1 was bedless last night; SLEEPLESS applied until tick 156752
```

So any restart between nightfall and morning hits it, not only an evening one. With the
playtest loop restarting often, every town takes a SLEEPLESS day per restart.

# Resolved (2026-10-22)

Dev ruling: wait for the room scan. `BedlessLedger.captureEdge(signals, bedsKnown)` holds a due
capture until `VillagerSleepModule.bedsKnown()` (`TownRoomsMap.hasScannedSinceLoad`); dropped if
still due at dawn. `BedlessLedgerTest` 9/9 (3 new tests red against the old form). Live: a
restart at day time 13096 logged "Nightfall: 16 of 20" 1.25 s after join, the same count a real
nightfall logged before the restart.
