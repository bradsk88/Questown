# Recipe weight + room-recipe hydration

## Missing recipe weights: warn once per id (`getRecipeWeight`, 2026-10-10)

`RoomRecipes.getRecipeWeight` adds the configured weight per ingredient and falls back to
`DEFAULT_ITEM_WEIGHT` (100) when an item/tag has no entry in
`RecipeItemConfig.getWeights()`. A missing entry is a tuning hint, not a fault — the
default is applied deterministically — so it now logs at WARN at most **once per id per
JVM** (`reportedUnweighted`), instead of one ERROR per recipe carrying the id.
`#c:chests` appears in most stocked rooms, so the old behaviour painted a startup burst of
ERROR lines that read as a broken server. `QuestBatchSeed.computeQuestCost` caches the
result per recipe id anyway; the dedupe matters because many *different* recipes share
the same unweighted ids. Callers: `QuestBatchSeed.computeQuestCost` (quest cost /
`Matches.matches`).

## Weights saturate at MAX ("never recommend"; r16 #5, 2026-10-24)

`RecipeItemConfig` gives "never recommend" items `Integer.MAX_VALUE` (the plate, the bowl rack,
`#questown:job_board_inputs`). `getRecipeWeight` summed them in a plain int, so a recipe with
several such ingredients overflowed: four plates made `dining_room` weigh **-4**. `getTopMatch`
(the room announcement's choice among a room's matched recipe ids, by max cost) then preferred
`empty_room` (0), so a working dining room was announced as "Empty Room" and a plate change
announced nothing (both sides read `empty_room`). The sum now saturates
(`UtilClean.saturatingAdd`, `SaturatingAddTest`): `dining_room` = MAX, announced correctly
(live: `room_changed [dining_room -> empty_room]` and back on a plate removed and restored).
Quest generation is unchanged: `AbstractQuestGarden.notValid` already rejects both MAX and
negative costs, so plate rooms are never quested either way. The plate's MAX weight carries a
TODO from when dining was off ("Bring back hunger/dining and reduce this cost").

## `RoomRecipes.getName`: block rooms fall through to the block's own lang key

A block room is registered as `block_room/block.<ns>.<block>`
(`RoomBlock.getRoomId`), and there is no `room.*` lang key for such ids, so
`getName` returned a raw key that rendered verbatim in the quest card and the
"…created at location" broadcast (day-1 playtest: the job-board room). `getName`
now special-cases the `block_room/block.` prefix and returns
`translatable("<ns>.<block>")` (the block's own name, e.g. "Job Board").
`RoomRecipesNameTest` pins both the block-room and shape-room keys.
(`TownMessages.roomRecipeChanged` still inlines `"room." + path` for block-room
ids — same disease, not yet patched.)

## filterSpecialBlocks: one card icon per bed (2026-10-22)

`RoomRecipes.filterSpecialBlocks` hides the ingredient after each bed, because a recipe lists two
`#minecraft:beds` per bed (a bed is two blocks and matching counts both). It was removed in
`f6097a58` on the belief that a bed counts once (RoomRecipes `deb6333`); both are reverted. See
`gui/COMPLEX.md` "Room quest cards show one icon per bed".

## Campfire range: radius box, ±2 Y (2026-10-23, r12 #2)

`TownCycle.findCampfire` (the Campfire quest, the chicken's campfire beat via
`ChickenArcConditions.isCampfireLit`, `TownPois`) and the wand's "registered campfire" gate
(`TownWand.handleCampfireClick`) both used to require the flag's exact Y. On a slope, a lit
campfire one block up never completed the quest, and nothing told the player why (r12: flag
y=74, campfire y=75; a second one at y=74 completed at once). The vertical rule now lives in
`CampfireRange`: up to `VERTICAL_TOLERANCE` (2) above or below the flag (dev ruling). The scan
tries the flag's own Y first (`Y_OFFSETS = {0, 1, -1, 2, -2}`), so a level campfire still wins
over a sloped one. The horizontal rules are unchanged, and they still differ: the scan covers
`-radius .. radius-1`, while the wand requires `|d| < radius`. Pinned by `CampfireRangeTest`;
the scan's wiring is registry-bound (`Level`, `Blocks.CAMPFIRE`), so it is checked live.

## The Dinner Plate gets a source and a real weight (dev ruling 2026-10-24)

`RecipeItemConfig` weighted `questown:plate_block` at `Integer.MAX_VALUE` ("never recommend",
TODO "bring back hunger/dining and reduce this cost"), so the garden never offered a dining room.
Dining is back, but the plate also had **no source**: no crafting recipe anywhere in the
resources and no job made it (r16's dining room used creative plates). Dev ruling: a crafter
makes plates. `crafter/plate` (`questown_jobs/crafter_plate.json`, child of `crafter/bowl`): 1
plank -> 2 plates at the crafting table, the bowl's shape (`PlateProducerDataTest`). The plate's
weight is now `RecipeItemConfig.PLATE` (a crafting-table item from planks), so a dining room
weighs four of those.

- **Saved configs keep the old value.** `questown-item-weights-server.toml` stores the whole map
  as one string (`ItemWeightCodec`) and `getWeights` decodes only the stored string, so an
  install that already wrote the file keeps `plate_block=2147483647` until the entry is edited
  or the file deleted. The new default reaches fresh installs.
- The recognition side (a 4-plate room still matches `dining_room` over `empty_room`, now that
  the plates no longer dominate `getTopMatch`) is not unit-testable: `getRecipeWeight` reads the
  loaded config and `RecipeItemConfig`'s static init touches the item registry. Left to a live
  check.
- The growth driver discards a dining-room quest (4 plates > its 3-item row), like the inn.
