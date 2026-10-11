---
title: JOB_UNLOCK quests post/lose in a per-tick ping-pong when a root's unmet item oscillates; lost quests stay in the handle
status: wontfix
closed: 2026-10-11
resolved: 2026-10-11
created: 2026-10-11
---

## Context

Held `village/growth` run, 2026-10-11 (new day-1 flow: campfire → mat →
flag-UI → sleep → sign, mat-gated arrival). Roster 2-3, day-1 quests done.

A townie whose root is stuck in `UNMET_ITEM` (gatherer, no dining table +
no accessible food) makes `TownQuests.tickUnmetUnlockQuests` oscillate:

```
[01:57:20] Stuck root gatherer (missing #questown:villager_food) -> JOB_UNLOCK quest for root baker posted to VillagerUUID[a72aa9c9]
[01:57:20] Broadcasting message: messages.town_flag.quest_lost [job_unlock, crafter]
[01:57:20] Stuck root gatherer (missing #questown:fishing_rods) -> JOB_UNLOCK quest for root crafter posted ...
[01:57:20] Broadcasting message: messages.town_flag.quest_lost [job_unlock, baker]
```

The townie's `getUnmetItem()` alternates between `#questown:villager_food`
and `#questown:fishing_rods` tick-to-tick as its work state cycles
(seeking_work <-> downtime). Each missing item maps to a different producer
root (baker vs crafter), so:

1. tick N: unmet=fish_rod -> post `crafter_qt_stick` JOB_UNLOCK.
2. tick N+1: unmet=food -> `stillNeeded` for crafter is false ->
   `markRecipeAsLostByKey` (quest_lost broadcast) ...
3. tick N+2: unmet=food -> `createUnlockQuestIfAbsent` posts
   `baker_qt_extract` (the dedupe `openJobUnlockQuestExists` sees the
   crafter one as lost) ... repeat forever.

## The handle-accumulation half

`markRecipeAsLostByKey` marks the quest lost but **the quest entry stays in
the batch** and `VillageGrowthExecutor`'s KNOWN probe counts every
`!isComplete()` entry as open. Within two minutes the flag's open list held
**107 x `baker_qt_extract` + 107 x `crafter_qt_stick`** (up ~10 every
couple of ticks). Each post also fires `notifyNewQuests` ->
"New quests are available!" broadcasts every few ticks (the 3-bursts in
the log).

## Impact

- The quest handle is polluted; any gate or UI that counts open quests
  sees ~200 phantom quests.
- Chat spam for the player.
- Masks the real problem (townie can't find food/a table) in the noise.

## Fix directions (unvalidated)

- Dedupe on the *root*, not the producer: one open/lost JOB_UNLOCK per
  stuck root, re-evaluated in place instead of post-lose-post.
- Or: remove lost JOB_UNLOCK quests from the batch instead of marking
  them lost (if the lost state is not needed elsewhere).
- Or: stop the oscillation at the source — make `getUnmetItem()` stable
  for a work cycle instead of flipping with the state.
- The stuck-root situation itself (gatherer UNMET food with no dining
  table) may also be a fixture/pacing gap worth looking at separately.

## Repro

Held agent-drive `village/growth` on the 1.20.6 branch with the new
day-1 beat order; let a gatherer sit without a dining table past
`UNMET_UNLOCK_QUEST_GRACE_TICKS`. Watch for the post/quest_lost pairs in
the `Questown:Quests` log and the growing open list.

## Triage notes

`wontfix` = terminal, no further action: the fix shipped in `8f670d10`. Kept as
the investigation + design record. Live verification of the fix is owed
by the next held `village/growth` run (the post-batch section of
`commands/test/COMPLEX.md` tracks that).

## Resolved (2026-10-11)

Fixed with the **per-root dedupe** direction (first candidate above), at
the quest-handle seam, not the source: `getUnmetItem()` flipping between
work states is left as-is (the oscillation is the town's honest statement
of its best-scoring gate reason), and the handle now absorbs it.

- `TownQuests.reconcileUnlockQuests` (pure, static): one open JOB_UNLOCK per
  stuck root; retarget in place when the root's missing item flips producer;
  withdraw when the root stops mapping to any viable producer. Town-wide
  (top-need) quests keep the legacy keep/withdraw rule.
- The stuck root is encoded on the quest via
  `MCQuest.setUnlockStuckRoot` → `fromRecipeID` (NBT `from_recipe_id`), so
  the dedupe survives a flag reload.
- Withdraw replaces `markRecipeAsLostByKey` (removed): `QuestBatch
  .withdrawQuest` removes the quest outright and notifies the listener; the
  now-empty JOB_UNLOCK batch is declined from the handle. The old path's
  `lost()` placeholder (an ACTIVE room-typed phantom) is gone for this type.
- `MCQuest.retargetUnlock` swaps the producer wanted-id + flavor in place
  (type-guarded).

Pinned by `TownQuestsJobUnlockTest` (22 tests, green): the reconcile
decisions, the stuck-root NBT round-trip, retarget, and withdraw plumbing.
Machine-side only — the held `village/growth` re-run to roster ≥ 5 (which
would verify it live and also exercise bug 2, still open in
`day1-quests-reopen-after-batch.md`) has not run yet.

The stuck-root situation itself (gatherer UNMET food with no dining table)
remains a separate fixture/pacing question, noted above.
