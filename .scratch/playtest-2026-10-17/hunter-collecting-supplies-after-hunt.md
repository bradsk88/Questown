---
title: 'Hunter back in COLLECTING_SUPPLIES after the hunt timer expires - no loot extracted'
status: needs-triage
origin: 2026-10-17 playtest round 2 — user chat 05:53:32
---

"We're back to stuck in collecting supplies. Also, the hunter doesn't have their loot
and they have not deposited it, but they're standing, looking at a chest as if they
cant find supplies, when everything is there."

## Verified timeline (/tmp/dev-server-4.log + run/logs/debug.log, villager 9d507196)

- 05:46:39 job hunter:sword created (IDLE → COLLECTING_SUPPLIES); stuck ~4 min on the
  bed/wall nav (see villager-stuck-on-bed-pathing-over-wall.md).
- 05:49:09 `Reached max ticks for hunter:sword. Falling back to hunter:sword.`
- 05:50:25 `[PROBE:supplies] TOOK stone_sword` (right after the user closed the door).
- ~05:50:30 hunt starts (short variant: [tools, work, time:2000]).
- **05:51:39 `Reached max ticks … Falling back to hunter:sword` again — mid-hunt**,
  34 s before the timer ends; the recreated job goes IDLE → DROPPING_LOOT, then back
  to WAITING_FOR_TIMED_STATE.
- 05:52:13 mat (100,69,-84) `Timer … expired. Moving to next state` →
  `[state=3, ingCount=0, workLeft=0.0]` (3 = product ready for the 3-state job), and on
  the same tick the hunter goes WAITING_FOR_TIMED_STATE → **COLLECTING_SUPPLIES**
  instead of extracting. No LOOTTRACE extract follows. 05:54:09 the fallback fires again.

## The watchdog

`JobLogic`: `workSpot == null && ticksSinceStart > expiration.maxTicks()` →
`changeJob(fallback)`. For this job `ExpirationRules.never()` → `maxTicks = 3000`
(2.5 min at 20 t/s, exactly the 05:49:09 / 05:51:39 / 05:54:09 cadence), fallback =
the same job. Its comment says the re-creation is meant to be harmless ("effectively
keep doing this job forever") — but it fires while the leaver is mid-hunt
(workSpot null while away), and the recreated job loses the cycle.

## Open (not yet verified)

- What DROPPING_LOOT dropped at 05:51:39 (the sword?) — no item line in either log.
- Why state 3 + fresh job reads as COLLECTING_SUPPLIES (needs the held sword to
  extract? or the fresh instance doesn't treat the mat as its production spot —
  `ProductionJob.doGetTarget` logged "Production spot was null somehow" just before).
- Whether the 10-15 `collecting-supplies-stall` (also mat @ state 3) was this same
  mid-cycle reset rather than the restart.

Repro idea: hunter fixture whose pre-hunt delay pushes `ticksSinceStart` past 3000
while the time state is still running.

## Fix (2026-10-17, same session)

`JobLogic`'s max-ticks watchdog now skips while `status.isWaitingForTimers()`. Unit:
`JobLogicTest.tick_shouldNotHitMaxTicksFallback_WhileWaitingForTimedState` (red before:
"the watchdog re-created the job mid-timer"), plus a control that it still fires when
IDLE with no work spot. **Still to verify end-to-end** (hunter autotest or next playtest):
the hunt product is extracted when the cycle runs past 3000 ticks. The open questions
above (what DROPPING_LOOT dropped; "Production spot was null") stay open until then.
