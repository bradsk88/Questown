# Feat: LOOT quest type, the onboarding raw-food quest completes on production

Status: planned (2026-09-26).

## Problem

The hunter's onboarding quest is "get mutton": a specific-item quest completed when mutton is
deposited in a store-room chest. Two issues.

1. The requirement is narrower than the intent. The intent is "bring back raw food" (the hunter
   hunts, any raw fodder counts), but the quest only accepts `minecraft:mutton`.
2. The completion mechanism is the wrong contract. An `ITEM` quest reads as a player-facing
   "bring me X", satisfied by a chest deposit. The raw-food quest is about the town producing
   food via work, a different contract. The mutton-eating fix (2026-09-24) makes the mutton reach
   a chest, but completing on deposit still couples the quest to the deposit path. The cleaner
   signal is "a townie extracted loot from their job".

## Current mechanism (verified in code)

- Quest model: `Quest<KEY, ROOM>` (`town/quests/Quest.java`), `KEY = ResourceLocation`. The
  `QuestType` enum is `ITEM, ROOM, JOB_CHANGE, CONCURRENT_JOBS, UNKNOWN`. The mutton quest is
  `MCQuest.item(batchUUID, null, Compat.getItemId(Items.MUTTON), 1)` at `TownQuests.java:803`, an
  `ITEM` quest whose wanted id is `minecraft:mutton`.
- Matching (chest path): `QuestBatches.processItemQuests` (filters `getType() == ITEM`) calls
  `TownQuests.questMatchesStack`, which is exact `wantedId.equals(getItemId(stack))`. There is a
  literal `// TODO: Also handle tags` in `processItemQuests`.
- Trigger (chest path): `TownFlagTicker.handleNewStoredData` calls
  `processItemQuests(getAllStacks(...))`, items currently in chests.
- Tag plumbing already exists in `gui/Ingredients`: `fromString("#tag")`, `isTag`, `getTag`,
  `toString`, `asWorkRequest`, `getName` (a tag renders as `"#" + tag`), and
  `representativeItemStack` (a cycling icon). It is used by needs and work requests, not quests.
- Serialization: `gui/QuestTypes` uses `type.name()` / `QuestType.valueOf(name)` for the
  `recipe_type` NBT and the network codec. Adding an enum value round-trips with no change there
  and leaves old saves valid.
- The raw-food tag already exists: `#questown:villager_simple_furnace_food_raw` (rabbit, porkchop,
  mutton, chicken, beef, potato), `TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW` (a
  `TagKey<Item>`; `.location()` is `questown:villager_simple_furnace_food_raw`).
- The extract hook: work output is handed to the townie after the extract step, in
  `RealtimeWorldInteraction` (`preExtractHook` calls `tryGiveItem`; `onWorkActionCompleted` runs
  the `resultGenerator`). There is also an existing `addItemExtractionListener` /
  `ExtractedItemAware` mechanism.

## Design (aligned with dev)

Introduce a `LOOT` quest type, satisfied when a townie extracts matching loot from their job.
Production, not a chest deposit. The `ITEM` type keeps its meaning (a player-facing "bring me X"
via chest deposit): the sword, apple, and exotic-wood quests stay `ITEM`.

### 1. New `QuestType.LOOT`

- Add `LOOT` to `Quest.QuestType`, before `UNKNOWN`.
- `QuestTypes` needs no change: `name()` / `valueOf` round-trip the new value, and old saves stay
  valid.
- Factory plumbing: `Quest.QuestFactory.newLootQuest(owner, itemId, count)`; the
  `MCQuestBatch.FACTORY` implementation; `MCQuest.loot(batchUUID, ownerId, itemId, count)`; and
  `QuestBatch.addLootQuest(...)`.
- Blast radius, all `switch` sites over `QuestType` (add `case LOOT` to each):
  - `UIQuest:140` (icon / ingredients)
  - `UIQuest:201` (name)
  - `QuestsScreen:290` (tooltip)
  - `QuestsScreen:507` (lesson icon)
  - `QuestBatches:181` (duplicate check / re-add)
- No `values().length` or canonical-ordering assertion covers `QuestType` (those count assertions
  are `ChickenBeatState` and `ChickenBubbleIcon`). The `QuestType.` references in tests are
  equality checks and do not break when a value is added.

### 2. Tag-aware matching, one `asIngredient` helper

`asIngredient(ResourceLocation rl)` returns `Ingredient.of(item)` when `ITEMS.containsKey(rl)`,
else `Ingredient.of(TagKey.create(ITEM_REGISTRY, rl))`. It drives:

- `TownQuests.questMatchesStack` (replaces the exact equality, clearing the
  `// TODO: Also handle tags`). Result is identical for existing item quests.
- The LOOT completion path (step 3).
- Display (step 4).

### 3. LOOT completion on extraction

At the extract step (the townie receives the loot, in `RealtimeWorldInteraction`), for the town,
match each open `LOOT` quest's `asIngredient(wantedId)` against the extracted item and complete it
(complete on the first match, no accumulation). The item is not consumed: it stays carried and the normal
deposit / eat path (governed by the 2026-09-24 deposit-before-dining fix) decides what happens to
it. The chest path already filters `getType() == ITEM`, so LOOT quests are excluded from it with
no change, and there is no double count.

### 4. Display and broadcast

- `UIQuest:141` icon: `case LOOT -> nCopies(count, asIngredient(wantedId))`. A tag ingredient
  renders its cycling representative item.
- `UIQuest:202` name: `case LOOT ->` the raw-food name (a localized "Raw food", or the tag name via
  `Ingredients.getName`; see open decisions).
- `QuestsScreen:290` tooltip and `:507` lesson icon: `case LOOT ->` an item-like tooltip and
  lesson entry.
- `TownMessages.questCompleted` / `questLost` (`TownMessages.java:102` / `113`): branch on type,
  LOOT uses the tag name, ITEM uses the item name.
- Inspect commands (`QtDevInspectCommand`, `InspectQuestsCommand`): handle `LOOT` in the "wants"
  output.

### 5. The mutton quest becomes a LOOT quest

`TownQuests.java:803`: `MCQuest.item(..., Compat.getItemId(Items.MUTTON), 1)` becomes
`MCQuest.loot(batchUUID, null, TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW.location(), 1)`.
Update the flavor text (currently "…place the mutton in a store room chest…") to describe bringing
back raw food. The quest stays in the `jobChangeAndFood` onboarding phase.

## Key facts (verified in code)

- `QuestTypes` is `name()` / `valueOf`, so adding `LOOT` is backward-compatible with no codec
  change.
- `processItemQuests` filters `getType() == ITEM`, so LOOT quests are excluded from the chest
  path with no change.
- `TownFlagBlock.hasActiveExoticWoodQuest` (`TownFlagBlock:329`) and
  `SupplySwordCommand:123` branch on `ITEM` for the wood and sword quests. Those stay `ITEM` and
  are unaffected.
- The raw-food tag and `TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW` already exist.
- The extract hook is `RealtimeWorldInteraction` (`preExtractHook` / `onWorkActionCompleted` /
  `addItemExtractionListener`).
- The deposit-before-dining fix (2026-09-24) is implemented and verified. LOOT completion is
  orthogonal: it fires on extraction, before the item is deposited or eaten.

## Vocabulary to code

| Vocabulary | Code |
|---|---|
| mutton quest | `MCQuest.item(...)` at `TownQuests.java:803` |
| item-quest matching (chest) | `TownQuests.questMatchesStack`, called from `QuestBatches.processItemQuests` |
| chest-deposit trigger | `TownFlagTicker.handleNewStoredData` to `processItemQuests` |
| tag plumbing | `gui/Ingredients` (`fromString`, `isTag`, `getName`, `representativeItemStack`) |
| quest type codec | `gui/QuestTypes` (`name()` / `valueOf`) |
| raw-food tag | `#questown:villager_simple_furnace_food_raw` / `TagsInit.Items.VILLAGER_SIMPLE_FURNACE_FOOD_RAW` |
| extract step | `RealtimeWorldInteraction` `preExtractHook` / `onWorkActionCompleted` |
| new LOOT type | `Quest.QuestType.LOOT`, `MCQuest.loot`, `QuestFactory.newLootQuest` |

## Phases

1. Add `QuestType.LOOT` and the factory plumbing (`QuestFactory.newLootQuest`, `MCQuestBatch`,
   `MCQuest.loot`, `QuestBatch.addLootQuest`). Add `case LOOT` to the five switch sites.
2. Add the `asIngredient` helper and rewire `questMatchesStack` (clears the `// TODO: Also handle
   tags`).
3. Add the LOOT completion path at the extract hook: complete open LOOT quests on a matching
   extracted item, do not consume the item.
4. Display and broadcast: `UIQuest` (icon and name), `QuestsScreen` (tooltip and lesson icon),
   `TownMessages` (completed / lost), inspect commands.
5. Convert the mutton quest to `MCQuest.loot(...)` with the raw-food tag and updated flavor text.
6. Tests: update any mutton-quest assertion that pins the `ITEM` / mutton shape, and add unit tests
   for `asIngredient` (item vs tag) and a LOOT match.
7. Verify: a held run where the raw-food LOOT quest completes on extraction without a chest
   deposit, and the sword / apple / wood quests still require a deposit.

## Test ledger

- [ ] Unit: `asIngredient`. An item RL yields `Ingredient.of(item)`; a tag RL yields
      `Ingredient.of(tag)`; a missing RL has a defined behavior.
- [ ] Unit: a LOOT quest matches a tag member via `asIngredient(...).test(stack)`. An ITEM quest
      still matches only its exact item.
- [ ] Unit: `QuestType.LOOT` round-trips through `QuestTypes.serializeNBT` / `deserializeNBT` and
      `toNetwork` / `fromNetwork`.
- [ ] Update: any mutton-quest assertion in `TutorialPhaseGateTest` / `QuestsTest` that pins
      `ITEM` plus the mutton wanted id, to `LOOT` plus the raw-food tag.
- [ ] Held run: the raw-food LOOT quest completes on extraction (a townie hunts, the quest is done)
      with no chest deposit. The sword / apple / wood quests still require a deposit.

## Decisions (resolved 2026-09-26)

- `QuestType` name: `LOOT`.
- LOOT display name: a localized "Raw food" (not the raw tag name).
- Count semantics: a LOOT quest completes on the first matching extraction, no accumulation.
