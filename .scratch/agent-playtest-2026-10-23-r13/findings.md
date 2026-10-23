---
title: 'Agent playtest 2026-10-23 r13: continuous run to verify r12 #9 (starving) live'
status: needs-triage
---

# Agent playtest 2026-10-23 r13 (continuous)

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
