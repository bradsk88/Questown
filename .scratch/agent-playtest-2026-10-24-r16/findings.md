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
   the furnace, second chest and second bed each produced one. **But the town did match it:** at
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
     12 minutes later (00:25:49, day 4) because game time was only 78378. It lapses only when game
     time reaches 95000.
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
