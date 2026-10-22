---
title: Stats page: show the villager's level ("Lvl N") right-aligned on the experience line
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:29:22 (evening from-zero run, `/tmp/dev-server.log`):

> On the stats page, On the same line as the "experience" text, but right
> aligned, it should say Lvl 2 (or whatever level they are)

On the villager stats page, the level should be displayed **right-aligned on the
same line as the "experience" text** — e.g. "experience ......... Lvl 2".
Currently the level isn't shown there.

## Acceptance criteria

- The stats page shows the villager's current level, right-aligned on the same
  line as the "experience" label.

## Notes

Small UI change on the villager stats screen. The level data is already tracked
server-side (`SimpleVillagerHandle.levels`, `getExpForCurrentLevel`) and likely
already in the villager UI payload (`TownVillagerUIs`). Find where the "experience"
line is drawn on the stats screen and add a right-aligned "Lvl N" using the
existing flow layout (`docs/solutions/conventions/gui-layout-flow-and-linter.md`
— flow layout, not hardcoded positions; verify with the `gui-lint` oracle).
Ties into `stats-page-show-didnt-eat-didnt-sleep.md` (same stats-page surface).

## Audit (2026-10-22)

Still open: the experience line (`VillagerStatsScreen.renderExperience`) shows no level. In the dev-approved UI batch (2026-10-22).

## Resolved (audit 2026-10-22)

"Lvl N" right-aligned on the experience title line (VillagerStatsData.level over the wire). Verified live r8 2026-10-22.
