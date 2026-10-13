---
title: '"Quest lost" fires on the already-complete empty-room quest when the room is re-registered'
status: ready-for-agent
---

2026-10-14: FIXED `582d26c1`.

# "Quest lost" fires on the already-complete empty-room quest

Captured 2026-10-14 (fresh-world playtest, server-18, in-game chat 03:48:02):

> "Quest lost" should not happen here. The empty room quest should be
> perma-done, like the campfire.

## Observed (run/logs, 03:47:37)

Sequence in one tick:

1. `quest_lost [room.empty_room]` broadcast
2. `quest_completed [room.store_room]`
3. `room_changed [room.empty_room]`
4. `quest_completed [room.empty_room]` (queued 1 tick later — re-completion of the lost placeholder)
5. `ERROR Refusing to apply reward more than once: Instantly{AddBasicsBatch[job_board+store_room]}`

The user built a room, wanded the door (empty_room quest completed 03:46:21),
then placed a chest in the same room and re-registered — the room re-typed
from `empty_room` to `store_room`.

## Root cause

`QuestBatch.markRecipeAsLost(oldRoom, recipeID)` finds the quest by
`oldRoom.equals(q.completedOn) && recipeID.equals(q.getWantedId())` and marks
it lost — **without checking `q.isComplete()`**. The completed empty-room
quest's `completedOn` is exactly that room, so the room re-typing loses a
perma-done quest. The re-completion of the lost placeholder then double-fires
the batch reward (rejected by the once-guard — the 03:47:37 ERROR).

## Fix

Skip completed quests in `markRecipeAsLost` (guard `!q.isComplete()`).
Campfire is "perma-done" only because its block room never re-types; the
guard generalizes that to wand-registered rooms. One guard removes both the
user-facing "Quest lost" on a done quest and the duplicate-reward attempt.
