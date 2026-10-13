---
title: '"Create N items" quests should show deposited progress like (3/8)'
status: fixed
---

# Item quests: show deposited progress (3/8)

Captured 2026-10-14 (server-19, in-game chat 04:26:16):

> "The 'Create N items' Quests should probably show an indication like
> (3/8) if the player/village has already deposited 3 items into the chest
> that apply to the quest."

The quest card shows the target count but not how many of the wanted item
have already been deposited into the town chest. Surface the running
count (numDeposited / target) on the quest card / tooltip so the player
knows how far along they are. The deposit count is already tracked
(server-side item-quest completion scans town containers), so this is a
rendering/wiring change on the quest card.

## Fix

Committed same session (2026-10-14 night): `UIQuest` gains a nullable
`deposited` field (wire: -1 == null); the card name appends "(n/target)"
capped at target. Server-side, `TownQuests.openItemQuestProgress()` counts
matching items in the town containers with the same ingredient matching as
the completion path; wired through `QuestsHolder` into the flag QUESTS tab
and `showQuestsUI`. Pinned by `UIQuestItemProgressTest` (3 tests).
