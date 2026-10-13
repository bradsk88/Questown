---
title: 'Food quest should have been a LOOT quest; chest cleanup re-deposit completed it'
status: needs-ruling (root-caused 2026-10-14 night; no code defect found in either part)
---

# "Food" quest: wrong type, and re-depositing existing items completed it

Captured 2026-10-14 (server-19, in-game chat 04:27:36 + 04:27:48):

> "Hmm. That quest I was just talking about ('food') should have been a
> loot quest. I just completed it by doing chest cleanup and re-depositing
> the 10 carrots that were initially added."
> "That shouldn't be the case."

Two parts:
1. **Type**: the quest should have been posted as LOOT (a villager brings
   back food), not as an item/"create" quest the player satisfies by
   depositing. Which quest in which batch is the culprit? (The
   hunter-gatherer batch's raw-food LOOT quest is the intended one — this
   suggests a different food ask was posted as ITEM, or the raw-food quest
   resolved to cooked items, cf. `raw-food-quest-naming-mismatch.md`.)
2. **Completion semantics**: the quest completed when the player
   re-deposited the 10 carrots that were *initially seeded* into the
   world — items the town never produced or brought in. Item-quest
   completion scans town containers, so pre-existing stock counts.
   Decide the rule: should completion require the deposit of items that
   entered after the quest was posted (or items a townie produced), or
   should the quest have been a LOOT quest whose completion is keyed on
   extraction (see `.scratch/gannet-top-need/loot-quest-close-on-extraction.md`)?

## Findings (2026-10-14 night, s19 log re-read)

Both parts are perception, not mechanism - the log settles it:

- 04:26:47 `[LOOTTRACE] extract \'Carrot\' vs LOOT quest questown:villager_food
  -> true` immediately followed by the `quest_completed` broadcast. The
  completion came from `RealtimeWorldInteraction.postExtractHook` ->
  `processLootQuests` - a **gatherer extracting a carrot from a crop**
  (the `[WHY] job=gatherer:gather` lines bracket the same second; a
  Fullness change in the same tick shows a townie mid-dining cycle).
- The quest WAS a LOOT quest (`MCQuest.loot`, `#questown:villager_food`,
  count 8, posted by `addQuestsForJobChangeAndFood`). LOOT completion is
  extraction-event-only - it never scans containers, so the re-deposited
  seeded carrots could not have completed it. The user\'s chest tidy
  coincided with the gatherer\'s crop extraction 49s before the user\'s
  remark.

So:
1. **Type**: no defect. The "should have been a loot quest" perception came
   from the "Raw food" card name + the item-style "Create 8 ..." phrasing;
   the rename to "Food" (raw-food-quest-naming-mismatch.md) addresses it.
2. **Completion**: no defect in the observed event - it was a real
   extraction, which is exactly the designed LOOT semantics
   (`.scratch/gannet-top-need/loot-quest-close-on-extraction.md`, closed
   2026-10-04: close on first matching extraction, no accumulation).

## Open ruling (design, not bug)

The quest card says "produce 8" but completes on the FIRST matching
extraction (count is decorative for LOOT quests - `processLootQuests`
completes on the first match, by design). Either:
(a) keep close-on-extraction and change the card copy for LOOT quests to
not promise a count (e.g. "A villager must bring back food"), or
(b) make LOOT quests accumulate to the count across extractions.
Park for the user; both are one-line-ish, but (b) changes the
downstream-pace the 2026-10-04 doc tuned for (the 2.75 gate).
