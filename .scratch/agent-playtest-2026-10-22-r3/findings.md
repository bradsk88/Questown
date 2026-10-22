---
title: 'Agent playtest 2026-10-22 round 3: findings from a fresh-world session'
status: needs-triage
---

# Agent playtest 2026-10-22 r3 (worktree fresh world, savanna, flag at 200 126 200)

Handoff `blaze` (supersedes `hymn2`). Watch list: miner beat hand-off (675646d9), leavers at
dusk (4ca11bbe), no-table diner give-up.

## Fine (checked)
- Player-placed flag: `_qtdev help` says the helper chicken only runs for an `empty_town` flag;
  a player flag starts on the quest path (Campfire, Town Gate). Stick on the flag opens quests.
- Watch item 4 (4ca11bbe) held live: at nightfall the hunter's max-ticks fallback logged
  "Fallback hunter:sword won't fit in the day; asking the board" (debug.log 05:13:22) and it
  deposited its BOP and went to bed instead of leaving town.
- Hunter reuses its stone sword (takes it from the chest each trip, puts it back).
- Flag crafting: each Craft closes the screen ("Created Town Wand"); one craft per open.

## Odd
1. (still present, = 10-21 #5) "Town Gate created at location [201, 192]"; the mat is at x=200.
   Same +1 on x for every single-block special room: job board at 200,203 -> "[201, 203]",
   mineshaft at 214,205 -> "[215, 205]". Real rooms are exact ("[205, 196]" = the door).
2. (still present, = 10-21 #2) Economics tab: after "Free food" is claimed (chat: "The town's
   storeroom received 20 free carrots.") the open screen still shows "Free food ->".
3. **[FIXED 2026-10-22, verified live]** Job board "Auto-post new requests" toggle: tooltip
   says "Auto-post is ON" but the box shows no tick. `WorkScreen` filled the tick with
   `0xFFFFFF` (ARGB alpha 0, draws nothing); now `0xFFFFFFFF`. Since 9d0acd65.
4. (judge) The same toggle row sits on the panel's bottom border, half outside it: dark-grey
   label on the world background, barely legible. `tgY` adds `backgroundHeight - 34` to a `y`
   that was already moved down 30 px for the title, so it lands at panel top + 162 of 166. A
   full page (3 cards x 43 px from top + 30) leaves no free band inside the panel, so the fix is
   a layout call: taller panel (166 -> ~182) or 2 cards per page.
   Recommendation: taller panel; paging stays as it is.
5. Morning double meal, both in one `dining_no_table` session (relates to 10-21 #17, but a
   different path: no `dining_raw_food` fallback, and in the morning). 05:08:22 carrot ->
   "Fullness -269 -> 5000"; the job did not end, it went DROPPING_LOOT (the villager still held
   a raw mutton from its gatherer days), then at 05:08:26 TOOK a second carrot and ate it
   ("4750 -> 5000") before switching to `hunter/sword`. Guess (not traced): dropping the held
   loot restarts the meal cycle at state 0. Costs one food per meal when the diner carries loot.
- Day 4 morning: one meal (no loot in hand), consistent with #5's guess.
6. (still present, = 10-21 #28, new symptom) "Small Storeroom -> Storeroom" quest title: the x
   button is gone now, and the last "m" runs over the card's right border. Title is one
   unwrapped `ctx.text` (`QuestsScreen` ~L230). Wrap vs ellipsis vs shorter copy is a copy call.
   Lead for #5 (BELIEVED, not traced): `JobLogic` changes job only on
   `!hasAnyItems && computeState.get().isDroppingLoot()` (~L121). With loot in hand, the drop
   happens in DROPPING_LOOT, and on the next tick the status is already recomputed to
   COLLECTING_SUPPLIES (TOOK carrot 50 ms after the DROPPING_LOOT dump), so the exit check never
   sees "dropping + empty" and the meal cycle restarts. Settle with a held `eating/eat_direct`
   variant whose diner starts with a raw item in hand.
7. **[FIXED 2026-10-22, unit-tested]** Phase 2.75 top-need batch: "chose need #minecraft:coals
   -> producer baker (unlocked [cook, gatherer, hunter, miner])" -> "unlock baker + room
   breadmaker". The baker does not make coal; `baker/stock_coal` declared
   `initial_request: minecraft:coal` (and `stock_wheat` wheat), and `initial_request` is the
   declared product for the producer pick and board-request satisfaction. f2f049cc set them
   against its plan's "initial_request minecraft:bread"; now bread. Red/green:
   `BakerStockJobLoaderTest.bakerStockJobs_doNotDeclareTheirInputsAsProducts`. Full :test 1415/0.
   (judge) `baker/bread` still declares wheat (its own input), as the plan specifies, so a
   wheat shortage would still pick the baker over a farmer if the farmer were locked.
8. **[FIXED 2026-10-22, unit-tested; live check pending]** Earned BOPs vanished. The hunter
   reached levels 2-4 and deposited three blessed BOPs (05:13, 05:23, 05:32); I took three
   tutorial BOPs; the flag should hold 4. Its save held 1 plain BOP and the BOP tab said 2.
   `TownFlagBOPItemHandler.insertItem` shrank the carried 1-count stack before copying it, so
   `copy()` returned `ItemStack.EMPTY` and the flag stored EMPTY: counted by `getBopCount`,
   skipped by `serializeBOP`, never collectable. Then `Compat.insertInNextOpenSlot` aimed every
   later blessed deposit at that EMPTY entry ("first empty slot"), `isItemValid` refused it
   (slot < count), and the helper returned true anyway: the BOP and the latch were gone. The
   782a0ede guard (2026-10-09) blocked empty inputs, not this order. Also: simulate=true
   consumed the caller's stack. Fix: snapshot before shrink, simulate returns a copy.
   `TownFlagBOPDepositTest` (red 2/3 -> green 2/2). Old worlds heal on reload (empties are not
   saved). Not changed: `insertInNextOpenSlot` ignores `insertItem`'s refusal.
   Possibly related, not checked: `.scratch/day1-playtest-2026-10-11/ghost-empty-item-entities.md`.
9. With both storeroom chests full (54 unstacked slots on day 7), the hunter's `bop_depositing`
   reported NO_SPACE ("town has storage space=false") and flipped bop_depositing <->
   seeking_work about 10 times a second for ~20 s (05:29:0x-05:29:2x), then gave up until
   05:31:58. A BOP goes to the flag, not a chest, so the storage gate looks wrong for this job.
   Not traced further.
