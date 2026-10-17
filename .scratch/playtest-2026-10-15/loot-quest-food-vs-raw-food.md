# Food quest naming: "Raw Food" (gatherer) vs "Food" (cook)

> status: fixed
> origin: 2026-10-15 playtest — user chat 03:53:27 (reversal of the 03:23 call)

## The two calls

- **03:23:24** — after the LOOT quest completed ("Quest complete: Food"): the
  quest "should have been called 'Raw Food'". → commit `100a722c` renamed the
  single lang key `menu.quests.food` "Food" → "Raw Food".
- **03:53:27** — "The CURRENT quest, should say 'Food', not 'Raw Food' :sweat_smile:."
  **"This quest belongs to the cook, who will COOK it."** The current quest on
  the board (post cook-arrival, 03:32) shows "Raw Food" but should say "Food".

## Problem

Both food quests appear to share the single lang key `menu.quests.food` (per
the 100a722c analysis: "the single lang key `menu.quests.food` ... is the only
LOOT-quest name"). A gatherer feeding-quest ("bring RAW food") and a cook's
food quest ("provide Food, the cook cooks it") want **different** labels —
one shared key cannot carry both.

## Open questions (verify in `TownQuests` / quest data before proposing)

- How many distinct food quests exist, and which jobs own each?
- Is the current "Raw Food" quest the cook's, or the gatherer's showing on the
  board after the cook joined?
- Proposed shape: separate lang keys per quest (or per owning job), keep
  gatherer = "Raw Food", cook = "Food".

## Restated 2026-10-17 06:17:56 (playtest round 3)

"The first loot quest should be entitled Raw Food (and it is) but the second loot quest
(which asks for 8x) should be titled 'Food'." — confirms the split: the hunter-gatherer
batch's 1x raw-food LOOT quest keeps "Raw Food"; the later 8x LOOT quest (the cook's,
posted by the job-change-and-food phase) needs its own "Food" name.

## Fixed 2026-10-17

`UIQuest.lootNameKey`: raw tag -> "Raw Food" (`menu.quests.raw_food`), else "Food" (`menu.quests.food`); card + completion/lost broadcasts.
