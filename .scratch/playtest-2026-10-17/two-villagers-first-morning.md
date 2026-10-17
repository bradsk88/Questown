---
title: 'Two villagers arrive the first morning: the raw-food gate never held'
status: fixed
origin: 2026-10-17 playtest — user chat 04:31:52 (repeat of 2026-10-15 second-villager-after-raw-meat)
---

"And I'm still getting two villagers in the morning. The second villager should
ONLY arrive after the raw food quest has been COMPLETED."

## Evidence (/tmp/dev-server-3.log)

- 04:31:26 two `Registered entity with town` lines in one morning, no
  `Holding ... villager arrival(s)` line.

## Root cause

The 2026-10-15 gate (`isRawFoodSatisfied`) read "no LOOT quest posted" as
satisfied. The raw-food LOOT quest is posted by `tryPhase2_hunterGatherer`, which
required two villagers — so on the first morning both queued spawns (the gate
reward's first villager + the basics PHASE_KICKOFF spawn) saw no LOOT quest and
spawned. The rule and the phase threshold were a deadlock by construction; the
gate could never hold the second villager. The 10-15 fix was only unit-pinned
(the predicate), never exercised on the real morning.

## Fix

- Hunter-gatherer phase opens at one villager.
- `TownQuests.isArrivalGateOpen(villagerCount)` (first villager / non-onboarding
  town always open; otherwise only after the raw-food quest completes) +
  `releaseArrivals` counts each released arrival within one morning.
- `morningTick` pushes arrivals last on the LIFO asap stack (claimed first).
- Docs: `town/entity/COMPLEX.md` "Second-villager hold".
