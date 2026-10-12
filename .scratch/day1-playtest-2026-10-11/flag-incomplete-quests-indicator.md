---
title: Flag should visibly indicate it has incomplete quests
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 17:10:00 (`run/client2.log`):

> When the flag has incomplete quests, it should indicate to the user
> somehow. Easiest implementation would probably just be red version of
> the BOP "sparkles" but I think an balloon/bubble could be nicer.

## Notes

Related (distinct) existing todos: `docs/todo/flag-speech-bubble.md`
(hint of the *most relevant* quest, diegetic bubble) and
`docs/todo/bop-progress-indicator.md` (BOP tab progress + "v4 tab
indicators / red dots" — this suggestion overlaps with 4E). Decide at
triage whether this folds into one of those.

## Triage notes

Design item: an at-a-glance indicator that the flag has incomplete quests (beyond the tab red-dot). Needs a product decision.

## Decision (2026-10-13, walkthrough G1)

**(b) Diegetic bubble above the flag** ("!" icon) while any quest is
incomplete — one bubble system serving both the flag (G1) and the job board
(G2, `job-board-no-requests-bubble.md`), and the foundation for the older
`docs/todo/flag-speech-bubble.md` (the bubble later grows to show the most
relevant quest hint). Chosen over red BOP sparkles: visible AND informative,
and the mechanism amortizes across the three.

## Implementation (2026-10-12)

Done via the flag's block-entity update tag (the dead-door sync channel,
ADR-0011): `TownFlagBlockEntity` syncs `attentionIncompleteQuests` (live
`TownQuests.hasIncompleteQuests()`), job-board positions and
`attentionNoWorkRequests`; `AttentionBubbles` (town/rooms, mirrors
`DeadDoorBubbles`) makes flag/board positions candidates in the one-bubble
crosshair contest; the winner draws the "!" texture bubble
(`BubbleRenderer.renderTextureBubbleAtBlock`, `textures/bubble/attention.png`).
See `town/entity/COMPLEX.md` "Attention (\"!\") bubble state". Awaiting
in-game verification on the user's day-1 playtest.
