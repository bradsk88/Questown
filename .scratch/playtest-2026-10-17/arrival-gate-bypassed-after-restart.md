---
title: 'Second villager arrived the first morning after a server restart while Raw Food was still open'
status: fixed
origin: 2026-10-17 playtest round 3 (server restart 06:00 onto 26159da3), observed in log
---

Before the restart the arrival gate held villager 2 on two mornings (05:36:05,
05:46:39 "Holding 1 queued villager arrival(s)"). After the restart (same world), the
first morning (campfire wake 06:02:16) spawned villager 2 (`Spawned visitor 90826535`,
VUID 33cc8b9b) with no Holding line; Raw Food completed only later (06:04:04).

## Verified

- Persisted state was correct: `questown_morning_rewards.children` = one
  `spawn_visitor_reward` (reward_applied 0b); every onboarding quest saved
  `onboarding: 1b`; the raw-food LOOT quest saved `status: ACTIVE`.
- `MCRewardList.serializeNbt/deserializeNbt` + `MCMorningRewards.initializeChildren`
  round-trip that list into `currentChildren` (the list `morningTick` pops).
- `SimpleVillagerHandle.entities` is never cleared by initialize (only `remove` at :385).

## Leading hypothesis (NOT verified)

`isArrivalGateOpen` saw villagerCount 0 → "first villager never held". Ordering in
run/logs/debug.log: `Initialized morning rewards for new flag` 06:01:00.680 (the
onFlagPlace path) → hunter `Registered entity with town` 06:01:00.713 (entity-deserializer
thread) → `Running initializer` 06:01:00.783 → quests/morning rewards/villagers
initialized from tag 06:01:00.883–01.152. The hunter may have registered against a
not-yet-loaded flag state.

## Next

1. Log the gate's inputs on every morning release (villagerCount, onboarding?,
   rawFoodComplete) so the next occurrence names the open input.
2. Reproduce: load a saved onboarding town with one villager + a held spawn, run one
   morning — a test of the load ordering, not of the pure predicate.

## In progress (overnight 2026-10-17, UNCOMMITTED)

A reload step is added to village/day1 (uncommitted in the working tree:
`VillageGrowthExecutor` RELOAD_UNLOAD/RELOAD_LOAD phases, `Day1BeatChecks.pause/resumeArrivals`,
`Compat.setSpawnChunkRadius`). On the day after villager 1 arrives (raw food still open)
it releases every forced chunk, sets spawnChunkRadius 0, moves the fake player 4000
blocks away, waits for the flag chunk to unload, then returns and waits for the flag +
villagers to reload; checks `reload-flag-chunk-unloaded`, `reload-villagers-restored`,
then the existing `villager-2-only-after-raw-food` covers the bypass.
First run's log: /tmp/claude-1000/-workspace-IdeaProjects-Questown/927d3466-6b45-494d-b2de-2e0997372aa0/scratchpad/day1-5.log
(not yet read). If villager-2 FAILs after the reload, the bypass reproduces in-process.
Carrot loop (diner-carrot-take-put-loop.md): investigation started at Jobs.java:372
("is putting") + DinerNoTableWork (food as NEED_EAT tool, CONSUME_FOOD ingredient).

## Result (2026-10-17, later)

The day1 reload step reproduced a gate bypass: after the villagers' chunk reloaded, the
town listed villager 1 twice (stale unloaded copy + reloaded copy, same UUID) and villager
2 arrived with raw food open (day1-6/-7: `FAIL villager-2-only-after-raw-food`). Fixed:
UUID dedupe on register, unregister on unload, and the gate counts
max(live, saved roster). day1-8: 31/31 incl. "Replaced a stale entity …" and the gate
holding the morning after the reload. The flag BE survives this reload, so the true
full-restart order (count 0 before re-registration) is covered by the roster count but not
run end-to-end — the next real-restart playtest will show it.
