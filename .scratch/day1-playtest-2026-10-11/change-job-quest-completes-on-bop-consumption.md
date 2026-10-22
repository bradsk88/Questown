---
title: "Change to <job>" quest should complete the moment the BOP is consumed, not when the job change lands
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:24:18 (evening from-zero run, `/tmp/dev-server.log`):

> I'm starting to think that the "change to hunter" quest should get completed
> as soon as the BOP is consumed for the job change. I think its more satisfying
> for the player.

The job-change quest (e.g. "change to hunter") currently completes when the job
change actually takes effect (scheduled now and applied at the morning —
`scheduleJobRootChange` → `handleMorning`). The player finds it more satisfying
if the quest completes **the moment the BOP is spent** on the job change, rather
than waiting for the overnight application.

## Acceptance criteria

- The "change to <job>" quest marks complete when the player consumes the BOP
  for the job change (the spend moment), not when the job actually changes in
  the morning.
- The job change still applies on its normal schedule (morning); only the quest
  completion timing moves earlier.

## Notes

Design decision — get the player's buy-in on the timing. Find where the
job-change quest is marked complete (the `JOB_CHANGE`/root-change completion
path, likely tied to the job actually changing) and move it to the BOP-consumption
moment (`JobRootChangeMessage` / `BopTransactionSyncer.syncConsumedBOP`). Think
through the edge: a blessed (gold) BOP guarantees a specific root, and the
morning quest override can still apply — does completing the quest early create
a mismatch if the root the player picked doesn't land? Probably not (the quest
is "you changed the job", the root is a sub-detail), but confirm. Ties into
`docs/design-principles.md` (reward-on-the-spend-moment, if applicable).

## Decision (2026-10-13, walkthrough E1)

Roll the selection **at the BOP-spend moment, apply at morning**; the
`JOB_CHANGE` quest completes at the spend moment. Revised from "just move
quest completion" after the user's point: the roll itself should happen
upfront so the outcome is settled when the quest ticks.

Investigation (in main tree, 2026-10-13):
- Spend: `JobRootChangeMessage.handle` reads the blessed guarantee off the
  consumed BOP → `SimpleVillagerHandle.scheduleJobRootChange` stores the
  guarantee (or clears it), sets `jobChangesPending`, broadcasts "changes in
  the morning".
- Morning: `handleMorning` → `changeJobRootNow` does the actual roll:
  (1) blessed guarantee wins, (2) `overnightJobOverride()` beats the random
  roll, (3) random shuffle of `ServerJobsRegistry.getAllRootJobs()` minus the
  current root.

**The strong morning reason, confirmed:** `TownQuestsHandle.overnightJobOverride()`
returns `hunter/sword` while the town is in the
hunter/gatherer tutorial (`quests.isWaitingForHunterGathererTutorial()`) —
a story beat that can only be consulted at dawn. It is *not* a reason to
roll at dawn: it only needs to **win** at dawn.

Design:
- At spend: compute the pending root — blessed → that root; otherwise the
  random roll from the registry roots minus the current root (both fully
  computable at spend; the candidate set is static) — and store it.
- At morning: priority unchanged — stored blessed root wins; else the
  tutorial override; else the pre-rolled random root. Keep the existing
  dawn roll as a fallback only when no pending root is stored (migration /
  edge).
- `JOB_CHANGE` quest marks complete at the spend moment.

Shipped (2026-10-13, e8178b5a): implemented per the walkthrough E1 decision - the
roll happens at spend (blessed guarantee, else pre-roll from the registry roots
minus the current root, persisted in `pre_roll_roots` NBT) and the matching open
JOB_CHANGE quest completes at the spend moment
(`TownFlagBlockEntity.requestJobRootChange` ->
`TownQuests.markJobChangeCompleteAtSpend`). Morning priority unchanged: blessed >
tutorial override > pre-roll > dawn re-roll. Full unit suite 1175/0. Left
ready-for-human: the "more satisfying" read is a player-feel check for the next
playtest.

Open for implementation: locate where the `JOB_CHANGE` quest is currently
marked complete (not yet found — next step); persist the pending root in
NBT if the morning can be skipped across a save/reload (check whether
`jobChangesPending` already persists).

## Resolved (audit 2026-10-22)

Decision E1 shipped: `TownQuests.markJobChangeCompleteAtSpend` completes the JOB_CHANGE quest at the BOP-spend moment (the roll happens at spend, applies at morning); covered by `TownQuestsJobChangeSpendTest` (spend-completes).
