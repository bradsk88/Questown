---
title: 'First-work-request quest should unlock when the first villager arrives, not at post time'
status: fixed
origin: 2026-10-15 playtest — user chat 02:59:19 (+ pacing note 03:01:36)
---

# Gate the first-work-request batch on the first villager

User report: "Also, it doesn't make sense for me to make a request yet. No
villagers yet. This quest should get unlocked when the first villager
arrives."

Related pacing note (03:01:36): "I've finished all my quests and there's
nothing left to do (except the work request, which doesn't make sense yet
and isn't possible yet)." — the day-2 board introduction currently posts
immediately after the job-board quest completes, ahead of any workforce.

Design change: post the first-work-request batch on first-villager arrival
(the same hook the visitor-arrival broadcast uses), not at job-board
completion. Consult docs/design-principles.md + CONTEXT.md (onboarding
pacing) before implementing; the post-time-baked quest NBT semantics mean
existing worlds keep the old timing.

## Fixed 2026-10-17

See `.scratch/playtest-2026-10-17/first-work-request-before-villager.md`.
