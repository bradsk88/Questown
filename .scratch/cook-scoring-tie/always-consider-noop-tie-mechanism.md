---
title: "always_consider no-op jobs tie real work at score 1.0 — pick the mechanism"
status: needs-triage
created: 2026-10-15
---

## Context

Follow-up to `.scratch/playtest-2026-10-14/cook-status-blocker-surfacing-10-14.md`
(hint side fixed 2026-10-15, `8f5a2138`). The coin flip itself remains: the
cook re-rolls among ~7 jobs that all score ~1.0, because
`TownPossibleWork.gateFacts` short-circuits every `ALWAYS_CONSIDER` job to a
full score (`JobFacts(maxState, …)` → `v = 1.0`) — that short-circuit is
*correct* (these jobs must stay considered so they can inspect the furnace
at run time), but it makes the no-ops (cook/extract, cook/fuel, downtime)
tie the real cook-food jobs whenever the food jobs have their ingredients.
Selection then shuffles the whole preselected list
(`TownVillagerData.chooseFromList` → `Collections.shuffle`) and takes the
first candidate — facet 3's status flapping.

The independence principle (CONTEXT.md / `docs/design-principles.md`) forbids
a hardcoded job-to-job tie-break (no "fuel before cook"); a fix must be
rule- or world-state-derived. Three candidate mechanisms:

## Option A — score-band the ALWAYS_CONSIDER jobs (scoring time)

In `evaluateJob` (or the gateFacts short-circuit), cap `ALWAYS_CONSIDER`
jobs just below `PREFERRED_JOB_ACCEPTANCE` (0.65) but above
`MIN_JOB_ACCEPTANCE` (0.25) — e.g. `min(v, PREFERRED − 0.15)`. Effect:

- Real work available (food jobs score 1.0 > 0.65): the PREFERRED band
  excludes the no-ops → the cook picks real work, every cycle. **Kills the
  observed flip.**
- Everything blocked (food jobs at 0.0): the MIN band is used; the no-ops
  (0.5) are the only candidates → the cook still cycles extract/fuel/downtime
  as today (harmless variety; those no-ops *are* the right thing to attempt
  when nothing else works).

Job-set-agnostic: the band is a property of the special rule, not of any
pair of jobs. New surface: one seam in `evaluateJob` (pure, unit-testable:
given (v, hasAlwaysConsider, preferred, min) → expected band) plus the two
config thresholds it references.

Risk: a town that genuinely wants a no-op to run *while* some other job
scores high (e.g. a leaver should extract even though a gatherer has wood)
loses the tie. Mitigation: the PREFERRED-band filter already drops *all*
jobs below 0.65 today, so this only changes which jobs sit in the top band.

## Option B — carry scores into selection (selection time)

Today only the preselected `JobID` list survives `applyScores`; the scores
are dropped. Carry them (`List<Pair<JobID, Double>>`) into
`TownVillagerData.chooseFromList` and choose score-descending, shuffling
only exact ties. Effect: deterministic best-first selection; random only
among true ties.

- Fixes the "real work available but a no-op got shuffled in front" case
  **only if the no-op scores lower** — which it does not today (all 1.0).
  So B alone changes nothing for the observed flip; it is a prerequisite
  for A to actually bite (today A's banding would be invisible, because
  selection never sees scores). A+B is the pair: A makes the band real,
  B makes selection respect it.

## Option C — hysteresis (selection time, status-side)

Keep the current job if it is still in the preselected set, unless a
strictly higher-scoring job appears. Kills facet 3's *status flapping*
directly (the cook holds its no-op instead of re-rolling into a different
one every cycle) without reordering who gets picked. Weakest option: it
masks the flip rather than fixing the selection.

## Recommendation

**A + B.** A is the rule-derived banding the ticket asked for ("a no-op job
simply scoring below real work when it has nothing to do"); B makes
selection score-aware instead of shuffle-blind (removes the whole class of
"equal-score shuffle" flapping, not just this one). C is not needed once
A+B land: with the band in place, the all-blocked state's remaining no-op
tie is harmless variety, not a blocker.

Acceptance sketch:
- Unit (seam): banding math — always_consider job capped below PREFERRED,
  above MIN; non-always_consider job untouched; thresholds from config
  values, not literals.
- Unit (seam): `chooseFromList` picks the highest-scoring canFit job;
  shuffles only exact ties; request-matching still takes priority.
- Autotest: the cook with raw food + idle furnace selects the food job on a
  measured fraction of cycles (or: a held-run watch of the cook's status
  line not flapping) — decide at implementation whether a scenario can pin
  this or it stays held-run-verified.

## Notes

- `DevWorkScoreCommand` (commands) dumps per-job scores — the before/after
  evidence tool for this fix.
- DOWNTIME is a `ServerJobsRegistry` SpecialJob (`DeclarativeJob.java:393`,
  `DowntimeWork.asWork`), NOT in `Works.regularJobs()` — it does not go through
  the `TownPossibleWork` scoring pass at all, so the band does not touch it
  (it is selected by the special-jobs path when nothing else fits, a
  separate mechanism). The observed ~7-way tie is among the cook root's
  datapack jobs (cook/extract, cook/stock_fuel, the cook-food variants), all
  `Works.regularJobs()` members — the band covers exactly that pool.
- The band must stay strictly inside (MIN, PREFERRED); a value at/under
  MIN would make the no-ops disappear from the MIN fallback band and the
  cook would stop attempting the furnace entirely when idle.

## Tried 2026-10-22 (dev chose A+B): A breaks the cook; reverted

Implemented exactly as written (A: ALWAYS_CONSIDER capped at (preferred+min)/2 = 0.45 in
`evaluateJob`; B: preselected sorted best-first and `JobCycler` walking that order instead of
shuffling). Unit tests green. `-Dquestown.autotest.only=cook`: **2/6** (baseline shard run the same
day: all 6 pass):

- `cook/simple_furnace_food` (WARP): cooked_beef 0 (expected 3..8), beef -1, coal 0. The warp picks
  from the once-per-warp preselected list; with real cook jobs at 1.0 the band drops cook/fuel and
  cook/extract out of it, so nothing is fuelled or extracted. (The same trap as the "Tried and
  dropped" note in `jobs/COMPLEX.md`: gating ALWAYS_CONSIDER also fed the warp list.)
- `cook/fuel [fuelled_output_ready]`, `[storage_full]`, `cook_extract_food` (REALTIME): cooked food
  never left the furnace (container expectation 0 of >=3).

So cook/fuel and cook/extract are not no-ops in general: they are the furnace's fuel and output
stages, and they only look like no-ops while the furnace is idle. A score band that ignores the
furnace state starves them. Reverted (no commit).

Candidate for the dev (not implemented): score an ALWAYS_CONSIDER job by whether its slot
precondition holds *now* in realtime (fuel: a furnace lacks fuel; extract: a furnace holds output),
1.0 when it does and banded when it doesn't, while the warp's preselected list keeps every
ALWAYS_CONSIDER job unconditionally (the warp checks the furnace per step). B (score order) only
matters once A gives the scores meaning.
