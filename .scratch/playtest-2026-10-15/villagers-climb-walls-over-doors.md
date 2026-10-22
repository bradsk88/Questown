---
title: 'Villagers climb walls instead of entering through doors'
status: needs-info
origin: 2026-10-15 playtest round 2 — user chat 05:37:47
---

# Villagers prefer jumping over walls to get inside rooms

User report: "Villaers seem to prefer jumping over walls to get inside
the room. Can we make doorways more attractive? Higher 'navigability' in
pathfinding?"

## Root cause

Per-cell path cost is `FLOOR_COST + Mob.getPathfindingMalus(type)` and a
door cell (closed or open) carries no extra cost. A 1-block wall top is a
cheap step-up, so when the wall is a step or two closer than the doorway,
A* routes the visitor over the wall. The visitor entity
(`VisitorMobEntity`) had no `getPathfindingMalus` override — the existing
`VillagerPathNavigation` seam only re-classifies cells (campfire, water,
fences), it doesn't price them.

## Fix (2026-10-15, same session)

`VisitorMobEntity.getPathfindingMalus` now returns a 0.5f discount
(`DOOR_PREFERENCE_MALUS`) for `DOOR_WOOD_CLOSED` / `DOOR_IRON_CLOSED` /
`DOOR_OPEN` cells, making doorway routes the cheaper choice. Constant is
deliberately named and tunable: bump it if doorways still lose to walls in
the next playtest.

Held-run verification pending: needs a live world with a room + a
visually-observed approach (the user saw it; the next playtest confirms).

## Reverted 2026-10-17

The discount made open doors impassable (negative malus = blocked in vanilla). See `.scratch/playtest-2026-10-17/villager-stuck-on-bed-pathing-over-wall.md`.

## Audit (2026-10-22)

Still open: the 2026-10-15 door-discount fix was reverted 2026-10-17 (it made open doors impassable). Needs a new approach; see .scratch/playtest-2026-10-17/villager-stuck-on-bed-pathing-over-wall.md.

## Audit 2 (2026-10-22, later)

No wall-climb report in `.scratch/` after the 10-17 revert (grep "over the wall|climb.*wall|jump.*wall":
only this ticket and its 10-17 sibling). The 10-15 report predates the door discount, so it is a
real vanilla-cost behaviour, but it hasn't recurred in the playtests since. A fix needs a positive
penalty on the wall-top route (a negative malus is impassable), and that needs a fixture to tune
against. Parked as needs-info until it recurs.
