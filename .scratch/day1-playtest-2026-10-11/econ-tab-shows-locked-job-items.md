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
