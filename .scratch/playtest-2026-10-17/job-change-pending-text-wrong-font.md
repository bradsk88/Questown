---
title: '"This villager will change job in the morning" renders in a different font than other screens'
status: fixed
origin: 2026-10-17 playtest round 2 — user chat 05:45:07
---

"'This villager will change job in the morning' is rendered in a different font
than what other screens use. It should use the same one."

Entry point: the change-profession tab (`JobChangeConfirmMenu` /
its screen) pending-change text; compare against the shared text path other
screens use (`RenderContext.darkText` / `Compat` text wrappers).
