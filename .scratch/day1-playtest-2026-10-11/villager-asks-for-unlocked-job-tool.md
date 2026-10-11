---
title: Villager says "I need a fishing rod to work" for a job that is not yet unlocked
status: needs-triage
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:31:36 + 17:31:44 (`run/client2.log`, one
thought across two messages):

> The villager is currently sayin "I need a fishing rod to work". But
> accoding their jobs screen, they have NOT unlocked that job yet.
> They shouldn't be asking for that.

## Notes

A gatherer's UNMET_ITEM need names an item (fishing_rods) whose
producer job the townie cannot hold yet (root not unlocked). The status
line surfaces the tool/item need before the job that could produce it
exists — the player sees a demand for an unattainable unlock. Related:
the JOB_UNLOCK quest system should be the only place this need
surfaces (or the status line should be gated on workability —
`docs/design-principles.md`: unlock ≠ workability).
