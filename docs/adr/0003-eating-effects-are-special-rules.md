# Eating effects (hunger + mood) are special rules, not item results

Eating jobs used to express their outcome by *extracting a result item*: an `EffectMetaItem` carrying a mood (`COMFORTABLE_EATING` / `UNCOMFORTABLE_EATING` / `ATE_RAW_FOOD`) plus, conceptually, the hunger refill. The extraction pipeline (`tryGiveItems`) then branched on item type — `KnowledgeMetaItem` → register knowledge, `EffectMetaItem` → apply effect, everything else → normal inventory placement. The effect branch did not run `postExtractHook`, so jobs whose only product was an effect item silently skipped their `EXTRACTING_PRODUCT` special rules — which is exactly how dining villagers stopped refilling hunger and starved (`eating/dine_at_time`, resolved 2026-05-30).

We removed `EffectMetaItem` entirely. Hunger fill was already a special rule (`FillHungerSpecialRule`); mood application is now `ApplyMoodEffectSpecialRule`, applied via a new `moodUpdater` on `AfterExtractEvent` wired in both the realtime and warp interaction paths (the same context-threading pattern as `hungerUpdater`). The three `Diner*Work`s now produce **empty** results and declare their hunger + mood rules at `EXTRACTING_PRODUCT`. With no product item, extraction takes the empty-result path that has always fired `postExtractHook` — so the starvation bug class is structurally impossible, with no item-type special-casing left in `tryGiveItems`.

## Considered and rejected

- **Keep effect items, just fire the hook in their branch.** This was the initial fix (commit 2a345983). It worked for eating but reintroduced an asymmetry (normal=per-item, effect=once, knowledge=never) that was a standing footgun, and left the deprecated item concept in place.
- **Migrate knowledge items in the same pass.** Deferred. `KnowledgeMetaItem` is not a side-effect carrier — it ferries a specific randomly-rolled loot identity (item + prefix + biome) from the gatherer's result generator into `TownKnowledgeStore`. Replacing it needs a real redesign of how that identity reaches the store without an item carrier, and the gatherer path is already test-flaky. The `KnowledgeMetaItem` branch in `tryGiveItems` remains until that work happens.

## Consequences

- Warp mood application now goes through `ApplyMoodEffectSpecialRule` → `moodUpdater` (`MCTownState.withVillagerData(...withEffect(...))`), preserving the prior warp behaviour. Hunger in warp remains unmodeled per [ADR-0002](0002-warp-does-not-model-hunger.md).
- The `effect_meta_item` is no longer registered. Any pre-existing save holding that transient item will drop it on load — acceptable for an alpha; it was only ever a same-tick extraction carrier.
