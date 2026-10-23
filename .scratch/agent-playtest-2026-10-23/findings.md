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
5. (judge, onboarding) The change-job panel says "Insert a Block of Progress to make this villager
   a Hunter", but the BOPs are in the flag (tutorial grant "3 BOPs added to flag"), not the
   player's inventory. Nothing on the panel points at the flag's Blocks of Progress tab ("Take
   Block of Progress"). A first-time player may not connect the two.
6. **[FIXED 06222f76, verified live]** (bug, P1) Starving hunter (fullness -272%) with 6 raw beef in
   the storeroom looped `dining_no_table -> downtime -> dining_no_table` every ~200 ticks (5 s at
   40 tps) and never ate. The board's `isReadyForDowntime` gate ran before the diner stage-down,
   and downtime's instant-eat restarted at the first diner, so the raw-food stage (r5's fix,
   `DiningStages.next`) was never reached. Downtime now skips a starving diner
   (`DiningStages.downtimeMayInterrupt`); `DiningStagesTest` 8/8; eating autotests 5/6 (known red
   `dine_at_time`; `eat_direct`, a listed known red, passed).
   The day1 driver now sleeps from dayTime 13000: sleeping on the first dark tick raced the
   toast (which skips sleeping players) and failed `toast-at-first-dusk`.
7. (judge, by design today) "Quest completed: Raw Food" fired the instant the swordless-until-now
   hunter finished *eating* raw beef (`dining_raw_food`), before any hunt: `[LOOTTRACE] extract
   'Raw Mutton' ... (extracted 1/1)`. `FirstHunterLootGuarantee` prepends mutton to any `hunter`
   root result, meals included; `jobs/declarative/COMPLEX.md` "The hunter guarantee" says this is
   deliberate ("job-agnostic within hunter", because `hunter/sword` never starts in ~78% of seeds).
   A player sees the raw-food quest close on a meal. Tried narrowing it to the root's production
   jobs (diners/downtime/rest/BOP/seeking excluded): unit-green, hunter 7/7 + loot 5/5, but
   `self_sufficiency` cook unlocked 2/3 vs 3/3 unnarrowed. Reverted; the real fix is the doc's
   "deeper fix" (why `hunter/sword` doesn't start). `ChickenArcLootGuarantee` has the same
   any-job shape, also documented as deliberate.
