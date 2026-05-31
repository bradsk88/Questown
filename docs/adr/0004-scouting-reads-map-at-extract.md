---
status: accepted
---

# Scouting registers known loot via an extract-phase rule that reads the map

The explorer "scouts": each trip it produces a **gatherer map** for a biome and learns one loot drop from that biome (recorded as **known loot**, not taken). Today the learned drop rides the extraction pipeline as a `KnowledgeMetaItem` — a fake item carrying the rolled (item, prefix, biome), registered by `withKnowledge` and unwrapped by `TownKnowledgeStore`. That item exists only because the result generator can't reach the town to call `registerFoundLoots`; it forces an item-type branch in `tryGiveItems` and a wrap/unwrap pair. It is the last of the deprecated meta-items (effects were removed in [ADR-0003](0003-eating-effects-are-special-rules.md)).

**Decision:** delete `KnowledgeMetaItem`. The result generator keeps producing the biome-stamped map. A new `ScoutLootSpecialRule` at the explorer's `EXTRACTING_PRODUCT` phase reads the **just-extracted map**, rolls one loot (item, prefix) for *that map's biome* via `world().asServerLevel()`, and registers it through a new `knowledgeUpdater` on `AfterExtractEvent` (the same context-threading pattern as `hungerUpdater`/`moodUpdater`). This requires exposing the extracted item on `AfterExtractEvent` (rules currently can't see what was extracted — `AssociateItemWithTown` stamps it blind). The `KnowledgeStore` itself is unchanged; its `stripper` collapses to `i -> i.get()`.

Because the rule reads the map's actual biome, the map and the loot it teaches share a biome **by construction** — preserving today's per-trip behavior, where learned loot is immediately usable (its biome is in town because the same trip's map is).

## Considered and rejected

- **Decide in `beforeTick`, carry to `afterExtract` via `UnsafeVillagerData`** (the downtime pattern). The natural fit for cross-hook state — but **`beforeTick` does not run during warp** (`PreTickHook` fires only from the realtime `DeclarativeJobTickerDependencies`), while the explorer **does** scout during warp today. This would silently stop offline scouting and break warp/realtime parity. `UnsafeVillagerData` is also explicitly transient, opening a restart-mid-trip hole. `afterExtract` runs in both realtime and warp, so the whole operation belongs there — and once it's one phase, the map is right there to read and no decision needs carrying.
- **Give the result generator town/villager access** so it can register directly. Avoids the meta-item but broadens a widely-implemented interface for one caller's benefit and reintroduces side effects into result generation (rejected as "Option B" in the same discussion).
- **Accept biome divergence** (scout rolls its own biome independent of the map). Simplest, and only a *soft* problem (knowledge self-heals as maps accumulate), but learned loot could sit dormant early-game where it's immediately usable today.

## Consequences

- `AfterExtractEvent` gains the extracted item + a `knowledgeUpdater`; wired in realtime (`registerFoundLoots`) and warp (`ts.withKnowledge`). The `tryGiveItems` `KnowledgeMetaItem` branch and `withKnowledge` overrides are deleted.
- `knowledge_meta_item` is no longer registered; a pre-existing save holding the transient item drops it on load — acceptable for an alpha, as it was only ever a same-tick extraction carrier (same call made for `EffectMetaItem` in ADR-0003).
- Scouting now happens strictly at the extract phase; there is no longer any explorer state that must survive between hooks.
- **Verification gap:** the explorer is not wired into the autotest suite, and the scout rule's happy path dereferences `ItemsInit.GATHERER_MAP` + the server loot tables (unavailable in plain unit tests). The shared `extractedItem`/`knowledgeUpdater` plumbing is exercised by every job's extraction in the jobs autotest (28/28, realtime + warp); the rule's no-extract guard is unit-tested; the loot roll itself is a faithful port of the prior `ExplorerWork.getFromLootTables` roll (same `Loots.getFromLootTables` call and params), differing only in sourcing the biome from the extracted map. The happy path is otherwise covered by the in-game explorer flow.
