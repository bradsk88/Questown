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
