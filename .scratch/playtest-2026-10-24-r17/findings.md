# Dev playtest 2026-10-24 r17 (fresh world, main checkout at 827fdeea)

Server log `/tmp/dev-server-27.log` (launched 23:42:18 box time, ready 23:42:44, Dev joined
23:43:08). Old `run/world` archived to `~/questown-worlds/main-run-world-2026-10-21-before-r17.tgz`.

## Findings

1. (design request, dev chat 23:48:16) **"IMO the mineshaft and fishing station crafting recipes
   should be hidden until the relevant quests have been opened."**
   - Where: the flag crafting tab lists `FlagCraftRecipes.ALL` unconditionally (wand, welcome mat,
     job board, mineshaft from stone stairs, fishing station from a fishing rod). The screen
     (`FlagCraftingScreen`) and `FlagCraftMessage` both address rows by index.
   - Shape (not started): a per-recipe "visible when" rule on `FlagCraftRecipes` (mineshaft: a
     quest for `questown:block_room/block.questown.mineshaft` has been posted, i.e. the miner beat;
     fishing station: a quest for its block room has been posted), computed server-side, sent with
     the menu, and re-checked in `FlagCraftMessage` so a hidden row cannot be crafted by index.
   - Open: "opened" = posted (even if later completed) - assumed yes, so the row stays once the
     player has seen the quest. Hidden, not greyed out (dev's word).
2. (bug, low, pre-existing; from the startup warnings 00:02:47) **The big soup pot's room weight
   never applies.** `RecipeItemConfig` registers `add(SoupPotBlock.ITEM_ID, BIG_SOUP_POT)` with the
   bare id, and the stored config shows `soup_pot=469`, while room recipes look up
   `questown:soup_pot` and log "No weight specified for item ... [questown:soup_pot]", so it
   weighs the default 100. The small pot registers via `Questown.ResourceLocation(...)` and is
   fine. Fix: register the big pot with its namespace too (saved configs keep the old key).
3. (GUI request, dev chat 00:05:44) **"'Take block of progress' should be on the bottom of the
   menu."** `TownBlockofProgressScreen.renderText` flows the text (town has N, optional "Earned by",
   optional red "flag full", "many uses") and sets `buttonY` to wherever the text ends, so the
   button sits mid-panel and moves whenever an optional line appears (the r16 skill trap: a click
   at the old y took nothing). Fix shape: anchor the button to the panel's bottom edge
   (`(height - backgroundHeight) / 2 + backgroundHeight - button height - margin`), keep the
   text flowing above it, and confirm with gui-lint that the text never reaches the button.
4. (visual request, dev chat 00:07:06) **"There should be some kind of poof or sparkle when
   villagers disappear to leave town."** The vanish is one synced boolean:
   `VisitorMobEntity` sets `entityData visible = !job.shouldDisappear(town, position())` each
   tick (the leaver job's `remove_from_world` special rule; `ProductionJob.shouldDisappear`).
   Fix shape: on the server, when the value flips true -> false (leaving) emit a particle burst at
   the townie (e.g. `ServerLevel.sendParticles(ParticleTypes.POOF, ...)`, ~12 particles around
   the body); likely the same on false -> true (returning). Route the call through `mc/Compat`
   if the strangler gate flags it.
5. (bug, high: client crash, 00:09:24) **Clicking the raw-food icon on the raw-food quest crashed
   the client.** `ItemJobsScreen.getFirst` -> `jobs.get(0)` on an empty list
   (`ArrayIndexOutOfBoundsException: Index 0 out of bounds for length 0`, crash report
   `run-client/crash-reports/crash-2026-10-25_00.09.24-client.txt`). The server found no known
   producer (`[item-jobs] producers of [rabbit, porkchop, mutton, chicken, beef, potato]: []`):
   loot producers count only once their drops are known, and the hunter had not returned yet.
   **Fixed same session:** empty-list guard + a "No job in your town is known to make this yet"
   line. Pre-existing (not from today's picker change: different lookup).
