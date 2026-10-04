---
title: New cooks unlock a working starter set; recipes stay in the level tree
status: ready-for-agent
created: 2026-10-15
updated: 2026-10-16
assignee: agent
parent: village-self-sufficiency
---

## Context

During the 5-day `village/self_sufficiency` held run (handoff `redpoll`), the
cook was staffed with a single unlocked job — `cook/extract` — and re-selected
it on its `cooldown_ticks: 50` (`Job changed to cook/extract [from cook:extract]`
storm; at 150–300 t/s that pinned the server: `Can't keep up` 5.8–18.5 s behind).

**User rulings (authoritative — do not re-interpret scope):**
1. Not a cost bug to guard — a staffing-design gap; the fix is a feature.
2. **The cook's food recipes ARE a leveling progression — never grant them in
   bulk at staffing.** My initial "tag the template → all 6 foods, every cook"
   option was explicitly rejected for this reason.
3. A second (non-onboarding) cook starting as **extract + fuel only — a
   stoker/collector "grunt" who learns cooking by leveling — is acceptable**
   (user asked, considered, and did not object; option A locked).

## Final spec (locked, 2026-10-16)

| Cook | Unlocked at staff | Where |
|---|---|---|
| Onboarding cook (tutorial `PHASE_JOB_CHANGE_FOOD`) | `cook/extract` + `cook/fuel` + **`cook/mutton`** (starter recipe; hunter is guaranteed to have produced mutton) | universal set + onboarding grant |
| Any other new cook (register or job-change) | `cook/extract` + `cook/fuel` (stoker + collector; recipes via the job tree) | universal set only |

Locked out for everyone at staffing (progression-gated): `cook/rabbit`,
`cook/porkchop`, `cook/chicken`, `cook/beef`, `cook/potato`, `cook/fish`,
`cook/stock_fuel`, `cook/stock_ingredients`.

## Root cause (verified in code, branch `1.20.6-neoforge-wip`)

- `ServerJobsRegistry.initJobs` builds each root's `defaultWork` (the set a
  villager unlocks at staff) from exactly `isUnlockedInitially(job) ==
  (job.parentID == null)`. For cook that is `[cook/extract]` only — every other
  cook work has a progression parent.
- `TownVillagerHandle.register` (spawn path) and `ChangeJobReward.getApplier`
  (job-change path — **the path the self_sufficiency cook uses**: log proves
  `cook/downtime → cook/seeking_work → cook/extract`) both unlock `defaultWork`.
- `WorkSeekerJob` only sees unlocked jobs → single-job cook re-selects `extract`
  on cooldown. That is the storm.

## Design (locked)

- **Universal:** new boolean `unlocked_with_root_job` on job JSON → `Work`
  field → `isUnlockedInitially == (parentID == null) || flag`. Tagged on
  `cook_fuel.json` only. (Extends the existing "starter job" concept from
  `CONTEXT.md`; update that entry — and `getAllRootJobs()`'s contract stays
  parent-null only.)
- **Onboarding grant:** `TownQuests.processJobChanges` is the onboarding-only
  "cook staffed" event (owner adoption of the onboarding JOB_CHANGE quest,
  `q.isOnboarding()`). On adoption of the cook quest, `unlockJob(owner,
  JobID("cook","mutton"))` for the adopted owner — logged so the held run can
  assert it. Idempotent (set add).
- **Trap (caught in review):** `ChangeJobReward` is SHARED with non-onboarding
  job changes — the mutton grant must NOT live there; `processJobChanges`
  adoption is the onboarding-scoped hook.
- `cook/mutton` is a runtime JobID — `makeCookJobs` expands
  `cook_simple_furnace_food.json` per item in
  `villager_simple_furnace_food_raw` (rabbit, porkchop, **mutton**, chicken,
  beef, potato); no `cook_mutton.json` file exists.

## Test seams (TDD, Forge-free)

- RED→GREEN: `Work.isUnlockedInitially(@Nullable JobID parentID, boolean
  unlockedWithRoot)` pure static — 4 cases (parent null / non-null × flag
  false / true). `JobID` is a plain 2-string buildable record; `Work` loads
  without registry (no statics) though constructing one needs an `ItemStack`.
- **Untestable half (name it, don't fake it):** `ResourceJobLoader`
  (JSON→`Work`, registry-bound), `getDefaultWork`, and the
  `processJobChanges` onboarding grant get compile + held-run coverage.

## Acceptance criteria

- `defaultWork("cook") == [cook/extract, cook/fuel]` (tag on `cook_fuel.json`).
- Onboarding cook additionally has `cook/mutton` (grant log line + behavior).
- No recipe beyond mutton unlocked at staff (level tree intact).
- **Held `self_sufficiency` run at high tick rate:** cook day 1 shows
  `effective ≈ target TPS`, zero `Can't keep up`, no `cook/extract [from
  cook:extract]` storm; village working on its own (cook functional, food
  chain producing). Update the `AUTOTESTS.md` TPS caveat (prior band was
  measured with the staffing gap live).

## Notes

- Plan: `docs/plans/cook-skill-unlock.md`.
- Working-tree noise that is NOT ours: `.pi/skills/questown-unit-tdd-fix/SKILL.md`
  (left modified from the `redpoll` session; do not stage).
