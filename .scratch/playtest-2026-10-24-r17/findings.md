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
6. (GUI, dev 00:14:35, screenshot `run-client/screenshots/2026-10-25_00.14.10.png`) The #5
   empty-state text overlapped the "Page 1 / 1" row (a fixed +24 from the title), and said
   "Townies". **Dev rule: players must NEVER see the word "townie".** Fixed: text placed under
   the page-navigation row, aligned to it; wording says "Villagers". (The other `townie` hits in
   `en_us.json` are keys only.)
7. (GUI request, dev 00:15:01) **"I'd like the menu background extended so the 'view completed
   quests' button isn't weirdly floating in space."** (Quests screen.) Fixed: the panel grows down
   by gap + button + 6 px padding while the footer button shows (`QuestsScreen.panelHeight`).
   Needs a client restart and the dev's eyes (not seen yet).
8. (feedback, dev 00:17:58) **"A message should say 'you feel groggy from sleeping on the ground'
   (status effects applied) or something."** `CampfireSleepForgeEvents.applyGrogginess` gives
   Mining Fatigue + Weakness for 6000 ticks, created with `visible=false` (no particles, no icon),
   and said nothing. Fixed: a chat line on wake, `message.questown.campfire_sleep.groggy`
   ("You feel groggy from sleeping on the ground: Mining Fatigue and Weakness for 5 minutes.").
   Server-side: loads at the next server restart. Open: whether the effect icons should show too.
9. (design request, dev 00:19:40-48) **"While we will leave it mostly to the player to get wool for
   building beds, it would be nice if the hunter could bring SOME back (maybe 1 every 5 trips, or
   whatever random chance would be equivalent) to unblock a scenario where wool or steel (for
   shears) is scant."**
   - Today: wool in `loot_tables/jobs/hunter_sword/default.json` (weight 5 of 130),
     `desert.json` and `savanna.json` (10 of 85); **none** in forest, taiga, dark_forest, jungle,
     snowy, mushroom_fields. A biome's own table replaces the default, so a forest hunter never
     brings wool.
   - Trips: `hunter/sword` 1 attempt, `sword_half_day` 2, `sword_full_day` 4 (`biome_loot`).
   - Rolls: `Loots.loadFromTables` calls vanilla `LootTable.getRandomItems` repeatedly until the
     trip's item count is reached, so a per-roll chance is not a per-trip chance; the per-trip rate
     depends on how many items a trip brings.
   - Possible gate: the `VillageSelfSufficiencyExecutor.seedGatherKnowledge` comment says a
     leaver's biome_loot extract is narrowed to items the town already knows. If so, wool must
     become known before it can come back; verify in the extract path before picking numbers.
   - Plan: compute a weight (or a conditioned pool) per table that gives ~1 wool per 5 short
     trips, add it to every hunter table, and check the knowledge gate.
10. (judge/status text, dev ~00:27) **"Hunter is looking for supplies currently. But they have all
    they need..."** Log: Villager 1 levelled to 2 (00:26:05), deposited its BOP (`bop_depositing`
    00:26:13-17), tried to eat, then `downtime` (status `NO_SUPPLIES`, the downtime job needs
    nothing) and `seeking_work` from 00:27:21. Storage held 4 stone swords + food throughout. It
    is evening/night: `Nightfall` logged 00:26:35 (woke 00:17:00 at dayTime 0). A trip only starts
    when it fits before the next day window (`ServerJobsRegistry.canFit`, `DayWindowTest`: the
    short trip is 2000 ticks), so the hunter is correctly waiting for morning. **The bug is the
    text:** it reads as missing supplies when the real reason is "too late in the day to set out".
    Not the sword-fetch stall (no COLLECTING_SUPPLIES, no `[STALL:collecting]`). To check: what the
    tooltip said exactly, and whether it resumes at dawn. Fix shape: when the seeker is idle
    because no job fits the remaining day, show e.g. "It's too late to set out today. They'll
    leave in the morning."
    **Dev (00:3x): "I think it was just too late in the day. Their status probably should have
    been 'there's not enough time for this villager to do their work' - or just skip ahead to
    'relaxing'."** Two options to choose between when building: (a) a dedicated status text for
    "no job fits the rest of the day"; (b) go straight to the downtime/"relaxing" status.
    Agent suggestion: (b) with the reason as the tooltip's second line ("Relaxing. Too late in the
    day to set out."), so the screen shows calm, not a problem, and still says why.
11. (GUI, dev 00:31:47) **"It's a bit annoying that 'take BOP' closes the whole menu."** Same
    screen as #3. The button's handler sends `GiveBOPMessage` and then
    `Minecraft.getInstance().setScreen(null)`. Staying open needs the count to refresh:
    `menu.blocksOfProgressCount` (and `bopRootJobs`) are filled when the menu opens, so either the
    server answers the take with the new count (preferred: true after a rejected take too) or the
    screen decrements locally. Hide or disable the button at 0. Build together with #3
    (bottom-aligned button).
12. (bug, dev 00:32:49) **"'View completed quests' shows incomplete quests too, it shouldn't."**
    `QuestsScreen.cardsData()` returned the whole list in the completed view (by the earlier
    design). Fixed: completed only, and the button toggles back ("← View open quests"),
    returning to page 1. Client-side; needs the dev's eyes after a client restart.
13. (design, dev 00:33:57) **"The campfire shouldn't go out in the morning. It's useful for
    finding the way back to the village."** `CampfireSleepHandler.onWake` extinguished it
    (0eb1191c, 2026-04-08). Fixed: it stays lit. Server-side: loads at the next server restart.
14. (GUI, dev 00:38:11) **"When looking at the 'what jobs make this item' UI, the 'Produces' row
    should only show matching items."** Fixed: `ProducedMatches.of` filters the row (and the
    Add button's item) to results matching the clicked item, falling back to all when none match.
    Client-side; needs a client restart.
15. (GUI, dev 00:39:07) **"The first row on the work requests UI is darker than the rest."** The
    request picker (`AddWorkScreen`) drew a translucent dark fill (0x30000000) behind each category
    slot, and categories come first, so the first row read darker. Fixed: no tint; the cycling icon
    and the category tooltip mark categories. (Assumed this screen: the job board's only dark fill
    is the page-number bar.) Needs the dev's eyes after a client restart.
16. (GUI, dev 00:39:48) **"Crafting menu got too big."** Screenshot `2026-10-25_00.39.54.png`:
    5 recipe rows + the 4-line "Begin moving" paragraph + button = ~257 GUI px in a 240-px GUI;
    the title and the button were cut off. Fixed: the paragraph is now the move button's hover
    tooltip (~197 px). #1 (hide mineshaft/fishing rows) will shrink it further.
17. (bug?, dev 00:41:05) **"Not sure why gatherer 'needs blocks of progress'."** Villager 2
    (gatherer 4f54c263) levelled at 00:40:14, entered `gatherer:bop_depositing`, deposited
    (`messages.bop.earned` 00:40:24, `[Experience] +0 from gatherer:bop_depositing`), then at
    00:40:49 re-entered `bop_depositing` (from `bop_depositing`) and sits in NO_SUPPLIES: the job's
    tool is the BOP itself (`relevant tools` = one ingredient), and it holds none, so the needs row
    shows Block of Progress. Open: why the job was re-entered after a completed deposit (latch not
    cleared? a self-restart of the job?). The hunter's deposit at 00:26 went straight to downtime.
    Watch: the 3000-tick BOP-deposit limit (r16 #9) should end it near 00:43:20.
18. (text, dev 00:40:41) **"^ that message should be two lines."** Most likely the level-up chat
    line (00:40:14, `message.villager.leveled_up` = "%s reached level %s"); to confirm which.
19. (cook bug, dev 00:45:03) **"I put coal in the furnace, but the villager keeps walking up to
    the furnace with raw food, standing for a second, then inserting it, but it does not go into
    the furnace or cook."** Cook = villager 4f54c263 (the former gatherer), job `cook:mutton`.
    It took a raw mutton 30 times by 00:45:07, about every 13 s, each followed ~8 s later by
    `Job changed to cook:mutton [from cook:mutton]`, with no insert. That is the JobLogic max-ticks
    path: `workSpot == null` past `maxTicks` -> `changeToNextJob` -> the board re-rolls the same job
    (the "Reached max ticks ... asking the board" line is DEBUG, so absent from the dev log). So the
    cook finds no usable furnace spot for mutton. Hypothesis to confirm with the dev's screenshot:
    the coal went into the furnace's top (input) slot, blocking the mutton. The #17 gatherer
    `bop_depositing -> bop_depositing` loop is the same max-ticks path.
20. (GUI, dev 00:46:25) **"We should remove the quests tab from villager UIs. It's not useful."**
    #17 screenshot `2026-10-25_00.40.57.png`: villager 2's inventory tab shows two Block of Progress
    icons in its needs area during the deposit loop, and the villager "Quests" button (#20).
    #19 dev 00:47:15: "Yep, something very broken with this cook. They are not successfully
    inserting the mutton. Not sure why. Stop playtest now." No furnace screenshot was taken, so
    the slot hypothesis is unconfirmed; repro from the archived world.

## Session end (00:47, dev asked to stop)

Client then server stopped by pid; the world saved on shutdown (`run/world/level.dat` 00:47:52).
Archive: `~/questown-worlds/r17-dev-playtest-2026-10-25.tgz` (run/world, run/logs, the
/tmp/dev-server-27 and dev-client-27/28 logs, the 2026-10-25 screenshots, the 00:09 crash report).

Fixed during the session (committed, in 1.20.6-neoforge-wip; never seen running, because the
client/server were not restarted): #5/#6 item-jobs empty screen, #7 quests panel footer, #8 groggy
message, #12 completed-only view + toggle, #13 campfire stays lit, #14 Produces filter, #15 picker
tint, #16 crafting menu height.
Open: #1 recipe hiding, #2 soup pot weight key, #3/#11 BOP tab button + stays open, #4 leave-town
poof, #9 hunter wool, #10 "too late in the day" status, #17 BOP-deposit re-entry loop, #18 which
message should be two lines, #19 cook cannot insert mutton (top priority), #20 remove villager
Quests tab.

## Resolutions (agent, dev at dinner, 2026-10-25)

- #19 cook **fixed**: the kitchen's furnace was built into the room's wall (x -147 of walls
  x -149..-147). Room matching counts wall blocks (kitchen quest completed), the job scans read
  the interior only, so the cook found no work spot (`spotsByState: {}`) and looped on max ticks.
  The job-block scans now include walls (`JobBlockScan.CELLS`), and a wall block is used from an
  interior cell. Verified on the saved r17 world: fuel went in, then (with 4 mutton seeded into the
  storeroom) the mutton went in and came out cooked. The coal-in-top-slot guess was wrong (the
  furnace was empty in the save). Side notes from the repro: villager 3 (8212b2ea) also became a
  cook on load (a pending BOP job change).
- #17 BOP deposit loop **fixed**: after the deposit, the board's cascade had no exit for a done
  deposit; the gatherer (not due for downtime, no work to roll) "stayed" 501 times. New branch
  `bop-deposited` -> downtime (`BoardIdleExits`). To confirm live in the agent playtest.
- #2 soup pot weight key **fixed** (namespaced; saved configs keep the old key).
- #3 / #11 BOP tab **fixed**: Take button bottom-aligned, hidden at 0, and the server re-opens the tab after a take. To check in the agent playtest (gui-lint).
- #20 villager Quests tab **removed** (and the inventory tab's dead "Quests" button, whose handler was empty).
- #4 leave-town poof **added** (12 POOF particles on leaving and on returning).
