---
title: Onboarding storeroom quests should complete on any storeroom, not the exact recipe
status: closed
created: 2026-09-04
closed: 2026-09-05
priority: p2
---

# Storeroom quest completion: superset for onboarding, exact for random

The town quests ask for a "Small Storeroom" (`store_room`). If the player builds
a bigger storeroom (put all the blocks in the room, then register the door) they
do **not** get completion for the small-storeroom quest, because the room
completes only its single **top match** — the most-elaborate recipe it contains
— and a 6-chest room's top match is `store_room_huge`, never `store_room`.

This is a design decision, not a one-line bug. Implementation is deferred; this
file records the decision and the agreed scope.

## Root cause (verified in code)

- A room's candidate recipes are those it **contains** (a superset test,
  `NoMCRomRecipe.foundMatchRecipe`: `found.size() >= recipe.size()`).
- Exactly one is picked as the **top match** by highest weight
  (`Matches.getTopMatch` -> `QuestBatchSeed.computeQuestCost` ->
  `RoomRecipes.getRecipeWeight`, sum of `RecipeItemConfig` weights).
- All three "register the door" paths funnel completion through that single top
  match:
  - `TownFlagBlockEntity.roomRecipeCreated` ->
    `runForTopMatch(..., quests.markQuestAsComplete(room, r))`.
  - `roomRecipeChanged` only forwards when the top match **changed**.
  - `ActiveRecipes.update` only fires `roomRecipeChanged` when the recipe key
    changed.
- Storeroom tiers share the identical `forge:chests` tag, so weight scales with
  chest count; the biggest tier always wins. The `store_room` quest (the
  cheapest) never wins.

## Agreed design

- **Randomly generated quests: exact match.** "The villagers want this specific
  room." A room that is a superset of the wanted recipe does **not** complete a
  random quest. Random quest generation is `QuestBatchSeed.grow` (which can emit
  a `store_room` room quest via `getNeededRooms`, fed by the organizer job's
  `"room": "questown:store_room"`).
- **Onboarding quests: superset match.** A storeroom quest added by the
  onboarding flow completes on **any** storeroom, i.e. any recipe in the
  storeroom chain. Onboarding quests are the tutorial-phase and kickoff quests in
  `TownQuests` (`addQuestsForVillagerKickoff`, `addPhase6StorageUpgradeQuest`,
  ...).
- **Superset set = the storeroom chain** (decided: "any storeroom", not "any
  room with chests"): `store_room`, `store_room_medium`, `store_room_big`,
  `store_room_huge`, `warehouse`, `storeage_facility`. This is **not** "any room
  containing `forge:chests`", which would also match `armory`, `range`,
  `kitchen_stocked`, `bakery_stocked`, etc.
- **Superset applies to the onboarding upgrade quest too** (decided: agree with
  the read). The phase 6 quest is the storeroom upgrade
  (`store_room` -> `store_room_medium`); a `store_room_big`/`store_room_huge`
  room contains `store_room_medium`, so it counts as upgraded and phase 7
  (flagpole) unlocks.

## Why the top-match rule stays intact

A blanket "complete every recipe the room contains" change would mis-fire the
upgrade system and quest generation, which are keyed on the top match:
- `TownQuests.canBeUpgraded` / `markRecipeAsConverted` are keyed only on the top
  match (a room is "upgraded" iff its top match moved small -> large).
- `QuestBatchSeed.getCost` / `alreadyRequested` use the same weight model to pick
  and size quests; a room that "satisfies" many recipes at once would over-count
  against the batch's target weight.
- The `FirstRoomUpgrade` advancement / tutorial phase 7 gate key on the medium
  upgrade.

So the fix adds a **separate** completion path for onboarding quests only.
Completion keys on the **recipe id** (`getWantedId`), and `alreadyRequested` /
dedup are keyed by recipe id, not room, so the top match is never replaced — no
double completion, and a random `store_room` batch quest is still not satisfied
by a huge room.

The only non-storeroom recipe that is a superset of another is the storeroom
chain itself (verified against `containsAllTags`): the kitchen upgrades
(`kitchen_small` -> `restaurant`/`diner`), and the enchanting/library/bedroom
tiers, are **not** supersets. So the superset behaviour is confined to storerooms
and does not touch other upgrade chains.

## Deferred: UI feedback

The quest UI should indicate **"a superset of this room exists, but not this
exact room"** — i.e. show that a room partially satisfies the quest without
completing it (a distinct status from active / completed / lost).

This is **deferred to later**; implementation is not in scope for the superset
completion fix. Current `Quest.QuestStatus` has only UNSET / ACTIVE / COMPLETED /
LOST; a new status (and its UI rendering in the flag's quest tab and the
`TownQuestsContainer`) would be needed. Do not build this in the same change.

## Implementation

- `Quest.onboarding` (getter/setter), set at the tutorial/kickoff batch-creation sites in
  `TownQuests` (`addQuestsForVillagerKickoff`, `addPhase4CrafterQuests`,
  `addPhase5SupplyChainQuests`, `addPhase6StorageUpgradeQuest`,
  `addPhase7ExoticWoodQuest`) and carried through `MCQuest.completed()`/`lost()` and NBT
  (`NBT_ONBOARDING`).
- `TownQuests.roomRecipeCreated` runs the superset completion for onboarding quests (a room
  matching the wanted recipe completes it) before the top-match path; the top match still drives
  the message, the `FirstJobBlock` advancement, and `roomRecipeChanged` / `markRecipeAsLost` (which
  is not superset — a superset room doesn't trigger lost/upgrade semantics).
- `QuestBatchSeed` (random/procedural) quests leave `onboarding` false, so they stay exact.
- Tests (`TutorialPhaseGateTest`) drive the mechanism — match the room's recipe IDs against each
  onboarding quest's wanted id, complete on match — through `markQuestAsComplete`, mirroring the
  real path. The full `roomRecipeCreated` (which needs a live `ServerLevel`) is exercised by
  play-test. Asserts a random `store_room` quest is **not** completed by a huge room, and an
  unmatched onboarding quest stays active.
- Doc map: `src/main/java/ca/bradj/questown/town/quests/COMPLEX.md`.

## Notes

- The reported symptom is the phase 6 upgrade quest; the kickoff small-storeroom
  quest and a random small-storeroom batch quest share the same root cause but
  only the onboarding ones change behaviour.
