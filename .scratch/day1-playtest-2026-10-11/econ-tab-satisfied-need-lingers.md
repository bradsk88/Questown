---
title: Econ tab keeps showing a satisfied need (swords lingered 5 days)
status: needs-triage
created: 2026-10-12
---

## Context

Fix-verification held run `village/self_sufficiency` (2026-10-12, 200 t/s,
log: `.scratch/day1-playtest-2026-10-11/fixverify-selfsuff-2026-10-12.log`):
`_qtdev inspect economy 0 64 0` showed
`ItemEconomicsData[ingredientKey=#questown:swords, timesNeeded=1]` in three
snapshots (03:13:55, 03:16:09, 03:23:46 — days 1 through 5).

The sword need was registered at day-1 morning (the fresh hunter's legit
root-tool need). The executor satisfied the open stone-sword quest at
~03:13 ("[seed] satisfied the open sword quest (real player path)") and the
hunter worked `hunter/sword` for the rest of the run — yet the econ tab kept
showing `#questown:swords timesNeeded=1` for the whole run.

## Why it matters

The unmet-need design (`town/COMPLEX.md`, "Unmet-need registration") says
`clearUnmetItemNeeds` clears the tracker for a root "the moment work exists
again" — a townie whose root job is now performing should not keep
advertising its old need on the economics tab. Either the clear path did not
fire for the hunter root, or the entry came from a source the clear path does
not own.

## Notes

Low severity (one entry, one need, self-evident once the sword exists). Check:
does `clearUnmetItemNeeds` run for the hunter root once the hunter performs
`sword`? Or is this entry a demand/work-request record (not an unmet-item
record) that lives in a different lifetime? The pre-fix 2026-10-11 live
repro showed the same lingering `#questown:swords` entry, so this predates the
2026-10-12 fallback retirement (which did not change need clearing).
