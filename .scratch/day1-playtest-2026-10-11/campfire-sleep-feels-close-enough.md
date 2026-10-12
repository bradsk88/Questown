---
status: ready-for-agent
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
