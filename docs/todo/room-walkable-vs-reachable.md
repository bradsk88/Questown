Count room interior by reachability, not standability
====================================================

Status: deferred 2026-09-09. We kept the current behaviour (fraction 0.5) and added
two regression tests that pin it. This is the idea we chose not to act on this
round, kept for a future session.

Background
----------

Room detection rejects a room whose interior a two-block-tall villager cannot
enter. The check is `WallDetection.IsWalkable(level, dp, y)`: a floor cell counts
as walkable only if the feet block (at `y`) and the head block (at `y + 1`) are
both passable (air or empty collision shape). A room is rejected when
`walkable < ceil(total interior cells x MIN_ROOM_WALKABLE_FRACTION)`, with the
fraction defaulting to 0.5 (`Config.MIN_ROOM_WALKABLE_FRACTION`).

The question this came out of: a chest "floating" at head height (air at its feet,
chest above) sits in the head cell, which has a solid collision shape, so that
floor cell is not walkable. Does a room with floating furniture get wrongly
rejected?

What we found (verified by autotest)
------------------------------------

A chest has a full collision shape, so a villager standing at the feet cell puts
their head in the chest. The cell genuinely cannot be stood in, so counting it
as unwalkable is correct. The only knob is the 0.5 fraction.

Two tests pin the breaking point (both in `TestBlueprintRegistry`, run via
`runServer -Dquestown.autotest=true -Dquestown.autotest.only=flag/<name>`):

- `flag/floating_chest_room` — a 5x5 room (9 interior cells) with one chest
  floating at head height against a wall. Result: accepted, door live. Eleven
  percent unwalkable is well under the 0.5 line.
- `flag/floating_furniture_over_half` — a 4x4 room (4 interior cells) with three
  of them carrying a floating chest at head height. Result: rejected as
  `DoorTrouble.NOT_ENCLOSED`. `required = ceil(4 x 0.5) = 2`, only one cell is
  standable, so the room fails.

The line is exactly at 0.5: 50 percent of the interior unwalkable survives, just
over 50 percent does not. Lining a single wall can never cross it (one wall of an
NxN interior is 1/N of the cells, never over half), so the breaking point is
overall density, not a chest-lined wall.

The idea: standability vs reachability
--------------------------------------

The current filter asks, per floor cell, "can a villager stand in this cell?".
The alternative is to ask "can a villager get to this cell from the door?".

A villager cannot stand in a cell that has a solid block at head height, but they
can walk up beside it and use it. So a floating-chest cell is not standable but
is reachable, and under the reachability question it would count as usable, so a
room that is over half floating furniture at head height would pass.

Only the solid-block-at-head-height case changes under this idea. Two others
do not:

- A wall torch or hanging lantern at head height has no solid body, so the
  current filter already lets the cell through (both questions agree: usable).
- A solid ceiling over the whole room blocks the cell, so a villager cannot
  reach it either; both questions agree (blocked). This is the too-short room
  case, which must stay rejected, and the reachability idea keeps rejecting it.

Cost
----

Reachability is real pathing, not a one-line tweak. It has to reason about
whether a cell is reachable from the door through the room, not just inspect the
one cell. That is why it is the most faithful fix ("can a villager use the room")
and the most work, and why it was deferred in favour of keeping the 0.5
fraction.

If revisited, decide what "reachable" means precisely: a cell reachable by
standing in any walkable neighbour and looking at it, or reachable by pathing
into it. The former is simpler and matches how a villager actually uses a chest.
