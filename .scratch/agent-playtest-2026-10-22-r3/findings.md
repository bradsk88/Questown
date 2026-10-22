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
