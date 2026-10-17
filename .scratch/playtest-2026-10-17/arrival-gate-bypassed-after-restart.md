---
title: 'Second villager arrived the first morning after a server restart while Raw Food was still open'
status: needs-triage
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
