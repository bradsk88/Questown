# Why the cook/food chain sometimes never staffs (self_sufficiency soft-lock)

2026-10-04. Verified from 10 fresh `village/self_sufficiency` samples (~50%–80% soft-lock,
env/seed-dependent) + code reads. This is a **continuation** of the DEV-DIAG
`raw_food hunt` investigation (2026-10-02 → 10-04; `DevWorkScore`/`DevLootFire` exist).

## Verified chain (soft seeds vs healthy)

The cook is **never offered** in soft seeds, not "offers and fails":

```
cook quest offered only in tutorial phase 2.5        TownQuests.tryPhase2_5_jobChangeAndFood (L677)
   └─ gated on  hunterRawFoodLootComplete()         L1113
       └─ = a LOOT for  villager_simple_furnace_food_raw  is .isComplete()
           └─ closes only when a townie EXTRACTS raw meat   (onLootExtracted → processLootQuests)
```

Evidence (soft `run_1719_f1` vs healthy `tickrate-valid` / `run_1736_f1`):

| | cook quest opened | `extract … → true` (LOOT claim) | meat-grant trace (`tryGiveItem`) | end-state |
|---|---|---|---|---|
| **soft** | **0** (119× "no open JOB_CHANGE quests to drive") | **0** | **0** | `594` × `hunter/seeking_work` |
| **healthy** | 1 (staffed) | 2 (Raw Mutton→raw, Apple→cooked) | present | `87` × `cook/extract` |

So in the soft seeds **no raw-meat extract ever happens** — the hunter sits in
seeking_work and never completes a mutton hunt — so the (already-fixed) LOOT-close has
no input, the phase-2.5 gate never opens, and the cook is never offered.

## The improvement fixed the wrong link

This session's "LOOT-quest closure on extraction" work fixed **`extract → LOOT closes`**
(the `onLootExtracted` seam + `LootExtractGuard.questCheckFires(nonEmpty, rule)`; verified
by the `loot_on_extract` autotest GREEN, for realtime *and* warp — see `town/entity/COMPLEX.md`
"LOOT quest close"). That half works. But the self_sufficiency soft-lock is the **upstream**
half those samples show: **the hunter never extracts in the first place.** The claim is not
the blocker; the hunter stall is.

## What's a lead vs. what's verified
- **VERIFIED** (read code + diffed logs): the **soft/healthy differentiator is the
  hunter's transition to the sword work**, NOT a tool/food/quest issue:
  - **Healthy** (`tickrate-valid`): the hunter reaches `hunter/sword` at 17:05:00 (working);
    the townie end-state at 17:05:00-08 is `hunter/sword + hunter/sword + gatherer/gather`.
  - **Soft** (`run_1719_f1`): `hunter/sword` = **0 samples** the whole run. The hunter
    (townie `a3040eba`) is assigned the sword job (`ChangeJobReward -> root set, works
    unlocked`) at 17:19:41, then **sits in `hunter/seeking_work` the entire run**
    (296 samples). The sword-job's scoredump still shows it as `score=1.006, All ingredients
    and tools available [Satisfies mutton request]` — so the job is **globally available**
    and **assigned** to the townie, yet the townie never picks it up.
- **Verified**: the rejection is at the *can-start* level. `nextForVillager` (
  `TownPossibleWork.java:571`) calls `nextValid(possible, canAlwaysStart, canFitInDay,
  requestedResults, td)`. `JobCycler.isValid` (`jobs/town/JobCycler.java:50`) rejects a
  candidate when `!canAlwaysStart.test(c) && !canFitInDay.test(c)` OR when no
  `requestedResults` is satisfied by `ServerJobsRegistry.canSatisfy(td, c, ingredient)`.
  The scoreDump (a global probe, no can-start gates) shows the job is available, so the
  rejection is inside `canAlwaysStart`/`canFitInDay`/`canSatisfy` — not the scoring or
  the job definition.
- **Not yet verified**: the specific condition inside `canSatisfy` (
  `ServerJobsRegistry.java:579`) or `canAlwaysStart` (`ServerJobsRegistry.java:65`) that
  rejects the sword candidate in the soft run but accepts it in the healthy run. This is a
  2-line read (`ServerJobsRegistry:575-640` + the hunter/sword `work_states`,
  which is a `time: 2000` timed state with `remove_from_world`) and the likely fix site.

## Suggested fix shapes (none of them applied; pick before we touch code)
1. **Harness-side (low risk, no town logic touched)** — in the scenario,
   post the mutton work request *earlier in the townie's day* (before the
   `canFitInDay` window closes) so the hunter's selection window is open when it matters.
   Minimal scenario-level change; no production path touched.
2. **Town-side (higher risk, needs sign-off)** — add `ALWAYS_CONSIDER` to the
   `hunter/sword`, `sword_half_day`, `sword_full_day` global special rules so
   `canAlwaysStart` is true regardless of the day budget. This is a design change to the
   job's energy model; should go through a plan.
3. **Investigate the `canFitInDay` predicate** — confirm exactly what it's comparing
   (remaining tick budget vs the 2000-tick work) before choosing 1 or 2.

## Confound worth checking with the observer
Have the hunter "stuck early, no cook ever unlocks" been seen on a live server? That pins
whether the autotest arena's day/energy budget is representative of live play.

