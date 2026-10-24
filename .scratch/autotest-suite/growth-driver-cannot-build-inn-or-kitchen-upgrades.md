---
title: 'village/growth driver cannot satisfy inn / kitchen_cafe / kitchen_stocked quests'
status: ready-for-agent
origin: 2026-10-20 growth run (growth-dropfirst2.log)
---

village/growth can stall at roster 4 when the random quest rolls after the miner beat produce a
forge room, then `inn` (owned by a villager) and the kitchen upgrades `kitchen_cafe` /
`kitchen_stocked`. The driver logs `UNCLEAR open quest '...inn...' does not name an item I
recognise` hundreds of times and never builds them, so the town never reaches villager 5. Runs
whose rolls avoid these pass 5/5. Next: teach `furnishNextRoomQuest` the inn and the kitchen
upgrades (room recipes; upgrades add blocks to an existing room), or make the driver pick
another open quest.

## 2026-10-24 (overnight, agent)
The driver now discards a room quest whose recipe can't fit a 3x3 spare building (the inn) via
`QuestsHolder.removeBatch` (the remove-batch screen's path, moved onto the owner). The kitchen
upgrades fit and are furnished as before. Growth passed 5/5 afterwards, but no inn was rolled, so
the discard has not fired live yet: keep this open until a run logs
`unbuildable-discarded[questown:inn]` and continues.
