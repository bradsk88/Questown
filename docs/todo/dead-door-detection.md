Dead Door Detection
====================

> **Superseded / closed 2026-08-24.** `dropDeadDoors` is still commented, but dead-door
> handling is now covered by newer design: ADR-0013 (zombie registrations discarded in-tick
> when the block is gone) and `DoorTrouble` / `DeadDoorBubbles` (a door that won't make a
> valid room now surfaces as a need bubble, not a silent deregistration). Behavior changed
> from silent deregistration to a visible bubble. Closed as superseded. See ADR-0013 and
> `TownRoomsMap` line 450.

The code for detecting and deregistering doors that never produce a valid
room already exists in `TownRoomsMap.dropDeadDoors()` but is commented out
with a performance TODO.

Location: `src/main/java/ca/bradj/questown/town/rooms/TownRoomsMap.java`
lines 238-250 (commented out block in `pendingRooms` lambda) and
lines 275-293 (`dropDeadDoors` method).

Current behavior: When a player registers a door on an invalid structure
(missing wall, no ceiling, open-air), the door stays registered forever.
No error message. No deregistration. The player must infer failure from
the absence of the "A new [Room Type] was constructed" chat message.

Proposed behavior:
  1. Uncomment the dead door tracking in the `pendingRooms` lambda
  2. After N full scan cycles with no valid room detected, call
     `dropDeadDoors()` which deregisters the door
  3. Add a player-facing chat message when deregistering:
     "The door at [x, z] doesn't lead to an enclosed room. Check walls
     and ceiling."

The original TODO mentions performance concerns. The tracking uses a
`doorsToDrop` map that counts scan cycles per non-room door. The
threshold is 100 ticks (line 276). Consider whether this is too
aggressive or too slow for the player experience — a faster timeout
(e.g. 20 ticks) would give quicker feedback during the tutorial.
