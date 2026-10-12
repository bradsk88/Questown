---
title: Show a bubble ("!") above the job board when the town has no work requests
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:33:35 (`run/client2.log`):

> If the town has no work requests, a bubble should appear above the
> board (probably just a "!")

## Notes

Diegetic attention indicator on the job-board block, mirroring the
flag-incomplete-quests-indicator idea (`flag-incomplete-quests-indicator.md`).
Trigger: town has zero outstanding work requests. Related:
`docs/todo/flag-speech-bubble.md`.

## Triage notes

Mechanical: a diegetic '!' bubble when the job board has requests, via the BubbleRenderer.renderIconBubbleAtBlock pattern (NeedBubbleClientEvents). Needs a block-state property or client-side BE scan. Implementation path identified.

## Decision (2026-10-13, walkthrough G2)

Confirmed as scoped: "!" bubble above the job board while the town has zero
outstanding work requests; `BubbleRenderer.renderIconBubbleAtBlock` via the
`NeedBubbleClientEvents` pattern; block-state property or client-side BE
scan. Second consumer of the G1 bubble system.

## Implementation (2026-10-12)

Shipped with G1 in the same bubble system: the flag's update tag carries the
job-board positions and an `attentionNoWorkRequests` flag (live
`workHandle.getRequestedResults().isEmpty()`); `AttentionBubbles.near` turns each
board of a zero-request town into a "!" candidate; drawn by
`BubbleRenderer.renderTextureBubbleAtBlock`. See
`flag-incomplete-quests-indicator.md` "Implementation". Awaiting in-game
verification.
