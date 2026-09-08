---
title: chicken-arc SUNSET_AND_MAP sleep beat requires manual interaction and holds the player through a real night
status: ready-for-agent
created: 2026-09-08
priority: p2
updated: 2026-09-08
closed: 2026-09-08
---

## Resolution (2026-09-08)

The freeze is resolved by driving the clock, not by changing the sleep feature:
the agent skips the night with `time set <dawn>` via the FIFO (the sleep wakes
at dawn and `onWake` sets the bit), instead of holding the player through a real
night. `AgentBeatGuidance.SUNSET_AND_MAP` now says exactly this (the `time set`
clock skip is an agent action), and `_qtdev inspect chicken` surfaces it. The
remaining gap - the campfire right-click that *starts* the sleep still needs a
live player or a command that fires `RightClickBlock` - stays a real limitation
(see hold-run-command-set-gaps.md); the clock skip is the part the agent does.

# Campfire-sleep beat (SUNSET_AND_MAP) can't be driven from the console and freezes the player through a real night

Observed while driving a held `village/growth` run with
`-Dquestown.village.agentDrive=true` (executor calls `observe()` only, the agent
drives the player actions via the FIFO console command set).

Two coupled problems, both real, both in the held-run path:

## 1. The beat cannot be driven from the console

The campfire-sleep beat is fired by `PlayerInteractEvent.RightClickBlock`
(`ItemsInit.onInteractBlock` -> `TownWand.onRightClicked` ->
`TownWand.handleCampfireClick` -> `CampfireSleepHandler.beginCampfireSleep`).

The console command set cannot produce that event:
- `_qtdev click` drives `gameMode.useItemOn(...)`, which does NOT fire
  `PlayerInteractEvent.RightClickBlock`, so the campfire-sleep branch never
  runs. (`useItemOn` returns before any right-click-block event; the wand's
  campfire action lives in the event handler, not `useOn`/`onRightClicked`
  reached via use-on.)
- `_qtdev clickwand` works because it goes through the flag's `use` path, but
  it is flag-only; there is no console command that right-clicks a campfire.
- The sleep requires the **paired** wand (the one the flag created, carrying the
  flag's position in NBT; `TownFlagBlock.GetParentFromNBT`). A `give`'d
  town_wand is unpaired, so `handleCampfireClick` rejects it as
  "not_registered" (no parent, fails the flag-radius gate).

So the one beat the agent's console hands cannot perform is the campfire
sleep. The agent had to hand the step to a live player (the dev) to
right-click the campfire with the paired wand — a held run must not require
that.

## 2. After the sleep starts, the day does not advance (frozen)

`CampfireSleepHandler.beginCampfireSleep` calls vanilla
`player.startSleepInBed(headPos)`. Vanilla bed sleep holds the player asleep
until the **real** dawn; it does not warp the clock forward. With the clock at
night (`time set 13000`), the player sleeps through the full ~1000-tick night
(`[campfire-sleep] heartbeat ... isNight=true` keeps ticking) instead of
waking at dawn. The sleep only completes when the clock is manually advanced to
dawn (`time set 23990` -> `onWake fired ... isDay=true`), which then sets
`chickenObservedSleepSinceSunset=true` and advances the arc to
`WAITING_FOR_WALL_BLOCK`.

The autotest has a `-Dquestown.autotest.warp` property (default 24000), but the
campfire-sleep path does not use it to fast-forward the night; it relies on the
real day cycle. A held run that sets the clock to night and then expects the
sleep to complete on its own will hang.

## Repro

1. Held `village/growth` with `agentDrive=true`, agent drives the arc to
   `SUNSET_AND_MAP` (wand obtained, campfire lit, campfire quest done).
2. Clock at night. No console command triggers the campfire sleep; the only
   path is a live player right-clicking the campfire with the paired wand.
3. Even after the sleep starts, the day holds at night until the clock is
   manually advanced to dawn.

## Acceptance criteria

- A console command (e.g. `_qtdev clickcampfire <pos>`) that fires the
  `PlayerInteractEvent.RightClickBlock` path with the **paired** wand so the
  campfire-sleep beat can be driven in a held run without a live player.
- The campfire sleep should advance the clock to dawn (or the run should not
  require sleeping through a real night) so the beat does not freeze the
  player.

## Notes

- Verified: `_qtdev click wand 0 64 0` logged
  `-> CONSUME (now holds town_wand)` (the flag's `use` path converts the
  stick), but the chicken-arc `hasItem` still read false — the arc reads the
  player's *held cursor* every tick, and the console `clickwand`/`give` cannot
  change the held slot a live cursor controls. The wand had to be `give`'d into
  the hand for the arc to see it held.
- Verified: `onWake` sets `chickenObservedSleepSinceSunset(true)` and
  extinguishes the campfire; the beat advanced `SUNSET_AND_MAP ->
  WAITING_FOR_WALL_BLOCK` only after the clock was advanced to dawn.
- Log: /tmp/village-server.log (2026-09-08, fresh world, roster=0 -> 1 townie
  after the sleep).
