---
title: 'Away overnight: the morning pass never runs (BOP job change to hunter not applied)'
status: ready-for-human
origin: 2026-10-17 playtest round 5 — user chat 18:21:46 (dev-server-8, build 3ed88de2)
---

"I was out of town overnight, and it looks like the morning reward did not apply. I don't have a hunter."

## Cause (log)

- 18:00:31 BOP spent: `change_job_in_morning [Villager 1]`, pending root hunter:sword.
- 18:04:57 / 18:08:42 `Town flag ... stopped ticking because: Closest player is 10001 blocks away`.
- 18:20:51 resume at world time ~80296 (dayTime 8296 = NOON): catch-up warp ran, no morning.

The morning pass (`TownFlagBlockEntity.morningTick` → pending job changes, morning rewards,
arrivals) runs only from `AbstractTownFlagTicker.detectMorning` seeing the MORNING window
(dayTime < 6000). A flag that is not ticking across dawn and resumes after 6000 never sees it.

## Fix

`TownFlagState` wake path (genuine absence): after the catch-up warp, `runMissedMorning` calls
`morningTick(now)` when the pure `MissedMorning.missedDuringAbsence(lastTick, now)` says a dawn
was crossed and the ticker won't catch it (resumed past the morning window, or stopped inside one
so its latch is still set). Pinned by `MissedMorningTest` (5). One morning per absence, even if
several days passed (pending changes/rewards are consumed once anyway).

## Verify (needs restart)

Spend a BOP, walk >100 blocks away before night, return after the next noon: the villager has
the new job and the log shows `Running the morning the town slept through`.
