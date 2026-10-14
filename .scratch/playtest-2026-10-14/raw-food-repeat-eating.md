---
title: 'Villagers eating raw food appear to eat repeatedly — is that fullness-chasing?'
status: answered (REVERSED 2026-10-15 user ruling - one meal per hunger cycle, NOT fullness-chasing)
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

## Ruling reversal (2026-10-15, user)

The user reverses the "intended fullness-chasing, by design" answer above:
that framing was attributed to them without a ruling. The design intent they
state is:

> Villagers who eat raw food go back to work **after eating once**, even
> though it didn't return them to 100% fullness. This just means they will
> need to eat again sooner.

- "Fullness-chasing" (keep dining until ~100% full) is **not** the intent
  and is **not** the code's behaviour. No code change: the mechanism above
  already does exactly this — one meal per hunger cycle (half fill, back to
  work), the timer re-triggers sooner. The ~2x cadence is a *consequence* of
  the smaller meal, not a goal.
- Consequence for the record: `HUNGER_FILL_HALF`'s 0.5f is the meal-size
  knob (the next meal comes sooner because the meal is smaller), not a
  cadence target to defend. The raw meal's mood penalties stand unchanged.
