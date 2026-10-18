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
