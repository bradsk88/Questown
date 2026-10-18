---
title: 'BOP-made onboarding cook had no fuel / cook_mutton jobs'
status: resolved
origin: 2026-10-18 playtest (dev-server-12) — user, in Claude chat
---

"Looks like the cook I got from BOP during onboarding did not get their fuel or cook_mutton jobs
unlocked as they should have." Cause: `SimpleVillagerHandle.unlockAndChange` (the morning BOP root
change) unlocked only `cook/extract`; ChangeJobReward/registration also unlock the root's
`getDefaultWork`. Fixed via `Delegator.getDefaultWork`. Pinned: day1
`cook-starter-job-unlocked[fuel|mutton|extract]` (red on pre-fix: fuel + mutton) and
`SimpleVillagerHandleTest.morningRootChange_unlocksTheNewRootsDefaultWork`.
