---
title: A "big bedroom" quest (capacity for several villagers) instead of one bedroom per villager
status: wontfix
created: 2026-10-11
---

## Decision (2026-10-11, design walkthrough)

**Superseded — dropped in favor of keeping one `bedroom.json` quest per
villager.** The real issue was not the bedroom size but that the game was
*under-posting* bedroom quests (2 villagers, only 1 bedroom quest). See
`bedroom-quests-under-posted.md` for the live issue. `big_bedroom.json` stays as
unused recipe data.

## Context

Playtest chat, 2026-10-11 23:25:23 (evening from-zero run, `/tmp/dev-server.log`):

> We currently have two villagers in town but only one bedroom quest... I feel
> we should have a "big bedroom" quest instead.

The town has two villagers but only a single bedroom quest. The current model
seems to be one bedroom (and thus one bedroom quest) per villager. The player
prefers a single **"big bedroom"** quest that, once built, has capacity for
several villagers — rather than building a separate bedroom for each one.

## Acceptance criteria

- A single "big bedroom" quest provides sleep capacity for multiple villagers
  (not one villager per bedroom).
- The number of bedroom quests does not scale one-to-one with the villager
  count.

## Notes

Design decision. Investigate how sleep capacity / villager count currently map
to bedrooms (the bedroom room-recipe, bed count, and how `handleMorning` /
sleep assigns villagers to beds). The change is to decouple "number of bedroom
quests" from "number of villagers": one big bedroom (with enough beds / area)
covers the roster. Consider what defines "big" (bed count? room area? a distinct
room recipe) and whether the onboarding batch that posts the bedroom quest
(`MCQuestBatches` / the basics batch) should post a single big-bedroom quest
instead of one per villager. Ties into the onboarding quest batch design
(`docs/onboarding-chicken/REQUIREMENTS.md` day-one flow) and the two-villagers
finding (`two-villagers-after-first-sleep.md`).
