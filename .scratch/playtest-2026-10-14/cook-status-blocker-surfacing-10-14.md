---
title: 'Cook status: still "no place to work" when the real blocker is missing raw food / cooked meat unextracted (2026-10-14 recurrence)'
status: ready-for-human (surfacing facet TDD'd per the 2026-10-15 ruling — union of live needs; the behaviour-side coin flip — idle no-op jobs tie with real work, facet 3's status text — remains a separate decision-layer item, now scoped with three candidate mechanisms + recommendation in `.scratch/cook-scoring-tie/always-consider-noop-tie-mechanism.md`)
---

# Cook status/blocker surfacing — 2026-10-14 playtest recurrence

Captured 2026-10-14 (server-20, in-game chat 04:37:51–04:40:33):

> "Once again The Cook did NOT indicate (through bubble or economics tab)
> that coal was the blocker."
> "Their status says 'cook has nowhere to work or they are looking for
> tools'. But now they have a fueled oven (they just fueled with the coal
> I provided) and raw food available."
> "My mistake. I had removed all the raw food. But still. They should have
> said 'needs raw food'"
> "And now there's freshly cooked meat in the oven and they still say
> 'has nowhere to work'"

Three facets, all on the 1.20.6 branch **after** the Q3 surfacing commit
(f1b5967c, "status text + econ tab names coal"):

1. **Coal blocker not surfaced**: user had to discover the coal need
   themselves; neither the bubble nor the econ tab said it. Verify whether
   the f1b5967c surfacing applies to this cook state (the Q3 fix targeted
   `smelterFuelBlocker`-style gating — the cook's gate may be a different
   one).
2. **Missing raw food not named**: with the oven fueled and no raw food,
   the status said "has nowhere to work / looking for tools" — should say
   "needs raw food" (the supply gate should name the missing supply).
3. **Cooked meat in the oven, still "no place to work"**: extraction
   should be the workable state; the gate is lying. Dump the decision
   (the `questown-status-why` pattern: inputs to the branch that sets the
   status) on a repro.

Related: `.scratch/day1-playtest-2026-10-11/cook-status-says-nowhere-to-
work-when-blocked-on-fuel.md` (the day-1 fuel facet) and
`econ-tab-top-blocker-missing-coal.md`.

## Findings (2026-10-14 night, live instrumented run on the playtest world)

Server-22 (same world, `-Dquestown.tp.fifo` console + `forceload` +
`_qtdev work_score 0 67 280 cook` dumps), evidence in
`/tmp/dev-server-22.log` (05:44–05:52):

1. **The first "No rooms match base room questown:kitchen_small" reading was
   an observation artifact, not the user's bug.** With no player connected
   the flag ticker is stopped (log: "Town flag at 0,67,280 stopped ticking
   because: No pl[ayer]"), room detection never runs, and the score dump
   reports no rooms at all. Player joins + TP next to the flag → kitchen
   matches, cook-food jobs read "All ingredients and tools available".
   (The user's player WAS present 04:37–04:40 on server-20, so this artifact
   did not apply to their window; s19 04:24:31 "room_changed" +
   "quest_completed" broadcasts show dynamic re-matching was live then.)
2. **The real mechanism: the cook's selection is a near-uniform coin flip
   over ~7 tied jobs.** Live `preselected=` (s22, after join):
   `[stock_ingredients, stock_fuel, fuel, porkchop, mutton, beef, extract]` —
   every score within ~0.01 of 1.0 (the "randomizer" is the differentiator;
   e.g. porkchop 1.009 vs extract 1.005 vs fuel 1.001). `fuel` and `extract`
   carry `always_consider` (bypass the room/workable gate) and are no-ops
   when the furnace is lit with nothing to extract / doesn't need fuel — yet
   they tie with the actual cook-food jobs. When the flip lands on a no-op,
   the job has no workable spot → status "has nowhere to work" → 50-tick
   cooldown → re-roll. That flip-flop IS the 04:36–04:40 oscillation
   (extract↔fuel every ~5s in the s20 log, 20+ cycles).
3. **All three facets are the same coin flip seen from the surfacing side.**
   The econ-tab/bubble blocker names the best-scoring job's need, and the
   tracker replaces the townie's key every pass (`UnmetNeedTracker.enter`
   removes prior rows for the townie+root). So the named blocker flaps
   pass-to-pass between "coal" (when fuel/stock_fuel wins), "raw food" (when
   a cook-food job wins), and nothing at all (when extract wins — its
   NoRequest/NoRoom carries no supply item):
   - facet 1 (coal un-named): fuel wasn't best-scoring on the passes the user
     looked at; the Q3 coal-naming (f1b5967c) only fires when the smelter
     job is the one that fails its room check, not a general supply-gate
     naming.
   - facet 2 ("needs raw food" un-named): same flapping — a cook-food job
     only names raw food on the passes where it best-scores.
   - facet 3 (cooked meat, "nowhere to work"): extract best-scored; whether
     extract's own workable-spot logic was also lying is NOT settled (needs
     a [WHY] dump at the moment the furnace holds output — this run's
     furnace was cold/empty, so extract was a genuine no-op).

## Ruling needed (decision layer, outside Q3's "surfacing only")

The defect is in selection/scoring, not the surfacing plumbing: the
surfacing faithfully reports the (flapping) best-scoring job's need. A fix
would make no-op-when-idle jobs not tie with real work — e.g. `fuel`/`extract`
score from their actual workability (needs unlit+empty furnace / lit furnace
with output) instead of `always_consider`-bypassing the gate, or the tracker
keeps the union of a townie's live needs instead of replacing the key each
pass. Both are decision-layer changes the Q3 ruling ("surfacing only")
excluded. Parked for the user with this evidence.

## Ruling (2026-10-15) + TDD outcome

The user picked the **union of live needs** as the hint semantics (not a
priority ordering): "the fuel is a real need until fuelled, and the raw food
is a real need until cooked. The hint should show the UNION of those —
whatever the current state is" — motivated by the **job independence
principle** (any individual job should be removable without meaningfully
impacting the other jobs' abilities to be selected and to run; now in
`CONTEXT.md` + `docs/design-principles.md`), which rules out a
fuel→extract→cook-food priority as a fix.

Implemented (TDD, this session):

- `UnmetNeedTracker`: `enter` no longer replaces the townie's key for the
  root — it joins the live set; new `applyPass` enters the root's live named
  keys each recompute pass and exits the keys that dropped out (per-key
  liveness, not only the whole-root clear), returning the pass's first-enters;
  new `exitKey`; new `primaryKey` = the townie's oldest live named key
  (min `since`, ties key-ascending) — the bubble's single item. 14 tests
  (4 red-verified against the pre-fix replace-on-new-key `enter` in a /tmp
  variant); `:test` full suite green (1247 tests, 0 failures).
- `TownPossibleWork`: `refreshUnmetNeeds(root, unfilteredJobs)` replaces
  `registerUnmetNeeds`/`clearUnmetItemNeeds` — every recompute pass (not
  only no-viable-job passes) computes the union of named keys across all
  unlocked candidate jobs (a job above the acceptance threshold can still be
  blocked on a supply, which is exactly the user's scenario), diffs it
  against the tracker, writes one econ record per first-enter supply key,
  and sets each townie's `UNMET_ITEM` bubble to its `primaryKey`. The
  `smelterFuelBlocker` coal naming (f1b5967c) now flows through the same
  union: an unlit furnace contributes `#minecraft:coals` from its smelter
  job's gate alongside `#questown:raw_food` from the cook-food jobs.

Facet status after the fix: facet 1 (coal un-named) and facet 2 (raw food
un-named) are closed for the hint side — both keys stay live until each is
satisfied, so the bubble/econ tab keep naming the oldest outstanding one
instead of flapping with the pass's argmax. **Facet 3's status text**
("has nowhere to work" while the flip lands on an idle no-op job) is the
behaviour side of the same coin flip — out of scope for this change; a
per-job workability-scoring fix for the `always_consider` no-ops remains a
separate open item (it would also be job-set-agnostic: a no-op job simply
scoring below real work when it has nothing to do).

Mechanism options (A: score-band ALWAYS_CONSIDER jobs; B: carry scores into
selection; C: hysteresis) + recommendation: `.scratch/cook-scoring-tie/
always-consider-noop-tie-mechanism.md` (written 2026-10-15 while the
scoring-side decision awaits a ruling).
