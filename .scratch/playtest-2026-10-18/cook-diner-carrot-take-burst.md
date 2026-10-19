---
title: 'Fresh cook in dining_no_table took ~378 carrots in ~20 s'
status: needs-triage
origin: 2026-10-18 playtest (dev-server-12, build dd7a43f5) — observed in the log, not reported by the user
related: commit fe1b7238 "Diners eat the finished meal instead of fetching more food" (the 2026-10-17 carrot take/put loop)
---

## Facts (/tmp/dev-server-12.log)

- 22:11:52 villager c50d73a4 (Villager 3) root-changed gatherer -> cook/extract (BOP); 22:11:57 ->
  `cook:dining_no_table`.
- `[PROBE:supplies] job=cook:dining_no_table TOOK carrot` lines: 378 in minute 22:12, ~20 per
  second at 22:12:50–52 (totalTakes counter 598 -> ~980). 22:12:53 the cook moved to
  `dining_raw_food`, and the burst stopped.
- Other diners in the same minute (hunter a65e35e4, gatherer a13c1942) show normal take counts.
- Not checked: whether the carrots were put back (no PUT probe line exists), i.e. a take/put loop
  vs real consumption; whether the cook root change (work-spot states cleared at 22:11:52) is
  related.

## Reproduced in village/growth (2026-10-19, worktree build, autotest at ~370 t/s)

`build/autotest-logs/growth-poss.log` (a 6-minute capped run, cook starter-request fix not yet in):

| job / item | TOOK lines |
|---|---|
| hunter/sword stone_sword | 3538 |
| cook/dining_raw_food mutton | 3183 |
| cook/dining_raw_food beef | 2940 |
| cook/dining_raw_food chicken | 1986 |
| cook/dining_raw_food red_mushroom | 971 |

One dining_raw_food pass logged ~275 "TOOK 1 mutton" in ~2 s, then "will not prioritize food after
giving up". The hunter cannot have produced 3000+ mutton, so these are takes that get put back (or
re-counted), not real consumption — still unverified: no PUT probe exists. Raw food is
DinerRawFoodWork's state-0 *tool* and state-1 *ingredient* (`TOOLS_REQUIRED_AT_STATES` /
`INGREDIENTS_REQUIRED_AT_STATES`), and the sword is hunter/sword's tool: both bursts are on
tools, which suggests a take-tool / return-tool loop. Next: add a PUT probe beside the TOOK one
and run `eating/eat_raw_food`.
