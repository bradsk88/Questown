---
title: 'After sleeping, both hunters stand at the storeroom chests in COLLECTING_SUPPLIES forever'
status: ready-for-agent
origin: 2026-10-21 playtest (dev-server-19, build 5ef2b66b) — user chat
---

## Facts

- Hunters cycled normally (trip → EXTRACTING_PRODUCT → DROPPING_LOOT → GOING_TO_JOB, ~2 min)
  until the player slept at the campfire (SleepFinishedTimeEvent 03:10:14, catch-up warp).
  From 03:10:18 both sat in COLLECTING_SUPPLIES (17+ min, still stuck at 03:27).
- [WHY] at the stuck state: holdsWorkOutput=false; hasWorkItems=true; the town-gate room's only
  job block, the welcome mat (-72,70,-208), is at state 3 (finished), so "no workable blocks at
  states [0,1,2]"; "work without items" (prioritised extraction) returned null, so nobody
  extracts state 3; the status falls through to collecting supplies, whose take finds "No match"
  in the chest. Both stone swords are in a chest.
- Every earlier session where the player slept (dev-server-13/15/16/18, before 5ef2b66b) the
  hunters resumed within seconds. Only dev-server-19 (first with 5ef2b66b) stuck.
- Reloading the world clears it: the stuck world (save-all snapshot) loaded on cc60824f AND on
  5ef2b66b both went COLLECTING_SUPPLIES -> GOING_TO_JOB within a second. So the bad state is
  in-memory (job instance / per-villager store), not in the save. Workaround: restart the server.
- Not reproduced by autotest yet: `hunter/sword [warp_then_realtime]` (direct warpTime, 1 or 2
  hunters) passes. Differences from the playtest: the warp came from campfire sleep (natural
  warp), with the hunters already mid-trip before it.

## Next

A scenario that runs realtime first (hunters out on a trip), then a natural warp
(NATURAL_WARP_FREEZE, like sleeping), then realtime — and asserts the hunters extract the mat's
state 3 and hunt again. Then find why `tryChoosingItemlessWork` returns null at state 3 after
that warp on 5ef2b66b (the new "drop loot before a new cycle" node and the JobLogic carry-on are
the changes in play).

## Repro attempts (2026-10-21) — none reproduce it

`hunter/sword [sleep_mid_trip]` (no respawn, unlike warp_then_realtime, whose KILL/RESPAWN
before realtime clears in-memory state the way a reload does): two hunters, two swords, the
growth driver's sleep step (clock to morning + TownFlags.onSleepFinished) during realtime.
Variants: sleep at noon mid-trip; night (dayTime 16000) mid-trip; late night (21000) after the
evening drop-off. All pass: both hunters extract and set off again after the sleep.

Still unexplained: the playtest's mat left at state 3 with nobody extracting. Playtest-only
factors not in the fixture: 4 villagers made hunters at once at 02:56 (two later changed), a
cook/miner sharing the storeroom chests, board requests, the real campfire sleep path.
