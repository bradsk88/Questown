---
title: Economics tab lists demand for items no villager can make (locked jobs)
status: needs-info
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

**Conclusion:** the playtest's "axes"/"fishing rods" were *not* a fresh
roster's live needs. They were either stale `EvictingQueue` entries from an
earlier state of that session, or a world where BOP had been spent on an
axe/rod gatherer variant. The design decision is how the econ tab should
(a) prune stale needs so it reflects the *current* roster, and (b) present a
tool-need (e.g. the legit `#questown:swords`) so it reads as "a townie needs
this tool" rather than bare "demand."
