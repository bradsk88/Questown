---
title: Town-leaver workers return empty at night instead of staying out until their timer ends
status: needs-triage
created: 2026-09-20
---

## Context

Observed in a held village/growth run (2026-09-20, with the no-warp-production
gate already in place). A hunter (a town-leaver job: the worker "leaves town"
to an imaginary hunt, no real sheep involved) was watched through the new
`inspect villagers` status line:

1. Morning: `hunter/sword`, `WAITING_FOR_TIMED_STATE`, carrying wooden sword.
2. Midday: `GOING_TO_JOB`, had upgraded himself to the stone sword from the
   storeroom chest.
3. Evening: back in town, empty-handed, `WAITING_FOR_TIMED_STATE` then
   `RELAXING` (`hunter/seeking_work`). No mutton.
4. Night: downtime. Next morning: `hunter/sword`, `NO_JOBSITE`, empty hands,
   no loot-grab observed.

The dev's suspicion (their words): an old bug where night-time causes
town-leaver workers to return without loot, then in the morning they grab
the loot from the flag. They should stay gone until their work timer runs
out. The "morning loot-grab from the flag" half was NOT observed in this
run; instead the hunter sat at `NO_JOBSITE` in his sword phase with a
usable sword (Damage: 1; hunters ignore damage until the tool breaks) in
the registered storeroom chest. That stall may be part of the same bug or
a second one.

## Acceptance criteria

- A town-leaver (e.g. hunter) who is out of town when night falls stays
  gone until his work timer completes, and returns with the job's loot.
- An autotest (or held-run script) that sends a leaver out, advances to
  night mid-trip, and asserts he does not reappear in town empty-handed
  before the timer ends.
- The morning-after state is coherent: the hunter re-arms from the
  storeroom and resumes; no persistent `NO_JOBSITE` stall while a valid
  sword is in a registered container.

## Notes

- Reproduced on the branch with the wall-clock warp gate, so warp
  production is not masking the behavior anymore; this is the real
  leaver-day cycle.
- The `status:` line in `_qtdev inspect villagers` (added this session)

## Confirmed mechanism (2026-09-20, new `progress:` instrumentation)

A new `progress:` line in `_qtdev inspect villagers` shows the leaver's
job-block state and work/time ticks left (e.g. `progress: job block
1,64,0 state=2 time-left=3640`). With it the recall is definite, not
likely:

- day: gatherer `gather_half_day` `WAITING_FOR_TIMED_STATE`, `state=2
  time-left=3640`
- `time set 35500` (evening): same gatherer flips to `RELAXING` with
  `state=2 time-left=3190` still remaining

The evening signal recalls the leaver while ~3200 ticks of work are
left on the timer. The timer keeps counting down (3640 -> 3190 across
the wall-clock gap) but his status is yanked to RELAXING, so he
reappears in town with the trip unfinished.

Root cause: `ProductionStatuses.getNewStatusFromSignal` on
EVENING/NIGHT calls `getEveningStatus`, which unconditionally moves the
villager to `droppingLoot` (carrying) or `relaxing` (empty) regardless
of current status. It never consults `isUnfinishedTimeWorkPresent` /
the leaver's remaining timer. Only the *morning* path
(`JobStatuses.productionRoutine`) holds a `WAITING_FOR_TIMED_STATE`
villager out while `town.isUnfinishedTimeWorkPresent()`; the evening
path has no such branch. That asymmetry is the bug.
  made the cycle visible; use it for the repro.
