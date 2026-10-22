---
title: 'Agent playtest 2026-10-22 r9: fresh world (old-growth spruce taiga), flag at -36 64 -20'
status: needs-triage
---

# Agent playtest 2026-10-22 r9

Fresh random-seed world, creative, tick rate 40. Built at HEAD 90cbcea3 (after the shutdown
heartbeat fix 49dc0a9a and the vanilla checkbox d559ded6).

## Fine (checked)
- Flag placement: starter map granted, starting timber seeded (8 placements), quests Campfire +
  Town Gate. Gate tooltip reads well ("Craft a welcome mat and place it where you'd like people to
  enter/exit your village. Click to open crafting").
- Campfire placed -> "Quest completed: Campfire" at once.
- Flag crafting: Craft buttons greyed until the input is held; a spruce pressure plate makes the
  mat ("Created Welcome Mat"); the mat item carries the parent flag pos.
- Mat placed -> "Quest completed: Town Gate", then "New visitors will arrive in the morning",
  then the gate-created line and the empty-room quest, each in its own broadcast slot.

## Odd
1. **[FIXED 3c1b3d1a]** (UI) "Begin moving this town" on the flag crafting tab packed the town on
   one click, 8 px under the Craft column, with no in-game cancel (cancel/wake are commands only,
   queued for the dev in `.scratch/feedback-audit/README.md` #2). Now a first click arms it
   ("Click again to pack the town"). Live: one click armed, no shutdown; the second started it;
   the job board survived the ritual (49dc0a9a) and `/qt flag ... shutdown cancel` restored it.
2. (judge, creative only) The change-job panel says "Insert a Block of Progress to make this
   villager a Hunter (starting tomorrow morning)", but in creative the spend is instant
   (`SimpleVillagerHandle.scheduleJobRootChange` "NOW (creative mode)"): chat said "Job changed to
   Hunter" at once, at night. Survival gets the morning change the copy promises. Also, the line
   still says "Insert a Block of Progress" with the BOP already in the slot. Recommendation: say
   "starting now" when the player is in creative; after insertion, "Click Unlock to make this
   villager a Hunter".
3. (judge) The "Villager job: Hunter" quest card has a title and an empty body (no icon), unlike
   its neighbours (sword icons, bedroom blocks).

## Fine (continued)
- Quests after the storeroom: Stone Sword x4 (0/4), Bedroom, Villager job: Hunter (page 1/2).
- 3 BOPs from day-1 quest rewards; the flag BOP tab shows them; Take moves one to the hotbar.
- Change-job panel names the promised job (bf56fa28) live: "...make this villager a Hunter".
- Board empty state reads well; the vanilla checkbox shows its highlight + tooltip on hover.
4. (judge, principle 3) Day 2, the new hunter with no sword in town: its status tooltip says
   "Hunter is looking for supplies. They will search in room chests." and the job cycles
   seeking_work <-> downtime. Nothing on the villager names the sword. The economics tab does
   ("Swords 1"), and the quests ask for "Stone Sword x4". Recommendation: when the missing supply
   is a held tool, the status says "needs a Stone Sword (or better) to work".
   Sword placed in the storeroom chest -> the hunter ate, switched to hunter:sword and TOOK it.
5. (agent error, not a game finding) `item replace block ... container.0` overwrote the 20 free
   carrots the economics claim had just delivered; restored them into slot 1 by hand.

## Fine (continued)
- Board auto-posted the hunter's first request ("Raw Mutton") under the vanilla checkbox.
- Economics: Food 3 with "Free food ->", Swords 1; the claim delivered 20 carrots to the storeroom
  with chat "The town's storeroom received 20 free carrots."
- Campfire sleep night 2 (from beside the fire) skipped to morning.
6. (agent error + a code-comment inaccuracy) The `_qtdev build_room` shell is 4x4 including its
   walls, so the interior is 2x2 (z -11..-12 for a door at -36 64 -10; the shape dump shows it).
   My bed head at z=-13 replaced a back-wall block: "Bedroom destroyed" 0.6 s after "Room became
   Bedroom". Rebuilt the wall, bed inside -> "Bedroom created". The quest stayed complete (latched
   on the first detection). `ChickenRoomShellPlan.ROOM_SIZE`'s javadoc says "A 4x4 gives a 3x3
   interior (9 cells)"; the live shape is 2x2 (4 cells).

## Fine (continued)
- Day 2: hunter hunted, extracted mutton -> "Quest completed: Raw Food" (LOOTTRACE extracted 1/1),
  deposited mutton into the storeroom and put its sword back.
- Stone Sword x4 counts swords in the storeroom (1/4 with the hunter's sword in the chest); 3 more
  -> "Quest completed: Stone Sword x4" -> "New visitors will arrive in the morning".
7. (judge) Day 3 shows two identical "Room" cards (the tutorial empty-rooms batch, "two new rooms
   before the cook batch" by design). Two same-named cards read like a duplicate; "Room (1 of 2)"
   or one "Rooms x2" card would say it's intended.
8. (judge, minor copy) The request confirm for a category says "There are 10 known jobs which
   produce this item." for "Raw Food" (a category, not an item): "which produce it".
9. (known, queued Q1) The new cook flips cook:fuel <-> cook:extract every few seconds with nothing
   in the furnace (the coin-flip thrash).

## Fine (continued)
- Villager 2 arrived the morning after the hunter-gatherer batch completed.
- Two empty rooms -> two "Quest completed: Room"; the cook batch posts Small Kitchen, Small
  Storeroom -> Storeroom, Bedroom -> Big Bedroom, Villager job: Cook, Food (0/8).
- Board: the add-work screen leads with a framed "Raw Food / Any kind will do" category slot;
  posting it -> "Quest completed: Post your first work request on the job board" (player path).
- Clicking a villager card's name opens its menus on the dotted change-job tab, naming the
  promised job (Cook); BOP -> "Job changed to Cook for Villager 2" + quest completed.
- Second chest -> "Small Storeroom became Storeroom"; furnace -> "Room became Small Kitchen";
  second bed + torch -> "Bedroom became Big Bedroom". Night status: "Cook is resting until morning."
