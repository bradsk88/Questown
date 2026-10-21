---
title: 'village/growth stalled at 2 villagers: hunter COLLECTING_SUPPLIES with iron swords in reach'
status: resolved
origin: 2026-10-21 capped village/growth run (16:42–17:10), beside the sharded suite
---

## Facts

- One `village/growth` run stopped growing at 2 villagers. The welcome mat was at state 1.
  Two later reruns (the commit before and the current one, run in parallel) both passed.
- The run started at 16:42, before `3f13bf53` (17:10, "collect a waiting finished product
  before collecting supplies"). That commit fixed the after-sleep hunter stall
  (`.scratch/playtest-2026-10-21/hunters-stuck-collecting-after-sleep.md`), so this may be the
  same bug. Not confirmed.
- `[STALL:collecting]` fired 81 times, all from the same two villagers:

  | villager | job= in the line | reports |
  |---|---|---|
  | 14731d65 | hunter/sword | 57 |
  | 14731d65 | gatherer/gather, hunter/dining_no_table, hunter/dining_raw_food | 3, 2, 1 |
  | 2f6afb87 | cook/dining_no_table | 14 |
  | 2f6afb87 | gatherer/gather | 4 |

- Every report shows `held=[]`, `statesNeedingInput=[0]`, and one spot `[-19, 65, -15]:1
  claimable=true` in both the villager's own work-status store and the town's.
- `suppliesTarget=` prints the target **chest's contents** (`ContainerTarget.toShortString` →
  `container.toShortString`), not a list of wanted items. It grows from `bread` to
  `bread, iron_sword ×4` to `bread, iron_sword ×4, cooked_mutton ×3`, which is just that chest
  filling up. So the hunter stood next to a chest with 4 iron swords in it, at state 0 with
  empty hands, and never took one.
- One villager is reported under several jobs (the rootId/jobId pair changes). That fits the
  villager switching jobs (dining → gatherer → hunter) while the stall continues. The tripwire
  is per job instance, so each switch restarts its 1200-tick window.

## Next

1. Run a few capped `village/growth` runs on HEAD, each beside the shards. If none stalls,
   close this as fixed by `3f13bf53`.
2. If it does stall on HEAD: find out why the take from a chest holding swords fails. Read
   the hunter/sword state-0 input and tool (`python3 scripts/job-producers.py iron_sword`,
   then the job JSON) and the `[WHY]` lines around the first report.
3. The tripwire's `suppliesTarget=` label is misleading (it is the chest's contents). Rename
   it to `targetChest=` when the tripwire is next touched.

Log: `.scratch/autotest-suite/growth-stall-2villagers-2026-10-21.log.gz` in the main checkout
(untracked; 12 MB uncompressed).

## Resolution (2026-10-21 18:33)

Three parallel `village/growth` runs on HEAD `39e9dae8` (`tools/autotest-parallel.sh 3
village/growth`): all three reached 5 villagers (in 3m40s, 6m30s and 8m20s), with zero
`[STALL:collecting]` reports. Treated as fixed by `3f13bf53`. Reopen if a growth run on or after
`3f13bf53` stalls at 2 villagers. Step 3 above (rename the label) still applies.
