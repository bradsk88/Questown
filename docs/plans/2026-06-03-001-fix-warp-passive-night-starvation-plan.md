---
title: Passive world processes should run during night in warp
status: implemented
date: 2026-06-03
supersedes-note: Corrects the stale "warp ignores night" framing — night subtraction already exists; this fixes the gap it left.
implementation-note: |
  Implemented 2026-06-03. Note: the round-the-clock budget is named "wall-clock" (not "real") to
  avoid collision with the existing "realtime path" term — see CONTEXT.md / ADR-0006.
  Signals.ProductiveWallClockTimeline + buildProductiveWallClockTimeline/wallClockOffsetAt;
  AbstractAdvanceTime.advanceTime gained (wallClockTicks, productiveToWallClock) with a wall-clock
  cursor and trailing-night call; TownFlagState passes both timelines and no longer early-returns at
  night. Decoupled the two callback params: passive currentTick is ABSOLUTE wall-clock
  (currentTick+wallClockOffset, preserves seeded-sapling/tree parity), passive tickDelta is
  OFFSET-based (telescopes to wallClockTicks, fixes crop/furnace night undercounting). See ADR-0006.
  Verified by SignalsTest (+6) and AbstractAdvanceTimeTest seam tests (+3, actual advanceTime). Gap B
  still deferred.
---

# Passive world processes should run during night in warp

## Problem (and what is NOT the problem)

The note "warp ignores night" is **stale**. Night subtraction already
landed in the *Warp system rewrite (#283)*:

```java
// TownFlagState.advanceTime (~line 171)
ticksPassed = Signals.calculateProductiveTicks(dayTime, ticksPassed);
if (ticksPassed <= 0) {            // line ~172
    QT.FLAG_LOGGER.info("Time warp contained no productive ticks (all nighttime)");
    return storedState;
}
```

`calculateProductiveTicks` collapses the elapsed window down to the
`PRODUCTIVE_DAY_END_TICK = 11500` cutoff each day. Covered by `SignalsTest`.

**The real bug (Gap A):** that single collapsed `ticksPassed` then drives the
*entire* warp loop — including the world-level `onWarpTick` effects that should
keep running at night. In realtime, crops grow (vanilla random ticks) and our
processing blocks advance (per block-entity tick) **24h a day**; only the
*villagers* stop at night (`SimpleVillagerHandle:139`, `signals != NIGHT`). So
in warp:

- **Crops** (`GrowCropsWarpRule`, `tickDelta`-based) and **furnaces/processing**
  (`SmeltFurnaceWarpRule`, `advanceProcessing(pos, ticks)`) are undercounted by
  the fraction of the window that was night (~half a typical day, more if the
  player left near dusk).
- **Trees** (`GrowTreesWarpRule`, gated on `currentTick - plantTick >= 2000`)
  count only productive ticks, so saplings under-grow at night too.
- **Pure-night absence** (player leaves at ~11,400 and returns before next
  morning): `calculateProductiveTicks ≈ 0` → the line-172 early-return fires →
  **nothing grows at all**, despite thousands of wall-clock ticks of crop/furnace
  progress that should have happened.

**Out of scope (Gap B):** `WORK_IN_EVENING` jobs (diner, rester, BOP depositor,
downtime) work until `NIGHT_START_TICK = 22000` in realtime but the global
`PRODUCTIVE_DAY_END_TICK = 11500` collapse cuts them short in warp. Real, but a
separate, smaller fix. Tracked as a follow-up, not addressed here.

## Approach (decided)

**Decouple passive effects onto a wall-clock-tick budget; leave villager labor on the
productive budget.** Villager labor scheduling (`ImportantTicks` / `Warper`)
stays exactly as it is — only the world-level `onWarpTick` stream learns the
wall clock.

We keep passive effects **interleaved** with labor steps (so a farmer's
harvest-then-regrow still works mid-warp), which means the wall-clock knowledge
must live inside the warp loop — it cannot be a separate post-pass.

**Night placement: precise (not smeared).** Nights are mapped to their true
clock position rather than spread uniformly across the window. This matters
because the grow knobs were set small **on purpose** (`TREE_GROWTH_TICKS = 2000`)
so that plant → grow → harvest completes in a *single* warp. A uniform smear
thins each night out, so a sapling planted at dusk would not be credited the
whole night before the next work cycle and the chop would slip a cycle (or off
the end of the window). Precise placement keeps those dependency chains working
as designed. Implemented as a small day/night **segment list** (the warp is
capped at `Config.TIME_WARP_MAX_TICKS`, so this is O(days) — tiny), reusing the
math already in `calculateProductiveTicks`.

### Two timelines

| Timeline | Drives | Source |
|----------|--------|--------|
| **Productive** (`productiveTicks`) | villager labor steps (unchanged) | `calculateProductiveTicks(dayTime, wallClockTicks)` |
| **Wall-clock** (`wallClockTicks`) | passive `onWarpTick` effects | the pre-collapse, `MAX`-clamped elapsed |

The warp loop executes labor on the productive `stepTick` as today, but
maintains a parallel **wall-clock cursor**: each time it fires the passive callback it
maps the productive tick → wall-clock tick, advances the cursor, and passes the *wall-clock*
`currentTick`/`tickDelta`. `setCurrentWarpTick(wallClockTick)` ⇒ `plantTick` becomes
wall-clock ⇒ trees grow across night too, coherently.

## Changes

1. **`Signals`** — add a productive→wall-clock mapping helper (sibling to
   `calculateProductiveTicks`). Given `dayTime` (window end) and `wallClockTicks`,
   build the day/night segment list and expose `wallClockOffsetAt(productiveOffset)`
   plus the wall-clock total. Pure, no MC deps.

2. **`TownFlagState.advanceTime`** — capture the pre-collapse, `MAX`-clamped
   value as `wallClockTicks`; keep `productiveTicks = calculateProductiveTicks(...)`.
   Replace the `productiveTicks <= 0` early-return with a `wallClockTicks <= 0` bail
   (passive must still run on an all-night window). Build the mapping and pass
   `wallClockTicks` + the mapper into `advanceTime`.

3. **`AbstractAdvanceTime.advanceTime`** — **minimal** addition: two params
   (`wallClockTicks`, productive→wall-clock mapper). Villager labor scheduling untouched.
   Maintain a wall-clock cursor; the passive callback and the trailing passive call
   receive *wall-clock* `currentTick`/`tickDelta`; the trailing call covers leftover
   night and fires whenever `wallClockTicks` remain (so the pure-night,
   zero-labor-step case still grows).

4. **Passive callback (`TownFlagState`)** — `setCurrentWarpTick` and
   `WarpTickHook.run` now receive the wall-clock tick/delta. No rule-class changes:
   `GrowCropsWarpRule`, `SmeltFurnaceWarpRule`, `GrowTreesWarpRule` already read
   `tickDelta`/`currentTick` and just get correct values.

## Verification (V-B)

1. **Unit — `SignalsTest`:** exact assertions on the new productive→wall-clock
   mapping (day-only windows, night-spanning windows, pure-night, multi-day).
2. **Seam — `AbstractAdvanceTimeTest`** (actual `advanceTime`, no simulation):
   - passive callback total delta == `wallClockTicks` while labor uses
     `productiveTicks`, over a night-spanning window;
   - dependency-chain timing: a thing "planted" at a day-1 labor step is mature
     by the day-2 labor step *because* the night between them is credited.
3. **In-game autotest** (existing infra — blueprint `startTimeTick` set into the
   night, `warpAmount` keeping the window dark): a simple passive witness
   (farmer crop age, or smelter output) grows where today's code produces none.
   This is the sharp before/after — it fails on current code.

Per CLAUDE.md: no simulation of core logic; the seam tests exercise the actual
`advanceTime`, and the in-game test exercises the actual warp path.

## ADR

Warrants **ADR-0006** ("Warp carries two timelines: productive for labor, wall-clock
for passive world processes"). It is hard to reverse (the dual-timeline shape
threads through the warp loop), surprising without context (a future reader will
ask why labor and crops use different tick counts), and the result of a real
trade-off (precise vs. uniform-smear night placement, decided for dependency
chains). Write it alongside the implementation; relates to ADR-0002 (warp does
not model hunger) and ADR-0005 (trees grow in warp).

## Follow-ups

- Gap B: `WORK_IN_EVENING` jobs cut short at `PRODUCTIVE_DAY_END_TICK` in warp.
- Update the stale memory note / any doc still claiming "warp ignores night".
