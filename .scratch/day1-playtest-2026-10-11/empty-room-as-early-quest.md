---
title: Make "empty room" one of the first quests
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:23:12 (`run/client2.log`):

> It might actually make sense to make "empty room" one of the first
> quests. As a fresh player without a chicken, I might not understand
> the first two quests without that fundamental knowledge.

## Notes

Day-1 onboarding design: without the chicken, the opening quests
(campfire, mat) assume the player knows what a (room around a) flag
means. Teaching "an empty room" first would give the prerequisite
knowledge. Interacts with the mat-gated-arrival design (`.scratch/
mat-gated-arrival/`) — the chicken currently builds that room as part of
its door beat.

## Triage notes

Design item: make 'build an empty room' an explicit early quest to teach room registration. Needs a product decision + quest data.

## Decision (2026-10-11, design walkthrough)

**Add an "empty room" quest as a standalone batch, after campfire + mat, that
blocks the second onboarding batch (store room + job board).**

- **Position:** after the campfire and welcome mat (not first). It is its own
  standalone quest batch, and it **gates the basics batch** (store room + job
  board) so the player learns room registration before the basics land — no quest
  overload while learning the fundamentals.
- **Both paths:** on the chicken-assisted path the chicken-built room + registered
  door auto-completes it (the room is already up by the time the mat beat is
done — that's fine, it just completes). On the no-chicken path the player builds
  it themselves.
- **Requirement: walls + a registered door only** (no contents). Either a new
  minimal room recipe or reuse the existing TOWN_GATE door-room — implementation
  detail; the gate is "a room exists whose door is registered."

Onboarding batch order becomes:
1. Campfire + welcome mat (existing first batch).
2. **Empty room** (new standalone batch) — walls + registered door.
3. Store room + job board (existing basics batch, now blocked until #2 completes).

## 2026-10-12 live confirmation (playtest chat 17:44:22)

> I thought we added a "build an empty room" quest. I don't see it. But I do
> see specific-room quests...

Confirms the gap is visible in-game on day 1: specific-room quests (store
room, ...) are posted, but the generic "build an empty room" quest the user
expects is absent. Keep the design constraint from the original ticket: the
empty-room quest must sit early enough to teach room-building before specific
rooms are demanded.

## Shipped (2026-10-13)

Per the decision: a standalone "empty room" batch now sits between the
welcome-mat gate and the basics batch, and gates the basics.

- `data/questown/recipes/empty_room.json` — zero-ingredient room recipe
  (`questown:empty_room`), matches any room; the quest's gate is "a room
  exists whose door is registered" (walls + registered door, no contents).
- Chain: gate reward now posts the empty-room batch
  (`AddEmptyRoomBatchReward`); its completion posts the basics batch
  (`AddBasicsBatchReward`). Chicken path auto-completes (the chicken builds
  the gate room before the mat beat); no-chicken path: player builds a room
  and wands the door (flavor text says so).
- Quest id `SpecialQuests.EMPTY_ROOM`; lang `room.empty_room`.
- Test: `EmptyRoomRecipeTest` (zero-ingredient semantics + id match).
- In-game confirmation of the beat order: pending.

## Resolved (audit 2026-10-22)

Decision implemented: `TownQuests.addEmptyRoomBatch` (line 162) posts the standalone empty-room batch between gate and basics (log line 'Empty-room batch posted (onboarding beat between gate and basics)'); the icon/tooltip follow-up is also done.
