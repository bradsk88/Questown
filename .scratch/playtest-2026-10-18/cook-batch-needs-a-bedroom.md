---
title: 'Cook batch (job change, kitchen, cooked food) should include a bedroom quest'
status: needs-info
origin: 2026-10-18 playtest (dev-server-12) — user chat 22:04:15
---

"The batch of "cook quests" (job chnage, kitchen, cooked foot loot) should also include a bedroom,
since w ehave a second villager at thtat poijnt."

Related dev observation (same session): "there's basically a 1-to-1 relationship between
'batch contains a bedroom' and 'batch should reward with a villager'".

## Open question

A plain `SpecialQuests.BEDROOM` ROOM quest completes on ANY registered bedroom
(`backfillSatisfiedQuests` closes open ROOM quests a matching room already satisfies), and the
town already has the hunter-gatherer bedroom by then — so a second copy would close the moment it
is posted. Does the cook batch want a second bedroom (a count of 2 / a bed per villager), or a
bedroom upgrade?
