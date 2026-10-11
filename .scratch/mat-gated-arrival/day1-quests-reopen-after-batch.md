---
title: Day-1 and hunter-gatherer quest instances re-appear as OPEN (alongside their completed originals) after the hunter-gatherer batch closes
status: needs-triage
created: 2026-10-11
---

## Context

Held `village/growth` run, 2026-10-11 (new day-1 flow). Timeline (server
log `run/autotest-village.log`, 200 tps):

- ~01:45-01:55: day-1 beats complete; basics batch (campfire, town_gate,
  store_room, job_board) closes; hunter-gatherer batch opens (bedroom,
  wooden_sword x4, stone_sword, hunter_qt_sword JOB_CHANGE,
  villager_simple_furnace_food_raw LOOT).
- 01:57:26: `change_job` on the gatherer fires -> now `hunter`.
- 01:57:2x: wooden_sword (4 deposited via `complete_sword_quest`),
  stone_sword, bedroom, hunter_qt_sword, villager_simple_furnace_food_raw
  all complete. Open list = just the JOB_UNLOCK spam (see
  `job-unlock-quest-spam.md`).
- 01:59:55: roster = 3 (villager #3 arrived; the SpawnVisitor reward
  queued at batch close fired at dawn). The KNOWN probe now shows:

  **open**: `special_quest.campfire`, `special_quest.town_gate`,
  `store_room`, `job_board`, `bedroom`, `minecraft:wooden_sword`,
  `minecraft:stone_sword`, `hunter_qt_sword`, `villager_simple_furnace_food_raw`
  (all of the day-1 + hunter-gatherer quests, as fresh open instances)
  plus the new phase-2.5 batch (`kitchen_small`, `cook_qt_simple_furnace_food`,
  `villager_food`) plus the JOB_UNLOCK spam.

  **done**: the same 8-9 quest ids (the original completed instances).

So the handle contains **both** the completed originals **and** new open
instances of the same quests. The new instances do not re-close even
though their conditions hold (4 wooden_swords in the registered chest,
campfire lit by the flag, mat registered, job board present, bedroom
enclosed).

## What is confirmed

- The phase-2.5 batch (`tryPhase2_5_jobChangeAndFood`) fired correctly at
  roster 3 (JOB_CHANGE count 1 < 2, hunter raw-food loot complete).
- The re-appeared day-1 instances are separate entries from the completed
  ones (both show in open and done respectively; `toShortString` does not
  expose instance ids, so the dual-instance claim rests on that
  open+done pairing).
- Whatever re-posted them ran between 01:57:28 and 01:59:55 — the window
  where `questRequests` was non-empty (batch-close rewards claimed:
  `MCDelayedReward(SpawnVisitor)` + the batch's completion reward) and
  `TownQuests.tick` was routing through `addTutorialBatches` every tick.

## Not confirmed (candidates)

- `addTutorialBatches` re-invoking phase adds: ruled out for
  hunter-gatherer by the `questBatches.includes(JOB_CHANGE)` guard, and
  the phase list starts at phase 2 (no basics/gate phase there).
- The day-1 batches (campfire/gate/basics) are posted by
  `setUpQuestsForNewlyPlacedFlag` + the mat/campfire reward chain
  (`AddBasicsBatchReward`, `SpawnVisitor` rewards), not by
  `addTutorialBatches` — so a **reward re-claim** (asap-rewards queue
  re-queueing a reward whose condition re-holds) is the leading
  candidate: the mat-gate batch's rewards were re-claimed after the
  batch-close pending reward landed in `questRequests`.
- Or a `TownQuestsHandle`/NBT re-hydration resetting completion. No
  chunk-unload/reload lines for the flag were observed, making this
  less likely.

## Impact

- The visitor-3 batch cannot close: the re-posted day-1 instances sit in
  the open list (the run's roster-5 path was blocked on this).
- Player sees their finished day-1 quests "come back".
- Makes the 1.20.6 held village/growth run unverifiable for
  roster >= 4 on the current build.

## Repro

Held agent-drive `village/growth`, drive the new day-1 order to
roster 2, close the hunter-gatherer batch, wait for villager #3.
Diff the KNOWN `quests open=` / `done=` lists across the roster-3
arrival.
