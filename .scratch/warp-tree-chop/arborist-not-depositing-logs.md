---
title: Arborist cut_trees autotests fail: chopped logs never reach the chest (villager keeps them in inventory)
status: needs-triage
created: 2026-10-10
---

## Context

Unmasked by the `chopTreeInMemory` StackOverflowError fix
(see `chop-treememory-infinite-recursion.md` in this directory). While the
recursion crash was killing the server mid-scenario, `arborist/cut_trees`
never produced a verdict. Now that the warp completes, the scenario fails
with a real expectation failure:

```
jobs:arborist:cut_trees [FAIL] WARP: Some expectations failed
  Before: {minecraft:wooden_axe=1}
  After: {}
  [FAIL] minecraft:oak_log: expected >= 4, got 0 (before=0, after=0)
```

`arborist/cut_trees [full_cycle]` fails identically with `expected >= 5`.

## Evidence (2026-10-10 full-suite run, `/tmp/chop-suite-verify.log`)

- The warp **does** chop the whole 4-high starter column: 4×
  `[LOOTTRACE] extracted '1 minecraft:oak_log'` and the post-warp snapshot
  shows `Villager 0 (cut_trees): [wooden_axe, oak_log ×4]`.
- The expectation checker (`TestResultChecker`) counts `state.containers` —
  the farm-room chest at (5, 64, 2) — which is **empty** after the warp
  (`After: {}`; the axe was taken out as a tool and never returned).
- So the logs are in the villager's inventory, not in the chest, at check
  time. Either the deposit never happens inside the 24000-tick warp + settle
  window, or it happens later.

**Proven pre-existing** (not caused by the iterative-flood-fix): the 18:00
standalone `arborist/cut_trees [full_cycle]` repro on the OLD recursive code
(`/tmp/chop-repro-server.log`) shows the identical
`Before: {wooden_axe=1} / After: {} / expected >= 5, got 0` failure.

## Open questions

- Does the villager deposit *eventually* (extend the settle window and re-check)?
- Is the deposit gated on something the warp never satisfies (jobsite gate,
  storage-space check against the warped state, tool-return sequencing)?
- Is 24000 ticks simply too short for chop → return-to-chest → deposit with
  the axe still held?

## Root-cause narrowing (2026-10-10, verified against the code + git history)

**The deposit mechanism (where the logs are supposed to go):**
- `TestResultChecker` counts `state.containers` (the chest) — the villager's
  inventory is not counted.
- The only warp-time path that moves held items into containers is the status
  decision's `DROPPING_LOOT` leaf → `DeclarativeJobs` handler map →
  `wi.simulateDropLoot` → `ProductionTimeWarper.dropIntoContainers`.
- Two decision branches produce `droppingLoot()`: the morning
  `usualRoutine` LZCD tree (`JobStatuses.usualRoutineRoot` — "drop loot when
  hands full", "drop loot from non-full hands before starting more work",
  "drop loot when no work supplies available", "drop loot when no work
  possible" — all gated on `hasNonWorkItems`/`hasAnyItems` + `town.hasSpace()`)
  and the evening/night branch (`ProductionStatuses.getEveningStatus`:
  `hasItems → droppingLoot()`, unconditional).

**How the warp drives the decision:**
- `DeclarativeJobs.warp` does **one** status step per call:
  `getNewStatusFromSignal(status, Signals.fromDayTime(VIRTUAL_MORNING_TICK=1000), …)`
  → `wi.injectTicks(ticksPassed)` (bulk) → if the work completed, a forced
  `EXTRACTING_PRODUCT` tail grants the drops.
- `AbstractAdvanceTime.advanceTime` calls the job warper once per
  *important tick* (`computeImportantTicks`). In the failed run there were
  **zero `[WHY]` status transitions during the whole 24,000-tick warp** — the
  chop + grant happened in the last warp step, and no subsequent status step
  ever ran with the logs in hand. The evening branch never runs at all: the
  warp's single step is pinned to `VIRTUAL_MORNING_TICK`.

**Branch/branch + timeline facts (each verified):**
- `DeclarativeJobs.warp`: byte-identical 1.19.2 ↔ 1.20.6 (both 105 lines, 0
  diff lines).
- `JobStatuses.usualRoutineRoot`: identical except the `[WHY]` diagnostic
  logging added on 1.20.6.
- `AbstractAdvanceTime` (per-important-tick steps, two-timeline ADR-0006):
  identical on both branches.
- `ChopDownTree`, `TestResultChecker`, `SupplyItems.hasNonSupplyItems` (==
  1.19.2 `JobsClean.hasNonSupplyItems`): identical.
- June history on this branch: `c8578e07` (2026-06-02, un-archive,
  "32/32 autotest"), `9abbba6b` (2026-06-03, full_cycle ≥5), ADR-0006
  `92d8693b` (2026-06-03, "jobs re-run 32/32"). The blueprint + job JSON
  have not changed since `c8578e07`.
- No full-suite run on this branch completed between June and today — every
  run crashed inside an arborist scenario (the SOE bug), so if the deposit
  regressed in that window, no verdict was ever produced to notice it.

**Leading hypotheses (not yet proven):**
1. **The tick schedule changed**: `ImportantTicks`/`computeImportantTicks`
   (or the `ImportantTicks.adaptWork` bridge) may no longer emit a warp step
   after the forced extract, so no second status step ever reaches the
   morning "drop loot when no work possible" leaf. Check the git history of
   `town/entity/ImportantTicks.java` since June and compare the step list
   for a cut_trees warp.
2. **ADR-0005 in-warp worldgen** (`GrowTreesWarpRule`,
   `collectGlobalRulesForAllVillagerRoots`, ~2026-10): changed the warp's
   passive rule set and the world's log density — could shift which
   important ticks are "work-completion" vs "idle", or interact with the
   forced-extract tail.
3. **The 1.20.6 migration** altered a time/signal seam so the productive
   window no longer contains a post-extract step.

**Suggested next step (cheapest first):** enable the `[WHY]`-style gate dump
for the cut_trees warp (or a targeted `logger.logDetail` in
`DeclarativeJobs.warp` + `AbstractAdvanceTime`'s step list) in a held
`-Dquestown.autotest.only=jobs:arborist/cut_trees` run — read which gate
fails on the post-extract step (if any step runs at all). That single dump
settles hypotheses 1 vs 2/3.

## Acceptance criteria

- `arborist/cut_trees` and `[full_cycle]` PASS with verdict lines (oak_log
  delta ≥ 4 / ≥ 5 in the farm-room chest).
- `organizer/fetch` is a separate failure (emerald never relocated between
  the chests at ±4,64,-1) — track separately, but check whether it shares a
  deposit/relocation root cause.

## Related same-run failures (pre-existing, out of scope for this issue)

The 2026-10-10 suite (first full-suite completion on 1.20.6-neoforge-wip):
182 PASS / 29 FAIL / 0 SOE, all 57 tests ran to completion. Non-arborist
FAILs: 5 chicken-arc beats (stuck at WAITING_FOR_DOOR / WALL_BLOCK /
FREE_FOOD beats + flag bits), organizer/fetch (emerald),
eating/dine_at_time (Villager fullness −16%, min 25%),
farmer/harvest_wheat [no_supplies] and [held_tool_warp],
crafter/leather_boots, nav/roof_gatherer. None of these scenarios involve
tree-chopping; no full-suite baseline exists on this branch to diff against
(earlier runs crashed at tests 16-19).
