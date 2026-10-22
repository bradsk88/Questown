---
title: 'Food quest producers list includes miners and hunters (known loot is not per table)'
status: done
origin: 2026-10-20 playtest — user chat ("still showing all jobs that USE cooked food")
---

Clicking the "food" quest's items opens the item jobs screen; its producer list (log line
`[item-jobs] producers of [...]`) held gatherer, miner and hunter tiers next to the cook recipes.

Cause: `KnowledgeStore.getAllKnownGatherResults(biomes, prefix)` ignores `prefix` and returns
every leaver's finds, so every biome_loot job's `Work.results` holds a gatherer's apples.

Tried and reverted (2026-10-20): scoping the lookup to the prefix. village/growth then stalled
at 1 villager: the lookup also gates a fresh leaver's extraction, so the first hunter never
brought raw meat back. Next: fix the hint side only — a leaver counts as producing X when X is
both known and in its own loot table (needs a "possible drops of a loot table" helper), or
scope only the hint's call. Don't change the extraction gate without a growth run.

## Audit (2026-10-22)

Dev answer 2026-10-22 (feedback-audit Q5): narrow the player-facing count only; engine untouched. In progress in this audit (task: narrow producer count).

## Resolved (audit 2026-10-22)

Fixed per the dev's 2026-10-22 ruling (narrow the count only): `TownFlagBlockEntity.getTownDataForDisplay` + `LootTableDrops`, used by the item-jobs screen and the board confirm; autotest `flag/producer_count_own_tables` (red/green), `LootTableDropsTest`. Extraction gate unchanged.
