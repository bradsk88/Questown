---
title: chicken/F3_build_room_to_welcome_mat stalls at WAITING_FOR_FREE_FOOD
status: needs-triage
created: 2026-09-05
priority: p2
---

# F3_build_room_to_welcome_mat fails: stuck at WAITING_FOR_FREE_FOOD

Observed while verifying the job-board-as-room-block change. The scenario
advances cleanly through WAITING_FOR_SIGN -> WAITING_FOR_FREE_FOOD
(`signPlacedAsJobBoard=true, chestPlaced=true`) and then never leaves
WAITING_FOR_FREE_FOOD; the run ends with `final beat state:
expected=WAITING_FOR_VILLAGER_UI actual=WAITING_FOR_FREE_FOOD`, RESULT 0/1.

Reproduces identically **with and without** the job-board-as-room-block change
(baseline run with those files stashed fails the same way), so it is
pre-existing, not a regression from that work.

Unverified: whether this is a timing flake (beat window too short) or a real
break in the free-food beat. Logs from both runs were at
/tmp/autotest_f3b.log (with change) and /tmp/autotest_f3_base2.log (baseline).
