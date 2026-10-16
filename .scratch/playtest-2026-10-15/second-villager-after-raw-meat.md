---
title: 'Second villager arrives too soon — should unlock after producing raw meat'
status: fixed
origin: 2026-10-15 playtest — user chat 03:05:51 (round 1); refined round 2 chat 05:34:56
---

# Villager growth: gate the second arrival on the raw-food quest

User report (round 1): "I have two villagers already. That's too soon.
The second villager should be unlocked after we have produced raw meat."

User report (round 2, 05:34:56): "Villager page says 'and one more in the
morning' but I thought I explicitly asked you to hold the 2nd villager
until the raw food quest is completed." — i.e. the gate was not in effect;
this ticket was still open.

## Gate location (found this session)

The second-villager spawn is queued as a `SpawnVisitorReward` in the
flag's `morningRewards` (the tutorial's PHASE_HUNTER_GATHERER reward and
`TownQuests.defaultQuestCompletionRewards` both queue it there; dev/test
paths use `addImmediateReward` and are NOT affected). It fires in
`TownFlagBlockEntity.morningTick` (morning reward pop -> `asapRewards`).
The "…and one more in the morning" card renders when
`hasVillagerArrivingInMorning()` is true (`MultiStatusScreen` sentinel
card, fed by the same predicate).

## Fix (2026-10-15, same session)

- `TownQuests.isRawFoodSatisfied()`: all posted LOOT ("Raw Food") quests
  complete; an empty list reads as satisfied so test harnesses and
  pre-onboarding flags are never held hostage.
- `morningTick`: a queued `SpawnVisitorReward` whose gate is unmet rolls
  into the next morning instead of spawning (INFO log line).
- `hasVillagerArrivingInMorning()`: the card only promises an arrival the
  morning will actually make (pending spawn AND gate satisfied).
- Unit test: `TownQuestsRawFoodGateTest` (3 tests, green). The
  morningTick gate and the visitor's door pathfinding preference are
  registry-bound; pinned via the pure predicate instead.

Commit: 3ac14aea (session 2026-10-15 round 2)
