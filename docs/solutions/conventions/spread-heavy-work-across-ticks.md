---
title: "Spread a heavy pass across ticks — resumable queue + per-tick pump + a bound"
date: 2026-08-23
category: docs/solutions/conventions/
module: town / roomrecipes
problem_type: convention
component: perf_throttle
severity: medium
applies_when:
  - "A single computation would cost more than one game tick if done at once"
  - "The work can be split into independent units (a door, a job, a root) that don't need to finish in one call"
  - "You need the work to resume on a later tick instead of hitching the server"
tags: [perf, throttle, tick-spread, incremental, budget, room-detection, possiblework, resumable]
---

# Spread a heavy pass across ticks — the idiom

**Rule:** when one computation would cost more than a single game tick, spread it across
ticks with the idiom below. Do **not** invent a new scheduling mechanism — reach for this
one (or the existing instance of it) and choose the right *bound*.

## The idiom (four parts)

1. **A resumable queue** — the work is broken into units (a door, a job, a root) held in a
   queue that survives between calls.
2. **A per-tick pump** — each game tick runs *some* of the queue, then stops.
3. **Occupy-the-tick-while-in-progress** — while the pass is running, the ticker skips the
   rest of that tick's phases (`if (inProgress) return;`) so the pass gets a full tick, then
   resumes later.
4. **A bound** — a cap that decides *when the pass is done* for this tick. This is the one
   part that varies; see *Choosing the bound*.

## The two existing instances

Both use the idiom; they differ in the bound, the queue shape, and the trigger — **that is
expected**, not an inconsistency to erase.

| | Room-scan | possibleWork |
| --- | --- | --- |
| **Class** | `LevelRoomDetector` (RoomRecipes) + `MultiLevelRoomDetector` (Questown) | `TownPossibleWork` |
| **Context** | **debug / test** only | **production** |
| **Queue** | flat per-door (`doorsToProcess`); `MultiLevelRoomDetector` re-queues per-level | nested per-root → per-job (`rootsAwaitingRecompute`, `RootScoring`) |
| **Bound** | **count** — `maxIterations` / `Config.MAX_ROOM_SCAN_ITERATIONS` | **time** — `RECOMPUTE_NANOS_PER_TICK = 500_000` ns |
| **Pump** | `Supplier<Boolean>` task pumped by `AbstractTownFlagTicker.runDebugTask()` | inline in `TownPossibleWork.tick()` via `spendTickBudgetOnRecompute()` |
| **Trigger** | `startDebugTask(...)` from `DebugAllDoorsCommand` / `TownDoorTestItem` | `invalidate()` arms `shouldRecompute`; a pass starts on `buffer % WORK_PRECOMPUTE_FREQUENCY == 0` |
| **Doc** | — (the engine lives in the adjacent RoomRecipes repo) | `town/COMPLEX.md` — "TownPossibleWork — a recompute pass spread across ticks" |

**Read the *Context* row carefully.** The room-scan tick-spread is a **debug / test** helper.
The *production* room detection is **not** tick-spread — it is synchronous. The two use the
same engine but in different ways:

- `LevelRoomDetector.proceed()` (RoomRecipes) is the **resumable, count-bound engine** — one
  door per call, re-queue the rest, return `null` until the queue drains.
- `LevelRoomDetection.findRooms()` (RoomRecipes) is the **production** path: a tight
  `for (int i = 0; i < 2000; i++) proceed()` loop that runs the whole scan **in one call** —
  *no tick spreading*. This is what normal room registration uses.
- `MultiLevelRoomDetector` (Questown) wraps the engine across scan levels and is only
  reached through the **debug / test** pump (`getDebugTaskForAllDoors`, `TownDoorTestItem`).

So the **tick-spread idiom has one production instance** (`TownPossibleWork`) and **one
debug/test instance** (the room-scan debug task). The shared thing is the *concept*, not a
mechanism — the two pumps are different (the single-slot `debugTask` vs inline), and they
don't even coexist: `runDebugTask()` is checked in `tickInner` **before** `possibleWork`, so
a running debug task preempts the rest of the tick.

## Choosing the bound (the only real decision)

- **Count bound** (like `maxIterations`) when the units are *uniform and countable* — N doors,
  N rooms. "Process N units per tick" is a stable, predictable cap.
- **Time bound** (like `RECOMPUTE_NANOS_PER_TICK`) when *per-unit cost varies* and you want to
  bound **wall-clock** cost regardless of how many units — e.g. one job's container scan can be
  far heavier than another's, so counting jobs doesn't bound the tick; measuring nanoseconds does.
  The budget is deliberately a fraction of the 50 ms server tick (500 µs = "a tenth of a
  visible hitch"), not a config knob.

Pick the bound by "is one unit's cost roughly constant?" — if yes, count; if it varies and you
must protect the tick, time.

## Why not a shared abstraction

Do **not** extract a common base / helper for these two. The differences above (count-vs-time
bound, queue shape, trigger, and now context) are *meaningful*, not incidental — a shared base
would force the bound to unify, which is the wrong trade-off. This follows the repo's stated
stance (`docs/project-qa.md`: "Too many abstractions — hard to trace code flow") and the
resolved farmer/cook consolidation, which consolidated only via a *clean* seam
(`QTWorldAccess`). The project *does* standardize on a shared pattern when the use case is
uniform — see `docs/decision-proportional-deltas.md` (all warp hooks take `tickDelta`) — but
these two aren't uniform, so a shared class would be a leaky abstraction.

**The consistency win is discoverability, not a shared class.** This note (plus the instance
docs) is enough to stop a third divergent variant from being invented. If a *third* instance
appears, re-check whether the bound choice was right before adding it.

## When to reach for this idiom

Use it when all hold:
- one pass costs more than a tick, **and**
- the pass splits into independent units that don't need to finish in one call, **and**
- the result can be slightly stale (the pass finishes a few ticks late), **and**
- it is a **production** cost (for a debug / test-only path, a tight synchronous loop like
  `LevelRoomDetection.findRooms()` is fine — don't spread what only runs on demand).

Don't use it when the units aren't independent (a pass that must run atomically), or when the
whole thing fits in the tick anyway (then a plain `FlagTickInterval`-style skip-throttle is
simpler — see `town/COMPLEX.md`).
