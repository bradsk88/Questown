# Warp carries two timelines: productive for labour, wall-clock for passive world processes

status: accepted
date: 2026-06-03

## Context

Warp simulates what a town produced while the player was away. Since the
*Warp system rewrite (#283)*, the elapsed window is collapsed to a single
"productive" tick count via `Signals.calculateProductiveTicks`, which subtracts
night (work stops after `PRODUCTIVE_DAY_END_TICK = 11500` each day). That single
count then drove the **entire** warp loop.

That conflated two different things. On the realtime path:

- **Villagers** stop working at night (`SimpleVillagerHandle`, `signals != NIGHT`).
- **The world** does not. Crops random-tick, furnaces smelt, and saplings age
  24h a day.

So routing passive world effects (`onWarpTick`: `GrowCropsWarpRule`,
`SmeltFurnaceWarpRule`, `GrowTreesWarpRule`) through the *productive* count
under-grew everything by the night fraction of the window — and an all-night
absence early-returned before any passive effect ran at all (Gap A in the plan).

> **Terminology.** The round-the-clock budget is called **wall-clock** (not
> "real") deliberately: "real" collides with the **realtime path** (the live,
> player-present execution path) already in `CONTEXT.md`. Wall-clock is a
> *budget/duration* axis (day-only vs round-the-clock); realtime is an *execution
> pathway* axis (live vs offline-catch-up). Different axes — don't conflate.

## Decision

The warp loop carries **two timelines**:

| Timeline | Drives | Source |
|----------|--------|--------|
| **Productive** (`productiveTicks`) | villager labour scheduling (unchanged) | `calculateProductiveTicks(dayTime, wallClockTicks)` |
| **Wall-clock** (`wallClockTicks`) | passive `onWarpTick` effects | the pre-collapse, `MAX`-clamped elapsed |

`AbstractAdvanceTime.advanceTime` schedules labour on the productive `stepTick`
exactly as before, but maintains a parallel **wall-clock cursor**. For each
passive callback it maps the productive offset → wall-clock offset
(`Signals.ProductiveWallClockTimeline.wallClockOffsetAt`) and passes:

- an **absolute** wall-clock `currentTick` (`currentTick + wallClockOffset`) — so
  `plantTick`/age references stay coherent and seeded-sapling timing is preserved;
- an **offset-based** wall-clock `tickDelta` that telescopes to `wallClockTicks`
  across the whole window — so crops/furnaces are credited the night.

A trailing passive call covers the leftover night (and the whole window when an
all-night warp produced no labour steps), so passive effects run even when
productive ticks alone would have stopped at dusk.

**Night placement is precise, not smeared.** Nights sit at their true clock
position via a small day/night segment list (O(days); the warp is capped at
`Config.TIME_WARP_MAX_TICKS`). This matters because the grow knobs were set small
on purpose (`TREE_GROWTH_TICKS = 2000`) so a plant→grow→chop chain completes in a
single warp. A uniform smear would thin each night out and slip the chop a cycle.

## Considered and rejected

- **Smear night uniformly across the window.** Simpler mapping, but breaks the
  dependency chains the small grow knobs depend on (a dusk-planted sapling would
  not be credited the whole night before the next work cycle). Rejected for the
  precise segment list.
- **Run passive effects as a separate post-warp pass on `wallClockTicks`.** Loses
  the interleaving: a farmer's harvest-then-regrow mid-warp would no longer see
  the passive growth that happened between its own labour steps. Rejected — the
  wall-clock knowledge must live inside the loop.
- **Put all of the warp (labour included) on the wall clock.** Would resurrect
  the night-labour bug the #283 rewrite fixed (villagers working at 3am).
  Rejected; labour stays on the productive budget.
- **Name the round-the-clock budget "real".** Rejected — collides with the
  **realtime path** term; "wall-clock" names the meaning (time elapses through the
  night) without the overload.

## Consequences

- `advanceTime` gains two params (`wallClockTicks`, a productive→wall-clock
  `LongUnaryOperator`); the early-return guard is now on `wallClockTicks`, and the
  villager labour loop is skipped when `productiveTicks <= 0` (so an all-night
  window runs passive effects with no labour).
- The rule classes are unchanged — `GrowCropsWarpRule`/`SmeltFurnaceWarpRule`
  read `tickDelta`, `GrowTreesWarpRule` reads `currentTick`; they simply receive
  correct values now.
- The dual-timeline shape threads through the loop and is hard to reverse; this
  ADR records why labour and crops use different tick counts.
- Relates to ADR-0002 (warp does not model hunger) and ADR-0005 (trees grow in
  warp). Gap B (`WORK_IN_EVENING` jobs cut short at `PRODUCTIVE_DAY_END_TICK` in
  warp) is a separate, deferred follow-up.

## Verification

- `SignalsTest` — exact assertions on `buildProductiveWallClockTimeline` /
  `wallClockOffsetAt` (day-only, opening-night, night-in-middle, pure-night,
  multi-day), cross-checked against `calculateProductiveTicks`.
- `AbstractAdvanceTimeTest` (actual `advanceTime`, no simulation) — passive
  `tickDelta` totals `wallClockTicks` while labour stays on `productiveTicks`; the
  night between a day-1 and day-2 labour step is credited (dependency-chain
  timing); an all-night window runs passive effects with no labour.
