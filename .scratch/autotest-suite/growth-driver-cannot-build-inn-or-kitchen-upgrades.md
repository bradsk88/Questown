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
