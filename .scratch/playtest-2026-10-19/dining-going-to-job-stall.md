---
title: 'dining_no_table sits in GOING_TO_JOB for the full 2000-tick timeout'
status: needs-info
origin: 2026-10-19 playtest (dev-server-13), found investigating eating-takes-too-long.md
---

03:35:54 villager 20b70d5c -> gatherer:dining_no_table; [WHY] gate: town has workable spot with
state 1 at (-72,70,-216); status GOING_TO_JOB every check until 03:37:34 (100 s =
MaxTicksWithoutFood 2000), then dining_raw_food. Same 100 s pattern on the next meal. Unknown
yet: whether the spot is unreachable (y=70/71 vs floor), a stale state-1 left by an earlier
meal, or another villager's spot. Next: an autotest that dines with no table and asserts the
diner reaches its spot within N ticks.

## Update (2026-10-19, later) — likely fixed by 44fa5537, unverified

`dining_no_table`'s state 0 (NEED_EAT, work 3) is work-only, like cook/extract. The playtest [WHY]
dump showed the same signature the cook had: "Stage: 0 ... RoomsContainWorkState=false because no
spots found" (work-only rooms were filtered out of "town has workable blocks") with only a stage-1
spot visible, and the diner sat in GOING_TO_JOB until the 2000-tick timeout. 44fa5537 stopped
filtering work-only rooms. The realtime `eating/eat_no_table` scenario passes (it did before too),
so this is not reproduced on a fixture; confirm in the next playtest that no diner sits in
GOING_TO_JOB for ~100 s.

## village/growth observation (2026-10-19, after 44fa5537 + d144e193)

`build/autotest-logs/growth-wrapup.log` (5/5 villagers): 16 dining stints. Longest ~1200 ticks
(±400, the driver's observe grid) — a hunter `dining_no_table` at 18:39:25 that logged
`[WHY] ... GOING_TO_JOB → GOING_TO_JOB` — the rest ≤ 800. No stint reached the 2000-tick timeout
the playtest showed. Not proven fixed: the fixture has one spot layout and runs at ~370 t/s.
Next: in the next playtest, watch for a diner in GOING_TO_JOB for ~100 s; if seen, capture the
`[WHY]` dump for that villager.
