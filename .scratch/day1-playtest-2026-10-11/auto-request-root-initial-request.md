---
title: Auto-request a root job's initial_request when a new villager gets that root
status: done
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

## Re-scope (2026-10-13, walkthrough F2)

Not a town-wide behavior: this becomes a **toggle on the job board UI**
(decision in `job-board-organic-introduction.md`). Implementation now spans
two tickets: the board toggle (server flag NBT + board menu checkbox) plus
the auto-post on root acquire when the toggle is on. Default: ON.

## Correction (2026-10-12, lunch session)

The job-change path **already** auto-posts the root's `initial_request`:
`ChangeJobReward.getApplier` loops the root's `defaultWork` through
`ServerJobsRegistry.getDefaultWorkForNewWorker` (which returns
`work.initialRequest`) and `requestWork`s each. So the original ask is
already live (unconditionally) on the BOP/job-change path. What remains is
the re-scoped board toggle: flag NBT + board-menu checkbox + gate this
existing post on it + idempotency check. See the implementation notes in
`job-board-organic-introduction.md`.

## Shipped (2026-10-13)

The re-scoped remainder is in: the `ChangeJobReward` auto-post is gated on the
new flag toggle `boardAutoRequest` (default ON, NBT-persisted, checkbox in the
board menu via `ToggleBoardAutoRequestMessage`), and
`TownWorkHandle.requestWork` is now idempotent (`contains` guard — `List.add`
never dedupes). Original ask (auto-post on root acquire) was already live via
`ChangeJobReward` and stays so by default. Tests:
`TownWorkHandleRequestWorkTest`. Docs: `town/entity/COMPLEX.md` F2 section.

## Resolved (audit 2026-10-22)

Shipped 2026-10-13 as re-scoped: `ChangeJobReward` auto-post gated on the board toggle `boardAutoRequest` (default ON; `ToggleBoardAutoRequestMessage` exists in core/network), `TownWorkHandle.requestWork` idempotent; `TownWorkHandleRequestWorkTest`.
