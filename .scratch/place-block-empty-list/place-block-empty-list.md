---
title: "_qtdev place_block listed no interior cells for a registered store room"
status: needs-triage
created: 2026-09-08
priority: p3
---

## Observed

Held village/growth run (2026-09-08, ~04:34 game log): with a store room
registered (recipe_created broadcast, chest inside), `_qtdev place_block` with
no index printed its header and zero cells, repeatedly, across several minutes.
`TownFlagBlockEntity.interiorCells()` (which place_block lists) iterates
`getRegisteredRooms().getAllRooms()` and filters air cells at `room.yCoord`, so
a registered room with open floor should have produced cells.

In a later run the chicken's sign beat (which reads the same `interiorCells()`)
did return a cell for a store room, so the path is not always empty. Difference
between the two runs not identified: in the empty case the room already
contained the chicken's chest (recipe matched); in the working case it also
did. Unresolved.

## Why it matters

`place_block` is the agent's index-based hand for placing a block inside a room
without guessing coordinates. When it silently lists nothing, the agent falls
back to guessed `setblock` coordinates, which is how walls get broken.
`_qtdev look_room` (added 2026-09-08) now covers the eyes, so this is lower
priority, but the empty list with no explanation is still a trap.

## Next step

Reproduce on a held run: build a store room, confirm the recipe matches, run
`_qtdev place_block`, and compare against `interiorCells()` expectations
(room in getAllRooms? spaces empty? yCoord off by one?).
