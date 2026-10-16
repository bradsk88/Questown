---
title: '"There are N known jobs" hint should only count jobs known to this town'
status: fixed
origin: 2026-10-15 playtest — user chat 03:05:08
---

# Unmet-need hint counts ALL jobs, not town-known jobs

User report: '"There are 20 known jobs that produce this item" should only
show jobs that are "known" to the town.'

The hint (added 2026-10-15, commit `8f5a2138` unmet-need hints) counts
producers across the whole job dataset (lang key
`menu.work_add_confirm.there_are_n_known_jobs`). The town only *knows* the
jobs it has unlocked/seen — the hint should count against that subset,
matching the design principle that the town discovers jobs progressively.
Find the hint's producer-count computation (the unmet-need hint code) and
the town's known-jobs set (job unlock tracking) and intersect them.

Fixed: `TownVillagerLearningHandle.getTownKnownJobs()` (knowledge-tree parents+children of every villager's root, unioned with the unlocked set) re-exposed via `TownVillagerHandle` + `TownFlagBlockEntity.getTownVillagerHandle()`; `AddWorkFromUIMessage` INQUIRED filters `ServerJobsRegistry.getAllJobsThatProduce` through it before sending `WorkRequestConfirmMessage`, so the hint, the progressive-discovery card, and the job-icon column all see only town-known jobs. Registry-bound end-to-end (handle ctor reads Config.JOB_TREE_GROWTH) - held-run-covered. See town/entity/COMPLEX.md. Committed 2026-10-16 on branch pt-ui.
