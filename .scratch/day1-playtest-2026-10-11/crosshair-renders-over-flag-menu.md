---
title: Crosshair renders on top of the flag menu
status: resolved
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

## Triage notes

No HUD override in the mod; needs visual confirmation of whether the crosshair draws over the flag menu. Screenshot evidence captured 2026-10-11 (run-client/diags/flag_villagers.png); vision proxy was down, so LLM content-check is pending.

## Update (2026-10-11 evening playtest)

Reproduced again — chat 21:50:52: "Crosshair is still rendering above menus. Looks like we didn't fix that." So this was never fixed (or a fix regressed). It is a confirmed GUI bug: the crosshair must not draw while a flag screen is open. Check 1.20.6 overlay/crosshair suppression on the flag screens (they should declare they suppress the crosshair / the `RenderGameOverlay` pass should skip it when a screen is open).

## Root cause + Fix (2026-10-11 evening)

Root cause: `QuestownHudEvents.onScreenRenderPost` re-renders the **entire**
`Gui` layer stack after the card-screen family (to restore 1.19.2 HUD-on-top-of-
menu layering, since 1.20.6 draws the HUD *before* the screen). In 1.20.6 that
full re-render includes the crosshair layer, so the crosshair pops back on top of
the menu. Vanilla's own crosshair layer is gated only by `!hideGui` (not by
screen-open), so it renders in that pass.

Fix: `QuestownHudEvents` now sets a `reRenderingHud` flag around the re-render
and a `RenderGuiLayerEvent.Pre` handler cancels just the
`VanillaGuiLayers.CROSSHAIR` layer during that pass (`event.setCanceled(true)`).
The normal pre-screen HUD pass is untouched (flag false → crosshair renders as
vanilla would, then the opaque screen background covers it). Build-verified;
needs a live client to confirm the crosshair is gone over the menu.
