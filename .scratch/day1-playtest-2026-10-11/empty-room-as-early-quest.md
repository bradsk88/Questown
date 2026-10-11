---
title: Make "empty room" one of the first quests
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:23:12 (`run/client2.log`):

> It might actually make sense to make "empty room" one of the first
> quests. As a fresh player without a chicken, I might not understand
> the first two quests without that fundamental knowledge.

## Notes

Day-1 onboarding design: without the chicken, the opening quests
(campfire, mat) assume the player knows what a (room around a) flag
means. Teaching "an empty room" first would give the prerequisite
knowledge. Interacts with the mat-gated-arrival design (`.scratch/
mat-gated-arrival/`) — the chicken currently builds that room as part of
its door beat.

## Triage notes

Design item: make 'build an empty room' an explicit early quest to teach room registration. Needs a product decision + quest data.
