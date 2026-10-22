---
title: 'Agent playtest 2026-10-21: findings from a fresh-world session'
status: needs-triage
---

# Agent playtest 2026-10-21 (worktree fresh world, creative, flag at -1023 65 768)

## Odd
**[FIXED 2026-10-21]** 1. Quests screen, day 1: the "Town Gate" onboarding quest card carries an "x" whose tooltip is
   "Remove request" (borrowed `job_board.remove_work` key). Shown for any batched quest
   (`QuestsScreen` ~L222: `getBatchUUID() != null`). Campfire has none. A player can delete
   the onboarding gate quest; the label says "request" for something the player never requested.

## Fine (checked)
- Spawn in a snowy cliff bowl; plains 1086 blocks away. Vanilla worldgen, not ours.
- A worldgen `questown:empty_town` flag near the plains (designed, with autotests).
- Starting timber: partial 8-log line on uneven ground (placement needs air over solid).
- Starter map granted on flag placement. QuestsScreen gui-lint OK.
- Campfire/Town Gate tooltips read clearly.

## Log-only nits
- "stopped ticking because: Closest player is 55796 blocks away - Distance Limit: 10000":
  both numbers are squared distances (236 blocks real, 100 limit).

## Odd (continued)
2. An open QuestsScreen does not refresh when a quest completes (Campfire stayed an open card
   after "Quest completed: Campfire"; correct after reopening). Same stale-screen family as the
   flag villagers progress bar. Also the economy tab: after clicking "Free food" (claimed, chat
   confirmed) the open screen kept offering "Click to claim 20 free carrots" until reopened.
**[FIXED 2026-10-22]** 3. Placing a welcome mat with no flag link fails silently: server ERROR "Welcome mat is not
   associated to an existing flag", no chat/sound for the player. Survival mats only come from
   flag crafting, so it bites with a mat whose flag was destroyed/moved.
**[FIXED 2026-10-21]** 4. Town Crafting: the mineshaft recipe's output tooltip shows the raw key
   `[block.questown.mineshaft]` (no `block.questown.mineshaft` in en_us.json; only the room name
   and the recipe description exist).
5. (minor) "Town Gate created at location [-1022, 762]" but the mat is at x=-1023.
**[FIXED 2026-10-21]** 6. The completed Town Gate card still offers "Remove request".

## Leads to test
- Town UUID is `UUID.randomUUID()` per block-entity load and not saved: after my client
  restart (chunk unload/reload) the flag re-registered as e617fe0a (was 7cac9031). Comment says
  per-villager/knowledge data is keyed by it. Re-test once villagers exist: rejoin, check
  villagers/knowledge/quests survive.

## Dev-tool notes
- QtDiagClient chat hook parses every chat line as a command ("unknown command: Created").
**[FIXED 2026-10-22]** 7. Quest icon tooltips for tag ingredients are inconsistent: the storeroom chest shows the raw
   tag id `#c:chests`; the bedroom light shows a proper "Light Source".
8. Villagers tab row label is the lowercase job root "gatherer" (no villager name), and the
   gatherer status tooltip hard-codes "Gatherer has left town" while the hunter's uses "%s".
9. (dev tool) `_qtdev inspect villagers <flag>` says "the villagers tab is not shown for flag"
   while the client shows and opens that tab.
10. (minor) "Villager job: Hunter" quest card has no icon (every other card has one).
11. (log) "Fullness changed from 4990 to 5000" logged ~every second at INFO.
12. (log) "Basics batch posted ... on welcome-mat completion" fires on Room completion.

## Checked and fine (cont.)
- Block-room location messages report x+1 consistently (gate -1023 -> -1022, board -1026 ->
  -1025); the door room reports the exact door. (Folded into #5.)
- Campfire goes out after sleeping: deliberate (`CampfireSleepHandler.extinguishCampfire`).
- Gatherer day 1: "They need Food to work" until bread is stocked; then cycles ~110 s per trip
  and deposits loot (sapling, seeds, mushroom). Away = hidden and frozen in place, so a static
  position and a stale `[WHY] GOING_TO_JOB` sample are expected, not a stall.
- Town UUID churns on reload (3 UUIDs so far), but quests/rooms survived two reloads.
- Villager first job-change log line names a UUID (bd2d41a2) that is not the entity (3d515261);
  later lines consistent. Unexplained, no visible effect.
13. Villager job-change tab says "Insert a Block of Progress to change to a different, random
    profession", but the tutorial BOP is blessed and deterministically gave Hunter ("Blessed BOP
    dictates job root change to: hunter"). The copy is wrong for a blessed BOP.
14. (judge) Hunter ate bread (`dining_no_table`, 19:32:13) and then, with 12 bread still in the
    chest, switched to `dining_raw_food` and took wheat seeds (19:32:45). CONTEXT.md: raw food is
    the fallback "when no cooked food is available". Seeds are in `villager_raw_food`, so eating
    them is designed; the second meal and the choice of raw over bread are what look off.
**[FIXED 2026-10-22]** 15. (dev tool) `flagtab bop` from the client makes the server throw
    `IllegalArgumentException: Unexpected menu type: "bop"` (the tab is `blocks_of_progress`);
    PORTTEST.md still lists `bop`. A bad tab name from a client should be rejected, not thrown.

## Watch next session
- Hunter with a stone sword + bread stocked had not hunted 3.5 min later: downtime ->
  seeking_work (8 samples of `EXTRACTING_PRODUCT` while seeking work) -> dining -> raw food ->
  seeking_work. Needs a longer watch before calling it.
- UUID churn: re-test after villagers exist across a reload (villager link, knowledge).

## Session 2 (resumed 19:41)
16. **[FIXED 2026-10-21, town/entity/COMPLEX.md]** Raw-food meals LOWER fullness. `SimpleVillagerHandle.fillHunger` sets
    `newHunger = percent * baseFullness`, so `hunger_fill_half` sets fullness to 2500 whatever it
    was; `SpecialRules.HUNGER_FILL_HALF`'s comment says "filled up by 50% from its current value".
    Live: 4370 -> 2500 three times overnight (19:32:51, 19:35:36, 19:38:22).
17. Every meal is two meals: bread via `dining_no_table` (-> 5000), then ~30 s later
    `dining_raw_food` takes a raw item (seeds, brown mushroom, red mushroom) while bread is
    still stocked. `DinerNoTableWork`'s ExpirationRules fall back to `DinerRawFoodWork` on both
    the no-supplies and max-ticks paths, so a diner that has already eaten times out into raw
    food instead of returning to work. With #16 the second meal also halves fullness, so the
    villager is hungry again ~2 min later: 7 food items in 6 min overnight (bread 13 -> 9).
- Corrected: "hunter never hunts" was night. It became a hunter at 19:29 with ~90 s of
  daylight left (trip = 100 s); nightfall 19:31:34; time 23733 at 19:41. Re-check at dawn.
**[FIXED 2026-10-21]** 18. Chat shows a raw key for every job-change quest: "Quest completed: room.hunter_qt_sword", "Quest completed: room.cook_qt_extract".
19. **[FIXED 2026-10-22, dev: player's own request only; village/day1 first-work-request-completed-by-the-player]** "Quest completed: Post your first work request on the job board" fired when I used a BOP
    to change jobs; no work request was posted ("First-work-request quest completed on first
    posted request" in the log).
    (judge, 2026-10-22) Cause: the BOP job change runs `postStarterRequests` (the board's
    auto-request, default ON), and the board listener completes the quest on any add. That
    matches the decided design ("completes on any posted request",
    `.scratch/day1-playtest-2026-10-11/job-board-organic-introduction.md`), so I left it alone.
    If only the player's own request should count, call `markFirstWorkRequestCompleteIfActive`
    from the player paths (`AddWorkFromUIMessage` CONFIRMED, `_qtdev board_request`, the growth
    executor) instead of from the listener in `TownFlagBlockEntity`.
**[WONTFIX, dev 2026-10-22: by design; RoomRecipes deb6333 reverted in 511c76e]** 20. **Beds are double-counted in room recipes.** A room with ONE bed + torch was announced as a
    "Bedroom" (recipe: light + 2x `#minecraft:beds`); a room with TWO beds became a "Big
    Bedroom" (light + 4x beds) and completed the "Bedroom" quest. RoomRecipes matches each bed
    block (head and foot); Questown's `TownVillagerBedsHandle` counts heads only when assigning
    sleep. So bed quests and bed counts disagree by 2x. Screens: diags/bed1.png, bed2.png.
    (dev) Not a bug. A bed is two blocks, so the recipes list two bed blocks per bed: Bedroom =
    1 bed, Big Bedroom = 2, inn = 6. One bed + torch IS a Bedroom. Pinned by
    flag/bed_is_two_blocks.
21. (dev tool) My new `click` threw an NPE when a click closed its screen; fixed by keeping the
    screen reference (uncommitted at time of writing).
- Refinement to #17: at dawn the diner went straight back to `hunter/sword` after one bread;
  the bread-then-raw double meal happened only at night, when there was no work to return to.
- Hunter works by day: took the sword at 19:42:26, back at 19:44:29 (+200 XP), deposited 1 beef.
22. (judge) Villager arrivals are silent: villager 1 (day 1) and villager 2 (19:53:44) arrived
    with no chat line; the only cue is the villagers tab's "...and one more in the morning".
**[FIXED 2026-10-22]** 23. Level-up chat names the villager by truncated UUID: the player sees "3d51...810 reached
    level 3" (20:20), not "Villager 1". Same family as
    `.scratch/playtest-2026-10-15/change-job-broadcast-uuid-instead-of-name.md`.
24. (minor) Using the wand on the campfire after sleeping relights it silently (PASS, no message);
    a second use sleeps. Fine once you know; nothing tells the player the first use relit it.
**[FIXED 2026-10-22]** 25. Progress bar stuck at 100 reproduced (see the progress-bar ticket's "Reproduced live").
    Cause: the villagers page's status smoothing kept an unbounded history (`StatusSmoother`).
- Resolved lead: the stray "Job changed ... for <random uuid> [from gatherer:gather]" line is a
  freshly constructed entity logging under its temporary UUID before its saved UUID loads
  (seen on every reload: 85346cfa, cbf45aca, 425e3e7b). Log noise only.
- UUID-churn lead: after a client restart (chunk reload, new town UUID) the hunter re-registered,
  kept its job and kept working. No breakage seen.
26. Economy tab: the right-hand number is how many times an item was *needed* ("Food was needed 1
    times in the past 1 days"), but beside "Swords 4" it reads as a stock count; the chest held
    7 bread + 5 raw meat while it said "Food 1". The tooltip doesn't handle singulars ("1 times",
    "1 days"), and its wrap width depends on the cursor (a lone "1" on its own line at x~300).
27. (judge) "Free food" (red, 20 carrots) is offered while 7 bread are in storage; the one need
    was the overnight raw-food meal (#17). If #17 is fixed this may go away on its own.
28. Quest card title "Small Storeroom -> Storeroom" runs under the card's x button (overlap);
    gui-lint reported QuestsScreen OK.
**[WONTFIX with #20: by design]** 29. Consequence of #20: building the cook batch's rooms auto-completed its "Big Bedroom" upgrade
    the moment the batch posted (my 2-bed room already matched the 4-bed recipe).
    (dev) The Big Bedroom is two beds, so a 2-bed room correctly matches it.
30. (judge) Cook-batch "Food (0/8)" counts food *produced* (extracted), not stocked; with 27 food
    in storage a player may read 0/8 as broken. Tooltip only says "Click to see jobs that make this".
31. Known (`.scratch/playtest-2026-10-14/cook-status-blocker-surfacing-10-14.md`, ready-for-human)
    and still live on the first cook: kitchen + raw mutton, no fuel in town -> cook put mutton in
    the furnace, then flipped extract <-> fuel every 5 s; status tooltip "Cook has nowhere to work.
    Or they might be looking for tools." No coal hint.
32. (to check) Villager 2 was changed gatherer -> cook while away on a gather trip; does that trip's
    loot get lost?
33. (judge) A lit campfire in front of the storeroom door blocked every villager's path (vanilla
    pathing avoids lit campfires). The hunter showed an accurate "They can't find a way there"
    bubble but stalled in DROPPING_LOOT 2+ min; moving the fire fixed it in 1 s. The bubble
    doesn't say what is in the way. Relevant because using the wand on the campfire to sleep
    relights it (#24), so a campfire that was harmless when unlit becomes a wall.
34. (log) `[LOOTTRACE] extract 'X' vs LOOT quest ... -> false (extracted N/8)` prints
    `getExtracted() + count` even when the item doesn't match (`TownQuests.processLootQuests`),
    so a non-matching extraction reads as progress ("Raw Chicken -> false (5/8)" while the real
    count stayed 4/8). DEV-DIAG line; fix or drop with the rest of that diag.
35. (judge) Only `cook/mutton` is unlocked on the first cook; raw beef x2, porkchop x2 and chicken x1
    piled up in storage uncooked while the batch asks for 8 food. Progression-gated by design;
    nothing tells the player why the cook ignores the beef.
**[FIXED 2026-10-21]** 36. Escalates #4 (also chat: "Created block.questown.mineshaft"): the miner batch's room quest card is titled with the raw key
    `block.questown.mineshaft` (QuestsScreen, day 6). Every player reaches this card.
- Villager 3 arrived at dawn after the cook batch's food 8/8 (20:29:54); miner batch posted.
37. The mineshaft quest ("Place a mineshaft block in a room and register the door") completed the
    instant the block was placed, with no room around it yet (block-as-room). Registering the door
    afterwards produced a plain "Room". The quest text asks for more than the game checks.
38. ~~Villager deposits never stack~~: **by design** (dev, 2026-10-21; CONTEXT.md "Unstacked
    deposit"). Ticket closed wontfix. The miner problem is #40 (no signal), not the stacking.
- Miner at 40 TPS: trips at 20:32:43, 20:33:38, 20:34:32 (~55 s = ~2100 ticks), on spec.

## Session 2, day 7: storage full, live (40 TPS)
39. Storage-full stop works for a villager *holding* output: hunter stopped with the new no-space
    bubble and hint "Every chest is full: they have nowhere to put what they made." (diags/ns2.png).
    (judge) The bubble art sits on a grey framed square; the bread and sword bubbles are bare items on white.
40. **[FIXED 2026-10-21, see jobs/COMPLEX.md]** **Full storage is invisible on villagers not holding output.** Miner: its finished coal waited
    at the mineshaft (spot state 3) but it never collected it; it wandered as GOING_TO_JOB for 2+ min
    with status "Miner is going to work." Cook: "Cook has nowhere to work. Or they might be
    looking for tools." Neither shows the storage bubble. `countStorageBlockedVillagers` counts
    NO_SPACE only, so they don't count toward the flag bubble either. This matches the dev's
    "slow miner" more closely than the NO_SPACE fixture did.
41. Cook's bubble (raw mutton) and its villagers-tab status ("nowhere to work") disagree; the
    bubble is the accurate one (no raw mutton; only cook/mutton is unlocked, #35).
42. Could not see the flag's storage-full / incomplete-quests bubble: the cook loiters about a
    block from the flag, and bubble focus (one bubble, nearest the crosshair, cone 0.9) kept
    picking the cook's bubble from every angle tried. When the town is stuck, the stuck villagers
    gather at the flag and hide its bubble. Server-side state is pinned by `cook/fuel
    [storage_full]`; the render was not confirmed live.
