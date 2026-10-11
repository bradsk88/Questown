---
title: Villager menu status "needs job board to work" is wrong during evening idle
status: needs-info
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:16:20 (evening from-zero run, `/tmp/dev-server.log`):

> The villager's menu says "needs job board to work" but they're currently in
> the evening idle state. So that is not helpful or true.

A villager's status in the flag/villager menu reads "needs job board to work"
even though the villager is actually in the **evening idle** state. The message
is either mislabeling the idle state or reporting a reason that isn't the real
one — the player finds it unhelpful and untrue.

## Acceptance criteria

- A villager in the evening/night idle state shows an accurate status (e.g.
  "sleeping" / "resting" / "idle at night"), not a work-blocker reason.
- "needs job board to work" only shows when the villager genuinely is blocked on
  a missing job board (a real work-gate), not when they're simply idle.

## Notes

Find the status-text mapping: where the villager's displayed status/need is
decided (`ServerJobsRegistry.getStatusText`, the `IStatus`/status packet, or the
villager-menu status line). The "needs job board to work" is a work-gate message
(job-board precondition, cf. `docs/adr`/`CONTEXT.md` "Leaver job" / job-board
gate). During the evening, a townie should be in an idle/sleep status, not a
work-blocked one. Check whether the idle/evening state is falling through to the
work-gate reason text instead of its own idle text. Load the
`questown-status-why` skill if a live dump is needed to see the decision inputs.
