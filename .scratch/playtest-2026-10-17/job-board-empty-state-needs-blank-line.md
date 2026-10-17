---
title: 'Job board empty state: blank line after "No work requests yet."'
status: ready-for-human
origin: 2026-10-17 playtest round 5 — user chat 18:03:24 / 18:03:37 (dev-server-8, build 3ed88de2)
---

"The job board should have an extra line break after "No work requests yet""

`WorkScreen` drew the hint at `y + 8` after advancing `y` by the headline's wrapped height from
the pre-padding `y`, leaving ~2px between the two. The hint now starts one wrapped line step
(`font.lineHeight * 1.5`) below the headline's bottom. Compile-verified only; check visually after
the next restart (and `[gui-lint] WorkScreen — OK`).
