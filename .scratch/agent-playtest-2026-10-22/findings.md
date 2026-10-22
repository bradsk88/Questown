---
title: 'Agent playtest 2026-10-22: findings from a fresh-world session'
status: needs-triage
---

# Agent playtest 2026-10-22 (worktree fresh world, birch forest, flag at 15 71 161)

Played autonomously while the dev was away. Fixes respect `docs/design-principles.md`; taste
calls are marked (judge) for the dev.

## Found and fixed
1. **[FIXED 2026-10-22, 21830c45]** Duplication: in survival, placing a welcome mat (or job
   board, or hospital bed) that has no flag link left the stack in hand and also dropped a copy.
   `flag/unlinked_placement_no_dupe` pins it.
2. **[FIXED 2026-10-22, pending commit]** The crafting tab's icon tooltips read "[Oak Pressure
   Plate]" (chat-form name, with brackets). Now the plain name.
3. **[WONTFIX, dev 2026-10-22; f6097a58 change reverted]** The Bedroom quest card showed one
   bed. That is correct: a Bedroom is one bed. The recipe lists two `#minecraft:beds` because a
   bed is two blocks, and the card hides the second on purpose. This was reported because of the
   bed-counts-once change (RoomRecipes deb6333), which was itself a misreading and is reverted.
4. **[FIXED 2026-10-22, pending commit]** The empty job board read "Page 1 / 0" (all five
   paged screens). Now "Page 1 / 1".
5. **[FIXED 2026-10-22, pending commit]** The request confirm screen read "Requesting
   minecraft:wheat_seeds" (raw id, ran past the panel edge). Now "Requesting Wheat Seeds".
6. **[FIXED 2026-10-22, pending commit]** The villagers page card read "gatherer" (bare root id,
   no lang key). Now "Hunter"/"Gatherer". Also wrong in the 1.19.2 golden.
7. **[FIXED 2026-10-22, pending commit]** A second wand use on the campfire while already asleep
   stranded the first temp bed (a red bed stayed next to the campfire), left the fire lit, and
   lost the `first_campfire_sleep` advancement, so "Your camp is ready" returned every evening.
   Reached by the agent's two `clickheld` calls; a player can reach it with a double-click.

8. **[FIXED 2026-10-22]** Economics card tooltip read "Food was needed / 2 / times in the past /
   1 days": two hand-broken lang keys, each re-wrapped by the dynamic wrap (the count alone on a
   line), and "1 days" since `PERIOD_DAYS` is 1. Now one sentence ("Raw Mutton was needed 6 times
   in the past day"), wrapped as a paragraph on both economics screens. Verified live, gui-lint OK.
   Left: "needed 1 times" when the count is 1 (judge: copy).

9. **[FIXED 2026-10-22]** After a server restart, villager nameplates read "menu.villager.name"
   (seen through the bedroom wall at night). `register` re-runs on load and saved a
   server-resolved string, which was the raw key at that point; a runtime spawn got "Villager 3".
   The custom name is now translatable; broken names healed on the next load. Verified live
   ("Villager 2" over the townie by the campfire).

## Noticed, not changed
- **Ticketed (bug, judge the fix):** `self-satisfied-batch-villager-cascade.md`. One morning, 8
  villagers arrived in 3 s: each new visitor's batch was already satisfied, backfill closed it at
  once, and its reward spawned the next visitor. 3 -> 11 villagers. World saved.
- (to check) After that morning, "Fullness changed from 4990 to 5000" logged 4x every 10 ticks for
  70 s (03:12:48-03:13:57, 500 lines), with no meals in between. Source not traced.
- **Ticketed (judge):** `sleeping-leaver-shows-left-town.md`. A gatherer whose trip spanned
  nightfall slept in bed all night while its card said "Gatherer has left town to collect items";
  the trip resumed at sunrise.
- (judge) Crafting tab: the "N in your inventory" count is taken when the screen opens; with
  stone stairs given while it was open, Craft did nothing ("0 in your inventory") until reopened.
- (judge) Quests tab grows with completed (green) cards: 4 open quests sat on "Page 1 / 7", the
  other 6 pages all completed ones. The separate completed view only applies when all are done.
- (judge) The need bubble ("!") draws over the end of the nameplate ("Villag[!]2").
- (to check) A night-time wand use on the lit campfire from 13 blocks away did nothing and logged
  nothing (02:52:42, `[click held] ... -> PASS`); the same use by day reached `beginCampfireSleep`
  and failed TOO_FAR_AWAY (temp bed cleaned up). The first one probably hit a silent action-bar
  return (`no_safe_position`); not captured.
- **Ticketed (judge):** `restart-evening-false-sleepless.md`. A restart during the evening logged
  "Nightfall: 1 of 1 townies have no bed" for a townie with a bedroom, and SLEEPLESS was applied
  next morning. The first-tick capture runs before beds are re-assigned.
- **Ticketed:** `known-gather-results-ignore-loot-prefix.md`. Requesting wheat seeds says "20
  known jobs" produce them; 5 do. Every biome-loot job (hunter, miner, tool gatherers) claims
  the seed floor and every other prefix's finds. Not fixed: the same function gates leaver loot.
- (judge) The Block of Progress item is a small 8-px cube at GUI scale 0.5, so in the hotbar it
  reads as a tiny sliver next to full-size items.
- (judge) "Job changed to Hunter for villager Villager 1": the copy doubles "villager" when the
  townie has the fallback name.
- Villagers page trip bar advances live during the hunter's trip (62% then 85% over 8 s), and
  the card shows the carried sword (the 2026-10-21 StatusSmoother fix holds).
- The hunter re-takes a stone sword from the storeroom each trip and returns it with the loot
  (3 in the chest + 1 carried); `[PROBE:supplies] totalTakes` counts takes, not swords.
- The bedroom card's bed icons rotate colours (cyan, red); fine.
- `TownQuests` logs "Basics batch posted ... on welcome-mat completion", but the batch now
  posts when the empty-room quest completes. Log wording only.

- `[LOOTTRACE] extract 'Raw Porkchop' vs LOOT quest questown:villager_food -> false (extracted
  7/8)` (TownQuests.processLootQuests, DEV-DIAG): the count prints `extracted + count` even on a
  non-match, so it reads as a credit. The quest stayed at 6/8. Log only.

- Known (`.scratch/cook-scoring-tie/always-consider-noop-tie-mechanism.md`): after the raw mutton
  ran out, the cook flipped `fuel`<->`extract` every 2.6 s for 30 s (12 changes, no work), then
  went to downtime. Same coin flip as the 10-14 report.
- (judge) Food quest done in the daytime: the quests tab says "You will be rewarded in the
  morning" while chat says "New visitors will arrive after all players sleep". Same event, two
  framings.

- (judge) When every quest was done, the ticker's "No quests found" batch for the new miner
  (03:07:42, `TownFlagTicker.addQuests`) re-asked Mineshaft, Miner job, Wooden Pickaxe x4, Stone
  Sword x4 and Coal x8. All but coal were already satisfied, so backfill closed them in the same
  second, and chat repeated "Quest completed: Mineshaft" (first at 03:02:52) and "Villager job:
  Miner" (twice in 15 s). Backfill announcing is documented (`town/entity/COMPLEX.md` "Quest
  backfill"); the duplicate asks are the question. It repeated: three such batches in 3 minutes
  (03:07:42, 03:09:50, 03:10:45), each re-closing Mineshaft + Miner job + pickaxes at once; chat
  showed "Quest completed: Mineshaft" 4 times and "Villager job: Miner" 4 times. It stopped once
  everything was done ("You will be rewarded in the morning").
- (judge) A completed Coal x8 card (green, ticked) reads "(7/8)": the count is the live storeroom
  tally, and the cook burned one coal after the quest closed.

## Fine (checked)
- Storeroom full (one chest, unstacked deposits): the miner's card reads "Miner needs an empty
  chest in a room to deposit their items" with a crossed-chest icon. Clear and correct.
- 11 villagers: 3 "Can't keep up" warnings (60-66 ticks behind, 03:14-03:15) at 40 t/s.
- Mineshaft: crafted from stone stairs on the flag's crafting tab, placed in the empty room
  (quest done, "recipe_created" at 22,179); BOP on the gatherer: blessed BOP -> Miner at once
  (creative), quest done, miner took a wooden pickaxe from the storeroom and left.
- Villagers page status tooltip (04635315) wraps live: "Cook is resting / until morning." on two
  lines, inside the window (night status, 2026-10-22 02:45 session).
- Day-1 quest cards (Campfire, Town Gate) carry no discard x (last session's fix holds live).
- Campfire next to the flag completes the Campfire quest at once.
- An unlinked welcome mat now explains itself on screen ("This welcome mat isn't linked to a
  town flag. Craft one from your flag's crafting tab.").
- Onboarding order held: gate mat -> empty-room quest (card shows a door) -> wand the door ->
  basics (job board, storeroom) -> first villager after the campfire sleep -> storeroom chest ->
  first-work-request batch (4 stone swords, bedroom, hunter job) plus 3 BOPs.
- The evening "Your camp is ready" toast fires because the camp quest is complete (campfire at
  18 71 161 from earlier in this world), as designed.
- Villager 1 (gatherer) shows "They need Food to work" and alternates seeking_work/downtime:
  the gatherer takes food on trips, and the board has no request yet.
- First villager arriving after nightfall got SLEEPLESS the next morning (no bed at nightfall,
  bedroom built 70 s later). Matches the design: bed absence is sampled at nightfall.
- Creative-mode BOP job change applied at once (not "tomorrow morning") and took the quest's
  Hunter rather than a random root: both documented (instant creative spends, tutorial
  override).
- Storeroom stock: hunter took a stone sword from the chest and left for the gate. (My first
  storeroom had its floor level with the door bottom; villagers can't enter that. Skill note.)
