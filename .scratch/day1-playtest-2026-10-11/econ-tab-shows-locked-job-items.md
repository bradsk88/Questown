---
title: Economics tab lists demand for items no villager can make (locked jobs)
status: implemented
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:14:35 (evening from-zero run, `/tmp/dev-server.log`):

> Still seeing "axes" and "fixshing rods" in the econ tab, when no villagers
> have those jobs unlocked yet.

The economics tab shows unmet-demand / need entries for **axes** and **fishing
rods** even though no villager has the jobs that produce them unlocked. So the
econ tab is advertising needs that nothing in the town can actually satisfy —
which is confusing (and the player is right to flag it).

## Acceptance criteria

- The economics tab only lists needs/items that at least one villager can
  actually produce (i.e. whose producing job is unlocked/available), OR it
  clearly marks needs that are currently unproducible.
- No "phantom" demand for items whose producer job is locked.

## Notes

Likely the unmet-need / demand computation aggregates `initial_request` / needs
across all jobs (including locked ones) rather than only the jobs the current
roster can run. Check the economics aggregation (`getAggregatedItems` / the
unmet-need registration) — it should filter to unlocked/available jobs, or at
least distinguish "wanted but unproducible." Ties into the skills-tab layering
design (`skills-tab-hidden-until-level-2.md`): before level 2, few jobs are
unlockable, so the econ tab should reflect that smaller set. Use
`scripts/job-producers.py` to confirm which jobs produce axes / fishing rods and
whether those jobs are locked at this roster stage.

## Live check + root cause (2026-10-11, settled)

**A fresh world does NOT show "axes"/"fishing rods."** Ran a client-free held
`village/self_sufficiency` (fresh seed, 200 t/s), roster = `gatherer/gather`
(no-tool root) + `hunter/sword` (root) + cook. Dumped `_qtdev inspect economy
0 64 0` twice, 40 s apart: the econ tab showed exactly **one** entry,
`#questown:swords` (the hunter's legit root-job tool — the onboarding sword the
player supplies). No axes, no fishing rods, stable across both dumps.

**Root cause (code-confirmed):**
- The only gatherer variant with `parent=None` (a progression root → in the
  initial unlock set) is `gatherer_unmapped_notool_short`. Every axe/rod/shears
  variant has a `parent` and none set `unlocked_with_root_job`, so a
  **brand-new gatherer gets only the no-tool variant**; the tool variants are
  BOP/progression-gated (the user's assertion, confirmed).
- `WorkSeekerJob` gates performed work on `VillagerHolder::isUnlocked`, so a
  villager only *performs* unlocked jobs — a fresh gatherer can never perform
  an axe/rod variant and can never register an "axe"/"fishing rod" need.
- A no-tool gatherer's work states need *food* (`#villager_food`), not an axe.
- The unmet-need queue is an `EvictingQueue` (depth `ECONOMIC_RECORDS_DEPTH`)
  that is **never cleared when a villager changes jobs** — so a need registered
  earlier lingers until depth-eviction. That is the stale channel.

**Conclusion (superseded 2026-10-13 — see below):** the playtest's
"axes"/"fishing rods" were either stale `EvictingQueue` entries or a BOP-spent
world. The design decision remains how the econ tab should (a) prune stale
needs and (b) present tool-needs.

## Live repro + settled root cause (2026-10-13)

Reproduced on a fresh from-zero world (soft probe
`-Dquestown.needcrashprobe=soft`, `/tmp/dev-server.log`). The probe fired
**LIVE** (not a stale-NBT reload) for both tools, and the econ tab showed
`#questown:fishing_rods`, `#questown:axes`, `minecraft:shears`,
`#questown:villager_food` simultaneously. A live `_qtdev work_score` showed
`preselected=[]` (no gatherer variant above `MIN_JOB_ACCEPTANCE` — food was
out and no tools existed), so `applyScores` ran the `registerUnmetNeeds`
branch on every recompute pass.

**Root cause — two paths escaped the 2026-09 unlock gate (`de2a30e2`,
"Gate work selection on per-villager unlocks, not the town-wide set"), which
gated only `WorkSeekerJob`'s need check and `TownPossibleWork.nextForVillager`:**

1. **`TownVillagerData.FallbackSelector.tryFallback`** (called from
   `TownFlagBlockEntity.changeJobForVisitorFromBoard` when
   `nextForVillager` returns null): picks from `getPreferredWork` (the
   town-wide preferred list, shuffled) and the shuffled town-wide preselected
   set — **no `isUnlocked(villagerUUID, …)` filter**. With `preselected=[]`
   the fresh gatherer churned `gatherer:axe` → `gatherer:shears_full_day` →
   `gatherer:axe_half_day` (each hitting NO_SUPPLIES at state 0 and
   LIVE-registering its tool need). Same bug class as the 2026-09 fresh-hunter
   sword-tier climb, different root.
2. **`TownPossibleWork.registerUnmetNeeds`** (root-level, fires when the root
   has no viable work): picks the best-scoring variant from **all** variants
   (no unlock filter; scores carry a randomizer, so the best wobbles between
   no-tool gather and tool variants) and registers its gate tool for **every**
   townie of that root. Even with zero unlocked tool variants, the econ tab
   shows axe/rod demand.

**Git archaeology (why the fallback was never gated):** the random fallback
predates the unlocking system by ~10 months — shuffled preference pick in
`04c81748` (2024-08-10, "Code cleanup"), 100-call `preferredBuffer` in
`86518ce9` (2024-08-31, "Release polish"); the unlock system (`unlockJob`/
`isUnlocked`/`unlockedJobs`) landed 2025-06-15 (`86f5c03d`, "Experience and
Leveling"). In 2024 "town-wide set" == "this townie's set" (no unlock
concept), so the fallback was correct as written. `34f4a7ab` (2026-04-05)
replaced the main path's random pick with the round-robin `JobCycler`
("cycling through available jobs round-robin instead of picking randomly" —
the 2024 `TODO: [ASAP] Use a job attempt counter`); `de2a30e2` (2026-09-23)
added the per-villager gate to main path + WorkSeeker. Neither went back and
gated the fallback or the root-level need registration. The 2024 TODO
literally predicted this bug: "keep choosing 'gather with axe' over and over
while there are no axes in town."

**Secondary (Layer 2, confirmed live):** the unmet-need queue is not cleared
when a villager changes jobs — the villagers inspect showed current job
`gather_half_day` while the displayed need was "Fishing Rods" (a
stale cross-variant entry from an earlier churn cycle).

**Fix (implemented 2026-10-12, option b — retire the fallback; user decision):**

1. **`FallbackSelector` removed** from both call sites — the board path
   (`TownFlagBlockEntity.changeJobForVisitorFromBoard`) and the warp path
   (`TownFlagState.createWork.getRandomFinishableWork`). When the cycler /
   preselected set finds nothing, the board's idle-downtime escape hook (or the
   warper's dynamic resolution, in warp) handles the townie. The cycler already
   carries the fairness role the 2024 shuffle was a stopgap for, and the
   per-villager unlock gate (`de2a30e2`) now has no ungated bypass.
2. **`TownPossibleWork.registerUnmetNeeds`** — the best-variant pick is
   filtered on the town-wide `isUnlocked(JobID)` (at least one villager must be
   able to run that variant), so a root whose only viable path is a locked tool
   variant no longer registers that variant's tool need for every townie.
3. Layer 2 (clear-on-job-change) was **not** added: with the fallback gone,
   townies only ever perform unlocked variants, and the root-level
   register/clear pass re-binds needs to the (now unlocked-only) best variant
   every recompute — the cross-variant staleness had no production source left.

Docs: `town/COMPLEX.md` (work-selection + unmet-need sections) and the
hungry-townie flow node in `town/entity/COMPLEX.md` re-pointed.
