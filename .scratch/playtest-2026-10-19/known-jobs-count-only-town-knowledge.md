---
title: '"There are N known jobs that produce this item" should count only town-known producers'
status: resolved
origin: 2026-10-19 playtest (dev-server-13, build 5ee3e3d6) — user chat 03:34:54 (+ 03:36:54 ":point_up:")
---

"The "there are 20 known jobs that produce this item" should only show jobs where the item is
present in the towns "knowledge" store."

## Resolved

ead921f3 (count only jobs whose known results include the item). Not yet confirmed in a client.
