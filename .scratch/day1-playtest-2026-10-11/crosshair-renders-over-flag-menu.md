---
title: Crosshair renders on top of the flag menu
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:15:51 (`run/client2.log`):

> The player's crosshairs are rendering on top of the menu. They should
> be behid the menu.

## Notes

GUI bug: the crosshair layer draws over the open flag screen. Check
`RenderGameOverlay`/the flag screen's overlay-suppression on 1.20.6 —
the crosshair should not render while a screen is open.
