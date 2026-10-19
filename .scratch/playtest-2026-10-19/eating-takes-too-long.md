---
title: 'Villagers take far too long to eat: should take ~1/10 of the current time'
status: resolved
origin: 2026-10-19 playtest (dev-server-13) — user chat 03:40:34, 03:41:30, 03:41:57
---

"Villagers take way too long to eat. Should take 1/4 of the time the currently do." /
"They're still eating." / "Should take more like 1/10th the time it currently does."

## Findings (2026-10-19)

Two separate causes in /tmp/dev-server-13.log (villager 20b70d5c):
1. The eat step itself: work 25 (no table / raw) and 50 (table). A meal that went through took
   ~59 s. Fixed: 25 -> 3, 50 -> 5 (`DiningWorkDurationTest`; eating autotests 4/4).
2. A stall: 03:35:54 `dining_no_table` sat in GOING_TO_JOB toward a state-1 spot at
   (-72,70,-216) for exactly 100 s (MaxTicksWithoutFood 2000), then fell back to
   `dining_raw_food` - every meal attempt first burned a 100 s timeout. Open: see
   dining-going-to-job-stall.md.

## Resolved

c0261ae0 (diner work 50→5 / 25→3; DiningWorkDurationTest). Not yet confirmed by the dev in a client.
