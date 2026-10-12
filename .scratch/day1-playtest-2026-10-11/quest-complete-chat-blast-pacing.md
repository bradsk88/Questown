---
title: Pace out the chat text blast on welcome-mat quest completion
status: ready-for-human
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

## Triage notes

Design item: pace / coalesce the quest-complete chat blasts. Needs a product decision on the cadence.

## Decision (2026-10-13, walkthrough E2)

A **tick-delayed broadcast queue on the flag for ALL broadcast messages** —
a debounce: messages that already arrive spread apart are unaffected;
bunched ones get even spacing. Target cadence **~5s (100 ticks)** between
consecutive deliveries. Implementation: pending-message queue + last-delivered
tick on the flag's broadcast path (the `messages`/`broadcastMessage` handle);
the per-tick pump delivers the head once the spacing is elapsed.
