---
title: possibleWork spikes to 7ms behind a 0us p95
status: ready-for-human
created: 2026-07-27
updated: 2026-08-21
priority: p2
---

## Status: fixed, pending human review

After hoisting the per-state `Containers.get` scan to once per job (and guarding `bigLog`
behind `isDebugLogConsuming`), `perf/town_large` reports:

| metric              | before | incremental | hoisted |
|---------------------|--------|-------------|---------|
| `possibleWork` avg  | 62us   | 80us        | 53us    |
| `possibleWork` p95  | 2us    | 560us       | 514us   |
| `possibleWork` max  | 8197us | 4632us      | 760us   |

The max is now barely above the 500us tick budget, i.e. the residual is one job's
*first* scan plus a few cheap states — the repeated scans were the cost.

## What was done (original: incremental scoring)

`TownPossibleWork.recomputeNow` no longer does the whole pass in one tick. In `perf/town_large`:

| metric              | before | after  |
|---------------------|--------|--------|
| `possibleWork` avg  | 62us   | 80us   |
| `possibleWork` p95  | 2us    | 560us  |
| `possibleWork` max  | 8197us | 4632us |

The average is up and the p95 is up because the work is now spread over ticks instead of landing
in one — that is the intended shape. The max is what is still wrong.

## What was done

A recompute pass is now incremental state on the handle: a queue of roots, and within a root a
queue of its jobs (`RootScoring`). Each `tick()` scores jobs until a **500us budget** is spent
(`RECOMPUTE_NANOS_PER_TICK`), then resumes next tick. `recomputeNow()` (the warp path, which has
no ticks to spread across) drains the whole pass in place, as before. The rate-limit gate
(`WorkPrecomputeFrequency`) still decides when a *pass starts*, so ranking freshness is unchanged.

## Why the max is still 4.6ms

The budget is only checked **between jobs**, so it cannot bound a single job that costs more than
the budget — and one does. `getWorkPercentPossible` for one job:

- constructs a `Job` (`work.jobFunc.apply(UUID.randomUUID())`) and calls `dj.initialize(...)`;
- `getHighestPossibleState` then loops over the job's states and, for each state with ingredients,
  runs a full `Containers.get(...)` town-container scan, plus a `findMatchingContainer` for tools.

So the unit of work is "one job x all its states x a town-wide container scan each". At 16 rooms
that is milliseconds for a single job.

## Next steps (in order of expected payoff)

- ~~Hoist the container scan~~ DONE 2026-08-21: hoisted per job (the scan depends only on
  the job's location, which is constant across its states; cross-job caching was rejected
  because each job has its own predicate instances). `findMatchingContainer` (tools) was
  deliberately NOT hoisted — it scans every container in town, not just job-site ones, and
  `getHighestPossibleState` returns early on the first unmet state, so caching it could do
  strictly more work than today.
- ~~Make the scoring itself resumable per *state*~~ no longer needed; max is at the budget.
- ~~`bigLog(t, root, unfilteredJobs)`~~ DONE 2026-08-21: guarded behind the new public
  `TownFlagBlockEntity.isDebugLogConsuming(logId)` (toggled on, or INVISIBLE_LOG_LEVEL=trace).

## How to measure

```
./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.only=perf
```

Harness gotchas in [[HANDOFF]] — notably that `runServer` exiting 1 after `RESULT: N/N passed` is
the self-halting server, not a failure.
