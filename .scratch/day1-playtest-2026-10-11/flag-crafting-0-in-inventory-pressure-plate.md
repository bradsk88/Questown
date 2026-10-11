---
title: Flag crafting menu shows "0 in inventory" for a pressure plate the player has
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:04:04 + 22:04:27:

> Flag Crafting menu says "0 in inventory", but i do have a presure plate in my
> inventry.
> Maybe because it's spruce? But that sholnd't matter.

A flag-craft recipe that needs a pressure plate reports "0 in inventory" even
though the player is holding one. The player's guess: it's a *spruce* pressure
plate and the counter/recipe is exact-matching a specific plate (e.g. oak)
instead of accepting any pressure plate.

## Acceptance criteria

- The "N in inventory" badge for a recipe ingredient reflects the items that
  actually satisfy the recipe's ingredient (a wooden pressure plate should count
  if the recipe accepts any pressure plate).
- Either the recipe accepts any pressure plate (tag-based) and the counter
  matches, or the recipe genuinely requires a specific plate and the UI makes
  that clear.

## Notes

Two things to check: (1) how the flag-craft "in inventory" count is computed —
does it count by the ingredient's matching rule or by an exact item id? (2) what
the recipe's pressure-plate ingredient actually is (a specific block vs a tag /
any-plate). The likely fix is making the counter use the same matching as the
recipe's ingredient. Grep the flag-craft screen for the "in inventory" count and
the recipe ingredient definitions.

## Fix (2026-10-11 evening)

Root cause: both the server (`FlagCraftMessage.craftWelcomeMat`) and the client
(`FlagCraftingScreen`) exact-matched `Items.OAK_PRESSURE_PLATE`, so a spruce (or
any non-oak) plate counted as 0 and couldn't be consumed. Both now use
`Ingredient.of(ItemTags.WOODEN_PRESSURE_PLATES)` (the same tag the codebase
already uses in `TownFlagBlock`/`ChickenArcConditions`): server
`consumeMatching(Ingredient)` consumes any wooden plate; client
`countMatching(Ingredient)` drives the "N in inventory" badge and button
gating. The recipe icon still shows an oak plate as the representative.
Build-verified; needs a live client to confirm the count shows for a held
spruce plate.
