# Everyone stuck in COLLECTING_SUPPLIES despite supplies present in the storeroom

> status: ready-for-agent
> origin: 2026-10-15 playtest — user chat 03:54:48 → 04:06:38

## Timeline (chat)

- 03:54:48 — "Cook is sitting in 'collecting supplies' but they are showing no
  indication of a 'need'..."
- 03:55:10 — "Their economics tab does NOT indicate a need for coal, but I bet
  that's what is blocking them right now."
- 03:58:13 — user puts coal in the storeroom.
- 03:59:10 — "They're still not doing anything..."
- 04:00:16 — "Hunter is also stuck in 'collecting supplies'. Even though they
  have a sword in hand and are standing right in front of a chest with carrots
  in it."
- 04:02:26 — "Status still says 'collecting supplies', they want raw meat,"
  04:02:33 — "There's a chest full of raw meat." 04:02:39 — "But they're not
  going to it and collecting."
- 04:06:14 — "Now EVERYONE is stuck in collecting_supplies."
- 04:06:38 — "Triggered a debug log. Maybe that's useful."

## Notes

- World: server restarted 03:35:50 (accident), client reconnected ~03:52.
  The stall begins ~03:54, i.e. shortly after reconnection / first morning
  cycle of the restarted world.
- Two distinct symptoms: (a) cook wants coal, economics tab shows no need,
  never requests it; (b) villagers want raw meat / have the item in the chest
  (or in hand) but do not collect it.
- Check the 04:06+ `[WHY]` / Lacking debug dumps in `run/logs/latest.log`
  (user triggered the debug log) — that dump is the decision input, not the
  status name (questown-status-why doctrine).

## Possibly related

- The job-changed-broadcast-twice double-morning (now fixed in tree, not yet
  live) ran the morning pass twice at 03:15 on the prior server — this stall
  is on the RESTARTED server, so a second cause is possible.

## Root cause (verified in logs, 2026-10-16 ~04:20)

**The welcome mat's processing state is stuck at 3 — a state that does not exist in the active job's state machine — with no timer registered, so nothing ever advances it.**

Verified facts (from `run/logs/latest.log` + `run/logs/debug.log` + job JSONs):

1. **Active jobs are the 3-state "short" unmapped variants.** `[WHY]` dumps name the jobs `gatherer:gather` and `hunter:sword`, which are the ROOT ids of `gatherer_unmapped_notool_short.json` / `hunter_unmapped_sword_short.json` — 3 work states each (`[tools|ingredients, work, time:2000]`), so valid processing states are 0, 1, 2 (time state = 2). Both variants use `block: questown:welcome_mat_block` / `room: questown:special_quest.town_gate` — the town gate (welcome-mat) room at [341,-224] is their ONLY job room, which is exactly the single room the "town has workable blocks" gate sees.
2. **The mat's state timeline (gate dumps, both jobs agree):** state 0 (03:36–03:54) → 2 (work, ~03:54) → 3 (03:54:41–) → back to 0/3 cycling ~04:00–04:01 → **3 continuously 04:01 → present (3876 consecutive dumps, 18 min).**
3. **Timer lifecycle (debug.log):** 2600-tick timer set 03:52:30, expired 03:54:40 ("Moving to next state"); 3500-tick timer set 03:57:40–41 (with "Clobbered time on block ... from 3500 to 3500" ERROR bursts — the timer is re-added every flag tick during work progress, resetting the value), expired 04:00:35. **After 04:00:35 there are zero "Timer added" / "expired" entries** — the mat's final entry into state 3 never registered a timer, so `decayTimersAndCollectExpired` has nothing to decay and the state can never advance.
4. **State 3 is out of range for the active 3-state jobs** (maxState 3 ⇒ states 0–2). The 4-state "full_day" variants (`hunter_unmapped_sword_full.json`, `gatherer_unmapped_rod_full.json`, …) DO have a state 3 (time: 6000). The mat's state is shared across the job family (one welcome-mat block, one per-owner work-status ledger) while each variant has its own state table, so a state advanced under the 4-state layout (or a post-time-state `incrProcessing()` wrap) lands at 3, where the active 3-state variant's gate (`No rooms found with workable blocks at states [0,1,2]`) and timer machinery both have no coverage.
5. **Symptom chain (all log-confirmed):** mat @3 with no timer → no workable spot at states 0–2 → hunter stuck in `WAITING_FOR_TIMED_STATE` (03:54:40 `[WHY]`), gatherer in `NO_JOBSITE` → the two queued `#questown:villager_food` requests (`questown_jobs.questown_requests` NBT) are never satisfied → cook stuck in `COLLECTING_SUPPLIES` → everyone else stalls behind it (04:06:14 "Now EVERYONE is stuck").

Side observations:

- **"Clobbered time" ERROR bursts** (2600→2600 at 03:52:30, 3500→3500 at 03:57:40, 11 total): `AbstractWorkStatusStore.setJobBlockStateWithTimer` is re-invoked on every flag tick during the work state, re-setting the existing positive timer to the same value. Logged as ERROR each time; value-identical so not fatal, but it is the logged-error spam and indicates the work-progress path should not re-add a live timer.
- Timers decay correctly at real-time 20 t/s (2600 ticks in 130 s; 3500 in 175 s) — the decay machinery itself is sound; the failure is a missing timer registration on the final state-3 entry.
- Pre-restart (rotated log) the mat was state 0 in all 8800 gate dumps — the stall is a POST-restart phenomenon: the last full cycle before the 03:35:50 kill left the mat mid-cycle and the restarted store re-enters the time state without seeding its timer.

## Unblock (user, in-game — no code needed)

1. Try `/qt jobs timers drain 340 73 -224` first — flips the mat if a hidden timer exists (no-op if not; harmless).
2. If nothing happens (expected): **break the welcome mat and place it again** (or `_qtdev place_mat 340 73 -224`). `scanRoom` clears a vanished block's status ("Block is gone from {}. Clearing status."), so re-placing re-seeds state 0 and the cycle restarts. The stall will RECUR when the next cycle re-enters the time state, so this is a bridge, not a fix.
3. A server restart also re-seeds the mat to 0 (world persists) — same bridge.

## Fix direction (design question, not yet decided)

- **Primary:** a job family sharing one job block must have one state space. Either (a) make all unmapped variants use a uniform state table (align the "short" variants to 4 states, or drop the extra state in "full_day"), or (b) key the work-status state to the active variant and invalidate/re-seed the mat's ledger when the active variant changes, or (c) make `advanceBlocksWithExpiredTimers`/the work-completion path register the next step's timer for the ACTIVE variant's table (and wrap `state >= maxState` → 0 instead of leaving an out-of-range state).
- **Secondary:** stop re-adding a live timer in the work-progress path (the "Clobbered time" ERROR bursts) — guard `setJobBlockStateWithTimer` against same-value re-adds or route the work-progress update through a decay-only path.
- **Tertiary:** a mat sitting in an out-of-range state with no timer for >N ticks should self-heal (re-seed to 0) instead of soft-locking the town — a watchdog in the store tick.

## Refined diagnosis (2026-10-16, session 2 — corrects point 4 above)

The "state 3 is out of range" reading was wrong:

- `State.incrProcessing()` is a plain `processingState + 1` (no wrap). For a
  3-state job, `maxState == 3`, and **state 3 IS the legitimate terminal
  state**: workable states are `[0, maxState-1]`, and a block at `== maxState`
  awaits leaver extraction (`tryExtractProduct`,
  `AbstractWorldInteraction:651` — `state >= maxState` → extract +
  `getResetFunc`). The gate checking `[0,1,2]` is correct behaviour, not a
  mismatch.
- The mat DID extract successfully three times (three 0×12 dump windows after
  03:52:30) — extraction works when the leaver's circuit sees the mat.
- The final stuck state: the leaver's extraction circuit returns null.
  `productionRoutine` (JobStatuses) only routes to
  `tryChoosingItemlessWork` → `town.roomsWithCompletedProduct()` (rooms where
  some job block has `maxState == s.processingState()`, built by
  `DeclarativeJobTicker.computeRoomsWithCompletedProduct` from
  `deps.getJobSites()` + the per-owner `work.getJobBlockState`) when that is
  non-empty. While it is empty the circuit falls through to
  "get work supplies" (`dTownHasSupplies=true` — the swords ARE in the
  chest) → COLLECTING_SUPPLIES. So the failing link is
  **`roomsWithCompletedProduct` seeing a different view of the mat (null / not
  3 / not in its room source) than the gate dump** — a room-source / handle
  desync (the gate's spot map and the completed-product check read through
  different suppliers: `roomsV2` vs `deps.getJobSites()`).
- `isUnfinishedTimeWorkPresent()` is timer-driven (`ticksSource.apply(pos) > 0`
  over `roomsFinder` blocks), so a timer-less mat@3 does NOT pin leavers in
  WAITING_FOR_TIMED_STATE — the 04:31 [WHY] dump confirms the hunter had
  moved to COLLECTING_SUPPLIES and was being resolved there every tick.
- Aggravator (food cold-start): `#questown:villager_food` contains only COOKED
  items; the cook cannot reach its cooking state without eating first, so raw
  meat in the chest is dead stock until a cooked item exists. (User can drop
  bread into the storeroom chest to break the deadlock live.)

## Fix status

- **Shipped (worktree `pt-stall`, TDD):** the Tertiary watchdog —
  `AbstractWorkStatusStore.reseedStuckBlocks` reseeds any job block that sits
  in a non-fresh state with no timer for `STUCK_RESET_TICKS` (20000) to
  `State.fresh()`, with a WARN; counter resets on every state write.
  Pinned by `WorkStatusStoreTest` (4 new tests: reset-at-threshold,
  counter-reset-on-write, timer-present-skipped, fresh-never).
- **Shipped with it:** the Secondary clobber demotion (same-value timer
  re-add → debug, not ERROR).
- **Still open (the true root cause):** the room-source / handle desync that
  makes `roomsWithCompletedProduct` miss the terminal mat the gate can see.
  The watchdog self-heals its consequence (≤16.7 in-game minutes of freeze,
  then a fresh cycle) but the desync itself is unfixed. Repro to chase:
  a fresh 2-villager town, short variants, mat reaches state 3 while
  comparing `gate[spots=...]` (mat=3) against
  `computeRoomsWithCompletedProduct`'s view in the same tick (log
  `deps.getJobSites()` + `work.getJobBlockState(matPos)` inside that method
  for one held run).

## Verification notes for the fix

- Repro precondition: a town whose active jobs are the 3-state `*_short` unmapped variants (fresh day-1/2 town, welcome mat as the leaver job block).
- The gate dumps (`[WHY] ... No rooms found with workable blocks at states [0,1,2]`) and the mat spot value (`340, 73, -224=<state>` in `gate[spots=...]`) are the observables; "Timer added to [state=N...] (X to next state)" / "Timer at ... expired. Moving to next state" in debug.log are the timer-lifecycle observables.
- `DrainTimersCommand` (`/qt jobs timers drain <pos>`) and `_qtdev place_mat <x> <y> <z>` exist for live state manipulation.

## Audit (2026-10-22)

Mitigated, root cause open: the stuck-block watchdog (`AbstractWorkStatusStore.reseedStuckBlocks`, 20000 ticks) and the clobber demotion shipped; the `roomsWithCompletedProduct` vs gate-view desync named in 'Refined diagnosis' is still unfixed (no commit found).
