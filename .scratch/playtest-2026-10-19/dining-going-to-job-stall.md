---
title: 'dining_no_table sits in GOING_TO_JOB for the full 2000-tick timeout'
status: ready-for-agent
origin: 2026-10-19 playtest (dev-server-13), found investigating eating-takes-too-long.md
---

03:35:54 villager 20b70d5c -> gatherer:dining_no_table; [WHY] gate: town has workable spot with
state 1 at (-72,70,-216); status GOING_TO_JOB every check until 03:37:34 (100 s =
MaxTicksWithoutFood 2000), then dining_raw_food. Same 100 s pattern on the next meal. Unknown
yet: whether the spot is unreachable (y=70/71 vs floor), a stale state-1 left by an earlier
meal, or another villager's spot. Next: an autotest that dines with no table and asserts the
diner reaches its spot within N ticks.
