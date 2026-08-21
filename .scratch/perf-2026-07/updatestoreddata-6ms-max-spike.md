---
title: updateStoredData has a 6.6ms max spike in the flag tick
status: needs-triage
created: 2026-08-21
priority: p2
---

## Context

Recorded as an open lead when `updateWorkStatuses` was fixed (see
[[updateworkstatuses-is-64pct-of-tick-time.md]]): with that phase down to 4% of
flag-tick time, `roomsHandle` is now the largest phase (219us avg, 57%) and
`updateStoredData` is the largest remaining spike (**6.6ms max**) in
`perf/town_large` (20 townies / 16 rooms, `FlagTickInterval = 10`).

Not yet investigated — no sub-phase timing exists for either.

## Acceptance criteria

- Sub-phase timing identifies what inside `updateStoredData` produces the 6.6ms spike.
- Flag-tick max drops accordingly, or the spike is shown to be unavoidable/cheap enough to ignore.
