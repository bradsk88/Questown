---
title: 'Miner stops finishing trips after a while; resets every 150 s instead'
status: needs-info
origin: 2026-10-21 playtest — dev: "miner is very slow" (dev-server-25 and dev-server-26 logs)
---

## Facts

- **The JSON is not the cause.** `miner/coal` and `hunter/sword` both declare `time: 2000`; the
  half-day (4000) and full-day (6000) variants match too.
- **Isolated, the miner is as fast as the hunter.** The new probes `miner/coal [cycle_time]` and
  `hunter/sword [cycle_time]` (1 villager, realtime, no warp, 12000 ticks) each finish 6 cycles,
  about 2140 ticks apiece.
- **Live, the miner kept the hunter's pace and then stopped.** In dev-server-25, miner `20b70d5c`
  earned XP at 17:57:12, 17:59:20 and 18:01:28 (about 2 min apart, like the hunter). After that
  it finished no trips until 18:33:43 in dev-server-26, after the server restart.
- **From 18:03:58 the miner logs "Job changed to miner:coal [from miner:coal]" exactly every
  150 s** in both sessions. That interval is the 3000-tick max-ticks fallback
  (`JobLogic.java:182`, `ExpirationRules ... () -> 3000L`). It only fires when
  `workSpot == null && !status.isWaitingForTimers()`. So for those windows the miner had no
  work spot and was **not** on its mining timer.
- **The dev saw "Mining (away)" during the day, and the miner was invisible then**, which means
  it really was on a trip. (Being visible at night is normal: it isn't working.) So the miner
  does leave on trips but doesn't finish them, and the 150 s resets say that at those moments
  its status was **not** `WAITING_FOR_TIMED_STATE`. Open question: does the reset happen while
  it is away, and does it throw the trip away? (`JobLogic.java:179-180` records the same kind
  of loss for the hunter, 2026-10-17.)
- **No stuck-block reseed** (`AbstractWorkStatusStore.STUCK_RESET_TICKS`) was logged in either
  session.
- The hunter went `NO_SPACE` (storage full) from 18:13. Whether the miner was also holding
  output with nowhere to put it is unknown: `[WHY]` dumps are hard-coded to `gatherer:` and
  `hunter:` labels (`JobStatuses.maybeLogWhy`), so the log has no status lines for the miner.

## Next

1. Add `miner:` to the `maybeLogWhy` label gate. Restart the dev session in the morning (in-game)
   and read the miner's `[WHY]` status at the first 150 s reset.
2. If it is NO_SPACE: this is the storage-full stop working as designed (dev ruling: full
   storage → stop visibly). The bug is then a miner that leaves on trips it can't finish, plus
   a missing storage-full bubble.
3. Otherwise, reproduce with a `cycle_time` variant that matches whatever condition the dump
   names, then fix.

## Update (2026-10-21 18:52): storage-full reproduces the pattern

`miner/coal [storage_full]` (every town slot filled after spawn) reproduces the live log line for
line. The miner makes one trip, then sits in NO_SPACE holding its loot, and the same-job reset
fires every 3000 ticks (150 s) without leaving NO_SPACE. It ends with need NO_STORAGE, which is
the designed stop (dev ruling: full storage → stop visibly). The live hunter has been NO_SPACE
since 18:13, which supports this. The miner most likely filled storage first, because its loot is
several distinct items (coal, cobblestone, ...) and each one needs its own slot.

**Still unconfirmed in the live world:** a glance at the town chests, or a no-space bubble over
the miner or the flag, would settle it. If storage is not full, the cause is something else,
and the `[WHY]` dumps (now enabled for `miner:`) will show the miner's status at the next reset
after a restart.

Side finding: at nightfall a NO_SPACE villager switches to DROPPING_LOOT with need NONE, so the
storage-full bubble disappears overnight. That may be acceptable, since it's bedtime anyway.

## Likely root cause (2026-10-21 agent playtest)

Villager deposits never stack (`Jobs.tryDropLoot` uses the first empty slot only), so each mined
coal or cobblestone takes a whole chest slot. See
`.scratch/agent-playtest-2026-10-21/villager-deposits-never-stack.md`. In the agent's fresh world the
miner itself ran at spec (~2100 ticks per trip) while storage was filling one slot per item.
