---
title: LOOT quest type, onboarding raw-food quest completes on production
status: ready-for-agent
closed: 2026-09-27
created: 2026-09-26
---

## Triage notes

2026-09-27: implemented and verified. The raw-food LOOT quest completed on extraction in a held
`village/self_sufficiency` run (two hunters, off-town loot-table roll, `quest_completed`
broadcast, no chest deposit). The follow-on apple ITEM quest stayed ACTIVE with deposit wording.
Closed in the commit that landed the feature.

The hunter's onboarding quest "get mutton" is a specific-item `ITEM` quest completed by a chest
deposit. It should be "get raw food" and complete when a townie *produces* (extracts) the loot,
not when it is deposited. Full design, blast radius, and phases:
`docs/plans/2026-09-26-001-feat-loot-quest-raw-food-plan.md`.

This is a new `QuestType` (`LOOT`), not a flag on `ITEM`. The sword / apple / exotic-wood quests
stay `ITEM` (player-facing chest-deposit). The mutton-eating fix (2026-09-24) is already in; this
is orthogonal, it fires on extraction.

## Acceptance criteria

- A `QuestType.LOOT` exists and round-trips through `QuestTypes` NBT and network with no codec
  change.
- `asIngredient(rl)` resolves an item RL to `Ingredient.of(item)` and a tag RL to
  `Ingredient.of(tag)`; `questMatchesStack` uses it (clears the `// TODO: Also handle tags`).
- The mutton quest is `MCQuest.loot(...)` with `TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW`
  and updated flavor text; it completes when a townie extracts a matching item, with no chest
  deposit required, and the item is not consumed.
- All five `switch` sites over `QuestType` handle `LOOT` (`UIQuest` icon + name, `QuestsScreen`
  tooltip + lesson icon, `QuestBatches` duplicate check).
- The sword / apple / wood quests still complete only via chest deposit.
- A held run shows the raw-food LOOT quest completing on extraction.

## Notes

- Tag chosen: `#questown:villager_simple_furnace_food_raw` (rabbit/pork/mutton/chicken/beef/
  potato), reuses existing data, no new tag file.
- Icon cycles the tag's representative item (`Ingredients.representativeItemStack` already does
  this); name is "Raw food".
- Decisions (resolved 2026-09-26): `QuestType` name is `LOOT`; display name is a localized
  "Raw food"; a LOOT quest completes on the first matching extraction (no accumulation).
