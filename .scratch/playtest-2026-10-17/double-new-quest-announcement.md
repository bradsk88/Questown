---
title: 'Two back-to-back quest announcements on the first arrival morning'
status: ready-for-human
origin: 2026-10-17 playtest round 6 — user chat 19:36:04 (dev-server-9, build fe1b7238)
---

19:35:39 "New quests are available! Right-click the town flag to see them." then 19:35:40 "New
quest is available: post your first work request on the job board." User: "That is not
necessary. "New quests are avalable" is sufficent".

`TownQuests.addFirstWorkRequestBatch` no longer broadcasts its own line. Caveat: it is posted
alongside the arrival's quest batch (PostFirstWorkRequestQuestReward defers until a villager
exists), so the generic line covers it; if it were ever posted on a morning with no other new
quests, it would be silent.
