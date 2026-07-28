---
title: updateWorkStatuses is 64% of flag-tick time and the main stutter source
status: ready-for-human
created: 2026-07-27
resolved: 2026-07-27
priority: p1
---

## Outcome

Fixed. `updateWorkStatuses` went from **62% of flag-tick time to 4%**, and the town-wide tail
collapsed with it (`perf/town_large`, 20 townies / 16 rooms, `FlagTickInterval = 10`):

| metric                     | before  | after  |
|----------------------------|---------|--------|
| `updateWorkStatuses` avg   | 5314us  | 138us  |
| `updateWorkStatuses` p95   | 10148us | 226us  |
| `updateWorkStatuses` max   | 16509us | 352us  |
| whole flag tick avg        | 862us   | 387us  |
| whole flag tick p95        | 7948us  | 1699us |
| whole flag tick p99        | 14668us | 2704us |
| whole flag tick max        | 29562us | 5955us |

The acceptance target (flag-tick p95 back under ~2ms) is met.

## What it actually was

Not the room walk, and not block lookups. Sub-phase timing inside the scan showed **all** of it in
one call: `defaultStateFactory` — i.e. `ServerJobsRegistry.shouldInitializeWithState` — at ~9.3us
**per block position**, with `airCheck` and the cascading-block revealer at ~0us.

That method answers "does any job care about this block?" by walking every registered job. Two
things made that catastrophic:

1. **`Works.values()` rebuilt Work objects on every call.** The five hardcoded jobs were stored as
   method references (`ExplorerWork::asWork`, the four gatherer variants), so every `v.get()`
   constructed a fresh `Work` — ingredient maps and all — *per block position, per store, per flag
   tick*. Datapack jobs were already shared instances; these were not.
2. **There is one cook job per cookable item.** So "walk every registered job" is a walk over a
   list that grows with the item registry, per position.

## Fixes applied

1. `Works.staticInitialize` wraps the hardcoded suppliers in `builtOnce(...)` (Guava memoize), so a
   Work is built once per datapack load like the datapack jobs already were. (5314 → 4484us.)
2. `ServerJobsRegistry.shouldInitializeWithState` caches its answer keyed by
   **(block state, is-the-block-above-air)**. Those two are the only inputs every registered
   `shouldInitializeWorkState` predicate reads — verified across the JSON loader (`shouldInitWS`
   reads `p` and `p.above()` only), the special jobs (they are handed a `BlockInfo` that returns
   the same state for any position), and the hardcoded works (`isBed`/`isFlag`/`isBlock`, all
   state-at-pos). A predicate that inspects any other neighbour must widen the key — this is
   stated in the javadoc. Cache is cleared from `Works.staticInitialize` via
   `forgetWhichBlocksJobsCareAbout()`, because the answers depend on the registered jobs.
   (4484 → 190us.)
3. The **decay bug below** is fixed: `AbstractWorkStatusStore.tick` now decays store-global timers
   exactly once per tick, whatever the number of new rooms.
4. Incidental: the per-room enclosed-position list (and its `posFactory` expansion) is computed
   once per room instead of per scan, and the room rotation no longer allocates a
   `rooms.toArray()` per tick. Both were noise next to (1) and (2) — measured, kept because they
   remove allocation from the hot path.

## The correctness bug found alongside (fixed)

`doTick` both decayed store-global timers and scanned one room, and was called once per
newly-seen room. Registering N rooms in a tick decayed every timer by `N x ticksSinceLast`, so
timed states (gatherer quarter/half/full-day waits, the leaver `NEED_ROAM` tail) expired early in
proportion to how many rooms appeared at once.

`tick` is now: register new rooms → decay timers once → advance blocks whose timer expired → scan
new rooms → scan the next room in rotation. Regression test:
`WorkStatusStoreTest.Test_TimerShouldDecayOncePerTickRegardlessOfHowManyRoomsAreNew` (5 rooms
appear in one tick; a 10-tick timer must read 9, not 5).

## What is left

`roomsHandle` is now the largest phase (219us avg, 57%) and `updateStoredData` the largest spike
(6.6ms max). Neither was in scope here. See
[[possiblework-spikes-to-7ms-behind-a-0us-p95]] for the other tail.
