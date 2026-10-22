---
title: 'LOOT quest should be called "Raw Food", not "Food"'
status: done
origin: 2026-10-15 playtest — user chat 03:23:24, after the LOOT quest completed at 03:22:32 ("Quest complete: Food")
---

# LOOT quest naming: "Food" → "Raw Food"

User report (in-game chat, 03:23:24):

> The quest that just completed should have been called "Raw Food"

The completed quest was the LOOT food quest (server log 03:22:32:
`Broadcasting message: messages.town_flag.quest_completed
[translation{key='menu.quests.food', args=[]}]`).

## Fix (applied 2026-10-15, same session)

The single lang key `menu.quests.food` ("Food") is the only LOOT-quest name —
it drives the card title (`UIQuest.getName`, LOOT case), the quest-completed
broadcast and the quest-lost broadcast (`TownMessages.questCompleted` /
`questLost`). No other lang file ships. Changed to "Raw Food".

Client-side only; needs the next client restart to show.

## Resolved (audit 2026-10-22)

Superseded by the 2026-10-17 `UIQuest.lootNameKey` split (see loot-quest-food-vs-raw-food.md).
