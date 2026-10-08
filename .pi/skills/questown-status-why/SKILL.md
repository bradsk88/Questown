---
name: questown-status-why
license: MIT
metadata:
  version: 0.1.0
  author: Brad Johnson
description: >-
  Diagnoses a Questown townie/leaver "stuck in state X" soft-lock by dumping the
  *decision* that assigns the status (the LZCD circuit's inputs and their nested
  detail maps), not the status name. TRIGGER when a townie/leaver is stalled,
  soft-locked, or not progressing in a status ("stuck in NO_JOBSITE", "leaver
  won't produce", "townie idles", "why is it in COLLECTING_SUPPLIES"), or when a
  held/autotest world stalls and you need the cause, not the symptom. SKIP when a
  unit test or existing autotest already settles it (use questown-unit-tdd-fix),
  for perf/lag (use questown-lag-repro), or for an MC version migration. The
  load-bearing rule: a status is the *output*, not the cause — go to the branch
  that sets it and dump its inputs. Worked example (2026-10-08): a leaver in
  NO_JOBSITE was stuck because its room set was EMPTY (spots={} rooms={}
  job_blocks={}), and the identical world advanced one beat later when it saw a
  workable spot (BlockPos{-13,64,10}) — the 50/50 was "does the room set contain
  a workable block", not the timeout the band-aid added.
---

# Questown "why is the townie stuck in this status"

When a townie/leaver is **stuck in state X** (soft-lock, no forward progress),
the status is the **output**, not the cause. Do not reverse-engineer the cause
from the status name (`NO_JOBSITE`, `COLLECTING_SUPPLIES`, …) or from adjacent
hypotheses (food, supplies, canClaim). Go to the **branch that assigns the
status** and dump its **inputs**, including the **nested/detail data** the
decision carries (room / spot / block sets).

## The one rule

> **"What status is it in?" is the symptom. "What predicate selected that
> status — and what were its inputs?" is the cause.** Dump the decision, not
> the status. A status name is a label, not a mechanism.

## The decision path (where the "why" lives)

`DeclarativeJobTicker` → `ProductionJournal.tryUpdateStatus` → `ProductionStatuses.getNewStatusFromSignal` →
(the MORNING/NOON branch) `JobStatuses.productionRoutine` → **`usualRoutine` →
`usualRoutineRoot`**, which builds an **LZCD** circuit. Each node is a status
with `conditions` (each a `WithReason<Boolean>` gate) and an `ifCondFail`
fallback; `root.resolve()` picks the status.

- Circuit: `src/main/java/ca/bradj/questown/jobs/LZCD.java`, `Populated.java`.
- Gates: `src/main/java/ca/bradj/questown/jobs/RoomsWithWorkableStatefulBlocks.java` (the "no spots found" / `spots`/`rooms`/`job_blocks` detail maps), `JobTownStates.java`, `JobStatuses.java`.
- A spot is **workable at state S** iff a block's `spotStatuses==S` **and** `spotJobBlocks` true. `spots={}` + `job_blocks={}` ⇒ the leaver sees **no room, no block** ⇒ `NO_JOBSITE`.

## Fire / read the WHY dump

The decision inputs are already logged (a WARN, dev-visible):

- `JobStatuses.maybeLogWhy` / `JobStatuses.collectWhy` — dumps every gate (boolean + reason) plus the nested `spots={…} rooms={…} job_blocks={…}` maps.
- `JobStatuses.whyDebugJobId` is set each recompute by `ProductionJournal` (L129) so the dump attributes to the **real** leaver.
- It gates to `gatherer:`/`hunter:` and throttles at `WHY_INTERVAL_MS` (4000 ms).

Read it from the world log:

```bash
grep -a -A 60 '\[WHY\] job=gatherer' <world>/stdout.log | sed 's/^\s*//' | head -70
```

The decisive line is the `town has workable blocks for ANY WorkState{…}` gate and its
`spots={…} rooms={…} job_blocks={…}` detail. `spots={}` ⇒ **empty room set** (the usual stall).
A populated `spots={pos=state}` whose state isn't the leaver's ⇒ state mismatch.

**Widen / unthrottle** if you need more jobs or faster cadence: lower
`WHY_INTERVAL_MS` in `JobStatuses`, or widen the root gate in
`JobStatuses.maybeLogWhy` (currently `label.startsWith("gatherer:|hunter:")`).

## Two traps that cost real time here

1. **The jobId is `"unknown"/"unknown"` in the resolver.** The 6-arg
   `usualRoutine` builds its context with `new JobID("unknown","unknown")`. A
   gate on `ctx.jobId().rootId()` never matches a real leaver — the probe
   compiles but **never fires** (WHY=0 despite 16 non-WAITING ticks). That is why
   `whyDebugJobId` is threaded from `ProductionJournal` instead. Before spending a
   world on a new probe, confirm its gate compares against the **live** value, not
   a constant.
2. **Read *nested* detail, not just the top gate.** The first dump version printed
   each gate's boolean but not its `spots/rooms/job_blocks` maps — the decisive
   "no spots found" was the *detail*, not the gate's value. `Populated` carries the
   detail maps; descend into them.

## Deterministic harness for the decision (no live world needed)

`src/test/java/ca/bradj/questown/jobs/JobStatusesTest.java` already drives this
circuit with fakes:

- `ConstTown(hasSupplies, hasSpace, canUseMoreSupplies)` — `canUseMoreSupplies`
  maps to `containsWorkableBlocksAtAnyState`.
- `TestStatus.FACTORY`, `ConstInventory`, `jobWithItemlessWork` / `jobWithItemWorkOnly`.
- Existing asserts cover the mapping: **no workable block + no supplies →
  `NO_JOBSITE`**; **supplies present, no jobsit e → `COLLECTING_SUPPLIES`**.

So the *decision mapping* is deterministically tested. It does **not** test why the
**live** room set is empty — that is the `roomsWithWorkableStatefulBlocks()` /
room-population layer (the open hole), not the status decision. Add a case there,
or a fixture for the room set, to pin the live cause.

## A/B discipline (a 50/50 pass is not a fix)

A single passing world in a flaky (N/N) stall is **luck**, not a fix, until you:
(a) isolate the variable — does it still pass *without* your change? — or (b) name
the specific decision predicate your change moved. A band-aid that "lets the stuck
state time out / give up / produce" is not the cause of a stall that is *never
finding a job site*. Report open-vs-confirmed, never "probably fixed", from one pass.

## Ordered loop

1. Confirm the stall + the status (server log `_qtdev inspect villagers <pos>`,
   or the held-run log). This is the *symptom*.
2. Fire + read the WHY dump (above). Find the failing gate + its `spots/rooms/
   job_blocks` detail. This is the *cause candidate*.
3. Decide which layer: (a) the **decision** mis-selected (→ `JobStatusesTest`
   deterministic harness), or (b) the **live room set** is empty / wrong (→ the
   room-population layer: `roomsWithWorkableStatefulBlocks`, `RoomsStatusLogic`,
   room registration). Instrument *that* layer next, not the status.
4. A/B before claiming a fix (isolate the variable or name the moved predicate).
