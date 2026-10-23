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
