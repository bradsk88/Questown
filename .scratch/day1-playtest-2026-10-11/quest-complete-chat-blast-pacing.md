---
title: Pace out the chat text blast on welcome-mat quest completion
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:14:36 (`run/client2.log`):

> When you complete the welcome mat quest, you get blasted with chat
> text. It might be nice to pace them out a bit or something...

## Notes

On mat-quest completion the player receives a wall of chat messages
(quest-completed toast, town-gate-created notice, batch posting,
villager-arrival promise, …). Suggested direction: pace/stagger them
rather than emitting them in one frame.
