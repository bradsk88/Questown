---
title: 'Agent playtest 2026-10-23 r10: fresh world (plains at 300,470), flag at 295 64 475'
status: needs-triage
---

# Agent playtest 2026-10-23 r10

Fresh random-seed world, creative, tick rate 40. Built at HEAD cfbfee05 (stock jobs score
between the acceptance bands). Spawn was a y=111 meadow/mountain; moved to plains (300, 470).

## Fine (checked)
- Flag: starter map, 6 timber placements, quests Campfire + Town Gate.
- Campfire -> completed at once. Mat crafted from an oak pressure plate (only that row's Craft
  enabled) -> Town Gate completed, "New visitors will arrive in the morning", empty-room quest.
- 5x5 plank shell + wand on the door -> Room; chest in it -> Small Storeroom. The storeroom
  tooltip reads "Place a chest in a room with a wand-registered door."
- Job Board tooltip "Craft a job board at the town flag and place it near the flag. Click to open
  crafting"; clicking the card opens crafting. Board placed 3 west of the flag -> completed.

## Odd
1. **[FIXED, verified by village/day1 42/42]** (bug) "Your camp is ready / Use wand on campfire" fires at the
   EVENING signal (dayTime 11500), but vanilla refuses sleep until `level.isDay()` turns false
   (~12542 clear weather). Wand used at 11876 -> `startSleepInBed ... NOT_POSSIBLE_NOW`; the player
   sees "Respawn point set" (+ vanilla's action-bar "You can sleep only at night"). ~52 s real
   time at 20 tps where the toast's instruction fails. Now gated on `!isDay()` (vanilla's own
   gate); `Day1BeatChecks` window moved to 12542.
2. (agent note, not a game bug) `item replace` on the hand while a flag menu is open doesn't reach
   the client (vanilla only syncs the open menu's slots): Craft buttons stay greyed until reopen.
3. **[FIXED, verified live: "Gatherer / Gather (half day)"]** (bug) Work-request confirm ("Requesting Raw Food — There are 3
   known jobs which produce it") shows three boot icons; hovering one highlights it but shows no
   tooltip, so the player can't tell which jobs they are. `WorkRequestConfirmScreen.renderJobIcons`
   discarded `RenderUtil.stripOfRequestableItems`' return (the hovered job's name lines). Now
   shown via `showTooltip`. (The request icon cycling through wheat seeds is data:
   `#questown:villager_raw_food` lists seeds.)
4. **[FIXED, verified live: "Hunter is looking for food. / They also need a place to sit down and eat."]** (bug, copy) Swordless hunter's status tooltip:
   "Hunter can't find work. Check / They need Swords to work." The lang split one sentence over
   `status_1`/`status_2`; the need line (65897f02) replaces `status_2` for any non-working status,
   so `status_1` dangled. 19 non-working status pairs rewritten so line 1 is a sentence;
   `StatusFirstLineSentenceTest` guards it.
5. **[TRIAL, dev 2026-10-23]** (judge, onboarding) The change-job panel says "Insert a Block of Progress to make this villager
   a Hunter", but the BOPs are in the flag (tutorial grant "3 BOPs added to flag"), not the
   player's inventory. Nothing on the panel points at the flag's Blocks of Progress tab ("Take
   Block of Progress"). A first-time player may not connect the two.
   **Dev:** try a BOP tab on the villager card opened in-world (it was only shown from the
   flag's click-through). With no earned BOP it now opens the flag's BOP page, from any distance
   (`VillagerBopTab.decide`). Dev worries the tab strip is at capacity: up to 7 tabs in-world.
6. **[FIXED 06222f76 + see #12; live verification of 06222f76 alone was confounded]** (bug, P1) Starving hunter (fullness -272%) with 6 raw beef in
   the storeroom looped `dining_no_table -> downtime -> dining_no_table` every ~200 ticks (5 s at
   40 tps) and never ate. The board's `isReadyForDowntime` gate ran before the diner stage-down,
   and downtime's instant-eat restarted at the first diner, so the raw-food stage (r5's fix,
   `DiningStages.next`) was never reached. Downtime now skips a starving diner
   (`DiningStages.downtimeMayInterrupt`); `DiningStagesTest` 8/8; eating autotests 5/6 (known red
   `dine_at_time`; `eat_direct`, a listed known red, passed).
   The day1 driver now sleeps from dayTime 13000: sleeping on the first dark tick raced the
   toast (which skips sleeping players) and failed `toast-at-first-dusk`.
7. **[FIXED, dev 2026-10-23: meta jobs excluded]** (judge, by design today) "Quest completed: Raw Food" fired the instant the swordless-until-now
   hunter finished *eating* raw beef (`dining_raw_food`), before any hunt: `[LOOTTRACE] extract
   'Raw Mutton' ... (extracted 1/1)`. `FirstHunterLootGuarantee` prepends mutton to any `hunter`
   root result, meals included; `jobs/declarative/COMPLEX.md` "The hunter guarantee" says this is
   deliberate ("job-agnostic within hunter", because `hunter/sword` never starts in ~78% of seeds).
   A player sees the raw-food quest close on a meal. Tried narrowing it to the root's production
   jobs (diners/downtime/rest/BOP/seeking excluded): unit-green, hunter 7/7 + loot 5/5, but
   `self_sufficiency` cook unlocked 2/3 vs 3/3 unnarrowed. Reverted; the real fix is the doc's
   "deeper fix" (why `hunter/sword` doesn't start). `ChickenArcLootGuarantee` has the same
   any-job shape, also documented as deliberate.
   **Cause of the beef:** the agent seeded it (`r10-server.log` 06:30:00, `item replace block`
   4 Stone Swords + Raw Beef into the storeroom chest); the hunter had extracted nothing. So a
   meal on player-stocked meat closed the quest. Now `shouldPrepend` skips `MetaJobs.isMeta`
   (diners, downtime, resting, seeking_work, bop_depositing); `MetaJobsTest`.
8. **[FIXED, dev chose "no need line for diners"]** (judge, minor copy) A hungry cook reads "Cook is looking for food. / They need Coal to work.":
   line 1 is the diner status, line 2 the job's blocker (need line replaces any non-working
   line 2, diners included). Both true; the pairing reads like one thought.
9. (minor, churn) A hungry-but-not-starving cook with no cooked food in town bounced
   `dining_no_table -> downtime -> dining_no_table` every ~200 ticks (07:24:44-07:25:10, 5 cycles)
   until the board picked work (`dining_no_table -> fuel`). Same mechanism as #6 (board sends a
   diner to downtime; downtime's instant-eat restarts the diner) but the #6 guard only covers a
   starving diner, and the raw-food fallback is starving-only. Self-resolved; once cooked mutton
   existed the cook ate (07:26:56). Status flaps between dining/relaxing during it.
   **Update after #12:** the cycle now resolves: downtime -> dining_no_table (gives up after ~200
   ticks, no cooked food) -> dining_raw_food -> eats -> downtime (08:08-08:09, every ~70 s at 40 tps;
   one villager reached -7000 before a meal). Remaining cost is the meal frequency (r9 #11 balance
   note) and #14 (the cook can't cook what the town has).
10. **[FIXED, pending live verify]** (bug, copy) An eating villager's status read "Cook is busy
    with their work." The `diner` tooltip category had keys only for NO_SUPPLIES and NO_JOBSITE;
    every other diner status (work states 0-2, GOING_TO_JOB, COLLECTING_SUPPLIES,
    WAITING_FOR_TIMED_STATE, EXTRACTING_PRODUCT, IDLE) fell to `JobTooltips.FALLBACK_LINE_1`. Added
    diner copy ("%s is going to eat.", "%s is eating.", ...).
11. **[FIXED, dev chose "starter request = food tag" 2026-10-23]** (judge, P1 for onboarding flow) Day 5: the new cook idles with raw beef, porkchop and chicken
    in storage, coal 12, and the cook-batch "Food (4/8)" quest open (any `#villager_food`).
    `_qtdev work_score`: beef/porkchop/chicken 1.0 but "[Product not requested]"; the board's
    cook request is the auto-posted `cooked_mutton` (the cook's starter request), and the hunter
    brings beef/porkchop/chicken/leather, not mutton, so mutton scores 0. The picker only takes a
    job that satisfies a board request whenever the board holds any (`JobCycler.isValid`,
    `jobs/COMPLEX.md` "A slot-extract job requests what its slot holds"). The player has to work
    out that the board's mutton ask is what's blocking. Options for the dev: the cook's starter
    request names the category (`#questown:villager_food`, like the quest) instead of mutton; or
    the picker falls back to unrequested work when no requested product is producible. Not
    changed (design). Live day 6: the cook's status reads "Cook is looking for supplies. / They
    need Raw Mutton to work." while beef/porkchop/chicken sit in storage.
    **Resolved:** `postStarterRequests` posts each declared `initial_request` intact (tag stays a
    tag) and drops an item a declared tag covers, so a new cook's board ask is
    `#questown:villager_food`, not `cooked_mutton`. `StarterRequestsTest` 4/4. #14 (mutton-only
    recipe set) is unaffected.
12. **[FIXED, verified live]** (bug, P1, root cause of #6 and #9) The starving flag is in-memory
    only (`SimpleVillagerHandle.starving`, not saved) and the hungry listener set it only when the
    hunger broadcast caught fullness at exactly 0. After any reload a townie already below zero was
    never starving again: the board's starving branch (stage-down to raw food) and the #6 guard never
    applied, so it bounced dining_no_table <-> downtime (#9) and starved beside raw meat. The #6
    "verified live" was confounded: the hunter reached raw food through DinerNoTableWork's own expiry
    (it sat in dining_no_table overnight), not through the guard. Now `<= 0`; the next broadcast
    (every 10 below zero) re-flags a reloaded townie. `SimpleVillagerHandleTest` red/green; eating
    5/6 (known red dine_at_time). Live after restart: both townies went dining_no_table ->
    dining_raw_food and ate (fullness -3020 / -5060 -> 2500).
13. **[FIXED, verified live; gui-lint QuestsScreen OK]** (UI) A villager's Quests tab with nothing open was a blank
    "Page 1 / 1": `VillagerQuestsContainer` inherits `isAllComplete() == false`, so the flag tab's
    all-done state never applied. `QuestsScreen` now draws "No open quests right now." when it has
    no cards and no all-done state.
14. **[PARKED, dev 2026-10-23]** (judge, onboarding, P1) Day 6-7: the onboarding cook can only cook mutton. Its
    `unlocked_jobs` are extract, fuel and mutton (the `TownQuests` grant); beef/porkchop/chicken
    are progression-unlocked (BOP). The hunter brings mostly beef/porkchop/chicken (storage: beef
    8, porkchop 4, chicken 1, mutton 0), so the cook idles (`seeking_work -> downtime`) while
    `Prepared for cook: [porkchop,chicken,beef]` and "Food" is on the board. Its status says
    "They need Raw Mutton to work." which points the player at hunting, not at spending a BOP to
    unlock a recipe; the cook's card has no visible way to spend a BOP on recipes (its tabs: stats,
    change job, Quests, and a BOP tab that opens the flag's BOP page). How does a player learn to
    unlock cook/beef? Also: the request confirm for Food lists 5 known producing jobs (incl.
    "Cook / Potato") that no townie can do yet.
    **Dev:** leave for now; players can cook in a furnace themselves. The real fix is holistic:
    a red dot on the skills page for "new skills to unlock" and investment in that UI.
15. **[FIXED, copy]** The Mineshaft quest said "Place a mineshaft block in a room and register the
    door." and the crafting row "Place it in a room so a miner can work there." A mineshaft is a
    block-room (`blocks/COMPLEX.md`): placing it registers its own room, and every miner job's
    `room` is `questown:block_room/block.questown.mineshaft`. Live: the quest completed the moment
    the block was placed, 3 s before the door I'd built for it was registered. Now "Craft one at
    the town flag from stone stairs and place it near the flag." / "Place it near the flag so a
    miner can work there."
16. (agent error, not a game bug) "Two BOPs into the change-job slot, one Unlock, both gone" was
    false: the slot caps at 1, the cursor BOP went back to the inventory on close. My check grepped
    the newest `entity data` log line, which belonged to a later command. Read a command's output
    from the log offset taken just before that command.
