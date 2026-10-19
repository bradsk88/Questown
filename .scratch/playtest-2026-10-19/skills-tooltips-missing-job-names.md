---
title: 'Skills UI hover tooltips do not show job names'
status: resolved
origin: 2026-10-19 playtest (dev-server-13) — user, in Claude chat after "I don't see it" (04:10:27)
---

"The hover tooltips on the skills UI do not show the job names. So I didn't THINK I saw it."
(The dev looked for the cook's extract job in the villager's skills tree and could not tell which
node was which.)

## Resolved

b2a7cef1 (tooltip drawn at z+400 above the pane, as in the 1.19.2 golden). Verified by a porttest capture: the hover shows "gather".
