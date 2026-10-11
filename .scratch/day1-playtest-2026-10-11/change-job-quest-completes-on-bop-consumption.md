---
title: "Change to <job>" quest should complete the moment the BOP is consumed, not when the job change lands
status: ready-for-human
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
