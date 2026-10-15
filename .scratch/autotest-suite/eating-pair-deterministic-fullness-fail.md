---
title: eating/dine_at_time + eating/eat_direct fail deterministically (fullness −16% / −12% vs ≥25%)
status: needs-triage
created: 2026-10-15
priority: p2
---

## Question this ticket answers

The handoff asked: regression from this session's food commits
(`e98069b1` / `17a2d6b4`) or pre-existing?

**Answer: not the food commits — one is a scenario-design gap
(dine_at_time: no test builds the plate room `DinerWork` requires), and
one is a live behaviour flake (eat_direct: the scenario's own diner
sometimes does not finish its eating cycle inside the monitor window).
A check-side scoping fix landed 2026-10-15 that makes the fullness check
assert on the townies the scenario actually put to work.**

## Correction to the original forensics (2026-10-15, after the check-side fix)

The Mechanism B attribution was **inverted**. Live re-verification with
scoped checks + assignment-scope debug logging showed:

- The −12 % / −16 % line is the **scenario's own diner** (the townie the
  scenario assigned `dining_no_table` to). The 88 % / 84 % line is the
  **stray** — the *previous* eating scenario's eater, whose high fullness
  (it just ate there) survives flag teardown into the next flag's handle.
- With the check scoped to the assigned townie only (below), the stray no
  longer matters: `eat_direct` still fails on its own diner, which in the
  failing runs stays in `COLLECTING_SUPPLIES` (repeated `[PROBE:supplies]
  TOOK item=bread`) and never reaches `NEED_EAT`/`EXTRACTING_PRODUCT` —
  no `Fullness changed` fill line ever fires for it. The check then reads
  pure decay from the spawn baseline (≈−12 % after the ~600-tick window).
- The 19:40 run's eater *did* enter `NEED_EAT` (value=0) within ~2 s and
  finished eating → 88 % → PASS. Whether the diner completes the cycle in
  time is seed/execution-dependent — a behaviour flake, not a check bug.
- `dine_at_time` fails by construction (dev ruling 2026-10-15): the
  scenario expects table dining (`DinerWork`, job ID `dining`), which
  requires a room containing plate blocks; **no autotest blueprint builds
  such a room**, so the townie always gets `dining_no_table` and the
  drained-to-0 fullness deterministically decays to −16 %. Pre-existing
  since the 10-10 baseline; not a code bug.

## Check-side fix (implemented 2026-10-15, TDD)

`TestExecutor.scenarioTownieTargets(current, assignmentTownies)` — pure
seam, unit-pinned by `TestExecutorFullnessScopeTest` (5 tests, red
verified against the unscoped form). Wiring:

- `assignJob()` snapshots `assignmentTownies` = the flag's handle keys at
  the job-assignment phase (the townies the scenario put to work).
- `checkFullnessIfNeeded()` asserts on `current ∩ assignmentTownies`
  (intersection: a scenario townie that died before the check is simply
  absent; a stray that registers *after* assignment — the observed
  timing: the handle is already at the expected count when the spawn
  wait passes, so a spawn-time snapshot misses it — is out of scope; a
  townie present *at* assignment got the job and was drained, so it is
  in scope). Null snapshot falls back to all townies (legacy).
- Respawn flows re-run `assignJob()` (REASSIGN_JOB phase), which updates
  the snapshot.

Note: the fix does **not** make the live scenarios pass — it makes the
check honest. `dine_at_time` and `eat_direct` still fail for the reasons
above; both stay on the known-baseline failure set.

## Open items

1. **Stuck eater (eat_direct flake):** why does the scenario diner stay
   in `COLLECTING_SUPPLIES` (having taken the bread) and never reach
   `NEED_EAT` in some runs, while in the 19:40 run it entered `NEED_EAT`
   within 2 s? Suspects: jobsite contention with a stray standing at the
   flag, the `not going to jobsite` gate (null, cannot be pre-computed in
   the WHY dump), or supply-check re-evaluation. Needs the
   `questown-status-why` dump sequence on a held run.
2. **Stray lifecycle:** why does a prior scenario's eater survive flag
   teardown and register onto the next flag's handle (the 88 % townie)?
   "Killed 2 stray visitor(s)" at scenario start does not cover it — the
   absorption happens *during* the next scenario. Separate ticket when
   chased.
3. **dine_at_time scenario design:** build a plate room in the blueprint
   (if table dining is the intended path), or mark the scenario as
   testing a not-yet-built feature.

## Evidence (log-forensics, no code change needed to reach this)

Full-suite eating-pair verdicts:

| run | date/time | dine_at_time | eat_direct |
|---|---|---|---|
| `autotest-full-1015.log` | 10-14 19:40 | FAIL (−16 %, **1 townie**) | **PASS** (88 %, 1 townie) |
| `autotest-full-final.log` | 10-14 22:41 | FAIL (−16 % + 84 %) | FAIL (−12 % + 88 %) |
| `autotest-post-fix2.log` | 10-15 04:34 | FAIL (−16 % + 84 %) | FAIL (−12 % + 88 %) |
| `autotest-baseline-3.log` | 10-15 04:51 | FAIL (−16 % + 84 %) | FAIL (−12 % + 88 %) |

The food commits are out:
- `e98069b1` is docs-only — its message says "No code change: the mechanism
  already does exactly this".
- `17a2d6b4` adds LOOT accumulation (the count-1 raw-food quest is
  explicitly "untouched") + the 200 t/s autotest boot — and the 200 t/s
  boot is in **both** the passing 19:40 run and the failing 22:41 run.
- `dine_at_time` was already failing in the 10-10 14-scenario baseline
  (pre-dates every 10-14 commit).

## Mechanism A — dine_at_time: the scenario townie never eats (pre-existing, by construction)

`checkFullnessIfNeeded` (TestExecutor:728) requires the scenario's
townies ≥ `minExpectedFullnessAfter` (0.25 for dine). Fullness is
drained to 0 first (`drainHungerBeforeTest` → `fillHunger(vuid, 0.0f)`),
and decays below 0 while the villager waits (SimpleVillagerHandle
`tickHunger`: `min = -Integer.MAX_VALUE // Hunger keeps ticking below
zero to trigger dining`).

So −16 % = the townie **never ate** during the window and
 deterministically decayed from 0. The value is a pure function of window
length — identical across all runs.

Why it doesn't eat: the scenario expects **table dining** (plate block +
`SpecialQuests.DINING_ROOM` room, `DinerWork` job ID `dining` —
`TestBlueprintRegistry.dineAtTimeBlueprint`), but the job-change log shows
the scenario townie is assigned `dining_no_table` (`DinerNoTableWork`).
Dev ruling (2026-10-15): `DinerWork` requires a room containing plate
blocks, and no autotest blueprint builds such a room — the scenario
cannot pass as written.

## Mechanism B (corrected) — eat_direct: the scenario's own diner sometimes doesn't eat in time

See the correction section above. The 19:40 PASS vs the later FAILs is
not a stray-absorption regression: the failing line is the scenario's
own diner, stuck in `COLLECTING_SUPPLIES` inside the monitor window
(seeded, not deterministic — the same blueprint passed 19:40 with the
eater completing the cycle). The stray (88 %) was a red herring that the
old all-townies assertion made visible.

## Scope notes

- `eat_direct` is a **behaviour flake** (seed/execution-dependent eating
  cycle), now asserted on the correct townie; `dine_at_time` is
deterministic given the missing plate room.
- Independent of the wall-walk fix (RoomRecipes `940aeb9`): the eating
  pair fails identically on the unfixed and fixed jars.
- `eat_raw_food` and `eat_no_table` pass in all runs.
