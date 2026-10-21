---
title: 'Agent playtest 2026-10-21: findings from a fresh-world session'
status: needs-triage
---

# Agent playtest 2026-10-21 (worktree fresh world, creative, flag at -1023 65 768)

## Odd
1. Quests screen, day 1: the "Town Gate" onboarding quest card carries an "x" whose tooltip is
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
   flag villagers progress bar.
3. Placing a welcome mat with no flag link fails silently: server ERROR "Welcome mat is not
   associated to an existing flag", no chat/sound for the player. Survival mats only come from
   flag crafting, so it bites with a mat whose flag was destroyed/moved.
4. Town Crafting: the mineshaft recipe's output tooltip shows the raw key
   `[block.questown.mineshaft]` (no `block.questown.mineshaft` in en_us.json; only the room name
   and the recipe description exist).
5. (minor) "Town Gate created at location [-1022, 762]" but the mat is at x=-1023.
6. The completed Town Gate card still offers "Remove request".

## Leads to test
- Town UUID is `UUID.randomUUID()` per block-entity load and not saved: after my client
  restart (chunk unload/reload) the flag re-registered as e617fe0a (was 7cac9031). Comment says
  per-villager/knowledge data is keyed by it. Re-test once villagers exist: rejoin, check
  villagers/knowledge/quests survive.

## Dev-tool notes
- QtDiagClient chat hook parses every chat line as a command ("unknown command: Created").
7. Quest icon tooltips for tag ingredients are inconsistent: the storeroom chest shows the raw
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
15. (dev tool) `flagtab bop` from the client makes the server throw
    `IllegalArgumentException: Unexpected menu type: "bop"` (the tab is `blocks_of_progress`);
    PORTTEST.md still lists `bop`. A bad tab name from a client should be rejected, not thrown.

## Watch next session
- Hunter with a stone sword + bread stocked had not hunted 3.5 min later: downtime ->
  seeking_work (8 samples of `EXTRACTING_PRODUCT` while seeking work) -> dining -> raw food ->
  seeking_work. Needs a longer watch before calling it.
- UUID churn: re-test after villagers exist across a reload (villager link, knowledge).
