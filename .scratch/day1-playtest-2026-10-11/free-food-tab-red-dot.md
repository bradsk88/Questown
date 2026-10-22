---
title: Red dot on the economics tab while the free-food offer is available
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:41:21 (`run/client2.log`):

> When that free-food action is available (one time, same one-time
> boolean as the dirt-food thing) that tab should have a red dot.

## Notes

Tab indicator (red dot) on the flag menu's economics tab while the
one-time free-food offer has not been taken. Pairs with
`free-food-economics-card.md`; same family as
`flag-incomplete-quests-indicator.md` (the "v4 tab indicators / red
dots" note in `docs/todo/bop-progress-indicator.md`).

## Triage notes

Design item: red-dot the free-food tab. Needs a product decision.

## Decision (2026-10-13)

Settled with the card (see `free-food-economics-card.md`): red dot on the
economics tab while `!givenBonusFood`, cleared on claim.

## Resolved (audit 2026-10-22)

`FlagTabs.java:158` passes `fi.bonusFoodAvailable()` as the economics tab's dot flag.
