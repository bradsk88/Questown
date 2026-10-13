---
title: 'Villagers eating raw food appear to eat repeatedly — is that fullness-chasing?'
status: answered (intended - raw meals fill half, by design)
---

# Repeat raw-food eating

Captured 2026-10-14 (server-20, in-game chat 04:43:44):

> "When villagers eat raw food, they seem to eat over and over.... Are
> they trying to hit 100% fullness?"

Observation: a diner townie on raw food repeats the dining cycle instead
of eating once and moving on. Open question to settle from the work
definition (`DinerNoTableWork` / `DinerRawFoodWork` in
`jobs/declarative/`): is a second eating cycle intended behaviour
(fullness < threshold after a raw meal, so they re-eat), or a loop
(the fullness gate doesn't latch / the "done" state isn't reached)?

Answer shape: intended (note the threshold behaviour) or bug (the diner
should stop at the post-meal fullness).

## Answer: intended fullness-chasing, by design

Mechanism (read 2026-10-14 night):

- Dining is triggered by the satiety timer, not a one-shot:
  `SimpleVillagerHandle.tickHunger` drains fullness every `flagTickInterval`
  ticks; when it crosses `<= 0` (in steps of 10) the hungry listeners fire
  (`addHungryListener`, TownFlagTileData initVillagers) and snap the townie
  to a dining job.
- Meal depth differs by work: cooked dining applies `HUNGER_FILL`
  (`FillHungerSpecialRule(1f)` - full base fullness); the raw-food fallback
  (`DinerRawFoodWork`, reached when no cooked food / no table) applies
  `HUNGER_FILL_HALF` (`FillHungerSpecialRule(0.5f)` - half), plus the
  UNCOMFORTABLE_EATING + ATE_RAW_FOOD mood effects.
- So a raw meal restores to 50% base; the timer drains it to 0 again in
  roughly half the interval of a full meal, and the hungry listener
  re-triggers the next dining cycle. A raw-food-only townie therefore eats
  ~2x as often as a cooked-food townie - each time a full dining cycle
  (walk over, 25 work ticks, consume), which reads as "eating over and
  over".
- Live evidence: s19 04:26:47 `Fullness changed from -1200 to 5000` -
  5000 = 50% of base 10000, the HUNGER_FILL_HALF signature.

Not a gate/latch bug: after eating, fullness is positive, so the hungry
listener cannot re-fire until it drains back through 0; `gaveUpDiningRecently`
guards the give-up case. The user's world was raw-food-only that night
because the cook was blocked (cf. the cook-blocker and food-quest tickets),
which is exactly when the repeat pattern becomes visible.

No code change. If the ~2x cadence ever reads as too frantic, the lever is
`HUNGER_FILL_HALF`'s 0.5f factor (or the raw meal's mood penalty), both
deliberate design knobs.
