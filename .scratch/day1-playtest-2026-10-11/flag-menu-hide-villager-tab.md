---
title: Hide the flag menu's villager tab until a villager has joined
status: needs-info
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

## Update (2026-10-11 evening playtest — REOPENED)

Chat 21:51:31: "The 'recall your townfolk' section shouldn't show until the
the town has villagers." So a villager-related **section** ("recall your
townfolk") is still visible pre-arrival even though the *tab* was gated in
530c8923. Two possibilities to check: (a) the tab gate in
`TownFlagBlockEntity.getInfo()` isn't actually hiding it in this build / the
condition fires early, or (b) "recall your townfolk" is a *section* outside the
gated tab (e.g. in the quests or main flag view). Locate the section's lang
key + owner and gate it on the same first-villager condition.
