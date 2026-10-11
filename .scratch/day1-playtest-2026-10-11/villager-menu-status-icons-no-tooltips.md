---
title: Status icons on the flag-villager menu have no tooltips
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:35:49 + 17:35:52 (`run/client2.log`, one
message):

> Ok yeah, so NONE of the status icons on the flag-villager menu are
> showing tooltips. They shoul dbe.

## Notes

The villager-stats/status icons (the 32×32 status art row in
`VillagerStatsScreen`-area code) show no tooltip on hover. Each icon
should carry its meaning in a tooltip.

## Triage notes

Root cause: 1.20.6 AbstractContainerScreen.render no longer calls the renderTooltip override (dead path). Fix (uncommitted batch 2): removed the dead override from AbstractPagedCardScreen and call renderTooltipContent(ctx, mouseX, mouseY) at the end of renderScreenContent. Same root as hover-text-missing-question-mark-status.
