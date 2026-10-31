# Armory tiers and armor stands

Dev, 2026-10-28 (after the Outfitter playtest): "I think there would be a progress (in general: more
room recipes is better). So maybe Armorers Table --> [AT, Chest] --> [AT, Chest, Stand] --> [AT,
Chest, four stands]. Note, we'd also want the armorer to actually place armor on the stands as a
preference - that's probably a special rule or two for the job."

Builds on `a3520486` (the Armorer's Table; the armory is currently armorer's table + 4 chests).

## Vocabulary -> code

| Term | Code / data | Status |
|---|---|---|
| Room tier chain | superset room recipes; a room "contains" every recipe whose blocks it holds, and its identity is the heaviest (`Matches.getTopMatch`, `town/quests/COMPLEX.md` "Top match") | verified: `kitchen_small` -> `kitchen_stocked`, store rooms, libraries already chain this way |
| A job's room | job JSON `"room"`; a job works in any room that contains that recipe (`DeclarativeJobs`, `getRecipeIDs`) | referenced in code; re-check in phase 1 |
| Room recheck | `TownRoomsMap.java:95` `ars.recheckRecipes` after each scan pass | verified |
| Room matching | RoomRecipes `RecipeDetection.getActiveRecipes`: room blocks -> `ItemStack`s (`simulateContainer`) -> `recipe.matches` | verified (RoomRecipes) |
| Recipe weight | `RecipeItemConfig` per item; unlisted items weigh 100 and log "No weight specified" | verified (warning seen for `armorers_table`, `blacksmiths_table`) |

## Phases

1. **Tiers with chests only (Questown data).** Four room recipes:
   `[AT]` -> `[AT, chest]` -> `[AT, chest, stand]` -> `[AT, chest, 4 stands]`. Names are open (below).
   The armorer jobs' `"room"` points at tier 1, so they work in every tier. Weights in
   `RecipeItemConfig` for `armorers_table` and `armor_stand`, so each tier's top match is the
   next one up. Tiers 3 and 4 need phase 2 to ever match.
2. **Armor stands count as room contents (RoomRecipes).** In `getActiveRecipes`, before
   `recipe.matches`, add one `ItemStack(Items.ARMOR_STAND)` for each `ArmorStand` entity whose block
   lies in the room's spaces at the scanned layers (`room.yCoord`, +1). The recipe JSON names
   `{"item": "minecraft:armor_stand"}`, so there's no new ingredient type. Republish the RoomRecipes
   jar to `~/.m2`. Perf: one entity query per room per pass over a small box; run
   `perf/town_large` before committing.
3. **A display job dresses the stands (its own job).** Dev 2026-10-28: putting armor on a stand
   is "NOT a part of the make armor job. But rather its own job. So we could in theory have two
   armorers and one is making the armor while another is displaying it. Much the same way that a
   cook extracts food when its done - even though someone else might have inserted it."
   Closest pattern: the stock jobs (`cook/stock_fuel`: the item is a work-state ingredient taken
   from town storage, the villager walks to its work block, and `add_item_to_container` puts it in
   at state 1). A `crafter/display_armor` job (name open) would take a piece of armor from storage
   and put it in an empty slot on a stand in an armory.
   The wrinkle: a declarative job's work spot is a block (`"block"` in the job JSON), and a stand
   is an entity. Options: (a) the work block is the armorer's table, and a new rule
   (`dress_armor_stand`) equips the item on a stand inside the same room; (b) a new work-location
   kind that targets entities (a bigger change to `WorkLocation` and the job-site search).
   (a) needs no new location kind, but the villager walks to the table, not the stand. A second
   rule could re-target the walk to the stand.
   Same shape later (dev: "can wait"): a cook "grab and display" job for food racks
   (`questown:food_display`).
4. **Path and driver.** The Outfitter's Armor batch asks for a tier (open). The growth driver
   furnishes by using each ingredient item on the floor, which spawns a stand; confirm in a
   `paths/outfitter` run.

## Test ledger

- Phase 1: data test that the chain is strictly nested (each tier contains the one before) and
  that each tier's weight is greater than the one before (extend `WorkTableRecipesTest`).
- Phase 2: RoomRecipes unit test on the clean matcher: a room's items plus entity items match a
  stand recipe, and miss without the stands. Held run: place and break a stand, and the room's
  recipe changes on the next pass.
- Phase 3: a held run with boots in storage and an empty stand in the armory: the display job
  takes them and the stand ends up wearing them. With two crafters, one makes and one displays.
  When every stand is full the job isn't offered (no empty slot = no workable spot).
- Phase 4: `paths/outfitter` x3 PASS.

## Open decisions (dev)

1. **Tier names.** For example: Armorer's Corner `[AT]`, Armory `[AT, chest]`, Armor Display
   `[+ stand]`, Grand Armory `[+ 4 stands]`.
2. **Which tier the Outfitter path asks for**, and whether higher tiers unlock anything (better
   armor templates, speed) or are only a prestige / room-quest ladder.
3. **Does armor on a stand count as town storage?** Quests, board requests (the 8-in-storage
   pause), and villagers fetching armor all read containers today. If a stand doesn't count, a
   dressed stand hides armor from a Leather Boots quest.
4. **When every stand is full**: the display job is simply not offered. Or does it swap a better
   piece in for a worse one (which then goes back to storage)?
5. **Today's armory** (AT + 4 chests) becomes which tier? Saved worlds keep that room, and it
   would match tier 2 under the new chain.
6. **Phase 3 approach**: (a) the work block is the armorer's table, plus a rule that dresses a
   stand in the same room, or (b) a new entity work-location kind. Recommendation: (a).

## Decisions (dev, 2026-10-28)

1. Names as suggested for now: Armorer's Corner `[AT]`, Armory `[AT, chest]`, Armor Display
   `[AT, chest, stand]`, Grand Armory `[AT, chest, 4 stands]`.
2. The Outfitter path asks for the **Grand Armory**.
3. Armor on a stand **counts as town storage**.
4. **Surplus goes in chests**: the display job is offered only while a stand has an empty slot.
5. Moot: no saved world has an armorer's-table armory yet (the table is new in `a3520486`). An old
   blacksmith's-table armory stopped counting at `a3520486`; swapping in an armorer's table fixes it.
6. **(b), an entity work location**: "I want the villager to \"work\" at the stand - they don't
   need to table at all for that job."

Dev, same day: "i think the blacksmith probably has a similar setup, but with item frames on
walls". Phase 2 counts item frames (and glow item frames) as well as armor stands, so smithy tiers
can be data-only later. Limit: rooms are scanned at the furniture layer and one above
(`room.yCoord`, +1), so a frame hung higher on a wall doesn't count.

## Status

- Phase 1 and 2 verified 2026-10-28: `paths/outfitter` PASS 75/75 (the driver built a Grand Armory
  with 4 stands; the room went Store Room -> Grand Armory and the quest completed),
  `crafter/leather_boots` PASS on the Armory-tier fixture. `perf/town_large`: the entity query
  isn't measurable (`roomsHandle` 66.5 ms with it, 69.8 ms with the old jar); `roomsHandle` is
  slow at HEAD either way (`.scratch/perf-2026-10/roomshandle-70ms-in-town-large.md`).
- Phase 3 (display jobs, displays as storage, "pause stops makers only"), smithy tiers and the
  showcase batch built 2026-10-28. Live: the `crafter/display_armor` and `crafter/display_weapons`
  fixtures PASS (boots on a stand, sword in a frame). The full `paths/outfitter` showcase run is
  blocked on room-detection speed (`.scratch/perf-2026-10/roomshandle-70ms-in-town-large.md`; the
  dev's next task).
- Phase 1 and 2 built 2026-10-28: RoomRecipes `a1029a4` (published to `~/.m2`; the previous jar is
  saved in the session scratchpad as `roomrecipes-1.20.6-neoforge-940aeb9.jar`), Questown tier
  recipes, names, weights, path. Phases 3 (display job, entity work location) and "stands count
  as storage" are not started.
