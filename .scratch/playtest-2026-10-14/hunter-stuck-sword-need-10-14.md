---
title: 'Hunter soft-locked: "no place to work" with swords + cooked food available; econ still lists sword need; job-change command had no effect'
status: fixed
---

# Hunter stuck + sword need not clearing — 2026-10-14 playtest

Captured 2026-10-14 (server-20, in-game chat 04:43:44–04:47:21):

> "hunter stopped working for some reason..."
> "Said 'has nowhere to work', but there are swords and cooked food
> available..."
> "Econ tab says they need swords, but there are swords in chest in
> registered rooom..."
> "Tried changing job by command. No difference."

Cluster of three observations on one world:
1. The hunter's status says NO_JOBSITE/nowhere-to-work while its job's
   inputs (swords) and food are present in registered rooms. Dump the
   decision that assigns the status (`questown-status-why` pattern) on a
   repro: which gate (job site? tool? supply?) is failing and why.
2. The econ tab still lists the sword need even though swords sit in a
   registered room's chest — the need's count isn't seeing that chest
   (room not registered? chest not in the town containers? need keyed to
   a different sword item?). Recurrence of
   `.scratch/day1-playtest-2026-10-11/econ-tab-satisfied-need-lingers.md`
   (swords lingered 5 days there).
3. "Changing job by command" had no visible difference — identify which
   command was used and whether it targets the pending-root vs the
   applied-root lane (a command that sets the applied root without
   clearing the pending one, or vice versa, would look like "no
   difference").

The playtest world is backed up (`~/playtest-world-2026-10-14`); this
repro is safe to run through on a held server.

## Root cause (2026-10-14 night, static read of server-19/20 logs + code)

`TownHasSupplies.doPopulate` collected "relevant tools/ingredients" only from
states that had a **workable** block (`RoomsWithWorkableStatefulBlocks.apply`
true). The hunter's mat was parked at its terminal (extractable, state 3 of 3)
state, so **no** state was workable → the sword tool-requirement was excluded
from the check → `town has supplies` = **false** even though a `stone_sword`
lay in the village chest. The status circuit's "get work supplies" branch
(its only gate is `dTownHasSupplies`) was therefore dead, and the hunter sat in
NO_JOBSITE with `hasWorkItems=false` until the world was reset.

Live signature from the gate dumps (both servers):
`gate[TODO=false] (No matches found for … in any containers)` with
`relevant tools = []` — the required set was empty by construction, not because
the chest was empty. `#questown:swords` does include `stone_sword`, and the
town's supply-check containers did list the chest; the item was never
considered because the requirement was filtered out before the container scan.

The second link (the actual hand-off) was verified clean, not the culprit:
`cleanRooms()` derives from `roomsNeedingIngredientsOrTools.cleanFns(...)`, and
the rooms-needing-input chain normalizes a terminal mat
(`processingState == maxState && !hasWorkLeft() && timeToNextState == null`)
to `State.fresh()` — so the gate room still reports the sword as a need and the
supply getter can reach the chest. The gate, not the hand-off, was the
bottleneck.

## Fix

`TownHasSupplies.doPopulate`: after the workable-state collection pass, if both
the ingredient and tool lists are empty **and** at least one state exists, fall
back to collecting the **full** requirement set (all states) so the town can
still acquire the tool needed to resume the cycle. Pinned by
`TownHasSuppliesNoWorkableStateTest` (3 cases: workable-state baseline, the
no-workable-state deadlock, and the no-requirements control).

## Observations 2 & 3 (econ need / job-change) — resolved as consequences

- **Econ "need swords"**: the need is keyed to the *held* tool, not the chest.
  With the hunter unable to re-take the sword, the stage-0 tool requirement
  stayed unsatisfied → the econ tab correctly (if confusingly) kept listing it.
  This is the same root cause, not a separate room-registration bug.
- **Job-change "no difference"**: changing the root does not clear the stuck
  work-state on the mat; the status is block-state-driven, not job-driven, so a
  re-root with the same stuck mat looked like a no-op. Same root cause.

FIXED <pending-commit>
