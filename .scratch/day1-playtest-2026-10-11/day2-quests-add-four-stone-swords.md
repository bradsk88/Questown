---
title: Day-2 quests should include one "4 stone swords" item quest
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:15:51:

> Got the day two quests. They look better than beofre, but I'd argue we shold
> also for 4 stone swords. One sword item quest only.

The day-2 quest batch looks better than before, but the player wants it to also
include a **single item quest asking for 4 stone swords** ("one sword item quest
only" — not multiple sword asks).

## Acceptance criteria

- The day-2 quest batch includes exactly one item quest whose target is
  `4 × stone sword`.
- No other day-2 quest asks for swords (the sword ask is consolidated into that
  one quest).

## Notes

Find where the day-2 quest batch is defined (the onboarding/procedural batch
data or the quest-batch build) and add/adjust the stone-sword item quest. Confirm
the count (4) and that it's a `stone` sword (not iron/gold). Check the day-2
batch doesn't already ask for swords elsewhere (consolidate to one).
