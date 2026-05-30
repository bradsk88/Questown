# Bug: `eating/dine_at_time` villager fullness goes negative

## Status: Resolved 2026-05-30 — autotest-verified 2026-05-30

Verify before beta cut: `./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=eating` — all four eating scenarios must pass, and `dine_at_time` must report `[PASS] Villager fullness: …% (min: 25%)` (observed 96%). In-game: a villager seated at a dining-room plate block should recover fullness after eating, not slowly starve.

## Symptom

Clean-world full autotest run produced:

```
[autotest] [FAIL] Villager fullness: -16% (min: 25%)
[autotest] jobs:eating/dine_at_time [FAIL] scenario: some expectations failed
```

After the dine-at-time scenario the villager's fullness was **-16%**. The other eating scenarios passed. Crucially, `-16% × baseFullness (5000) = -800`, which equals exactly 80 flag-ticks × 10/tick of hunger drain over the 800-tick window — i.e. the villager **ate nothing at all** despite consuming all 16 bread.

## Root Cause

`AbstractWorldInteraction.tryGiveItems` (shared by both realtime and warp paths) routed extraction results by item type:

- normal item → `postExtractHook(...)` then `setHeldItem(...)`
- `KnowledgeMetaItem` → `withKnowledge(...)` — **no `postExtractHook`**
- `EffectMetaItem` → `withEffectApplied(...)` — **no `postExtractHook`**

The dining jobs (`DinerWork`, `DinerNoTableWork`) declare `HUNGER_FILL` as an `EXTRACTING_PRODUCT` special rule, and their only product is an `EffectMetaItem` (the eating-mood effect). So on the normal extraction path the effect was applied but `postExtractHook` — the only thing that runs `HUNGER_FILL` → `fillHunger` — was silently skipped. Hunger was never restored.

`eat_raw_food` masked the same defect by *passing*: its villager could not extract normally and instead fell into the give-up path (`JobLogic` → `grabbedInsertedSupplies` → `tryExtractWithNoItem` → `postExtractHook(null)`), which *does* fire the hook. `eat_no_table` (fullness assertion omitted) and `eat_direct` (hunger never drained) both hid it too.

This was a long-standing latent bug, not a recent regression — the effect/knowledge branches never called `postExtractHook` (confirmed back through commit `3e655b7b`). The new fullness assertion in the autotest is what exposed it.

## Fix

In `tryGiveItems`, fire `postExtractHook` in the `EffectMetaItem` branch (mirroring the normal-item branch's `hooked != null ? hooked : ts`). Eating jobs extract only an effect item, so this is what makes `HUNGER_FILL` run when a villager finishes eating.

**Scoped deliberately to the effect branch only.** The first attempt also added the hook to the `KnowledgeMetaItem` branch, which regressed the gatherer: it emits a knowledge item per gather, so firing its `EXTRACTING_PRODUCT` rules an extra time inflated warp loot yields (`gatherer/axe [short_absence]` produced ~20 items vs the expected 0–14, and the gatherer even dined mid-warp). The knowledge branch is left untouched — no eating job produces knowledge results, so it is irrelevant to this bug.

The warp path's `hungerUpdater` is intentionally a no-op (`(in, up) -> in`) and was left unchanged — warp drives hunger separately and the eating scenarios are realtime-only (`skipWarp`).

## Tests

- `ExtractHookFiresForMetaResultsTest` (unit): drives `tryGiveItems` with effect / knowledge / normal results and asserts the hook fires for effect and normal results but **not** for knowledge (the deliberate scoping). The effect case fails without the fix.
- End-to-end: autotest `eating/dine_at_time` (and the rest of the `eating` category).

## Related

- `docs/bugs/resolved/chicken-sunset-chest-bit.md` — the other bar-C blocker from the same clean-world run (resolved 2026-05-30).
