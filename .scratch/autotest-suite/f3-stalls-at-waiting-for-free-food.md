---
title: chicken/F3_build_room_to_welcome_mat stalls at WAITING_FOR_FREE_FOOD
status: needs-triage
created: 2026-09-05
priority: p2
closed: 2026-09-12
resolved: 2026-09-12
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

## Resolved (2026-09-12)
A held `runServer` village/growth run (agent-driven, `-Dquestown.village.agentDrive=true`)
reproduced the stall and pinned the cause: it was a **real break, not a timing
flake**. The F3 welcome-mat beat places the welcome mat at the door/gate spot; that
replaces the door and un-registers the room, so there is no container and
`_qtdev free_food` fails with `no registered container` - the beat never advances.
The fix is a bare no-coordinate form on `_qtdev place_mat` (like `place_door`):
it drops the mat a block east of the flag, which never lands on the door, so the
room (and its container) survive and free-food clears. Verified live: the bare
form advanced the welcome-mat beat with no `room_destroyed`, and
`WAITING_FOR_FREE_FOOD` then advanced to `WAITING_FOR_VILLAGER_UI`
(`free_food deposited 10 carrots into a registered container`).

Note this was the *agent* path (the command that stands in for the player's
hands). If the F3 autotest's scripted `registerRoom` still places the mat at the
gate, the same fix belongs in `FlagPlaceBlockCommand` / the F3 blueprint; the
held-run reproduction confirms the mechanism regardless.
