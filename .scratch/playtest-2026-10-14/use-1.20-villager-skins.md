---
title: 'Utilize the new 1.20 villager skins (plus our custom ones)'
status: done
---

# Use the new 1.20 skins

Captured 2026-10-14 (server-20, in-game chat 04:53:03):

> "We should update questown to utilize all the new skins that were adding
> in 1.20 (plus our custom ones)"

1.20 added new villager skin variants (profession + biome variants).
Questown's townies currently render with a fixed set of skins; the ask is
to (a) pick up the vanilla 1.20 skin variants and (b) keep/expand the
mod's custom skins on top. Investigate how the townie entities assign
`VillagerData` (type/profession/biome) today and where the custom skins
live (entity models / textures), then scope the mapping of Questown job
roots to the new vanilla variants.

## Decision (2026-10-14, user) — scope

**No job-to-skin mapping yet.** Townie skins are currently chosen by UUID
hash from Steve, Alex, and a handful of custom skins. Minecraft recently
added more player skins alongside Steve/Alex (1.20.5+); task is to add
those new vanilla variants to the UUID-hash skin pool. Find which ones
1.20.6 ships and wire them into the existing selection code.

## 2026-10-14 implemented

Verified against the 1.20.6 client jar (`textures/entity/player/slim/` +
`wide/`): vanilla ships **9** character skins — alex, ari, efe, kai, makena,
noor, steve, sunny, zuri. The pool now = the 9 vanilla slim variants (the
townie model bakes from `PLAYER_SLIM`) + the 6 custom `questown` skins,
picked by floor-mod UUID hash (deterministic; `Math.abs` mis-picks on a
`MIN_VALUE` hash). Extracted to the pure `TownieSkins` (pool + pick) so the
renderer stays a thin wrapper; pinned by `TownieSkinsTest` (pool
composition matches the jar, slim-only, determinism). `VisitorMobRenderer`
now delegates to it.

## Resolved (audit 2026-10-22)

`mobs/visitor/TownieSkins` (pool + deterministic pick), `TownieSkinsTest`; `VisitorMobRenderer` delegates.
