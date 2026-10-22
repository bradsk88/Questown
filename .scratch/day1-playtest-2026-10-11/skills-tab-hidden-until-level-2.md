---
title: 'Skills tab: hidden until level 2, then a new job layer unlocks every 5 levels'
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:26:37 + 22:27:45:

> Let's discuss the skills tab. I think I want the villagers to start with no
> unlockable jobs. So the skills tab will be hidden. After reaching level two,
> the first layer of unlockable jobs will become accessible and so will the tab.
> Every 5 levels will make another layer accessible, visible, and the jobs
> within it unlockable.

Design for the villager skills tab and job unlocks, driven by villager level:

- **Start:** no unlockable jobs; the skills tab is **hidden**.
- **Level 2:** the first layer of unlockable jobs becomes available, and the
  tab appears.
- **Every 5 levels after:** another layer of jobs becomes accessible/visible and
  its jobs become unlockable (L7 → layer 2, L12 → layer 3, ...).

## Acceptance criteria

- The skills tab is hidden while a villager has no unlockable jobs (pre-level-2).
- At level 2 the tab appears and layer 1's jobs are unlockable.
- At levels 2 + 5k a new layer unlocks (layer N at level 2 + 5·(N-1)).
- Ties in with the tab-gating theme (economics/villagers tabs) — the skills tab
  gets its own level-based gate.

## Notes

This is a design that layers job availability on villager level. Find where
villager level is tracked and where the skills tab's visibility + the set of
"unlockable" jobs are decided; introduce the layer model (layer index from
level). Related: `skills-tab-icons-stacked.md` (the rendering bug this would
render moot pre-L2, but still fix). Confirm the exact layer→jobs mapping with
the data (which jobs are in which layer) before implementing.

## Resolved (audit 2026-10-22)

Shipped as ruled (2026-10-13 Q2: per-job available_at_level, no layer system): d942eed8. Townies start at level 1, the tab shows at level 2; tests JobAvailableAtLevelDataTest + WorkAvailableAtLevelTest.
