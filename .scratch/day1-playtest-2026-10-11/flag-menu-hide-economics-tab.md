---
title: Hide the flag menu's economics tab until onboarding is done
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:11:05 (`run/client2.log`):

> Economics should be absent too until a villager arrives. It spoils too
> much content tooearly.

Follow-up, 17:16:50 (same message, refined):

> I'd argue the economics tab should not show up until the onboarding
> quests are done. Let the onboarding quests guide the player initially,
> after that they can use the economics tab to play more openly.

## Notes

Open question at triage: reveal gate = first villager arrives, or all
onboarding quests complete (the 17:16 message is the later bar). Same
theme as `flag-menu-hide-villager-tab.md`.

## Triage notes

Fixed in 530c8923: TownFlagBlockEntity.getInfo() hides economics until completedProceduralBatches >= 1. Live-verified 2026-10-11: 'economics hidden' early.
