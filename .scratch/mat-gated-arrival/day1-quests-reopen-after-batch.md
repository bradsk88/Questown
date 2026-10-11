---
title: Day-1 and hunter-gatherer quest instances re-appear as OPEN (alongside their completed originals) after the hunter-gatherer batch closes
status: wontfix
created: 2026-10-11
closed: 2026-10-11
resolved: 2026-10-11
---

## Verdict (2026-10-11) — misdiagnosis of the bug-1 JOB_UNLOCK accumulation; no day-1 re-posting happened

Full-log forensics on `run/autotest-village.log` (the same run this issue
cites) refutes the "day-1 quests re-open" claim:

- **Every KNOWN `quests open=`/`done=` dump in the log was scanned**
  (62 distinct states, 01:47:36 → 02:00:59, plus the 02:08:55/57
  end-of-run dumps). Each day-1/hunter-gatherer id appears in `open`
  exactly once, in its legitimate pre-completion state (campfire+gate
  01:47:36 → town_gate → job_board → the 5-quest hunter-gatherer set
  01:51:42 → … → all nine done by 01:56:02). After completing, each id
  sits in `done` ×1 for the rest of the run. **No fresh open instance of
  any day-1 quest exists at any moment in the log.**
- The 217-entry open list at 01:59:55 was: **107× `baker_qt_extract`
  + 107× `crafter_qt_stick`** — JOB_UNLOCK phantoms, i.e. the
  `job-unlock-quest-spam.md` bug — plus the legitimate phase-2.5 kitchen
  trio (`cook_qt_simple_furnace_food`, `villager_food`,
  `kitchen_small`). Those phantom ids are `JobID.toRL` of the baker and
  crafter *root jobs* (`baker/extract`, `crafter/stick`), not day-1
  recipes.
- The mechanism is in the log verbatim at 01:55:47–48:
  `quest_completed [room.hunter_qt_sword]` (last JOB_CHANGE → the
  onboarding gate opens), then
  `Stuck root gatherer (missing #questown:villager_food) -> JOB_UNLOCK
  quest for root baker posted`,
  `Stuck root gatherer (missing #questown:fishing_rods) -> JOB_UNLOCK
  quest for root crafter posted`, `quest_lost [job_unlock baker]`,
  re-posted — the post/lose ping-pong. 214 posts total (107 per
  producer), flat at 107 each from 01:58:01 (the townie's need stopped
  cycling; the phantoms never complete because no baker/crafter root is
  ever unlocked).
- `Basics batch posted` occurs **exactly once** in the whole log
  (01:51:04) — the reward-re-claim candidate never fired.
- Timeline corrections for the record: the swords were deposited at
  01:52:41 (not 01:57:2x); the gatherer→hunter change fired at 01:55:47
  (not 01:57:26); villager #3 arrived at 01:58:06 (not 01:59:55).

## What actually blocked the run past roster 3

Not a quest re-post: after the hunter-gatherer batch closed, the stuck
gatherer (`UNMET_ITEM`: villager_food / fishing_rods / axes, with no
producer root unlocked) needed the **JOB_UNLOCK driver beat** — spend a
Block of Progress on the change-job tab (agent-side `_qtdev change_job
<villager>`) to unlock a producer root, or supply the missing items.
The 214-phantom handle buried the one clean unlock quest, the agent
never made the beat, the phase-2.5 kitchen batch (posted 01:58:06)
never closed, SpawnVisitor never fired again, and the 240000-tick watch
timed out at roster 3 (`FAIL reached-5-villagers`, 02:08:57). The
bug-1 fix (commit `8f670d10`, per-stuck-root dedupe + in-place
retarget + withdraw) bounds the handle, so a re-run needs only that
driver beat to proceed toward roster 5.

## Triage notes

`wontfix`: the described defect does not exist; the observed symptom is
the bug-1 JOB_UNLOCK spam (fixed separately). Kept as the
investigation record.

## Original report (superseded by the verdict above)

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

### What was confirmed

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

### Not confirmed (candidates)

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

### Impact (as originally observed)

- The visitor-3 batch cannot close: the re-posted day-1 instances sit in
  the open list (the run's roster-5 path was blocked on this).
- Player sees their finished day-1 quests "come back".
- Makes the 1.20.6 held village/growth run unverifiable for
  roster >= 4 on the current build.

### Repro

Held agent-drive `village/growth`, drive the new day-1 order to
roster 2, close the hunter-gatherer batch, wait for villager #3.
Diff the KNOWN `quests open=` / `done=` lists across the roster-3
arrival.
