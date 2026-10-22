---
status: done
origin: 2026-10-12 playtest — user chat 17:42:04
---

# Campfire/bed sleep: "you can't sleep here, the bed is too far away" feels too strict

> I get the "you can't sleep here the bed it too far away" message but it
> feels like I'm close enough that it shoudl work...

Vanilla's bed-sleep range check (8-block radius, straight-line) rejects the
sleep even though the player is standing right next to the campfire/bed.
Player-perception gap: by day 1-2 the campfire IS the bed, and the quest
teaching sleep probably places the player/campfire at the edge of the allowed
radius.

Options:
- Widen the effective sleep radius for Questown beds/campfires (a
  `canSleepAt`-style override on the block item / bed handler).
- Or make the quest's campfire placement + the "sleep here" hint land inside
  the vanilla radius so the first sleep always works.

Companion to `campfire-sleep-hint-unclear.md` (hint wording) — this one is
the distance rule itself.

## Implementation (2026-10-13)

Root cause (verified in 1.20.6 bytecode): `ServerPlayer.startSleepInBed` →
`bedInRange` → `isReachableBedBlock` rejects the bed when head/foot are >3 blocks
(x/z) / >2 (y) from the player. `findSafeSleepPosition` picked the first safe
direction in `Direction.Plane.HORIZONTAL` iteration order (always NORTH) — when the
player stands on the opposite side of the campfire, the bed head is 4-6 blocks away
and every sleep attempt fails with TOO_FAR_AWAY despite the player being right next
to the fire.

Fix: `findSafeSleepPosition(level, campfirePos, playerPos)` scores all safe
candidates by distance to the player and picks the closest (bed lands on the
player's side of the fire, reachable from any position within 4.5-block interact
reach). The old 2-arg overload delegates with the campfire centre as the "player"
point (equidistant → first-safe-side, preserving the test-seam behavior
`placeTempBedForTest` uses). `beginCampfireSleep` now passes `player.position()`.
New `CampfireSleepSideTest` (4 tests, mocked level). No bed version of this path
exists (Questown sleep is campfire-only).

## Resolved (audit 2026-10-22)

In code: `CampfireSleepHandler.java:63` passes `player.position()` to `findSafeSleepPosition`, which picks the safe bed side closest to the player; `CampfireSleepSideTest` (items) pins it. The one later TOO_FAR_AWAY (2026-10-22 agent findings line 69) was a wand use from 13 blocks away, so correct; no adjacent-player recurrence found (grep of all .scratch tickets for too far / TOO_FAR).
