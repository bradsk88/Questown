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
