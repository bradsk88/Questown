---
title: 'Hunter produced WAY too much food in one day; "Dropping loot" animals not visible in town'
status: ready-for-human
origin: 2026-10-15 playtest — user chat 03:26:56 + 03:27:27 + 03:27:44
---

# Hunt loot volume + invisible "Dropping loot" animals

Three linked reports:
1. 03:26:56 — "My hunter produced WAY too much food for one day."
2. 03:27:27 — "Also, I can't see them anywhere in town, but they're
   'Dropping loot'."
3. 03:27:44 — "Oh, tjey're in bed." (self-answered part: the townies
   themselves were in bed — sleep module)

Log evidence (03:22:32.529, single tick): a batch extraction of 10+ items
landed at once — 7+ × mutton, plus beef, chicken, leather, feather, all
logged as "extracted '1 minecraft:X' but no open LOOT quests exist". So the
hunter(s) banked a large multi-animal haul in one go with no quest to
absorb it.

Open questions:
- Is the volume one hunter's legitimate loop rate, or a burst (e.g. several
  animals killed in one hunt cycle with no pace gate)? Census the hunter's
  kill/extract log for the full morning.
- "Dropping loot" state shows but the dropped animals/items are not
  visible to the player — where do they drop (off-screen, in the bed
  room)? If the loot is invisible until extraction, that is the
  player-facing oddity.

## Audit (2026-10-22)

Part 3 self-answered (they were in bed). Parts 1-2 are taste/design (is one multi-animal haul per hunt the intended yield? should 'Dropping loot' show where the loot goes?). Queued in .scratch/feedback-audit/README.md.
