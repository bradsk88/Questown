---
title: Set the world to peaceful during held-run sandbox setup
status: needs-triage
created: 2026-09-08
priority: p0
---

## Context

This session's biggest failures were a dead player. An agent driving a held
village run kept feeding a flag and reading the clickwand `CONSUME` log as
success while the player it emulated was dead (empty inventory, unreachable by
`tp`/`give`). The chicken-arc's real gate is a *live* nearest player's inventory
(`ChickenArcController.tick` advances only when `getChickenEverSpawned()` is true
and `hasWandInInventory` reads true), so a dead player silently blocks every
interaction beat regardless of what the command logs.

A brand-new player in a peaceful world just would not get killed. Deadness is a
setup failure, not an in-run problem the agent should have to check for.

## The change

Held-run sandbox setup sets the world to **peaceful, world-level**, before the
run starts:

- `difficulty peaceful` (world-level, not `gamemode peaceful <player>`).
- Per-player gamemode peaceful reverts on a death; world-level difficulty does
  not, and prevents hostile mob spawns. So a death cannot revert it, and no
  spider kills the player.

## Acceptance criteria

- Before a held `runServer` autotest / play-test starts, the world's difficulty
  is set to peaceful.
- It is set world-level (`difficulty peaceful`), not per-player, so a death cannot
  revert it.
- The agent is not required to check whether the player it emulates is alive
  during the run, because it cannot die.

## Notes

- This is the companion to `_qtdev inspect`. Both follow the same principle: the
  agent's failures were a dead player and a cheatsheet, not missing game facts.
  The fix is the world a new player would be in, and a look-closer command, not
  more game knowledge in the docs.
- Do not put a deadness check *in* `inspect`; liveness is handled by peaceful
  setup, not by the look-closer command.
