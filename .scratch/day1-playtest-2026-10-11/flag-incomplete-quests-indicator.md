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
