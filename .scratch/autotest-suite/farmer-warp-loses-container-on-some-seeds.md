---
title: 'Farmer warp variants all fail on some world seeds: the supply chest vanishes from the warp state'
status: resolved
origin: 2026-10-20 suite runs (2 of 7 farmer sets, then 1 of 4 parallel servers)
---

On some world seeds every warp farmer variant fails together (2/9 pass: only the realtime-only
ones), e.g. "minecraft:wheat: expected >= 1, got 0". On others all 9 pass. The outcome is fixed
per server (per world seed), not per scenario.

## Deterministic repro

`run/server.properties`: `level-seed=-598020885233501430`, then
`./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.only=farmer/harvest_wheat`
→ 2/9, twice in a row. Seed 2679425582945881195 passes 9/9.

## Not a regression

The same seed fails identically on 1456cef0 (2026-10-19, before the 10-20 carry-output,
cook-extract and hint changes).

## What differs (good vs bad seed, farmer/harvest_wheat warp)

- Identical up to "State captured, starting warp": same rooms, the supply chest at (5,64,2)
  holds the hoe + 32 seeds and is listed in the realtime `[WHY]` container dump.
- The post-warp snapshot (`--- Warp state snapshot ---`) lists NO container in the bad run
  (good run: `Container 0 (5, 64, 2): wheat, wheat_seeds, ...`), the farmer holds nothing, and
  the 32 seeds are gone. So the container drops out of the warp's MCTownState during the warp.
- Also seed-dependent: "Seeded starting timber at new flag (9 placements)" vs 10 — the fallen
  log line (z=6, x=-1..6) and trees at (8,6)/(-8,-6) around the flag; unproven link.

Next: with the bad seed, log the warp state's container list per warp step to find the step
that drops it (TownState.withContainerItemRemoved / ContainerTarget.withItemAdded returning
null are candidates).

## Root cause and fix (2026-10-20)

The container was never the problem (it is in the warp's state on both seeds; the post-warp
snapshot was empty because the chest had been emptied). The crops were: on the bad seed the
fixture's 23 mature wheat were already gone right after `RoomBuilder.build` — at a crop cell
`raw light=0, canSeeSky=false` with air directly above. `TestArenaPreparer.flatten` clears from
the origin (y=64) up to a fixed height (tallest blueprint block + tree headroom); on that seed
the origin is plains with the natural surface higher than that, so the leftover terrain roofed
the arena into a dark cave and every crop failed `CropBlock.canSurvive` on placement. With no
wheat to harvest, the warp farmer planted all 32 seeds and nothing grew within the window.
Ocean seeds have nothing above, so they passed.

Fix: `flatten` clears each column up to max(fixed height, the WORLD_SURFACE heightmap). Bad seed:
2/9 -> 9/9 (wheat 0 -> 23 after build, light 0 -> 15). Sharded full suite after: 75/79, reds
arborist/cut_trees x2, floating_furniture_over_half, skip_chicken_command (known flakes).
arborist/cut_trees [full_cycle] failed in both sharded runs today — trees also need light and
sky, so it may be another seed/terrain case; not checked.
