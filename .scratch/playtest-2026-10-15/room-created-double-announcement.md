---
title: 'Room-created announcement prints twice (recipe_created + specific_room_created for the same room)'
status: done
origin: 2026-10-15 playtest — user chat 03:32:43; log 03:32:21.128
---

# Duplicate room announcements

User: "^ One of those 'room' announcements is redundant."

Log evidence: same millisecond (03:32:21.128), same room, same position:
- `messages.building.recipe_created [room.empty_room, [332, -242]]`
- `messages.building.specific_room_created [room.empty_room, ...]`

The generic "recipe created" and the specific "room created" both fire for
one room build. Pick one (the specific_room_created is the more
informative; or keep recipe_created only when no specific message
applies) — check the two broadcast sites in the building/room-creation
path and suppress the redundant one.

## Resolved (audit 2026-10-22)

bbbdf88f: `TownRooms.roomAdded` gives unmatched rooms only the generic "Room created" line and matched rooms the single recipe line; a73a1401's `BroadcastPacer` additionally drops an identical line just sent.
