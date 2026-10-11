---
title: Auto-request a root job's initial_request when a new villager gets that root
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:46:15:

> I think the initial_request value of the root job should just get
> automatically requested when a new villager gets that root.

When a new villager is assigned a root job, that root job's `initial_request`
item should be automatically posted to the work board (requested) so the
villager has something to work on, without the player having to request it.

## Acceptance criteria

- When a villager is assigned (or newly gets) a root job, that job's
  `initial_request` item is auto-added to the town's work requests.
- Idempotent: re-assigning / the same root doesn't spam duplicate requests.

## Notes

The `initial_request` is the item the job declares it needs to start (see
`scripts/job-producers.py` — OUTPUT = `initial_request`+`result`). Find where a
villager's root job is assigned (job change / onboarding job grant) and post the
root's `initial_request` to the work board there. Check for an existing
auto-request path (the onboarding may already post some) and extend rather than
duplicate. Confirm idempotency against the existing unmet-need registration.
