---
title: 'Hungry gatherer stands at the chest taking and putting back a carrot in a loop'
status: ready-for-human
origin: 2026-10-17 playtest round 4 — user chat 06:26:55 (dev-server-6, build 2c9f5880)
---

"Gatherer is standing at chest inserting and removing a carrot constantly."

## Log (run/logs/debug.log, villager 90826535 / "9082...81b")

- `[PROBE:supplies]` take counts 06:2x: **898× gatherer:dining_no_table carrot**,
  317× gatherer:gather carrot, 133× hunter:dining_no_table carrot.
- The loop pair, same second: `Villager 9082...81b is putting minecraft:carrot in
  BlockPos{x=105, y=69, z=-60}` → `Villager is taking minecraft:carrot from …` →
  `[PROBE:supplies] job=gatherer:dining_no_table TOOK carrot`, then
  `[WHY] job=gatherer:dining_no_table COLLECTING_SUPPLIES → …`.
- Same chest as the chest-walk-target fix (105,-60); this run already had that fix
  (2c9f5880), so the take now succeeds — and the diner then deposits it straight back.

## Hypothesis (unverified)

`dining_no_table` takes the carrot as its eat-state TOOL (DinerNoTableWork:
TOOLS_REQUIRED_AT_STATES NEED_EAT = #villager_food), then a drop/deposit path
(DROPPING_LOOT or the "put item" container step) returns it because the job's next
state can't start (no jobsite? flag-side eat spot), so it re-collects. Check which
state transition puts it back — the earlier 309-carrot gatherer:gather run (05:45) is
the same signature. Possibly the same family as eating/eat_direct + dine_at_time known
reds.

## Code read (2026-10-17, continued) — not yet a diagnosis

- The put-back is `Jobs.tryDropLoot` (DROPPING_LOOT): it drops every UNLOCKED item from
  `getItemsForDrop()` (= the whole journal). Locks come only from the player's slot locks
  (`ProductionJob` container listener), and `ProductionJournal.hasAnyLootToDrop` = any
  unlocked non-empty item. So a job keeps its "tool" (the diner's carrot, food listed as
  the NEED_EAT tool in `DinerNoTableWork`) only if the status logic never puts it into
  DROPPING_LOOT while holding it. Next: capture the `[WHY]` sequence for one take→put
  cycle to find which transition sends a freshly-supplied diner to DROPPING_LOOT.
- Not reproduced by village/day1 (day1-3/day1-4 logs: ≤39 takes per job, cooked food
  available). The playtest loop (898×, raw carrots) needs its conditions: the narrow
  store room and/or carrot-only food. A day1 variant seeding only carrots would test the
  second.

## Hypothesis from the playtest log (2026-10-17, later) — UNVERIFIED

/tmp/dev-server-6.log 06:26:50–52: `[PROBE:supplies] gatherer:dining_no_table TOOK carrot`
~10x/second (totalTakes 1345→1368) with no `[WHY]` transition between takes, so the take
and the put-back (`Jobs.tryDropLoot`, DEBUG "is putting") alternate inside one tight cycle.
The same session logged `[WHY] job=gatherer:dining_no_table … NO_JOBSITE` for this villager,
while the hunter was ALSO on `hunter:dining_no_table`. Candidate: two diners, one flag-side
eat spot (claim_workspot) — the diner without a spot holds a carrot it cannot use, the status
logic routes it to DROPPING_LOOT (the carrot is unlocked loot), it drops it, then re-collects.
Test: a day1 variant (or job blueprint) with two villagers hungry at once and only the
flag as an eat spot; expect the take count to explode for the second diner. Fix direction
if confirmed: a diner with no jobsite should wait (NO_JOBSITE) holding its food, not drop it.

## Root cause (2026-10-17 round 5) — the two-diner hypothesis was wrong

Reproduced with ONE villager (dev-server-8 18:07:54–18:09:44) and by `eating/eat_direct`: the
diner takes food, works state 0, consumes it at state 1, the flag spot reaches state 2 (DONE) —
then it goes back to the chest and takes more food instead of eating (extracting). Two causes,
both needed:

1. `dining_no_table` declares `DONE: 0` work at its terminal state; since the cook_extract fix,
   declared work counts as unfinished even at 0, so `getStatesWithUnfinishedItemlessWork` returned
   `[2]` and the itemless branch answered GOING_TO_JOB, which the root's "not going to jobsite"
   gate rejects → fallback → collect supplies. Fix: `DeclarativeJobs.declaresUnfinishedWork`
   (no unfinished work at maxState), wired in the realtime ticker and the warp.
2. The three hand-coded diner jobs lacked `PRIORITIZE_EXTRACTION` (every JSON job gets it), so the
   extraction branch was skipped whenever the town had food in storage. Added to all three.

The take/put loop is the fallback at the max-ticks expiry: `dining_raw_food` drops the held
carrot (DROPPING_LOOT), re-collects, and so on. With the meal now extracting, it never gets there.

Verified: `eating/` 4/4 (eat_direct 98 %, dine_at_time 96 %); full suite 70/73 with only known
failures (nav/roof_gatherer, chicken-arc/skip_chicken_command, farmer parity flake).
