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

## Update (2026-10-11 evening playtest — REOPENED)

Chat 22:12:51: "Economics shouldn't be showing yet. We haven't met a villager
yet." So economics is visible in a fresh world pre-villager even though it was
gated on `completedProceduralBatches >= 1` in 530c8923. Investigate: does the
gate fire early (e.g. a procedural batch completes during onboarding before any
villager), or is the economics **section** a different element from the gated
**tab**? The user's bar is "not until we've met a villager" — reconcile the
gate condition with that (first-villager, not procedural-batches).

## Fix (2026-10-11 evening)

The gate criterion was wrong: `completedProceduralBatches >= 1` is met by day 2
(onboarding batches tick over) even though no villager has arrived yet. The
player's raised bar is "not until we've met a villager", so `getInfo()` now sets
`showEcon = showVillagers` (`!getVillagers().isEmpty()`) — economics and the
villagers tab reveal together. `completedProceduralBatches` is still used for
quest seeding + the batch milestones (unchanged); it just no longer drives the
tab gate. Build-verified; needs a live client to confirm economics is hidden
pre-villager.
