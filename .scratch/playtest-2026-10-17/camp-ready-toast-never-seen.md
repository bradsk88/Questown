---
title: '"Your camp is ready" toast never seen — should show the first evening'
status: fixed
origin: 2026-10-17 playtest — user chat 04:31:13 (fallout from the 2026-10-15 20s cap)
---

"I never got a 'your camp is ready' toast. I should get that one the first
evening that a flag and campfire are present."

Evidence: `Broadcasting tutorial toast: tutorial.campfire_ready.title` at 04:26:06
(campfire quest completed, daytime). The 2026-10-15 fix capped tutorial toasts at
20 s wall-clock, so it expired unseen hours of game-time before dusk.

Fix: shown on the evening/night flag tick (`showCampReadyToastIfDue`) per player
who is awake, has not campfire-slept (`first_campfire_sleep` advancement) and was
not already shown it this session; hidden client-side once the player sleeps. No
wall-clock cap. Docs: `gui/COMPLEX.md` "Tutorial toast lifetime".
