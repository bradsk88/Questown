---
title: 'Villager "change profession" tab: red dot while a job-change quest is open and the villager is a gatherer'
status: fixed
origin: 2026-10-17 playtest round 2 — user chat 05:38:35 (feature request)
---

"The 'change profession' tab should have a red dot when a job change quest is
active and the villager is a gatherer."

Attention-cue pattern (design-principles "The UI reflects the world — hide empty,
badge ready"): the flag menu's free-food tab already red-dots when there is something
to claim. Condition: an incomplete JOB_CHANGE quest exists in the town AND this
villager's job root is gatherer (the default root, i.e. not yet given a job).
