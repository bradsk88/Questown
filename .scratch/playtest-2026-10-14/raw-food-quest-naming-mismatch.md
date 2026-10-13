---
title: 'Raw-food quest: name says "raw food" but wanted items are cooked; tooltip says "produce cooked food"'
status: fixed
---

# "Raw food" quest naming/tooltip mismatch

Captured 2026-10-14 (server-19, in-game chat 04:20:53–04:21:58):

> "The tooltip for the 'raw food' quest says 'produce cooked food' or
> something. That seems wrong."
> "I think the problem is just that the quest name is 'raw food'. All of
> the food options it is showing as 'wanted' for the quest are in fact
> cooked."
> "So the quest should probably just be titled 'cooked food'"
> "Or just 'Food' since it does contain things like apples and carrots"

Two coupled problems:
1. The quest's display name ("raw food") does not match the items it
   actually wants (cooked food / a food tag).
2. The tooltip copy ("produce cooked food") contradicts the name.

Likely the quest is keyed to a food **tag** that resolves to cooked
items, but the title/tooltip were written for the raw intent. The dev's
lean: title it "Food" (neutral, covers apples/carrots) rather than
"cooked food". Check the quest/recipe data it is generated from before
renaming.

## Fix

Per the user's lean ("just 'Food' since it does contain things like apples
and carrots"), the LOOT-quest card name is now the neutral "Food":
`menu.quests.raw_food` ("Raw food") renamed to `menu.quests.food` ("Food") in
en_us.json; the 3 call sites (UIQuest LOOT card name, TownMessages
quest-completed/quest-lost broadcast names) updated. The `#questown:villager_food`
tag does indeed mix cooked items with raw apple/carrot/melon/beetroot, so
"Food" covers both; the raw-hunt quest keeps its raw-ness via its flavor
text. Tooltip copy ("A villager must produce %d %s for the town") was
consistent and unchanged. Pinned by `UIQuestLootCardNameTest`.
