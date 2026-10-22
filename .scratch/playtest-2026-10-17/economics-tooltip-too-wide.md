---
title: 'Economics card tooltip is not width-limited like the other screens'
status: done
origin: 2026-10-17 playtest round 6 — user chat 19:36:46 (dev-server-9, build fe1b7238)
---

"Econimics tooltip should be small width like th eother UIs."

`TownEconomicsScreen.renderTooltips` passed unwrapped lines straight to `showTooltip`, so the
free-food line ran wide. It now goes through `AbstractPagedCardScreen.renderTooltipWithDynamicWrapping`
(made protected), the same wrap-to-the-space-right-of-the-cursor rule the other card screens use.
Compile-verified; check visually after the next restart.

## Resolved (audit 2026-10-22)

`TownEconomicsScreen.java:114` renders through `renderTooltipWithDynamicWrapping`; the 2026-10-22 agent playtest verified the economics tooltip wraps as a paragraph (findings #8, gui-lint OK).
