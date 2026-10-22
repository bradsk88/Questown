---
title: 'Job-change broadcast ("messages.jobs.changed") printed twice'
status: done
origin: 2026-10-15 playtest — user chat 03:16:12; log 03:15:55.572/.573
---

# messages.jobs.changed double-fired

User: ":point up: that printed twice. Should have only printed once."

Log evidence: two identical `messages.jobs.changed [hunter:sword, Villager 1]`
broadcasts at 03:15:55.572 and .573 — same millisecond. Find the two call
sites that broadcast the job-changed message (one is likely on the job
change itself, the other on the morning-queue / state transition that
follows it) and drop the duplicate.

## Resolved (audit 2026-10-22)

bbbdf88f: `AbstractTownFlagTicker.runMorning` marks isMorning on campfire wake, so the detection path no longer re-runs the morning pass in the same window (the double `messages.jobs.changed` was that second pass). Same-tick duplicate lines are also dropped by `BroadcastPacer` since a73a1401.
