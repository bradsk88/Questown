---
title: '"Your camp is ready" toast lingers past the first campfire sleep'
status: fixed
origin: 2026-10-15 playtest round 2 — user chat 05:36:31
---

# Campfire-ready toast stays up indefinitely

User report: "The 'Your camp is ready' toast is still up, even though
I've already wand-slept once."

## Root cause

`ClientAccess.showTutorialToast` uses vanilla's `TutorialToast`
(`net.minecraft.client.gui.components.toasts.TutorialToast`). Vanilla's
TutorialToast is a *persistent* toast: it stays on screen until the
player right-clicks a block — it does not auto-dismiss on a timer. The
"Your camp is ready" notification (`tutorial.campfire_ready`, fired by
`CampfireReadyReward` at 05:28:17 in the playtest log) therefore sat in
the toast stack until the player happened to right-click something.

## Fix (2026-10-15, same session)

`ClientAccess` now gives each tutorial toast a 20 s wall-clock deadline
(`TUTORIAL_TOAST_LIFETIME_MS`), tracked in an `IdentityHashMap` of
deadlines so queued-but-not-yet-active toasts expire too.
`ClientAccess.tickTutorialToasts()` hides each toast once its deadline
passes and is called from the existing client tick
(`CampfireSleepClientEvents.onClientTick`). The right-click-a-block
dismiss and the flag-menu `dismissTutorialToast()` (InventoryAndStatusScreen)
still work as before.

Commit: <pending> (session 2026-10-15 round 2)
