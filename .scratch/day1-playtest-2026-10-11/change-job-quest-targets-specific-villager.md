---
title: 'change-job' quest batch should target the specific villager not yet changed
status: ready-for-human
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 19:04:07 (`/tmp/dev-server-7.log`):

> The "change job" quests should choose a specific villager for the second
> quest. It should choose the one that has not already been changed. Or
> maybe just require the change to be from gatherer(specifically)->cook

The two-quest "change job" batch (hunter + cook = "after cook", per
`TownQuests`) can re-ask for a change on a villager who has already been
changed, making the second quest feel redundant or wrong.

## Acceptance criteria

- The second change-job quest names / targets a villager who has not yet had
  their job changed, OR the batch is re-scoped to a specific transition
  (gatherer → cook) so the quest text is honest.

## Notes

Design item — needs a product call between "target a specific villager" and
"require a specific transition".

## Note (2026-10-13, onboarding ruling D)

The onboarding ruling (expected-guaranteed-cook-got-arborist.md) landed the
"require a specific transition" side of this design item for the tutorial
arc: each onboarding JOB_CHANGE quest names its specific job, and while it is
open that job's root fills the BOP-spend guarantee slot — the second
onboarding change (cook) can no longer feel redundant or land on the wrong
root. The "target a specific villager" side (quest text naming a townie)
remains open for the post-onboarding job-change flow.

## Audit (2026-10-22)

Onboarding side done (ruling D: each onboarding JOB_CHANGE quest names its job and fills the guarantee slot). Open: the post-onboarding 'name a specific townie' side is a design call, queued in .scratch/feedback-audit/README.md.
