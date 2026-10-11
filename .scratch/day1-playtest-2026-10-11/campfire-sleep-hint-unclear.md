---
title: Campfire-sleep notification is cut off and never says HOW to sleep
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:08:52 + 17:09:05 (`run/client2.log`, one
message):

> Because I didn't have the chicken, I guessed that I should make the
> campfire first. As a player, the only input I got from the game is a
> top-right notification saying "sleep on the campfire whe" (cut off),
> But I wouldn't know HOW to sleep on the campfire with my current level
> of informaiton.

## Notes

No-chicken day-1 path. Two separate defects in one message: (1) the
notification text is truncated mid-sentence ("sleep on the campfire
whe"); (2) even complete, it states the *what*, not the *how* — the
player did not know the mechanic for sleeping on a campfire. Related:
`.scratch/chicken-arc-campfire-sleep/` (the chicken path that was absent
here).

## Triage notes

Fixed in 530c8923: en_us.json campfire_ready toast shortened to one line.
