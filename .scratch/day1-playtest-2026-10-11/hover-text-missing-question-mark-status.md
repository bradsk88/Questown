---
title: Hover text missing for a villager with the question-mark status
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:28:42 + 17:28:50 (`run/client2.log`, one
message):

> Hover text is not working for a villager with the question mark status.
> (When I hover over their status icon)

## Notes

Predecessor/sibling of `villager-menu-status-icons-no-tooltips.md`
(17:35:49: NONE of the status icons show tooltips) — this one is
specific to the "?" status icon. Check whether the "?" status has a
tooltip key registered at all, and whether the shared no-tooltips bug
covers it.

## Triage notes

Same root cause and fix as villager-menu-status-icons-no-tooltips (the 1.20.6 dead renderTooltip override). Batch 2.
