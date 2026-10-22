---
title: '"While you were away (0 days)"'
status: done
origin: 2026-10-17 playtest round 5 — user chat 18:21:05 (dev-server-8)
---

The summary fires from 12000 ticks away, but the day count was `ticks / 24000`, so a sub-day
absence printed "0 days". Now `AwayDuration.describe`: "half a day" / "1 day" / "N days".
Pinned by `AwayDurationTest` (3).

## Resolved (audit 2026-10-22)

`town/AwayDuration.describe` ('half a day' / '1 day' / 'N days'), `AwayDurationTest`.
