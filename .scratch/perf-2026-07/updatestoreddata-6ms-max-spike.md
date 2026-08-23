---
title: updateStoredData has a 6.6ms max spike in the flag tick
status: wontfix
created: 2026-08-21
updated: 2026-08-22
priority: p2
---

## Context

Recorded as an open lead when `updateWorkStatuses` was fixed (see
[[updateworkstatuses-is-64pct-of-tick-time.md]]): with that phase down to 4% of
flag-tick time, `roomsHandle` is now the largest phase (219us avg, 57%) and
`updateStoredData` is the largest remaining spike (**6.6ms max**) in
`perf/town_large` (20 townies / 16 rooms, `FlagTickInterval = 10`).

Not yet investigated — no sub-phase timing exists for either.

## Acceptance criteria

- Sub-phase timing identifies what inside `updateStoredData` produces the 6.6ms spike.
- Flag-tick max drops accordingly, or the spike is shown to be unavoidable/cheap enough to ignore.

## Decision: wontfix — cheap enough to ignore (AC branch 2) — 2026-08-22

Evaluated in a `/grilling` session rather than fixed. The 6.6ms is **not a steady cost**
(avg ~96us, max 6.6ms — a ~70x gap) and **not a one-time startup artifact either** — it is the
*offline-production warp* on return. The second AC branch ("shown to be cheap enough to ignore")
is the resolution; no sub-phase timing was added.

**What the 6.6ms is.** `updateStoredData` (= `TownFlagState.tick`) has two paths: a throttled,
bounded **container scan** (~187us, every `ContainerScanInterval`) and a **warp** branch that
fires when the town is "asleep" (`waking = !initialized || timeSinceWake > 10`). The warp
fast-forwards the whole town's production over the elapsed absence (`advanceTime` →
`WarpWorldAccess`, plus `recoverMobs`/`restoreWarpVisuals`/two container snapshots). That burst
is the 6.6ms; the container scan is not.

**Intent is settled by [[ADR-0006]]** — "Warp simulates what a town produced while the player
was away." So warp-on-return is the design, not a mis-gate.

**The code makes it *recurrent*, not one-time.** The flag is ticked every game tick
(unconditional `getTicker`), but `AbstractTownFlagTicker.tickInner` **returns early** while
`stopped` (player out of `TOWN_TICK_RADIUS`), skipping `updateStoredData` — so
`NBT_TIME_WARP_REFERENCE_TICK` is *frozen* while the player is away, the world's day-time keeps
advancing, and on return `timeSinceWake = (time away)` → `> 10` → **warp fires** → then
re-stamps the reference. Net: it fires **once per return-after-absence**, not once per load.

**Cost shape: scales with absence, capped.** `advanceTime` clamps the window
(`Math.min(ticksPassed, Config.TIME_WARP_MAX_TICKS)`). Short absences → cheap warp; the 6.6ms is
the *tail* — the longest (capped) absence.

**Why it's acceptable.** It is a **single one-tick** cost (~13% of one 50ms tick) that lands
exactly when the player returns, when the player is already back in the world — not
player-visible.

**Why `perf/town_large` misleads.** The arena keeps the flag active continuously (no real
away→return), so only `!initialized` trips the warp → the 6.6ms appears as a *single startup
spike* in that harness. That is a *measurement* artifact of the perf scenario, not of real play.
The real per-return cost cannot be measured with the current harness — it would need an
away→return scenario (the flag's `stopped`/`storeInactiveState` gate).

**Caveat (not a blocker).** This conclusion is *structural* (reasoning from ADR-0006 + the
code), not a real-play measurement. Revisit if the warp grows heavier, or if an away→return
scenario is ever added and shows a return stutter. `roomsHandle` (219us avg, 57% of tick — an
*average*, not a spike) remains the open perf lead; see [[../perf-2026-07/HANDOFF.md]].
