---
title: "Town flag 'stopped ticking' log spams every tick when no player is on the server"
status: ready-for-agent
source: agent observation 2026-10-12 (econ-tab crash-probe repro, headless runServer)
---

# `Town flag ... stopped ticking because: No players are found on server` is logged every tick, not once on transition

## What was observed

During a headless `runServer` repro (no client connected for ~8 min), the server log
accumulated **~67,000 identical lines** in ~6 minutes (~11.5 MB), one per tick:

```
[00:46:09] [Server thread/INFO] [Questown:Flag/]: Town flag at BlockPos{x=0, y=64, z=0} stopped ticking because: No players are found on server - Distance Limit: 10000
```

At a raised tick rate this is enormous log churn that also buries every other line
you actually care about (commands, quest events, the crash probe).

## Root cause

`TownFlagTicker.allPlayersLeftArea(TickData, boolean playersNearbyOnPrevTick)`
(`src/main/java/ca/bradj/questown/town/entity/TownFlagTicker.java`) has two "stop"
reasons. The "player went far" branch is gated on the transition:

```java
if (playersNearbyOnPrevTick) {                 // only when the flag was ticking last tick
    logStop(blockEntityPos, "Closest player is %d blocks away");
}
```

But the "no players on the server at all" branch is **not** gated, so it logs on
*every* tick while `nearestPlayer == null`:

```java
if (nearestPlayer == null) {
    logStop(blockEntityPos, "No players are found on server");   // <- every tick
    return true;
}
```

`playersNearbyOnPrevTick` is `!alreadyStopped` (the flag was ticking on the previous
tick) per `AbstractTownFlagTicker` (`stopped = allPlayersLeftArea(data, !alreadyStopped)`),
so the "went far" branch correctly fires only on the ticking→stopped transition. The
"no players" branch needs the same gate.

## Fix

Gate the "no players" log on `playersNearbyOnPrevTick`, matching the other branch:

```java
if (nearestPlayer == null) {
    if (playersNearbyOnPrevTick) {
        logStop(blockEntityPos, "No players are found on server");
    }
    return true;
}
```

Result: the line appears once when the flag transitions to stopped (or at boot if it
starts stopped), never per-tick. No behaviour change — only log frequency.

## Verification

- Unit test: `allPlayersLeftArea` with `nearestPlayer == null` and
  `playersNearbyOnPrevTick = false` should NOT emit the log (and `= true` should).
  (Log-capture may be awkward; if not unit-testable, note it per the no-simulate
  doctrine and verify by a headless `runServer` where the per-tick spam is gone.)
- The autotest `hold` branch (`-Dquestown.autotest.hold=true`) returns `false` before
  this code, so held runs are unaffected.
