# Plan — cook skill unlock (no single-job reselect storm)

Issue: `.scratch/cook-skill-unlock/cook-skill-unlock.md` · Branch: `1.20.6-neoforge-wip`
Status: **design locked** (2026-10-16) after user review — the cook's food recipes
are a leveling progression and stay gated; only the universal set is granted.

## What ships

1. **Universal starter set** — a cook staffed any way unlocks `cook/extract`
   (already the root) + `cook/fuel` (insert coal). Mechanism: a new boolean
   `unlocked_with_root_job` on a job's JSON, parsed onto `Work`, and
   `isUnlockedInitially` becomes `(parentID == null) || flag`. Tag only
   `cook_fuel.json`. Both staff paths (`register`, `ChangeJobReward`) are
   covered because both unlock `getDefaultWork`.
2. **Onboarding grant** — the tutorial cook additionally unlocks
   `cook/mutton` (the starter recipe; the hunter is guaranteed to bring mutton).
   Hook: owner adoption in `TownQuests.processJobChanges` for an onboarding
   JOB_CHANGE quest wanting cook; logged; idempotent.
3. **Everything else stays progression-gated** — no recipe (rabbit/porkchop/
   chicken/beef/potato/fish) or stock job unlocks at staff, for any cook.

## Staffing sets (who gets what)

| Path | Staffed cook gets |
|---|---|
| `register` (spawn) | extract + fuel |
| `ChangeJobReward` (quests, scenarios, non-onboarding job change) | extract + fuel |
| Onboarding cook (adoption of the tutorial cook quest) | extract + fuel + mutton |

A second cook is a stoker/collector until it levels recipes — a deliberate,
user-approved "trainee" design, not a bug.

## Changes (ordered)

1. `Work` — `boolean unlockedWithRootJob` (default false) +
   `withUnlockedWithRoot(...)` (follow the `proficiencyId` post-set idiom) +
   pure `static boolean isUnlockedInitially(@Nullable JobID parentID, boolean
   unlockedWithRoot)`.
2. `ServerJobsRegistry.isUnlockedInitially(Work)` — delegate to the static.
3. `ResourceJobLoader.workFromJsonV1/V2` — read the optional
   `unlocked_with_root_job` boolean and apply it to the `Work`.
4. `data/questown/questown_jobs/cook_fuel.json` — add the flag.
5. `TownQuests.processJobChanges` — on owner adoption of an onboarding
   cook JOB_CHANGE quest, `unlockJob(owner, JobID("cook","mutton"))`
   (named constant + comment; one `info` log line as run evidence).

## Test seam (TDD)

- `Work.isUnlockedInitially(parentID, flag)` — 4 cases. RED = compile (method
  absent); GREEN = minimal.
- Untestable half (registry-bound): JSON parse, `getDefaultWork`, the
  onboarding grant → compile + held run. Named in the issue, not faked.

## Verification (the gate)

Held `village/self_sufficiency` at high tick rate (the 5-day run's 200 t/s):
cook's first day — no `cook/extract [from cook:extract]` lines, `effective`
near target TPS, zero `Can't keep up`, the mutton-grant log line present, food
chain producing (daily report). Then update the `AUTOTESTS.md` TPS caveat
(150–300t band was measured with the staffing gap live; re-sweep if the
ceiling changed).

## Not a regression gate

`./gradlew build` is red by default (`ChickenArcHintsTest.todo_…`); gate on
`compileJava`/`compileTestJava` executed + the named test's `TEST-*.xml
failures="0"`. Run `sh tools/strangler-check` after (we don't touch `gui/`,
but it's cheap).

## Retro trail (this feature)

- Stumble 1 (user-caught): leaned toward the maximal unlock (all 6 foods, all
  cooks) — misread "at least 2 jobs" as a floor to maximize against a sacred
  progression tree. → Locked spec + the table above.
- Stumble 2 (user-caught): designed "every cook" without enumerating staffing
  paths; "second cook = grunt" was a consequence, not a design. → the
  staffing-set table is now part of the plan.
- Near-miss (caught in review): mutton grant almost lived in `ChangeJobReward`
  (shared with non-onboarding changes) — moved to the adoption site.
