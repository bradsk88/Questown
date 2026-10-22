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
   Cause: `Spaces.metaRoomAround` gives a block-room the door `p.offset(1, 0, 0)` and the
   message prints the door (`TownMessages.roomRecipeCreated` -> `getDoorPos()`). The door is
   the block-room's identity (quest completion, the `rooms={[201, 192]=...}` keys), so the fix
   belongs in the message (print the block pos from `broadcastBlockRoomCreated`), not the door.
   Not changed: `broadcastBlockRoomCreated` re-runs all of `roomRecipeCreated` (swap,
   advancements, quest completion) just to print, so threading a display pos touches 3 layers.
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
   Player-facing copy of the old behaviour (quest tooltip, live): "Your town's stock of Coal has
   run dry and no one can make it. Unlock the baker job with a Block of Progress so the town can
   produce it." Both halves are false: the town had a miner, and bakers burn coal.
8. **[FIXED 2026-10-22, verified live: after the fix the flag saved two blessed BOPs (gatherer, miner)]** Earned BOPs vanished. The hunter
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
10. (judge) The phase-2.75 "Unlock job: Baker" quest says "Unlock the baker job with a Block of
    Progress". The only BOP spend that changes a root is the villager change-job tab, which is
    random for a plain BOP. I spent the last plain (tutorial) BOP on the gatherer, as the copy
    says, and got a farmer ("Changing villager from gatherer to one of [farmer, smelter, ...]").
    The onboarding guarantee (`onboardingJobChangeGuarantee`, ticket
    expected-guaranteed-cook-got-arborist) covers only JOB_CHANGE quests, but this unlock quest
    is posted by the tutorial too (`tryPhase2_75_topNeedUnlock`). Should its root fill the same
    guarantee slot? Recommendation: yes, while the unlock quest is open.
11. Log-only: after a server restart, `InsertIntoSlotSpecialRule` / `TakeFromSlotSpecialRule`
    each log one ERROR "No container at [1, 1]" (05:39:08), before the room scan. Not seen again.

## Round 4 (fresh jungle world, flag at 400 71 -299)
12. **[FIXED 2026-10-22, art verified live (hunter downtime state:0 showed the relaxing picture, 06:10:47); state text not captured]** Villagers tab, day 2: the gatherer's
    status picture was the purple/black error checkerboard and its tooltip read "Gatherer is busy
    with their work." It was on downtime (`gatherer:downtime state:0/state:2`), which overrode
    art and text only for NO_JOBSITE; `StatusArt` returns `error.png` for work states and the
    tooltip fell back to `JobTooltips.FALLBACK_LINE_1`. Now relaxing art + "is relaxing for a
    moment." `DowntimeWorkOverridesTest` red -> green; full :test 1418/0.
13. (judge) Work-request "Learn More" (ItemJobsScreen) pages are titled with the raw job id:
    "Gatherer - rod_half_day", "Gatherer - shovel_full_day", "Gatherer - axe"
    (`renderJobTitle` uses `Compat.literal(jobId)`). The "20 known jobs" for a seed or sapling
    on day 2 are all gatherer tool/length variants, so the player pages 20 near-identical
    "Gatherer" cards. Display names per variant, or grouping by root, would read better.
- r4 BOP accounting held across two deposits: the BOP tab read 4 ("Earned by: Hunter x1"), then 4
  again after a spend and a deposit ("Hunter x2"). The 8 fix holds in a fresh world.
- r4 phase 2.75 picked "#questown:swords (x1) -> crafter" (crafter/stone_sword is a real
  producer), so the coal case of #7 did not come up live here. Swords were the top need
  while 3 stone swords sat in the storeroom chest; not traced. r5 repeated it (07:02:49, "#questown:swords (x1) -> crafter") with 4 swords in the chest. Unmet needs are not logged, so the registering job is unknown.
14. A townie on downtime alternates `downtime state:0` <-> `downtime NO_SUPPLIES` every ~4 s
    (hunter, 06:10:43 NO_SUPPLIES, 06:10:47 state:0; gatherer the same on day 2). Downtime has
    no ingredients or tools, yet its card then reads "Hunter is looking for supplies. They will
    search in room chests." with the supplies art. WHY dump (06:11:22): the supplies gate
    `gate[TODO=false] (No matches found for ...DeclarativeJobTicker$$Lambda in any containers)`
    fires with `relevant ingredients = [... no requirements]`: a work-only state 0 (no
    ingredients, no tools) is read as "supplies missing". Probably the same family as the
    work-only state-0 gate (AGENTS.md cook/stick note); shared status logic, left for the dev.
15. (judge) Random root change pool lists crafter twice: "Changing villager from gatherer to one
    of [farmer, smelter, cook, fisher, soup_cook, crafter, baker, arborist, gatherer, crafter,
    organizer, miner, hunter]". `SimpleVillagerHandle` shuffles `getAllRootJobs()` (JobIDs),
    and two crafter jobs have `parent: null`: `crafter/stick` (11 children) and
    `crafter/ladder` (no children; parent null since the 2025 data). A plain BOP is twice as
    likely to give a crafter, and can start one on ladder. Either ladder's parent should be
    `crafter/stick`, or the pool should be per root. Second #10 data point: the 2.75 "Unlock job:
    Crafter" quest; the plain BOP spend rolled hunter.
- Observation (not a bug claim): the r4 miner ate at 06:13:37 "Fullness -10739 -> 5000", about
  10k ticks hungry with carrots in storage. Below-zero ticking is by design
  (`tickHunger`: "keeps ticking below zero to trigger dining"); it had become a miner 3.5 min
  earlier and spent the gap in gatherer trips + bop_depositing. After the meal, "gave up waiting
  for different work after 501 ticks (dining_no_table)" -> `dining_raw_food` -> gave up again:
  the 10-21 #17 pattern, still present.
16. (judge, same family as #13) Villager "Jobs" (hammer) tab labels nodes with raw job ids:
    survival hunter shows "sword" -> "sword_half_day"; the creative all-jobs view shows "gather",
    "axe", ... In creative the tree root's tooltip is placeholder text "test" / "test2"
    (`VillagerAdvancementsScreen` builds the root `DisplayInfo` with `Compat.literal("test")`,
    `("test2")`); creative also shows the whole tree (gatherer first) on a hunter, which matches
    the open `skills-tab-icons-stacked.md` ("only gather visible").

## Round 5 (fresh desert world, flag at 1601 71 1480; mat 20 blocks east; no sleep, no free food)
17. (judge) A player who doesn't sleep keeps "Your camp is ready / Use wand on campfire" on
    screen the whole next day; a campfire sleep cannot start in daylight. The toast hides only
    when the player sleeps (`ClientAccess.tickTutorialToasts`) or via the flag-menu dismiss, and
    is shown once per session (`campReadyToastShown`), so a plain morning hide would also stop
    it coming back the next evening. Recommendation: hide at dawn and clear the per-session bit.
- A mat 20 blocks from the flag is accepted (Town Gate quest done; first villager spawned at
  the mat, x=1621). Morning arrived without the player sleeping and the visitor came anyway.
18. **[FIXED 2026-10-22]** Diner tooltip typo: "Gatherer is looking at for food / and a place to
    sit down and eat." (`tooltips.villagers.job.diner.status_1.NO_SUPPLIES`, since 2024). Now
    "is looking for food".
- Seen live: the economics tooltip singular, "Food was needed once in the past day"
  (handoff item 5 said singular had not been seen).
- r5 without free food: the first gatherer can never start (gather needs villager food; it cycles dining_no_table/dining_raw_food/downtime NO_SUPPLIES all night). The red "Free food ->" on the economics tab is the only rescue; designed, noted for the record.
- (still present, = 10-21 #19) "Quest completed: Post your first work request on the job board" fired at the BOP job change (the hunter's auto-posted raw mutton request), before the player posted anything.
19. Storage full in r5 (both storeroom chests 27/27, ~07:07 onward): miner and hunter sat in
    NO_SPACE for minutes (`[WHY] ... NO_SPACE` x10 per 2 min), the miner thrashed
    bop_depositing <-> seeking_work (#9 again, 51 changes in 2 min), but the flag showed no
    storage bubble (screenshot r5-013 at 07:13, looking straight at the flag from 4 blocks).
    BELIEVED, not instrumented: `StorageBlockTally` resets on any free slot
    (`Jobs.townHasSpace`), and the hunter takes its sword out of the chest each trip, which frees
    a slot until the next deposit; the thrashing miner is also only NO_SPACE half the time. So
    the 1200 villager-tick tally may never accumulate. Settle by logging the tally per flag tick.
20. **[FIXED 2026-10-22, verified live: after the restart cook/miner/hunter moved to dining_raw_food, ate beef/porkchop ("Fullness -6820 -> 2500"), hunter back to sword]** Town-wide halt once villager food runs
    out. r5, 07:17: cook, hunter and miner each looped `dining_no_table` -> "gave up waiting for
    ingredients after 201 ticks" -> board -> `dining_no_table` every ~2.5 s (35 changes per
    2 min each), no work, with raw porkchop/beef/chicken in storage. The board re-dines a
    starving townie unless it gave up on *raw* food recently, and the declared fallback
    (no table -> raw food) was only read after 500 ticks without a next job, which the loop
    never reached. Before cf1b7072 (last session) the give-up ran the meal hooks every tick,
    so starving townies were "fed" without food, which hid this. Fix: at the give-up hand-off,
    change to the job's declared no-supplies fallback when it is another job
    (`JobLogic.isAnotherStage`). Also the handoff's open question ("a no-table diner gave up after
    201 ticks"): this is that path.
- Autotests after #20 (2026-10-22 07:40): `only=eating` 5/5 PASS, `village/day1` 41/41 PASS.
  `eating/eat_raw_food` had been failing on the parent commit (fullness -40%, min 30%): its 0.3
  floor was only reachable with the free meals cf1b7072 removed; floor now 0.15 (one raw meal,
  then drain; observed 20%). AGENTS.md lists `eating/dine_at_time` and `eating/eat_direct` as
  deterministic failures; both PASS on the parent and after the fix, so that list is stale (not
  edited here: AGENTS.md has uncommitted dev changes in the main checkout).

## Round 6 (fresh plains world, flag at -49 84 -35; out of order: rooms before the gate, two mats, no campfire)
- Rooms built and wanded before the Town Gate register silently, and every later quest that
  matches them backfills at once (Room, Small Storeroom, Bedroom): fine. A second welcome mat
  registers ("Town Gate created" for each). The first villager comes without a campfire; the
  Campfire quest just stays open.
21. **[FIXED 2026-10-22, verified live]** Removing a bedroom door printed "Bedroom destroyed at
    location [-44, -35]" twice. The event fires three times in one tick; the broadcast queue
    dropped duplicates only against queued lines, so copy 2 was queued behind copy 1. Now the
    line just sent counts too (`BroadcastPacer`, `BroadcastPacerTest` red -> green; full :test
    1423/0). Live: 1 broadcast + 2 "Dropped duplicate broadcast". The triple firing itself is
    the open `messages-print-twice.md`.
22. r6 day 2 (08:05, daytime, raining, SLEEPLESS after I removed the bedroom door): the gatherer
    as a no-table diner took a carrot every ~8 s and never ate: "gave up waiting for ingredients
    after 201 ticks (job: dining_no_table)" -> dining_raw_food (no raw food in town) -> gave up ->
    dining_no_table, 8 cycles, no "Fullness changed" all day. The flag (the no-table work spot)
    had free standing blocks around it; no rain logic exists in the villager AI. Next morning it
    ate at once (08:08:14, -309 -> 5000) and went back to gathering through the remaining mat.
    Not traced: the villager's position during the loop was not sampled. Before #20 the same
    failure would have looped no_table -> board -> no_table.
- A broken welcome mat (one of two) is skipped, not dropped (`TownPois.getWelcomeMatPos`), and
  the gatherer kept working through the other mat. Fine.
- Full autotest suite after tonight's fixes (tools/autotest-parallel.sh --shard 4, 08:13):
  TOTAL 88/89; the one failure is `chicken-arc:skip_chicken_command`, on AGENTS.md's
  deterministic-failure list. No new reds.
