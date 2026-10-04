# Why the cook/food chain sometimes never staffs (self_sufficiency soft-lock)

2026-10-04 (battery v3 + canFit clock-window resolution + **live-gate pin** appended). Verified from 19 fresh
`village/self_sufficiency` samples (~50%–80% soft-lock, env/seed-dependent) + code reads.
This is a **continuation** of the DEV-DIAG `raw_food hunt` investigation (2026-10-02 →
10-04; `DevWorkScore`/`DevLootFire` exist).

## Verified chain (soft seeds vs healthy)

The cook is **never offered** in soft seeds, not "offers and fails":

```
cook quest offered only in tutorial phase 2.5        TownQuests.tryPhase2_5_jobChangeAndFood (L677)
   └─ gated on  hunterRawFoodLootComplete()         L1113
       └─ = a LOOT for  villager_simple_furnace_food_raw  is .isComplete()
           └─ closes only when a townie EXTRACTS raw meat   (onLootExtracted → processLootQuests)
```

| | cook quest opened | `extract … → true` | meat-grant trace | end-state |
|---|---|---|---|---|
| **soft** | **0** | **0** | **0** | `594` × `hunter/seeking_work` |
| **healthy** | 1 (staffed) | 2 (Raw Mutton, Apple) | present | `87` × `cook/extract` |

## The improvement fixed the wrong link

This session's "LOOT-quest closure on extraction" work fixed **`extract → LOOT closes`**.
That half works. The self_sufficiency soft-lock is the **upstream** half: **the hunter
never extracts in the first place.** The claim is not the blocker; the hunter stall is.

## THE ANSWER: `canFit` is an in-day clock window — the hunt only starts between dawn and ~9:07

- Selection gate: `JobCycler.isValid` (`jobs/town/JobCycler.java:50`) rejects a candidate
  when `!canAlwaysStart.test(c) && !canFitInDay.test(c)`. `canSatisfy` is NOT the
  blocker — the scoreDump shows the sword job `score>1.0, all tools available, Satisfies
  request: mutton` in the soft runs.
- **`canFitInDay` is `ServerJobsRegistry.canFit` (L710-723) — it is a wall-clock-in-the-day
  window, NOT the supply check the `ALWAYS_CONSIDER` javadoc describes** (the
  `SpecialRules.java:95-98` “abundance of supplies” comment misnames the mechanism it
  bypasses):

  ```java
  long jobDuration = w.jobFunc.apply(villagerID).getTotalDuration();  // sword hunt: 2000 (sum of time-states, DeclarativeJob:159)
  long finalTick = currentTick.dayTime() + jobDuration;
  if (fromDayTime(finalTick).compareTo(fromDayTime(currentTick)) < 0) return false;
  return {MORNING, NOON}.contains(fromDayTime(finalTick));
  ```

- With the `Signals` segments — MORNING [0,6000), NOON [6000,11500), EVENING
  [11500,22000), NIGHT [22000,24000) — and D=2000, the **acceptance window is
  day-time `t ∈ [0, 9500)`**: the hunter may only START the hunt between dawn and ~9:07
  in-game. 9500 → next dawn (60% of the clock) is a hard NO regardless of tools/score/request.
  *(Unit-pinned by `DayWindowTest`, see the evidence note further down.)*
- So "fitting in a day" = "can finish by end-of-noon". The stall is a **scheduling**
  interaction, not an availability one: the hunter (re)asks on the board only at its
  state-transitions, the seek job itself bails to downtime after 1000 ticks
  (`WorkSeekerJob:92` `withMaxTickLimit(1000).withMaxTickFallback(DowntimeWork)`), and in
  a SOFT seed those evaluations never land inside [dawn, 9:07). In a GREEN seed the hunter
  asks immediately after the sword is posted and adopts within seconds (probe-s1: posted
  20:51:33, adopted ~20:51:35; GREEN 1933_f4 same shape).
- **Evidence gap (honest) → resolved by Tier 3 (2026-10-04):** the per-line rejection
  (`Villager will not do {hunter/sword} because there is not enough time
  left in the day` — logged by that very `canFit` closure,
  `TownFlagBlockEntity.changeJobForVisitorFromBoard`) was NOT captured in Tier 2
  (that seed's hunter never reached the out-of-window ask — a scheduling mode);
  **Tier 3 captured it live: 490× `REJECT@canFit(gate1)` (`fit=false`), 0 canSatisfy
  rejects, `cas=false`.** See the Tier-3 section below. Every other link is code/log-verified.
- **Window arithmetic — now unit-pinned (2026-10-04, handoff `pintail` Tier 1):**
  `DayWindowTest` (`src/test/java/ca/bradj/questown/jobs/DayWindowTest.java`) rests the whole-day
  sweep of the `canFit` predicate on the registry-free `Signals` seam and asserts the accepted
  start set is **exactly `t ∈ [0, 9500)`** (boundary pair 9499→NOON fit / 9500→EVENING miss,
  no overnight wrap, window contiguous), plus the `fromDayTime` segment boundaries (6000, 11500,
  22000). Green 7/7. The registry-bound half of `canFit` itself (the `Work` supplier +
  `getTotalDuration` lookup, `D=2000` known from the `hunter/sword` job JSON
  `work_states["time":2000]`) is NOT unit-loadable without a Forge harness — pinned by
  `ServerJobsRegistryCanFitTest`'s single `todo_canFit…` failing assertion per the repo
  doctrine, so the suite stays honest about what the Forge environment still owns.
- **Tier 2 RAN (2026-10-04) — the per-ask rejection is a measured ZERO, not a gap:** the
  held SOFT run (6 days, `Questown:Flag` at DEBUG) reproduced the stall and captured **zero**
  `not enough time left in the day` lines — see the Tier-2 section below. The window's
  *shape* is a unit fact; the live stall is at a gate this rejection line does not cover.
- **Fix implication — REJECTED (user, 2026-10-04):** `always_consider` on the sword jobs
  IS the bypass for exactly this predicate (`!canAlwaysStart && !canFit → skip`) — per the
  rule's own comment and the only Java-side precedent (`DowntimeWork.java:93`). **The dev
  does not want `always_consider` on this job; do not re-propose it.** (And see the
  Tier-2 probe below — the predicate may not even be the live gate.)

## Battery v3 — the guarantee helped, but the stall survives (post `08249841`)

**Result: 4/8 GREEN (50%)**, vs 7/9 soft (78%) pre-fix. 8 seeds @ 200 t/s, early-exit on
`cookUnlocked=true`, 5-day watch otherwise. (`run/samples/BATCH-fast-SUMMARY.md`; batch v3
of `run/suff-batch-fast.sh`.)

1. **The guarantee reaches only the extraction path.** In GREEN `1933_f4` the LOOT closed
   on a **natural `Raw Chicken` roll** (chicken is 15% of the loot table — a raw food too),
   with the prepended mutton landing *after* the close. The prepend = insurance on the
   "a hunt completes" branch, not a rescue of the "no hunt ever completes" branch — which
   is where most of the residual soft lives.
2. **The residual SOFTs (4) split into two distinct variants, both upstream of any
   extraction:**
   - **Variant B (3/4: `1922_f3`, `1937_f6`, `1950_f8`) — the work-picker never adopts
     the hunt.** Swords CRAFTED (`ITEM/COMPLETED wooden+stone sword`),
     `JOB_CHANGE/hunter_qt_sword` OFFERED, then **0** `hunter/sword` transitions, **298**
     `hunter/seeking_work` watch-samples to the end. Per “THE ANSWER”: rejection is
     exactly `canFit`'s clock window never being entered — a scheduling stall.
   - **Variant C (1/4: `1912_f2`) — room-builder gave up upstream.**
     `[room-builder] could not resolve recipe blocks for questown:fisher_qt_fish` and
     `… farmer_qt_harvest_wheat`; the sword ITEM quests never even opened; roster frozen
     {gatherer=2, hunter=1}. A **separate** RoomRecipes-resolution bug, not the hunt gate.

## Tier-2 live probe — the predicted canFit rejection NEVER fires (2026-10-04, handoff `pintail`)

A held `village/self_sufficiency` SOFT seed at ~200 t/s (6 in-game days), `Questown:Flag`
logger at DEBUG so the rejection line would surface. Evidence: `run/samples/tier2-canfit-
probe-20261004.md` (local; `run/` is gitignored).

- **Stall reproduces live (verified):** the hunter sits in `hunter/seeking_work`
  (31.5k journal-snapshot lines), wooden+stone swords `COMPLETED`,
  `JOB_CHANGE/hunter_qt_sword` `ACTIVE`, 0 adoption, hands empty.
- **Score/canSatisfy ruled out live (verified):** the executor's stall score-dump (the
  DEV-DIAG in `VillageSelfSufficiencyExecutor.maybeEmitSeekingStallDiag`) shows
  `hunter/sword` **1.009**, `sword_half_day` **1.005**, `sword_full_day` **1.008** —
  "Satisfies request: mutton; all ingredients and tools available."
- **THE PREDICTED REJECTION IS ABSENT (verified):** across all 6 days there are **zero**
  `Villager will not do … not enough time left in the day` lines, although `canFit`
  denies 60% of each day. (The capture is proven to work — other `Questown:Flag` DEBUG
  lines DO fire in the same run; the logger is not the gap.)
- **Inference (not yet proven):** in `JobCycler.isValid` that line is *the* runtime
  signature of an ask that **reached** the clock-window gate and was **denied**
  (`!canAlwaysStart && !canFit → return false`; `always_consider` is off, so
  `canAlwaysStart=false`). Six days without it means the stalling hunter is **not** being
  denied per-ask — the board-ask either yields **no adoptable candidate** (empty/filtered
  `possible` set → cycler returns null with no gate evaluation → no line, no adopt) or the
  seek never reaches `nextForVillager`, or — the one silent branch — `canFit` passes
  (in-window) and `canSatisfy` fails on the live `requestedResults`, which `JobCycler`
  rejects with **no log line**. **This refutes "asked-but-clock-rejected" for this SOFT
  seed** and does not invalidate the Tier-1 unit pin (the window math is still correct for
  a job that actually reaches the gate). The live stall is at a gate the rejection line
  does not cover.

## Tier-3 live-gate pin — the pin IS `canFit` (2026-10-04, handoff `pintail`)

A held `village/self_sufficiency` seed at ~200 t/s, `Questown:Flag` at DEBUG,
**`ENABLE_DEV_COMMANDS=1` in the fresh gradle-daemon env**, with a targeted `DEV-DIAG`
block in `changeJobForVisitorFromBoard` that mirrors `JobCycler.isValid` per candidate
(logs each candidate + which `return false`). Evidence:
`run/samples/tier3-canfit-livegate-20261004.md` (`/tmp/tier3-server.log` full log).

- **THE GATE IS `canFit` (gate 1), verified live:** the hunter's `hunter/sword` ask,
  evaluated after the in-day window (day-time ≥ 9500 ≈ 09:30), is
  `cas=false fit=false satAny=false → REJECT@canFit(gate1)`. The cycler
  **has** a candidate (`candidates=1/3` → `hunter/sword` is the sole unlocked one) and it
  is **rejected** — this is *not* the "empty `possible` set" branch.
- **Tallies:** `hunter/sword` → **490** `REJECT@canFit`, **24** `ACCEPT(satisfy)`
  (in-window, the hunter does adopt — `cur → sword → dining`), **0** `REJECT@canSatisfy`.
  `always_consider` stays **off** (`cas=false`). The `canFit` closure's own "not enough
  time" line fires **13,326×** this run — the logger/capture is proven to work.
- **Reconciliation (honest):** Tier 2 and this are *different* seeds. Tier 2's stall was
  **Mode B** — the hunter never reached the in-window ask (a scheduling symptom of the same
  narrow window). This seed was **Mode A** — the hunter reaches the ask and is denied by
  `canFit` out-of-window, adopts in-window. **Both are downstream of `canFit` being a narrow
  in-start-window check** (the Tier-1 `DayWindowTest` `t ∈ [0,9500)` pin). So the pinned
  rejection gate is `canFit`; the residual SOFT question is whether the hunter's seeks land
  inside the window (Mode B), not which predicate denies them outside it.
- **Confirms the original diagnosis** and the Tier-1 unit pin; refines (does not refute) the
  Tier-2 "zero per-ask rejection" reading, which was a seed-specific scheduling mode.

## Trace (b) — why the SOFT hunter's seeks miss the window (2026-10-04, handoff `pintail`)

A live `village/self_sufficiency` SOFT seed (hunt/food chain stalled: `cook_qt_simple_
furnace_food` `ACTIVE`, cook `NO_JOBSITE` in a `[lock]` dump, `2 hunter` townies stuck at
`selected=null` for 2,000+ board-asks), `ENABLE_DEV_COMMANDS` in the daemon env, the
`[jobcyc]` mirror of `JobCycler.isValid` per candidate. `/tmp/tier3b-server.log` (local;
`run/` gitignored).

- **The SOFT hunter REACHES the board and asks** (not bailing before it): **2,036**
  `REJECT@canFit(gate1)` vs **24** `ACCEPT(satisfy)` (in-window). Zero `REJECT@canSatisfy`
  for the hunter. So this is **mode b2** (ask reaches the gate + out-of-window), not b1
  (empty-candidate / bail before the board). `canFit` is re-confirmed as *the* live gate
  on a real SOFT seed (Tier-3 was a Mode-A/GREEN seed where the hunter adopted 24×).
- **The DAY CYCLES** — 8 nights slept, day reports 1–5, `time set day` firing (the
  harness skips night: `if (level.isNight()) time set day`, → day-time 1000). So the
  in-window band `[1000, 9500)` is present every day; the stall is **not** a stuck clock.
- **THE ASKS ARE PHASE-LOCKED OUT-OF-WINDOW:** even with the in-window band present daily
  (the effective harness day is `[1000,13000)`, in-window = `[1000,9500)` = 71%), the
  hunter's board-asks land overwhelmingly in the out-of-window band `[9500,13000]`
  (2,036 out-of-window asks vs 24 in-window). The acceptance window
  (`DayWindowTest` `t ∈ [0,9500)` = dawn→~09:30, D=2000) denies them; the hunter
  (re)enters seeking_work in a cycle (sword hunt D=2000 → dining → seek-retry) whose ask
  phase is scheduling-locked to the out-of-window band, so `canFit` (gate 1)
  (`ServerJobsRegistry.canFit`, `fit=false`) denies and the hunter never re-adopts → the
  hunt/food chain can't keep up → the cook food quest stays `ACTIVE` and the cook is left
  partial (`NO_JOBSITE`).

**Answer to (b):** the SOFT hunter's seeks miss the window because the **ask phase is
phase-locked to the out-of-window band** (a scheduling/interference between the hunter's
hunt→dine→seek cycle and the day clock), and **`canFit` is the gate that then denies
them**. This is the scheduling facet (Mode B) the Tier-2 probe pointed at, now reproduced
and shown to be `canFit`-denied (not an empty candidate set, not canSatisfy, not a stuck
clock). The remaining open root-cause is *why the phase is locked out-of-window* — the
precise timing of the hunter's cycle vs the `[1000,9500)` band (would need dayTime-at-ask
logging, which `[jobcyc]` does not yet emit).

## Remaining options (none applied; `always_consider` rejected by the dev)

1. `always_consider` — **REJECTED (dev, 2026-10-04); do not re-propose.**
2. ~~Pin the live gate~~ — **RESOLVED (Tier 3, 2026-10-04):** a targeted `DEV-DIAG`
   block in `changeJobForVisitorFromBoard` (mirrors `JobCycler.isValid` per candidate)
   on a live SOFT-arena seed shows the hunter's `hunter/sword` ask is rejected **by
   `canFit` (gate 1, `fit=false`)** 490×, with **0** `REJECT@canSatisfy` and
   `cas=false`. The live rejection gate is `canFit` — confirming the Tier-1 unit pin
   (`t ∈ [0,9500)`) and the original diagnosis. The residual SOFT question is now
   *scheduling* (Mode B: the hunter's seeks miss the narrow [dawn, ~09:30) window), not
   *which predicate denies* (that is `canFit`). `run/samples/tier3-canfit-livegate-20261004.md`.
3. **Variant C** (room-builder `could not resolve recipe blocks`) — separate bug, separate
   repro. 1/4 of the residual but a permanent stall when it fires.
4. **Widen the admissible band / shorten the hunt / accept morning-only starts** (dev's
   call, not applied) — the non-`always_consider` fixes for the gate; note they don't help
   a Mode-B hunter that never *seeks* inside the window. Deprioritized: the SOFTs sat in
   `seeking_work` the whole 5-day watch.

## Confound worth checking with the observer
Have the hunter "stuck early, no cook ever unlocks" been seen on a live server? That pins
whether the autotest arena's day clock is representative of live play.
