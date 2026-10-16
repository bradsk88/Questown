---
title: 'Fresh-flag natural-warp false positive: "NBT had no town state" ERROR on flag placement'
status: ready-for-agent
origin: 2026-10-15 playtest — server log 02:40:15 ERROR, fired the second after the user placed the town flag (fresh world; no user report, caught by the signal watcher)
---

# Fresh-flag natural-warp false positive

`[02:40:15] [Server thread/ERROR] [ca.br.qu.QT/]: NBT had no town state.
That's probably a bug. Town state will reset` — logged the same second as
`[findflag] flag f78c31b1 … registered at BlockPos{x=342, y=73, z=-247}`.

## Root cause (verified in code + log)

The natural-warp path in `TownFlagState.tick()` (town/entity/TownFlagState.java):

1. `lastTick = flagTag.getLong(NBT_TIME_WARP_REFERENCE_TICK)` — a just-placed
   flag has never persisted the key, so `getLong` returns **0**.
2. `timeSinceWake = gt - 0` (world game-time, large) → `waking = true`.
3. `townAwake = false` (fresh `TownFlagState` instance, `lastTickWallMs == 0`),
   so the "genuine absence" branch runs:
   `warpTicks = daytimeTicks(0, gt)` > 10 → `warp(...)` →
   `TownFlagState.advanceTime(...)` → the NBT has no `NBT_TOWN_STATE` (true
   for a fresh flag — the `townInit` push at ~line 395 only fires when the key
   exists) → `QT.logBug("NBT had no town state…will reset")`.

So the *first tick of any freshly placed flag* performs a catch-up warp over
the world's whole age and logs an ERROR for the missing state that is the
**expected** initial condition of a new flag, not a bug.

## Impact

None observed: the warp ran against an empty state (no visitors, no
containers, no work statuses) and completed — no "Time warp raised exception"
line followed, and the world went on normally (quests posted, rooms
registered). The damage is log noise + a scary "please report it to the mod
author" line that will show up in every fresh-world playtest.

## Fix (applied 2026-10-15, same session)

Guard in `TownFlagState.tick()`: if `NBT_TIME_WARP_REFERENCE_TICK` is absent
from the flag NBT, seed it with the current `gt`, mark the state
initialized, stamp `lastTickWallMs`, and return before the waking/warp
logic. Only a never-persisted (fresh) or corrupted flag hits this; the
`logBug` in `advanceTime` stays as the real bug signal for the "reference
tick present but town state missing" case. Requires a server restart to
load; no effect on the current world (its flag already seeded).

## Verification

Red evidence: the live 02:40:15 ERROR line above (fresh flag, same second as
registration; deterministic from the code path — fresh NBT ⇒ `getLong` 0 ⇒
warp ⇒ logBug). Unit-testing `tick()` is registry-bound (`TownFlagBlockEntity`)
— held-run/autotest covered: a fresh flag placement must log no ERROR and no
warp.
