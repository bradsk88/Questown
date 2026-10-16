---
title: 'Villagers climb walls instead of entering through doors'
status: fixed
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
