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
