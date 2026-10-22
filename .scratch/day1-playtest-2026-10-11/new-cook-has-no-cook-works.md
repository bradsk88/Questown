---
title: A newly-created cook has no 'cook' works — only extract and fuel
status: done
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 (`/tmp/dev-server-7.log`):

- 19:18:59: > My manually-created cook didn't have any "cook" jobs. Only
  extract and fuel.
- 19:19:10: > Now that I've unlocked one, they're cooking.

A cook that the player created (manually — via BOP job-change or spawn)
started without the basic cook work available; only after unlocking one
could it actually cook. A cook whose only works are extract/fuel is a
gatherer wearing a cook's name.

## Acceptance criteria

- A villager holding the cook root has at least one basic cook work
  claimable on creation (no unlock prerequisite), mirroring how other roots
  get their base work.

## Notes

Check the cook job JSONs under
`src/main/resources/data/questown/questown_jobs/cook/` for the
initial_request/parent wiring, and how `isUnlocked(ownerUUID, workId)`
treats a freshly-changed cook (the `changeJobForVisitorFromBoard` /
`spendBopToRequestJobChange` paths). Possibly related: the
`cook-skill-unlock` and `cook-storm` `.scratch` folders from earlier
sessions.

## Shipped (2026-10-12, lunch session)

Root cause was the opposite of the guess: the simple-furnace template's
expanded jobs were NOT `unlocked_with_root_job` — the 2026-10-15 ruling had
restricted mutton to the onboarding flow, so every manual/staffed cook got
only extract + fuel. Fix: new `starter_recipes` JSON array on the template
(`["mutton"]`) — the loader ORs it into the expanded jobs' `unlocked_with_root_job`,
so all staffing paths (spawn, register, job-change, BOP) get the starter
recipe through `ServerJobsRegistry.getDefaultWork`. Per-item, data-driven;
beef/rabbit/chicken/porkchop/potato stay progression-gated (the bulk-grant
rejection from `cook-skill-unlock` still holds — amended there). The
onboarding-only `ChangeJobReward.onboarding` flag and its grant were deleted.
Test: `CookStarterRecipeLoaderTest` (JSON load gate; the registry-bound
expansion is `@Disabled` with the reason). Full suite green (1166/0/51skipped).

## Resolved (audit 2026-10-22)

`cook_simple_furnace_food.json:6` `"starter_recipes": ["mutton"]` ORs mutton into every cook's default work; later playtests' cooks run cook/mutton (e.g. 10-22 r3 findings cook steady state).
