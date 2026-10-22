---
title: Two villagers present after the first sleep (expected one)
status: done
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:16:48:

> I ahve two villager after my first sleep? Thta seems worng...

After the player's first campfire sleep (which should trigger the first morning
arrival), the town has **two** villagers. The expectation is one (a single first
arrival "in the morning").

## Confirmed live (2026-10-11 evening, from-zero run, `/tmp/dev-server.log`)

Reproduced exactly:
- 23:04:39 flag placed (0 townies).
- 23:06:11 `[campfire-sleep] SleepFinishedTimeEvent (morning) newTime=24000`.
- 23:06:12 **two** townies registered in the same second — `570dfe0b` and
  `c770764f` (`Registered entity with town` / first `Job changed`).
- Zero townies in the log before the sleep.

So the first morning arrival spawns **two** townies, not one. This is the bug
to root-cause.

## Root-cause leads (from the live log)

- `c770764f` is registered via `SimpleVillagerHandle.register` (the
  `"Registered entity with town"` line) as a `VisitorMobEntity` — i.e. a normal
  `SpawnVisitorReward` applied. That is the expected first arrival.
- `570dfe0b` first appears at the **same second** (23:06:12) but has **no**
  `"Registered entity with town"` line and is never logged as a chicken or
  visitor. It is doing `gatherer:*` jobs. So a second townie was registered
  through a path that does not log, OR a second `SpawnVisitorReward` was queued
  and applied in the same morning tick.

Leads to check:
1. **Two `SpawnVisitorReward`s queued** for the first morning: search where the
   onboarding/chicken-arc queues the first visitor (`RewardsInit.VISITOR`,
   `MCDelayedReward`, `TownQuests` batch rewards) and confirm it is not added
   twice (e.g. once by the campfire-ready quest + once by the first-sleep
   morning reward).
2. **The helper chicken** is being registered as a second townie in the flag
   menu roster even though it's a separate mob (the user sees "two" where one
   is the chicken + one is the visitor). Confirm whether `570dfe0b` is the
   chicken arc entity (check `HelperChickenSpawnController` / the roster the
   flag menu reads).
3. `SimpleVillagerHandle.register` (line ~575) logs once; check whether a second
   registration path skips that log (so two `register` calls but one log line).

## Acceptance criteria

- The first sleep yields exactly one new villager (the first morning arrival),
  not two.
- If the design is actually "more than one arrives," document that and confirm
  the count is intentional.

## Notes

Investigate the arrival/roster mechanism: where a morning arrival is spawned and
whether a first-sleep path can spawn two (e.g. an arrival + a separate
onboarding grant, or a double-fire of the arrival trigger). Check
`TownFlagTicker` / the arrival executor and the roster count after the first
sleep. Determine whether it's a real double-spawn or an off-by-one in the
roster count the player is reading.

## Resolved (audit 2026-10-22)

The arrival model is one onboarding arrival per batch morning (CONTEXT.md 'Onboarding arrival': gate batch -> villager 1, at most one per morning), and `village/day1` asserts it every day (`one-arrival-per-day[day N]`, PASS in this audit's 2026-10-22 day1 runs, 42/42).
