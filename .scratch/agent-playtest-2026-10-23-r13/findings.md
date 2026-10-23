---
title: 'Agent playtest 2026-10-23 r13: continuous run to verify r12 #9 (starving) live'
status: needs-triage
---

# Agent playtest 2026-10-23 r13 (continuous) and r14

r13 stopped on day 1 for #1. r14 is a fresh world on the same seed (-5440946815546586628), built at
1b47cc98, with the same town layout (flag (-24, 69, 135), campfire (-27, 69, 135), room 1 door
(-18, 69, 133), storeroom chest (-15, 69, 132), job board (-21, 69, 135), bedroom door
(-10, 69, 135)).

Goal: verify r12 #9 (the starving flag reads live fullness, e6debc70) in a fresh town, watch for
r12 #7 (failed raw meals), and note what else turns up. Same rules as r12: the server is never
stopped or rebuilt mid-run, no console seeding into containers, and creative inventory is used
only the way a creative player would. The player supplies swords, pickaxes and coal. Built at
HEAD a5b417d6. Tick rate 40. Random seed; spawn at (-39.5, 74, 149.5).

## Interventions
- Spawn was hilly forest; the town is on a flat lakeside strip, flag at (-24, 69, 135) (heights
  probed by console tp; that burned day 0, so the first visitor arrived while the room was built).
- Rooms built with console `fill ... hollow` + `setblock` doors/bed/lantern (a creative player's
  building); 4 stone swords put into the empty storeroom slots by console (the player's delivery).

## Fine (checked)
- Day 0-1 onboarding: flag -> Campfire (at flag Y, completed on placement) + Town Gate (plate Craft
  was the only enabled row) -> Empty Room (wand on door) -> Job Board + Small Storeroom -> Stone
  Sword x4, Bedroom (bed + lantern, completed), Villager job: Hunter (BOP -> Unlock), Raw Food,
  Post a work request.
- The gate room's 42-entry rooms-gate list is 42 distinct spots of one match (its whole interior),
  not duplicate matches: unrelated to #11's shared-block crash.
- `vopen bop` on a villager with no earned BOP shows "has earned a Block of Progress!": the diag
  command skips `VillagerBopTab.decide`, so a player never reaches that screen. Not a finding.

## Odd
1. (bug, P0 for r12 #9) **The starving fix e6debc70 does not apply in a fresh town.** Day 1, the
   first gatherer (fullness -76%, no food in town) churned `downtime -> dining_no_table ->
   downtime` about 20 times in 4 minutes. Each exit was `[board] ... via diner-ate` (debug.log),
   which fires only when `isStarving` is false; there was no `starving-diner-stage` at all. Cause:
   the starving listener is added inside `SimpleVillagerHandle.initialize(...)`, which only the
   NBT load path calls (`TownVillagerHandlerSerializer.deserialize` <- the villager pair's
   `fromTag`). A freshly placed flag runs `initializeFreshFlag(false)` -> each pair's
   `onFlagPlace` only, so `initialize` never runs and the listener never exists until a
   save/reload. The dining-switch listener (`TownFlagTileData` villager `onPlace`) does exist, so
   the townie is switched into a diner and bounced straight out. e6debc70's unit test called
   `initialize(...)` with empty maps, which no fresh town does. Run stopped on day 1 to fix it
   (the run's purpose was verifying #9).
   **FIXED 2026-10-23 (unit):** the listener moved to the `SimpleVillagerHandle` constructor.
   Red/green: `SimpleVillagerHandleTest.freshlyPlacedFlag_neverInitialized_fedTownieStillBecomesStarving`;
   all 62 `town.*` test classes green. Live check in r14.

## r14

- **r12 #9 + r13 #1 verified live (fresh town, no reload).** Day 1 hunter: fullness 56% at
  20:54, hunted through three trips to -105%, `[board] hunter:sword -> downtime via
  ready-for-downtime` (20:58:13), the dining-switch listener -> `dining_no_table`, then
  `[board] dining_no_table -> dining_raw_food via starving-diner-stage` (20:58:20; this branch
  needs `isStarving`), ate one raw item (`Fullness changed from -6480 to 2500`, 20:58:24), then
  `diner-ate -> downtime`. No diner-ate churn.
- **r12 #7 did not recur:** a single raw item was a full meal.
- Raw Food closed on the hunter's first extraction (Raw Mutton, 20:55:29), not on a meal.
2. (bug, r12 #8 evidence) `hunter:sword -> hunter:sword` at 20:56:23 has no `[board]` line, so
   the chained re-assignment bypasses the board cascade. It also bypasses the starving check:
   the hunter kept hunting from about 0% to -105% fullness and ate only after `ready-for-downtime`
   ended the job, about 2 minutes later at tick rate 40.
   **FIXED 2026-10-23:** that change is `JobLogic`'s max-ticks watchdog re-creating the same job
   ("Reached max ticks for hunter:sword. Falling back to hunter:sword."). A same-job fallback
   now asks the board (`JobLogicTest.tick_sameJobMaxTicksFallback_asksTheBoard`).

3. (observe, pre-existing flake) `hunter/sword [sleep_mid_trip]` fails about 1 run in 4 with one
   or both hunters ending `held=[]`, `hunted again=false`. Same rate with and without the
   watchdog change: 1/4 at HEAD 1b47cc98, 1/3 with it (sweeps 2026-10-23 ~22:05). Not on
   AGENTS' known list yet.

## r15 (fresh world, same seed, built at eeba8a2d): live checks of the day's fixes

- **r12 #2 verified live:** a campfire on a dirt step at y=70 (flag y=69) completed the Campfire
  quest on placement, and the wand accepted it as registered. On a lone dirt pillar the wand said
  "No safe place to sleep near this campfire" (the temp bed needs floor at the campfire's level,
  which is correct). With a 5x5 mound at y=69 it slept.
- **r12 #6 verified live:** a Bedroom 2 blocks tall inside (floor y=68, roof y=71) was recognized.
  The hunter slept in its bed (SleepingX -9, y 69.5625) and after the morning skip walked out at
  floor level (y 69.0) to the flag within seconds; it was not pinned on the bed top.
- **r12 #8 / r14 #2 verified live:** the watchdog logged "Fallback hunter:sword is the same job;
  asking the board" (22:17:56, 22:20:11), and the board sent the hungry hunter straight to
  `starving-diner-stage`. Level 2 at 22:24:02 -> `[board] hunter:sword -> bop_depositing via
  bop-deposit` at 22:24:44 -> deposit trip done at 22:24:48 -> XP moving again (200/1200).
  In r12 it was latched for 15+ minutes.
- r15 intervention: villager 2 was made a cook with console `qt jobs set` (the diag client's
  `vopen` only reaches the first villager), to check r12 #4 (a hungry coal-less cook).
4. (judge, answers the dev's r12 #4 question "the diner will finish eating and go back to its
   jobs, showing a need balloon. Is that incorrect?") **Partly incorrect.** r15, cook with no
   coal in town:
   - While the town has no raw food, the starving cook loops `dining_no_table ->
     dining_raw_food` (gave up waiting for ingredients) `-> seeking_work -> downtime` (watchdog,
     a different-job fallback) `-> dining_no_table`, about every 45 s, and never reaches a cook
     job (22:28-22:32, fullness down to -210%).
   - Once the hunter's catches reach the chest it eats (its `villager_stats` fullness jumps
     -210% -> -12% -> 28%, later 40%, 47%; the `Fullness changed` lines name no villager, so
     they are not attributed), but then only alternates `seeking_work <-> downtime`: no cook job is
     workable without coal, so the board never assigns `cook:fuel`. Its status tooltip reads
     "Cook is looking at the job board. They are checking for requests." Coal is never
     mentioned; that line comes only from inside a cook job.
   - So the player is told about coal only by the Economics tab, as r12 #4 said. The dev's
     model holds only once a cook job is actually assigned.
5. (observe, r10 #9 balance) Hunger drains fast: the r15 cook's `villager_stats` read 28% ->
   2% -> -24% at 32 s intervals (22:34:27-22:35:31, tick rate 40), about 26% per 32 s, so a full
   stomach lasts about 2 real minutes (~5000 ticks). One hunter's catches barely feed two
   townies. BELIEVED, not counted: the hunter eats many of its own catches.
