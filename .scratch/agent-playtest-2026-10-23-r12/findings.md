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
