# Server stall ("waiting for server") — villager wander retry loop

**Status:** Diagnosed from log2.txt (2026-09-03). Perf, not a crash. Fix
implemented: rate-limited wander failure + deferred retry.

## Symptom

Players had been building a town. Around 14:03 the server began emitting
`Can't keep up! Running 2117ms / 8014ms / ... behind` in a sawtooth (42 → 160 →
42 → 160 ticks behind) and never recovered; most block interactions stopped
responding ("waiting for server").

## What it is NOT

- **Not room detection.** The `perf/town_large` autotest (16 rooms, 20
  townies) shows the whole flag tick averaging 934us against a 50ms budget, and
  `roomsHandle` only 281us. A synchronous farm/room scan is a one-time spike,
  not a 13-minute sawtooth.
- **Not the two `room_created` lines at 14:02:55.** Those are a coincidence of
  timing; the room that resolved to a recipe was a store_room at 14:04:43.

## Root cause (verified in code)

From 13:20 onward the log carries **3000** `Could not find wander target` and
**6000** `RandomWorkSpot: Chose null work spot position` / `No work spot
override data found` errors — a villager whose town had no valid room to
wander to, retrying every tick.

The loop: `RandomShortLivedWorkSpot.beforeTick` finds no work-spot override →
`choosePosAndStoreOnVillager` → `town.getRandomWanderTarget` →
`TownPois.getWanderTarget`, which enumerates every room's interior positions.
When the town has no wanderable room it returns null, so nothing is stored, so
`beforeFindJobSite` then `logBug`s "No work spot override data found", and the
next tick runs the whole scan again. No cooldown, three log lines per attempt,
per stuck villager. As more villagers hit this, per-tick cost climbs past the
tick budget and the server spirals.

## Fix (this change)

- **Defer the retry.** `RandomShortLivedWorkSpot` now records a
  `questown:work_spot_retry_after` tick on the villager when a search comes
  back null, and skips rescanning until then (`RETRY_AFTER_FAILURE_TICKS`
  = 100). This breaks the per-tick loop that caused the stall.
- **Log the reason, once.** `TownPois.getWanderTarget` gained an optional
  `WanderDiagnostics` hook distinguishing *no rooms at all* from *rooms exist
  but the filter rejected every position*. `TownFlagBlockEntity` logs the
  specific reason with town context (day/night, room count, positions
  checked), rate-limited to once per 200 ticks per stuck position, replacing
  the old per-tick `Could not find wander target avoiding {}` warn.
- **Stop logBug-ing an expected-empty state.** `beforeFindJobSite` no longer
  calls `logBug` for a missing override (that is the normal "no spot assigned
  yet" / "no valid room" case, not a bug).

## Future-bug logging lesson

The original logs hid the bug: the *failure* went to a per-tick `warn` while
the *reason* (how many rooms, why the filter emptied) was never logged, and
`logBug` fired for an expected-empty state so often it read as noise. A search
that can return null for several distinct reasons should say which one, once,
with the context needed to act on it — not warn the same string every tick.
