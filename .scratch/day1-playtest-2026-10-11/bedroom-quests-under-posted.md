---
title: 'Bedroom quests: keep one bedroom.json per villager, but the garden under-posts them'
status: needs-info
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:25:23 (evening from-zero run):

> We currently have two villagers in town but only one bedroom quest... I feel
> we should have a "big bedroom" quest instead.

Follow-up decision in the design walkthrough (2026-10-11): the player prefers to
**keep one `bedroom.json` quest per villager** (drop the "big bedroom" idea). The
real complaint: **at the moment they had 2 villagers, the game had only asked for
1 bedroom** — it was under-posting bedroom quests, so the town was under-bedded.

## Decision (2026-10-11)

- **Keep the current model: one `bedroom` (one complete bed) quest per villager.**
  No "big bedroom" — `big_bedroom.json` (2 beds) stays as unused recipe data.
- **Fix the under-posting:** the town must post enough bedroom quests to cover the
  villager count (one complete bed each), not just one per quest batch.

## Mechanism (grounded in code, 2026-10-11)

- Bed satisfaction is already correct: `TownRoomsHandle.hasEnoughBeds(numVillagers)`
  counts bed *blocks* in all rooms and returns `beds / 2 >= numVillagers` (a
  complete bed = 2 blocks, one per villager).
- `bedroom.json` = 1 light + 2 bed blocks = **1 complete bed**. `big_bedroom.json`
  = 4 bed blocks = 2 beds (dead data; never posted).
- When beds are short, the quest garden's `addBedQuest()` posts
  `SpecialQuests.BEDROOM` (the one-bed `bedroom`). **At most one bed quest per
  batch** (guarded by `hasBedAlready(batch)` in `AbstractQuestGarden.doGrow`). So
  covering N villagers takes N separate batches — if a batch is already "used up"
  or the second villager arrives mid-batch, the town can sit under-bedded for a
  while with only one bedroom quest posted. That matches the observed "2 villagers,
  1 bedroom quest."

## Acceptance criteria

- When the villager count exceeds available complete beds, the town posts enough
  bedroom quests (one per missing bed) to reach `beds/2 >= villagers`, promptly —
  not one per batch with a long gap.
- No regression to the existing `beds/2 >= villagers` satisfaction check.

## Notes

Investigate the quest garden's bed-posting cadence: when `hasEnoughBeds` is false
and multiple beds are missing, does it post one bed quest and wait for the next
batch, or can it post enough to cover the shortfall? Consider letting a batch
carry more than one bed quest when the shortfall is >1, or triggering a
re-evaluation when a new villager arrives (the roster-change moment). Ties into
the two-villagers finding (`two-villagers-after-first-sleep.md`) — if two villagers
arrive at once, the shortfall jumps by 1-2 and the garden must catch up.
