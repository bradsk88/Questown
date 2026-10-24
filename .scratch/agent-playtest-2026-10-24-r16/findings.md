---
title: 'Agent playtest 2026-10-24 r16: continuous fresh-world run'
status: needs-triage
---

# Agent playtest 2026-10-24 r16 (continuous)

Fresh random world (blank `level-seed`), built at HEAD 5e3850be, tick rate 40, creative, normal
difficulty. Spawn (3.5, 67, -2.5): rolling meadow with a hill to the east. Rules: the server is
never stopped or rebuilt mid-run, no console seeding into containers; findings logged, not fixed
live. Open check carried from `hatch`: does the coal bubble stay up while the cook is in diner
statuses (r10 #8 hides the coal line in the tooltip then)?

Town layout: flag (0, 67, 0), campfire (-3, 67, 3), welcome mat (3, 66, 0), room 1 door
(7, 66, -8) (interior x 6..9, z -11..-9, floor y65, 3 high), storeroom chest (9, 66, -11), job
board (-2, 67, -3).

## Interventions
- Day 10: mineshaft crafted from stone stairs at the flag and placed at (-6, 68, 2); 4 wooden
  pickaxes put into empty storeroom slots 20-23 by console (the player's delivery); Villager 3
  (8d37b55b) made a Miner through the BOP path.
- Day 13, 01:42: 8 coal put into empty slot 25 of the second chest by console (the player's
  delivery); "Coal x8" completed at once.
- Day 13, ~01:46: a third bed set at (13, 66, -10)/(13, 66, -9) in the Big Bedroom by console (the
  player's response to "Didn't sleep"; tests #11).
- Day 15: a BOP spent on Villager 4 through its change-job tab (random roll -> baker); room 6
  (door (42, 66, -8)) built by console fill with a bread oven placed by `clickheld` at
  (44, 66, -11) -> "Bread Maker's Shop" completed.
- Days 11-13 ran unslept (natural mornings 01:30:36, 01:40:36) while the session was paused.
- Day 5-6: Dev teleported beside the flag (2.5, 66, 2.5) at 00:33:49 (dirt click) and stayed
  there until 00:47:30, then moved away (spreadplayers -12 14) and back (00:51+) as an A/B
  for #10. Day 4 and day 5 nights were not slept (natural mornings at 00:34:14 and 00:44:14).
- Day 4, 00:35: claimed the one-time free food (20 carrots) from the Economics tab's food card,
  as a player would (after a dirt right-click on the flag, per CONTEXT.md, did nothing; see #8).
- Room 1 built by console `fill 5 65 -12 10 69 -8 cobblestone hollow` + `setblock` oak door, plus
  a grass/air patch in front of the door (a creative player's building).
- Creative items put in hand by console `item replace`: flag base, campfire, oak pressure plate,
  stick, oak sign, chest (the player's own materials; crafted Questown items came from the flag's
  crafting tab).
- Bedroom (door (14, 66, -8), bed head (16, 66, -11), lantern (13, 66, -11)) and room 3 (door
  (21, 66, -8), empty) built the same way (console fill + setblock).
- 4 stone swords put into the empty storeroom slots 0-3 by console (the player's delivery, as in
  r13).
- Day 2, 00:12: 8 coal put into empty storeroom slot 10 by console (the player's delivery); a
  second storeroom chest placed by `clickheld` at (6, 66, -11); a second bed set at (15, 66,
  -11)/(15, 66, -10) by console.
- Day 3, 00:20: room 4 (door (28, 66, -8), interior x 27..30, z -11..-9) built by console fill,
  hanging lantern (30, 68, -10), 4 Dinner Plates (`questown:plate_block`, creative) placed by
  `clickheld`, first at z -11, then moved to z -10.

## Fine (checked)
- Day 0 onboarding, all in ~4 real minutes: flag -> Campfire (at flag Y, completed on placement)
  + Town Gate (mat crafted from the plate row, the only enabled row while holding a plate) ->
  Room (wand on door) -> Job Board + Small Storeroom (both completed on placement) -> "You will be
  rewarded in the morning"; first-work-request quest deferred ("no villager yet").
- Night 0 slept through with the wand on the campfire. Day 1 dawn: Villager 1 (a5326a63) arrived,
  3 tutorial BOPs, first-work-request batch posted. Stone Sword x4, Bedroom, Villager job: Hunter
  (villager card -> initial tab "Insert a Block of Progress to make this villager a Hunter
  (starting now)" -> BOP -> Unlock), Raw Food (hunter's first trip: mutton + leather), the
  tutorial second Room ("one new room before the cook batch", by design) and Post a work request
  all completed on day 1.
- The job board already listed an auto-posted Raw Mutton request while "Post a work request" stayed
  open: by design (`TownQuests.java:216`, auto-posted starter requests don't count; the board's
  auto-post tooltip explains it).
- No fresh-town hunger deadlock: Villager 1 hit -20% fullness two real minutes after arriving
  (known hunger rate, r10 #9 / r13 #5), took the hunter job, went `starving-diner-stage` with no
  food in town, then `work-roll` back to `hunter:sword` and hunted.
- Day 2: Small Kitchen (furnace in room 3) completed on placement; Villager 2 (7fe2ffee) made a
  Cook the player's way (BOP tab -> villager card -> "Insert a Block of Progress to make this
  villager a Cook (starting now)" -> Unlock): `cook:extract -> seeking_work -> cook:mutton`.
- **`hatch` open check answered: a coal-less cook keeps its coal need through diner statuses on
  the bubble and on inspect; only the flag-tab tooltip swaps it out, by design.** A cook made
  through the BOP path, no coal in town, frozen (`tick freeze`) in `cook:dining_no_table`:
  `_qtdev inspect villagers` -> `need=UNMET_ITEM`, "needs item: Coal (#minecraft:coals)" (twice,
  00:07 and 00:10); facing it, the focus hint line read "They need Coal to work." (aim1.png,
  00:10). That hint fires only for the bubble holder (`NeedBubbleFocus.java:165-166` @
  5e3850be, `if (townie != null && !isShowingBubble(townie)) return;`), so the coal bubble was
  held during the diner status. The icon itself was not visible: the diner stood in the
  storeroom doorway and the bubble falls inside the wall above the door. In downtime, in the
  open, the coal-icon bubble was plainly visible (bub3.png). The flag villagers-tab tooltip said
  "Cook is fetching food. / They will eat it soon." with no coal line: that is r10 #8's
  documented `StatusNeedLine.keepsOwnLines` rule (`gui/COMPLEX.md`, dev 2026-10-23).
- Day 2 upgrades: second chest -> "Small Storeroom -> Storeroom", second bed -> "Bedroom -> Big
  Bedroom", both completed on placement. The cook was `sleepless` at dawn 3 ("bedless last
  night"): correct, the nightfall capture (00:09:56, "1 of 2 townies have no bed") came before
  I added the second bed (00:12:28), the documented `BedlessLedger` v1 trade-off.
- Day 3: with coal in the storeroom the cook's need cleared. It ran `cook:fuel` twice (TOOK
  coal x2), `cook:extract` gave Cooked Mutton (`[LOOTTRACE] extract 'Cooked Mutton' ... -> true
  (extracted 1/8)`, card "Food (1/8)"), from raw mutton it took on day 2 (`cook:mutton` TOOK
  00:06:55). It then ate that cooked mutton itself (-2449 -> 5000) and now needs Raw Mutton
  ("Cook is resting. They need Raw Mutton to work." in the tooltip). Villager 3 is the cook
  batch's reward, so no arrival until Food reaches 8/8 (CONTEXT.md "Onboarding arrival").
- Food (8/8), the cook batch's last quest, completed on day 9 (01:07:29) after eight Cooked
  Muttons (the card read 1/8 on day 3, 2/8 day 4, 5/8 day 7, 6/8 day 8); quests tab: "You will
  be rewarded in the morning". Slow mostly because the cook eats its own output (#7) and spends
  long stretches starving (cook -11039 before a meal on day 8). The cook was still level 1
  (24/1000 xp) on day 9; the hunter was level 6.
- Day 10 miner beat (CONTEXT "Onboarding arrival" order): Wooden Pickaxe x4 and Mineshaft
  completed on delivery/placement, "Insert a Block of Progress to make this villager a Miner
  (starting now)" -> `miner:coal`. "Coal x8" is an ITEM quest (`TownQuests.java:889`
  `MCQuest.item(..., COAL, 8)`), which `QuestBatches.processItemQuests` satisfies from the stacks
  in town storage at that moment. The miner can't fill it on its own: 7 trips in 3 days, 2 coal,
  and the cook took each within ~20 s (`cook:fuel` TOOK coal 01:22:34, 01:33:06), so storage held
  0 and the card stayed 0/8 until the player delivered (like the swords and pickaxes). (judge:
  the card doesn't say the player is the expected source.)
- White swirls hanging near the doorway in the frozen shots are vanilla `POOF` particles (leaver
  warp effects), held by the tick freeze. Not a finding.

## Odd
1. (judge) **A starving hunter brings food home and goes straight back out without eating.**
   Villager 1 at -104% deposited raw mutton at 23:58:26 and stayed in `hunter:sword` for a second
   full trip (GOING_TO_JOB 23:58:30, back 23:59:20); only at that trip's end (#3's max-ticks
   fallback -> board -> `ready-for-downtime`) did it go `starving-diner-stage` and eat the mutton
   (-5920 -> 2500, 23:59:31). About 60 s (a tenth of a
   day at tick rate 40) starving next to a chest of food. The board only re-decides between trips;
   whether a starving leaver should break off after a deposit is a design call.
2. (bug, low, GUI) **The job board's "Request an item" picker shows the same items on every page.**
   With 4 requestable items it says "Page 1 / 2", and page 2 repeats page 1 (Raw Food, Wheat
   Seeds, Leather, Raw Mutton, checked by hovering both pages). `AddWorkScreen.java:120-121` @
   5e3850be compute `startIndex`/`endIndex` for the page and never use them;
   `:127` passes the whole `work` list to `renderRequestItemIcons`. The page count comes from
   `MAX_CARDS_PER_PAGE` (`:45`, sized for 42-px cards: (166 - 10) / 43 = 3) while the items are
   drawn as an 8-wide icon grid, so any list of more than 3 items gets duplicate pages.
3. (bug, low) **A leaver's trip that ends on its max-ticks tick has its product deferred to its
   next shift.** debug.log 23:59:20.602: "Reached max ticks for hunter:sword. Fallback
   hunter:sword is the same job; asking the board", the same millisecond as `[WHY] ...
   WAITING_FOR_TIMED_STATE -> EXTRACTING_PRODUCT`. The board picked `ready-for-downtime`, so the
   trip got no `[LOOTTRACE] extract` and no `[Experience]`. The product was not lost: on day 2,
   0.7 s after the hunter took `sword` again (00:03:37.926, still GOING_TO_JOB) it extracted
   Leather with +200 XP (00:03:38.600). So the trip's loot sat in limbo for the night (4+ real
   minutes). In a starving town that could be the food. Path: `JobLogic.java` @ 5e3850be, the
   max-ticks branch's guard is `workSpot == null && !status.isWaitingForTimers() &&
   ticksSinceStart > expiration.maxTicks()`. It protects a leaver while its timer runs, but on
   the tick the timer completes the status is EXTRACTING_PRODUCT, so the guard opens before the
   extraction runs. Since 064a1745 (r12 #8) the same-job fallback asks the board rather than
   re-creating the job, so a downtime switch can land on that tick.
4. (judge, low) **A diner's tooltip promises food the town doesn't have.** The cook's flag-tab
   tooltip in `dining_no_table` (COLLECTING_SUPPLIES) read "Cook is fetching food. / They will
   eat it soon." (`en_us.json` `tooltips.villagers.job.diner.status_{1,2}.COLLECTING_SUPPLIES`)
   while the town held no food at all (storeroom: leather and stone swords; the only mutton was
   already eaten). The cook gave up 5 s later (`dining_raw_food -> downtime`).
5. (judge/bug, low; CORRECTED same session) **A dining room works, but the game announces it as an
   Empty Room and says nothing when it becomes one.** The hunter's flag-tab tooltip asked for one
   ("Hunter is looking for food. / They also need a place to sit down and eat.") and no quest
   offers it. Room 4 (4 Dinner Plates = `data/questown/recipes/dining_room.json`) was wanded
   with the plates already in: the only broadcast was `recipe_created [room.empty_room, [28, -8]]`.
   Re-placing a plate and moving all four from z -11 to z -10 produced no `room_changed`, whereas
   the furnace, second chest and second bed each produced one, and so did a single crafting table
   in room 5 on day 14 (`room_changed [empty_room -> crafting_room]`). **But the town did match it:** at
   dawn 4 `TownRoomsHandle.resetDiningRooms` (iterates rooms whose match `anyMatch(dining_room)`)
   logged "Resetting plate claim and state at" all four plates (00:24:14), and from 00:24:45 both
   townies went `seeking_work -> dining` (the table diner, chosen only when `hasDiningRoom`).
   The first draft of this item (commit 140c55a0) said the recipe never matched; that came from
   reading the announcement as the match. So: the player built the right thing and got told
   "Empty Room". Whether the match existed at wand time (plates at z -11) is unknown, since there
   was no diner window before dawn 4.
6. (bug, medium) **A slept night's morning stamps day-clock ticks into game-clock state.** The
   sleep path passes the new *daytime*: `NeoTownWakeEvents.java:19`
   `TownFlags.onSleepFinished(event.getNewTime())` -> `runMorning(..., newTime)` ->
   `morningTick(newTime)` -> `SimpleVillagerHandle.handleMorning(tick)`. The natural (unslept)
   morning passes game time (`AbstractTownFlagTicker.java:196` `onMorningDetected(getTick(data))`;
   `TownFlagTicker` `getTick` -> `Util.getTick` = `level.getGameTime()`, `Util.java:41`). The
   state it writes is read on the game clock: moods tick with game time
   (`TownFlagTicker.java:64` -> `TownVillagerHandle.java:541` `moods.tick(tick)`, expiry
   `TownVillagerMoods.java:83` keeps an effect while `untilTick >= currentTick`), and the downtime
   gate gets game time (`TownFlagBlockEntity.java:1198`, in `changeJobForVisitorFromBoard`: `long tick = Util.getTick(getServerLevel());`).
   A sleep skip advances daytime but not game time, so every slept night widens the gap (here
   daytime 96000+ vs game time 78378 on day 4).
   - **Seen live: SLEEPLESS outlasts its day.** Dawn 3 (a slept morning, daytime 72000) logged
     "Villager 7fe2...f64 was bedless last night; SLEEPLESS applied until tick 95000"
     (`SimpleVillagerHandle.java:793` `until = tick + configs.sleeplessEffectDuration`, 23000).
     23000 ticks is ~9.6 real minutes at 40 tps, but the cook still had `[questown:sleepless]`
     12 minutes later (00:25:49, day 4) because game time was only 78378. **It lapsed exactly at
     game time 95000:** `villager_stats` showed `[questown:sleepless]` at game time 95014 and
     `effects []` (mood 60% -> 100%) at 95775 (~00:33). Applied at game time ~50460 (78378 at
     00:26:10 minus 698 s x 40 t/s), it lasted ~44500 game ticks, nearly double the intended 23000,
     and the overshoot grows with every slept night.
   - **BELIEVED, not shown: the `ready-for-downtime` branch is muted after a slept dawn.**
     `handleMorning` also calls `registerMostRecentDowntime(uuid, tick)` with the daytime value, and
     `DowntimeState.isReadyForRest(now)` (`DowntimeState.java:58-62`) needs `lastWork > lastRest`
     and `now - lastRest >= n` on game-time `now`. An inflated `lastRest` keeps both false until a
     game-time rest overwrites it or game time catches up. The board traces fit (`ready-for-downtime`
     fired on day 1 and once at 00:2x, never on days 2-3), but hunger confounds them: both townies
     were starving most of the time. Settle with a unit test of `handleMorning` at a daytime ahead
     of game time, or by logging `lastRest`/`now` in the cascade.
7. (judge, balance) **The town's only food producer starves while the cook eats its catch.**
   By day 4 the hunter had eaten 3 times in 4 days (last 00:16:17, day 3) and sat at -277%
   (`villager_stats` 00:29); the cook, in town since day 2, ate 6 times, including both Cooked
   Muttons it made for the Food quest (00:15:33 and 00:29:07, each right after its own
   `cook:extract`), so Food stood at 2/8 after two days of cooking. The hunter's loop each time it
   got hungry: `dining -> dining_no_table -> dining_raw_food -> sword` with no meal (no food in
   the chest at that moment), then its trip's raw food lands in the chest and the cook, already
   dining, takes it (porkchop/chicken/mutton, `Fullness changed ... for 7fe2ffee` 00:26:29,
   00:27:48). Hunger has no visible consequence (damage 0%, mood 100%), so the cost is only the
   Food quest and villager 3's arrival. Related: r10 #9 / r13 #5 (hunger rate), #1 above.
8. (doc, stale glossary) **CONTEXT.md's "Free-food bonus" still says right-click the flag with
   dirt.** `CONTEXT.md` (both this worktree and the dev's main checkout, line 279): "the player
   triggers by right-clicking the **town flag** base with a block of dirt". Since 6865917a
   (2026-10-12, "Free food: claim it on the economics tab instead of right-clicking the flag with
   dirt") the claim is the Economics tab's food card (`TownEconomicsScreen.mouseClicked` ->
   `ClaimFreeFoodMessage`; tooltip "Click to claim 20 free carrots for the town's storeroom.").
   Live: a dirt right-click on the flag just opened the flag menu; the food-card click logged
   "Bonus food claimed: 20 carrots deposited into a registered container" (00:35:01). Not edited
   here because the dev's main checkout has uncommitted CONTEXT.md edits.
9. (bug, medium) **A level-up BOP deposit times out on a 200-tick limit, loops, and strands a
   duplicate blessed BOP in the storeroom.** `BOPDepositorWork.java:108` passes
   `Config.MAX_TICKS_WITHOUT_DINING_TABLE` as the job's `maxTicks` (`ExpirationRules`'s 4th
   parameter), 200 in this config (`run/config/questown-server.toml:143`
   `MaxTicksWithoutDiningTableV2 = 200`), with fallback `seeking_work`. The job first drops loot
   into a storeroom chest (BOP included: "is putting questown:block_of_progress in BlockPos{x=6,
   y=66, z=-11}"), takes the BOP back, then walks to the flag. In this town (storeroom ~13 blocks
   from the flag) 200 ticks wasn't enough from 00:38:36: "Reached max ticks for
   hunter:bop_depositing. Falling back to hunter:seeking_work" every 5 s, and the board's
   `bop-deposit` branch re-sent it (7 cycles until nightfall). At nightfall the hunter dropped the
   BOP into the chest again (00:39:08) and never took it back; at dawn the board's
   `if (!carrying)` hand-out (`TownFlagBlockEntity.java:1237`) gave it a fresh BOP (00:44:14),
   which was deposited (00:44:20, `messages.bop.earned`, tab "Earned by: Hunter x3"). Net: 3
   credits for 3 level-ups (correct), plus BOP #3, blessed `{jobId:"sword",root:"hunter"}`,
   still sitting in the storeroom chest (6, 66, -11) as a free extra for any player who opens it.
   Earlier deposits (00:06, 00:18) finished within 3-5 s, so the limit only bites when the walk
   is long or something delays the trip. The same 200 is the no-table diner's no-supplies limit
   (#10).
10. (bug, cause unknown) **Diners took food and didn't eat it for ~10 minutes, then recovered.**
   Every diner `TOOK` followed (or not) by a `Fullness changed` within 20 s (script over
   latest.log + the rotated log): through 00:35:08, 9 of 11 takes became meals 3-5 s later. From
   00:36:58 to 00:45:22: 10 takes, 1 meal (hunter 00:44:27), both villagers, carrots and
   porkchops, in `dining_no_table` and `dining_raw_food`. From 00:46:06 on, all 6 takes became
   meals (one later cook attempt, 00:56:04, took nothing). Failure shape
   (hunter, 00:36:56-00:37:10, debug.log): TOOK carrot, GOING_TO_JOB, `state:0`, then "hunter gave
   up waiting for ingredients after 201 ticks (job: dining_no_table)". The cook once held a carrot
   in `dining_no_table` for the full 2000 ticks (00:37:22-00:38:12, `MaxTicksWithoutFood`)
   without eating. `JobLogic` counts `noSuppliesTicks` while
   `status.isWorkingOnProduction() && workSpot == null`, so a diner at `state:0` with no claimed
   eating spot runs out its 200 ticks; why the spot was missing in that window is not known.
   **Rejected: the player standing beside the flag.** I stood at (2.5, 66, 2.5) from 00:33:49 to
   00:47:30 (the window), but standing there again on day 7 (00:52-00:58) 3 of 4 meals
   succeeded. Next: log the diner's `workSpot` / claim state at `state:0`, or check plate claims
   (`TownRoomsHandle.resetDiningRooms` clears plate claims only at dawn, and the table diner
   `dining` hit max ticks just before each failure).
11. (judge, design gap) **The onboarding beds leave one townie permanently bedless.** CONTEXT.md
   "Onboarding arrival" (`CONTEXT.md:404`): each arrival's batch carries its bed (hunter-gatherer
   Bedroom = 1 bed for villager 2; the cook batch's Bedroom -> Big Bedroom = 2 beds, the second for
   villager 3), and villager 1 is the gate exception, arriving "before the town has rooms". So
   three townies get two beds. Live: every night since villager 3 arrived logged "Nightfall: 1 of 3
   townies have no bed for the night" (01:25:23, 01:35:23) and dawn put SLEEPLESS on Villager 3
   each time (01:20:36, 01:30:36, 01:40:36); its mood was 45% on day 13. No quest asks for a third
   bed. Surfaces on day 13: the flag villagers-tab tooltip said "Miner is underground. They are
   searching for resources." (nothing about sleep); the villager's stats tab said "Didn't sleep,
   works slower, Ate…" (`menu.villager_stats.effect.sleepless` = "Didn't sleep, works slower",
   no mention of a bed). A player learns the townie didn't sleep, not why.
   A third bed fixed that night ("Nightfall: 0 of 3", 01:45:23). Then the miner batch's reward,
   Villager 4 (day 14), came with no bed either: "Nightfall: 1 of 4 townies have no bed"
   (01:51:03). So it isn't only the gate exception: only the two bedroom batches bring beds, and
   every other arrival (gate, miner, presumably later top-need batches) adds a bedless townie.
12. (trivia) The raw-food mood effect id is misspelled: `MoodEffects.java:18`
   `ATE_RAW_FOOD = Questown.ResourceLocation("are_raw_food")`, shown in `_qtdev villager_stats` as
   `questown:are_raw_food`. Players see the right text (lang key
   `menu.villager_stats.effect.are_raw_food` = "Ate raw food"); renaming needs a save migration.
13. (bug, low, GUI) **The villager stats tab truncates the effects line with no way to read the
   rest.** Miner, day 13: "Didn't sleep, works slower, Ate…" (sleepless + ate-raw-food joined on
   one line, cut with an ellipsis); hovering the line shows no tooltip (st2.png).
14. (judge) **A top-need room gets built and then nothing staffs it.** Day 14: "Top-need batch:
   chose need #questown:pickaxes (x12) -> producer crafter (unlocked [cook, gatherer, hunter,
   miner])" posted a room-only "Crafting Room" quest ("...Build the crafter room and register its
   door so one has a place to work"). Built and completed on day 14; the batch reward is
   `MCDelayedReward(defaultQuestCompletionRewards)` (`TownFlagTutorialAdapter.makeReward`,
   `PHASE_NEW_JOB_ROOM`), so dawn 15 brought no crafter, only the next top-need batch (baker,
   "Bread Maker's Shop"; crafter is now excluded as built). Room-only is the dev's ruling
   (`TownQuests.java` comment, 2026-10-22: the old "Unlock job" quest's BOP spend "rolls a random
   root, so it rarely delivered the producer"). But that leaves the same gamble as the only path:
   a townie's change-job tab offers just "Insert a Block of Progress to change to a different,
   random profession". The pickaxe need stays unmet while the crafting room sits empty.

## Skill/tool traps (for the retro)
- The BOP tab's "Take Block of Progress" button moves down once an "Earned by: ..." line shows:
  (302, 376), not the skill's (302, 354). A click at 354 silently took nothing (day 10).
- The mineshaft needs **stone** stairs; cobblestone stairs leave the row greyed.
- `run/logs/latest.log` rotates at midnight (to `run/logs/<date>-1.log.gz`); a grep over
  latest.log alone silently loses the earlier lines.
