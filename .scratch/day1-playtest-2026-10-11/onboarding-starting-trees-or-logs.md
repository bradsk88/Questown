---
title: Seed the empty town structure with trees or fallen logs
status: implemented
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:25:31 (`run/client2.log`):

> The empty town structure should probably have some trees or fallen
> logs. Enough to build what is needed for onboarding.

## Notes

Worldgen/seeding design: the fresh town's starting area should carry
some wood (trees or placed logs) so a no-chicken player has material for
the onboarding builds. Scope question at triage: deterministic seed in
the structure vs. biome-dependent (biomes without trees would break the
flow).

## Triage notes

Design item: give day-1 players starting trees or logs so gatherer onboarding does not gate on distant trees. Needs a product decision + spawn data.

## Decision (2026-10-11, design walkthrough)

**Seed the fresh town's starting structure with trees + fallen logs, deterministically,
biome-independent, placed relative to the flag.**

- **Trees:** 2–3 fixed-shape trees (the existing `TreeFeatureResolver` seeds a
  sapling's grown shape by `pos.asLong()`, so a tree at a given position is
  reproducible) — gives the natural "chop a tree" first task.
- **Fallen logs:** a line of horizontal vanilla log blocks oriented like a felled
  trunk (the player's visual — not an item pile) — guaranteed wood with no chopping.
- **Amount:** enough to build the onboarding needs (door + welcome mat + job board
  + a small margin for no-chicken room walls), not an open-ended supply.
- **Placement:** relative to the flag position, so it works in any biome (a treeless
  biome must not gate the flow — the seed is biome-independent).

Purpose: a day-1 player (especially no-chicken) always has starting wood; the
"empty town" reads as having a little timber rather than bare ground.

## Implementation (2026-10-12)

`TownFlagBlockEntity.seedStartingTimber()`, called from the first-tick
initializer under the existing `!isInitializedQuests` gate (same gate as
`setUpQuestsForNewlyPlacedFlag`; NBT-persisted, so loaded flags never
reseed). Seeds relative to the flag: a line of 8 horizontal oak logs 6
blocks clear (pickable wood, no axe) + 2 oaks via the existing
`MinecraftWorldAccess.growTreeAt` (reproducible per-position shape).
Overwrites nothing (air + solid-below checks); biome-blind by construction
(the tree feature is ground-checked only). Docs: `town/entity/COMPLEX.md`
"Starting timber seed". Verification pending: compile + full autotest suite
(no chicken-arc / village regression from the seeded blocks).

## 2026-10-12 user reaction (playtest chat 17:32:59)

> Interesting. I was not expecting trees and logs to spawn when I placed a
> flag. I thought that would just be part of the empty_world structure. I
> think I'm ok with it though.

Accepted, but the player had no idea the seed was an intentional onboarding
gift. Consider a one-line flag-placement chat ("A few oaks grew around your
flag for easy first wood.") so the seed reads as a gift, not worldgen
weirdness. Optional follow-up; no behavior change requested.
