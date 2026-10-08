---
title: Verification sweep (2026-10-08) — tick-range fix holds; the new stall is onboarding progress
status: report
created: 2026-10-08
---

# Self-sufficiency sweep — what we learned (2026-10-08)

## Verdict

**The old soft-lock (leaver stops ticking → never TOOK → stall at `villager_simple_furnace_food_raw`,
~50/50 by seed) is gone.** Across this campaign's sweeps (~23 held worlds, cohorts 813–836 + 850–860;
830–848 cohort lost to a `/tmp` wipe mid-run, partial record in `sweep-evidence.md`), **0 worlds showed
the old signature** (TOOK=0 / entity-tick silence / COLLECTING freeze). Hostile-spawn worlds that
*previously always failed* now pass (831 at (−112,160); 855 at (48,100,0) made it past the old gate
stage before stalling later).

**Baseline caveat (read this first):** every sweep world ran the **working-tree build**, which — beyond
the two committed fixes (`480760a6` 3×3 force-load, `972b1a90` tick-coverage invariant) — also carries
the *uncommitted* 2026-10-13 session changes (Gate A `UtilClean.specialRulesAllPass`, Gate B
`State.freshIfNull`, Round-4 `isWelcomeMat`, equivalence tests) because `MOD_CLASSES` compiles
`src/main` as-is. If a result below looks gate-related, the uncommitted gate work is in play.

## The new stall is progress, not a freeze

Per-world terminal state at final read (full poll log in `sweep-evidence.md`):

| cohort | full completions | slow but progressing | hard stall |
|---|---|---|---|
| 850–855 | **853** (done at fishing_station stage) | 850/852/854 (kitchen_stocked, day 4, TOOK 54–63), 851 (fishing_station, day 4, TOOK 53) | **855** |
| 856–860 | **856, 858, 860** | — | — |
| 830–836 (partial) | **832** | rest reached day 3, extracting | — (lost to /tmp wipe) |

The stage cascade the town now walks: `… → kitchen_small → kitchen_stocked (cook stocks
fuel+ingredients: `cook_stock_fuel` / `cook_stock_ingredients`) → dining tier (`dining_no_table`,
`dining_raw_food`) → fishing_station → farmer (`farmer_qt_harvest_wheat`)`.

## What to tweak (onboarding knowledge, per the dev's hypothesis)

1. **The scenario "completes" before the intended end.** 856 finished with `kitchen_stocked` still
   ACTIVE; 860 with `farmer_qt_harvest_wheat` ACTIVE; 853 at `fishing_station`. The DONE threshold
   (`Quests.IdealQuestThresholdTicks` era) fires while 2–4 intended stages are still open — so a
   "passing" run silently skips the tail. Decide: raise the threshold, or make the tail stages part
   of the completion contract.
2. **kitchen_stocked is the slowest stage (3 of 6 in cohort held here).** The town's cooks drift into
   `dining_*` jobs (incl. `dining_no_table` — dining before there is a table) while the stock
   quest stays open. Ordering question: dining should probably not unblock until the kitchen is
   stocked (and the table quest before `dining_no_table` work is claimed).
3. **855 outlier (1 of 23): raw-food stage, TOOK=1 over 90+ min, cook never unlocked.** The leaver is
   alive and ticking (probe cadence healthy; not the old freeze) but escalates to tool-subjobs
   (`gatherer/shovel*`, `axe_*`, `rod_*` half/full/quarter day) for which the welcome mat is **not**
   a job block — `PROBE-C isJobBlock FALSE … freshView=false`. The plain `gather` job TOOKs once and
   then no leaver works it. Tuning: keep plain `gather` claimable while tool-subjobs exist without a
   matching block, or place the tool block before the ladder escalates.
4. **Diagnosability debt surfaced (separate from the stall):**
   - `[DEV-DIAG:]*` lines are full of unimplemented values (`hasSupplies={!} invFull=TODO hasItems=town
     has storage space=<?>`) — they read as signal but carry none.
   - `PROBE-C`/`PROBE:matRooms` fire on hot paths: **~1,000,000 lines per world in ~25 min** on normal
     (passing) runs. They were the only lens on 855, but they also drown the log — strip per the
     pending probe-clear (ProductionJob was already stripped in `a4fdbb85`; the rest is still queued).
   - `psweep.sh` capture state lives in `/tmp/psweep-args`; a `/tmp` wipe kills the whole harness with
     no visible failure ("Could not find or load main class run: psweep.sh capture <pid> first" at the
     *launched* world's stderr). Capture is cheap to redo (recaptured 19:35) — a startup self-check
     printing "capture missing" would save the next session.

## Reproduce

```
cd /workspace/IdeaProjects/Questown
# capture (needs a live runServer): see references/questown-dev-launch.md
bash run/psweep.sh capture <server-java-pid>   # e.g. from ./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.hold=true -Dquestown.autotest.only=village/self_sufficiency
bash run/psweep.sh launch 900 901 902
bash run/psweep.sh status 900 901 902
# verdict lines + per-world TOOK / STAGES stuck= / quest dumps live in /tmp/psweep-N/stdout.log
# (ephemeral — snapshot to .scratch/ as you go)
```
