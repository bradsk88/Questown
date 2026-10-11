---
title: The "highest demand item" quest keeps getting replaced instead of staying added
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:36:37–17:38:59 (`run/client2.log`, one
thought across four messages):

> My current batch of quests is a bit confusing.
> I see: raw food, four swords, and unlock hunter - those all make
> sense. Bedroom too.
> But I also have an ADDITIONAL quests for stone sword x1, and a
> job-change quest that seems to change every time I open the menu...
> If that's the "open a quest for the highest demand item" - it should
> NOT be constantly getting replaced, it should eget added and STAY
> added.

## Notes

The demand-driven extra quest (a JOB_UNLOCK-style quest for the stuck
root) visibly changes target every time the menu is opened. Note: the
playtest ran WITH the bug-1 fix (`8f670d10`, per-stuck-root dedupe +
in-place retarget) — the fix stops the 214-phantom accumulation but the
*retarget-in-place* behavior is exactly what the player experienced as
"changes every time I open the menu". Direction: pick the demand target
once and keep it until it completes (or the demand actually changes),
not re-roll per menu open / per need cycle.

## Triage notes

Design item: the demand quest is constantly replaced, churning the UI. Needs a product decision on replacement stability.
