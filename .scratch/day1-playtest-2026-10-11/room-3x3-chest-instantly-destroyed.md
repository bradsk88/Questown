---
title: 3x3 room (one interior block) instantly reports "store room destroyed" instead of "created"
status: implemented
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:27:34 + 17:27:42 (`run/client2.log`, one
message):

> I just built a 3x3 room (including walls, so only one space in the room
> for job blocks. When I placed a chest in, it instantly said "store room
> destoryed" (neved "created"). I suspect this was caused by some of our
> recent room detection fixes.
> I think a room of this shape should technically be allowed.

## Notes

Suspect: the recent room-detection fixes (RoomRecipes'
`LevelRoomDetector` / single-interior-block edge case). A 3x3 with walls
leaves exactly one interior block; placing the chest into it may be
triggering the "room destroyed" path (interior now filled?) instead of
the "room created" path. Repro: hand-build a 3x3 box, place a chest in
the center, watch the flag log/chat for the created-vs-destroyed
broadcast.

## Triage notes

Root cause: MultiLevelRoomDetector.hasWalkableInterior counted a feet-level work block (the 3x3 storeroom's chest in its single interior cell) as an unwalkable floor and rejected the room the moment it matched. Fix (uncommitted): WallDetection.IsFloorOpen + hasWalkableInterior now excludes feet-filled cells from the standable denominator (a low-roof cell is air-at-feet, so the guard still bites). Regression guard: flag/filled_storeroom_alive autotest, PASS 2/2. too_short_room_dead + floating_furniture_over_half still PASS; floating_chest_room is pre-existing flaky (fails identically on the un-fixed tree, NO_RECIPE).
