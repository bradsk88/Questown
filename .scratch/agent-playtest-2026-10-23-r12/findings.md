---
title: 'Agent playtest 2026-10-23 r12: one continuous run, no restarts'
status: needs-triage
---

# Agent playtest 2026-10-23 r12 (continuous)

Dev ask: a fresh, continuous playthrough with no restarts. Rules for this run: the server is
never stopped or rebuilt mid-run (bugs are logged, not fixed live); no console item seeding into
containers; creative inventory used only the way a creative player would (taking blocks/items into
hand). Built at HEAD 93afa779. Tick rate 40. Random seed; spawn in forest at (-33, 76, 5); moved
to the lakeside strip at (-32, 74, 194). Flag at -32 74 194.

## Fine (checked)
- Flag: starter map, quests Campfire + Town Gate. Mat from oak pressure plate (only its Craft
  enabled) -> Town Gate, "New visitors will arrive in the morning", Room quest. 5x5 plank shell +
  crafted wand on the door -> Room complete, Job Board + Small Storeroom posted.
- Camp toast appeared after dark (dayTime 13042), not at evening (r10 #1 fix holds).
- Storeroom (chest in room), Job Board (crafted from a sign, placed 3 W of flag): both complete.
  Slept via wand on campfire; day 2 a gatherer arrived.
- Day 2: quests Stone Sword x4, Bedroom, Villager job: Hunter, Raw Food, Post a work request.
  Took a BOP from the flag; the in-world villager card now shows a BOP tab (r10 #5 trial) and the
  change-job tab had an attention dot; BOP in, Unlock -> Hunter. Delivered 4 stone swords to the
  storeroom chest (player delivery for the sword quest). The hunter took a sword and Raw Food
  closed on its first `hunter:sword` extraction (18:20:51), not on a meal (r10 #7 fix holds).
- Bedroom: bed alone gave "Room created"; adding a lantern -> "Room became Bedroom". The quest
  card's icons (light + bed) do say so.
- Work request: board showed the hunter's auto "Raw Mutton"; "+" -> Raw Food ("Any kind will
  do") -> confirm "6 known jobs" -> Yes -> quest complete.

- Day 3: quests Small Kitchen, Storeroom upgrade, Big Bedroom, Villager job: Cook, Food (0/8).
  Furnace in room 3 -> Small Kitchen. Second BOP -> villager 2 to Cook; the board got
  `#questown:villager_food` (r10 #11 fix live). Second chest -> Storeroom; second bed -> Big
  Bedroom. Player delivered 16 coal (design: coal is the player's job until a miner); the cook
  went fuel -> mutton -> extract within ~40 s, Food 2/8, then ate one cooked mutton.
- Day 4-8: Food crawled from 2/8 to 8/8 (closed day 8, 18:58) on cooked mutton only; the cook
  knows only mutton (r10 #14, parked). Day 9: villager 3 arrived; quests Coal x8 (closed at once
  from the delivered coal), Wooden Pickaxe x4 (player delivery), Mineshaft (crafted from stone
  stairs; completed on placement near the flag), Villager job: Miner (BOP -> villager 3; board
  got `coal`). The flag had 2 BOPs on day 9 though only 1 of the starting 3 was left and the
  hunter never deposited: a quest reward, probably Food's ("rewarded in the morning").
- Day 10: villager 4 (gatherer) arrived; quest Crafting Room (crafting table in a new 3-high
  room) -> done, "New visitors will arrive in the morning". The miner woke `sleepless` (4
  townies, 2 beds; no message told the player), so I built a second Big Bedroom (2 beds +
  lantern, 3-high).
- Day 11: the miner deposited its BOP; quest Bread Maker's Shop (bread oven in a new 3-high
  room east of the kitchen) -> done.

## Odd
1. (observe) "Seeded starting timber ... (1 placements)" here vs 6 in r10's plains world. Check
   whether the starting-timber seeding depends on nearby trees and whether 1 is enough.
2. (bug, P1 onboarding on uneven ground) The Campfire quest never completed for a lit campfire
   placed 4 blocks from the flag on a gentle slope: flag at y=74, campfire at y=75 (one block
   uphill). `TownCycle.findCampfire` scans only the flag's own Y (`pos.offset(x, 0, z)` within
   `CAMPFIRE_SEARCH_RADIUS`), so any campfire one block higher or lower is invisible. No message
   tells the player why. Confirmed: a second campfire level with the flag (y=74, 2 blocks away)
   completed the quest within a second. Not fixed live (no-restart run).
3. (observe, r10 #9) Day 2 afternoon: the hungry hunter cycles dining_no_table <-> downtime with
   no cooked food in town (no cook yet). Watching whether it reaches raw food when starving.
   Day 3 morning: it went dining_no_table -> dining_raw_food (the starving chain works).
4. (judge, consequence of r10 #8) Day 3: the new cook has no coal (only the pickaxe gatherer and
   miner produce it; `docs/design-principles.md`: coal is the player's job until the miner
   exists). The cook is hungry and stays in diner statuses, so its status reads only "Cook is
   looking for food. / They also need a place to sit down and eat." (#8 now keeps a diner's own
   lines), while the real blocker for *all* food is coal. The coal need is visible only on the
   Economics tab (Coal 2). Before #8 the tooltip would have said "They need Coal to work." A
   hungry cook in a coal-less town is exactly when the coal line mattered.
5. (observe) Economics tab day 3: "Swords 6" while 4 stone swords sit in the storeroom chest and
   the hunter holds one. What does the number count? Check `ItemEconomicsData.timesNeeded`.
6. **[CONFIRMED]** (bug, P0 for this run) Day 4: both villagers stood motionless on top of the Big Bedroom's beds
   all day (positions identical to the 1e-15 over minutes: V1 [-21.5, 76.5625, 205.5] on the red
   bed head, V2 [-22.52, 76.5625, 205.48] on the blue bed head), awake (no `SleepingX`; their
   bed-sleep listener fired at the 18:29:14 morning skip). Each cycle: seeking_work -> "Reached
   max ticks ... Falling back to downtime" -> seeking_work. The hunter's last real work was day 2
   18:20:53, before its first night in a bed; the cook worked all of day 3 and stalled after its
   first bed night. Interior is 3x3: 2 beds side by side (x -22/-23, z 204-205), lantern at
   (-24, 206), door at (-25, 205), door cell (-24, 205) free. Player test at dusk: broke the blue
   bed -> V2 dropped to the floor (y 76.0) but didn't walk in 15 s (dusk, confounded).
   A/B, day 5 morning: V2 (no bed, slept on the floor) walked to the storeroom, ate, and went to
   the flag within 40 s; V1 stayed pinned on the red bed at the identical position. Breaking the
   red bed freed V1 at once (at the flag 10 s later). So a townie that sleeps in a bed is pinned
   on top of it after waking, until the bed is removed; the town stalls (no hunts after day 2).
   Cause (confirmed by the roof raise; dev's call "raise the roof"): the room is 2 blocks tall inside (floor
   y75, roof y78); a townie standing on a bed top (y 76.5625) is ~1.95 tall, so it reaches
   ~78.5 and is wedged under the ceiling. r9's cook stood on its furnace for 13 min in the same
   2-high rooms. Dev ruling 2026-10-23: raise the roof for this run, fix after the continuous run.
   Done at 18:40 (roof moved to y79, beds restored, Big Bedroom re-recognized). Next morning
   (day 6, 18:42:58) both slept in the beds and walked off them: the headroom explanation holds
   (the hunter went straight back to hunter:sword). To settle after
   the run: does a townie wake on top of the bed (vanilla stand-up position not found?) and is
   a 2-high room meant to be valid for beds?
   Side note: the sleep debug line prints the bed as `TownPosition{x=-22, z=76, scanLevel=205}`
   (y and z in the wrong fields); check whether a TownPosition is built with y/z swapped there.
7. (bug?, P1, hypothesis refuted) Day 6: starving townies never finish a raw-food meal. Both at fullness -67% ->
   -209% within ~4 minutes. 18:43-18:47 the cook entered dining_raw_food repeatedly, TOOK a raw
   chicken 7 times (`[PROBE:supplies] job=cook:dining_raw_food TOOK chicken`, totalTakes 33) and
   logged "gave up waiting for ingredients after 201 ticks (job: dining_raw_food)" 8 times; the
   board then rolled it from dining_raw_food straight into work (`-> cook:fuel via work-roll`).
   No `[Experience] +0 from *:dining_raw_food` (a finished meal) exists in the whole run; the
   hunter has no finished meal at all. Storage held 1 chicken + 1 porkchop.
   Hypothesis (BELIEVED, not read in code): `DinerRawFoodWork` holds a raw item as the state-0
   *tool* (for the in-hand render) and then needs a separate raw item as the state-1
   *ingredient*, so a meal needs two raw foods, and with one in storage it never completes. r10
   had 6+ beef, which would explain why raw meals worked there. Settle: read the
   tool-vs-ingredient supply path for DinerRawFoodWork, or watch a meal with 2 raw items stocked.
   Refuted after the run: new scenario `eating/eat_raw_food_single` (one beef in town) passes;
   the gatherer took the beef and finished the meal (fullness -489 -> 2500). Likely downstream of
   #9 (the cook was rarely flagged starving), not proven; re-check in the next live run.
8. (bug, P1 progression) The hunter levelled to 2 at 18:43 ("Villager 1 reached level 2") and has
   sat at `xp 0/1200` since, though every hunt logs `[Experience] +200 from hunter:sword`
   (18:51, 18:52, 18:53, ...): the BOP latch (`hasBlockOfProgress`) makes addExperience
   early-return until the BOP is deposited, and `bop_depositing` has never run this game. The
   deposit is handed out only by the board cascade's `bop-deposit` branch
   (`TownFlagBlockEntity` ~L1223, ahead of downtime). The hunter's last `[board]` trace is
   18:37:05 (`dining_raw_food -> sword via work-roll`); since then it chains
   `hunter:sword -> hunter:sword` without the cascade ever running, so the deposit (and the
   starving/downtime branches) never apply to it. The flag gets no BOP and the hunter's XP is
   frozen. CONTEXT.md "Block of Progress" says the latch stalls a townie only "for the brief
   level-up -> deposit window"; here it's 15+ real minutes and counting.
   Contrast: the miner (villager 3) reached level 2 at 19:09:57, went through the board, ran
   `miner:bop_depositing`, and "A Block of Progress was deposited" at 19:15:29 (~5.5 min). The
   hunter, chaining sword -> sword outside the board, is still latched.
9. **[ROOT CAUSE FIXED 2026-10-23, after the run]** (bug, P1) Starving flag misses (day 9):
   a. The hunter's fullness steps by 10 from an odd offset (-11099 -> -11109); the hungry
      listener broadcasts only when `newVal <= 0 && abs(newVal) % 10 == 0`
      (`SimpleVillagerHandle.tickHunger`), so a townie whose fullness ends in a non-zero digit
      after a meal (`setFullness` = percent * base) is never re-flagged starving. The hunter has
      had no broadcast since 18:23. This also explains why its board branches never apply (#8).
   b. The cook (fullness -7650/-7660, broadcast every second since 19:06:2x) still churns
      `dining_no_table -> downtime via diner-ate` every ~5 s (26 times 19:04-19:07), and
      `diner-ate` requires `!isStarving`. Only `setFullness(percent > 0)` writes false. Not
      settled in-run: whether the board's `isStarving(ownerVUID)` reads a different key than
      the listener's `delegator.getUUID(e)`. A starving cook never reaches dining_raw_food.
   Day 11 scale: in 3 minutes the cook and the new gatherer each made ~34 dining_no_table <->
   downtime round trips; all five townies sit at -150% to -220% fullness; the cook hardly cooks.
   Hunger is the dominant drag on this run.
   Root cause (both shapes): the hungry listener in `SimpleVillagerHandle.initialize` read the
   method's `fullness` *parameter* (the load-time snapshot, empty in a fresh town), not the
   live field, so it never flagged anyone; `starving` came only from dawn's
   `makeAllTotallyHungry` and a meal cleared it until the next dawn. (a)'s `% 10` theory was
   wrong: dawn sets fullness to 1 *and* starving true, which is where the odd values come from.
   Fix: read `this.fullness`. `SimpleVillagerHandleTest` red -> green; eating 7/7 (incl.
   dine_at_time and eat_direct, both listed as deterministic reds).
10. **[FIXED 2026-10-23, after the run]** (bug, P0) Day 12, 19:22:08: placing the Kitchen-Diner furniture (a food display + empty plate
    blocks, via setblock as a builder would) disconnected the player: "Connection Lost ...
    EncoderException: Failed to encode packet 'clientbound/minecraft:custom_payload'". Server
    cause: `Failed encoding custom payload questown:network_message: ... Empty ItemStack not
    allowed` at `ItemStack$2.encode` <- `SyncBlockItemMessage.encode(SyncBlockItemMessage.java:22)`:
    the block-item sync writes an empty stack through the non-optional ItemStack stream codec
    (1.20.6 has `ItemStack.OPTIONAL_STREAM_CODEC` for possibly-empty stacks). Same tick:
    "[questown] no item-handler capability for FoodDisplayEntity". Server kept running. The room
    became "Cafe" (furnace + food display) at 19:22:07, just before the kick.
    Rejoin is a kick loop: a fresh client joined at 19:24:08.108 and was dropped at .646 with
    the same encoder error (the empty-stack sync is re-sent on chunk load). A player cannot get
    back in to break the block. Recovery from the server console needed `forceload add` first:
    with no player online the town's chunks unload and `setblock` answers "That position is not
    loaded". After removing the food display + 2 plates, the client rejoined (19:29:16) and stayed.
    The Kitchen-Diner quest (furnace + food display + 4 plates) can't be completed in this build:
    any empty plate/display kicks the player. Server never restarted.
    Fix: `SyncBlockItemMessage` encodes/decodes with `ItemStack.OPTIONAL_STREAM_CODEC`;
    `SyncBlockItemMessageTest` red ("Empty ItemStack not allowed") -> green. Verified live on the
    day-12 world (19:38): food display + 4 plates placed, no disconnect, "Kitchen Diner" quest
    completed.

## Run end

Stopped on day 12 (dev, 2026-10-23: "Stop; fix the P0s"). The server ran continuously from
18:11 to 19:36 (one server, never restarted); the client was relaunched three times after the #10
kick. World: `build/agent-playtest/r12-world-day12.tgz`; full server log:
`build/agent-playtest/r12-server-full.log`.
