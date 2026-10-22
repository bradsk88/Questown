---
title: 'A leaver asleep in bed shows "has left town" on the villagers page all night'
status: closed
closed: 2026-10-22
origin: 2026-10-22 agent playtest
---

# What happened

The gatherer (Villager 3) left on a trip at 03:00:15 (`[WHY] job=gatherer:gather ... GOING_TO_JOB`);
nightfall came at 03:00:19 (`Nightfall: 0 of 3 townies have no bed`). At day time 20891 the
villagers page card read **"Gatherer has left town to collect items."** while the entity was in
bed: `SleepingX: 19, SleepingY: 71, SleepingZ: 166`, Pos `19.64, 71.5625, 166.31`. The Cook and
Hunter cards showed the night icon ("is resting until morning").

The trip was on hold overnight: a normal trip took ~56 s (02:59:15 -> 03:00:11), this one
completed at 03:05:32, after sunrise (`WAITING_FOR_TIMED_STATE -> GOING_TO_JOB`, then extract).

# Why

The card shows the job status: `FlagMenus.createStatusPacket` -> `v.getStatusForServer()` ->
`job.get().getStatus()`. Sleep doesn't change the job status, so a leaver caught mid-trip at
nightfall keeps `WAITING_FOR_TIMED_STATE` (mapped to the gatherer's GATHERING text, "has left
town"). The night icon on the other cards is the RELAXING job status, not a sleep indicator.

# For the dev (judge)

The page contradicts the world (a villager visible in bed is listed as out of town). A fix would
override the status packet when `v.isSleeping()`. There is no "asleep" text yet: RELAXING copy
doesn't fit (the gatherer's reads "has finished their work for the day"). Needs copy/art.

# Resolved (2026-10-22)

Dev: "Villagers should not be starting work that won't fit in a day. How can this even happen?"
Cause: the gatherer's trip was started by the max-ticks fallback (`JobLogic`, `Reached max ticks
for gatherer:gather. Falling back to gatherer:gather.` at 03:00:11.946, day ~11180), which
skipped the board's `canFit`. Fixed: the fallback hands off to the board when its job won't fit
(dev picked this over always-board and a departure gate). With no trip spanning the night, the
card question is moot. See `jobs/COMPLEX.md` "Max-ticks fallback respects the day-fit rule".
