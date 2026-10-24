---
title: 'village/growth: a creeper at the flag killed the town (roster 3 -> 0)'
status: needs-triage
origin: 2026-10-24 overnight growth run (log: growth-creeper-wipe-2026-10-24.log.gz)
---

One `village/growth` run (20-min cap, worktree HEAD after 2fff592d plus the driver's
unbuildable-quest discard) reached roster 3 at 05:42:59, then lost every townie:

- 05:45:29 Villager 1, `explosion.player` (Creeper 11502), at (-0.56, 64, -0.53), beside the flag.
- 05:45:41 Villager 3, `drown`, at (-2.5, 61.4, 1.5).
- 05:45:50 Villager 4, `explosion.player` (Creeper 11201), at (-1.55, 64, 0.01).
- 05:47:23 Villager 2, `drown`, at (-4.7, 61.4, 1.0).

The drownings are at y 61 a few blocks from the explosions (the village floor is y 64), so they
look like a crater that filled with water (BELIEVED; the world was not inspected). The run then
timed out at roster 0. None of the six other growth runs that night had a townie death.
`VillageGrowthExecutor` sets no difficulty or `doMobSpawning` rule. `explosion.player` names the
driver's fake player as the creeper's target (BELIEVED from the damage source name).

Options: peaceful difficulty / `doMobSpawning false` for the growth world (the scenario tests the
quest flow, not survival), or keep mobs and treat a wipe as a separate signal. Hostile mobs
matter to ADR-0012 (threat as pressure), so this is the dev's call.
