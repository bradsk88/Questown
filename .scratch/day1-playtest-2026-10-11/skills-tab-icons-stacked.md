---
title: Skills tab renders broken — icons stacked at the center, only "gather" visible
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:19:28:

> The skills tab seems broken? It looks like mutliple icons are stacked in the
> same position center). And the villagers only seem to have the "gather" job
> visible.

The villager skills tab renders multiple job icons stacked in the same
(centered) position instead of laid out, and only the "gather" job is visible
per villager.

## Acceptance criteria

- The skills tab lays out each job icon at its own position (no stacking/overlap
  in the center).
- A villager's unlocked + unlockable jobs are all visible, not just "gather".

## Notes

Likely a 1.20.6 port/layout regression in the skills/villager screen (the
per-villager skill tree layout). Grep the villager skills screen
(`VillagerSkillsScreen` / the skill-tree layout) for the icon-position
computation — icons collapsing to center usually means a coordinate that
defaults to 0 / a missing per-node offset. "Only gather visible" may be the same
root cause (all nodes at the same pos, one drawn last) or a separate data
issue. Companion design item: `skills-tab-hidden-until-level-2.md`.

## Audit (2026-10-22)

d15ad88d fixed the skills-pane border bleed and tab icons; the stacked-icons layout itself is unverified since. Check in this audit's client session (UI batch), together with the placeholder 'test'/'test2' text in VillagerAdvancementsScreen (r3 finding #16).

## Verified (2026-10-22, live r8, `run-client/diags/r8r-skills.png`)

Laid out: root (green) -> leather boots -> a column of 5 gatherer jobs, each at its own position;
not stacked, and more than "gather" shows. Sibling nodes abut exactly as in the 1.19.2 golden
(`tools/porttest/goldens/1.19.2/skills.png`). Judge item, not fixed: with 5 siblings the column
runs past the pane's top and bottom edges (the golden only had 2).
