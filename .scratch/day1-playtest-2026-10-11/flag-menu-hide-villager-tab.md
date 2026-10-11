---
title: Hide the flag menu's villager tab until a villager has joined
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:10:37 (`run/client2.log`):

> When I open the flag menu before any villagers have joined, I should
> not see the villager tab. It should be totally gone.

## Notes

Pre-arrival day-1. The empty villager tab both confuses and spoils.
Companion item: the economics tab (see `flag-menu-hide-economics-tab.md`).

## Triage notes

Fixed in 530c8923: TownFlagBlockEntity.getInfo() hides the villager tab until the first villager. Live-verified 2026-10-11: 'villagers shown' once a townie exists.
