---
title: Need an organic way to introduce the player to the job board
status: ready-for-human
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:46:32 + 22:46:47:

> We'll need to thin kabout how to organically introduce the player to the job
> board.
> As yet, I have not encountered anythign that would have driven me to check on
> it.

The player never had a reason to open the job board (the work-request board).
There's no in-game signal, quest, or moment that drives them to it. We need an
organic onboarding moment that makes the player discover the job board.

## Acceptance criteria

- A design that introduces the job board organically (a quest, a need, a villager
  prompting it, or a first-arrival beat) so a new player naturally opens it.
- The empty-state copy work (`job-board-empty-state.md`) supports this but a
  teaser alone won't drive discovery.

## Notes

Design discussion. Candidates: a day-1/day-2 quest that asks the player to
post a request; a villager who can't start because the board is empty ("I need
someone to request my first job"); a toast/teaser tied to the first-villager
arrival; the job-unlock quests already point at the board. Decide which beats
the onboarding and where it fires. Keep the "tease, don't CTA" copy principle
(`docs/design-principles.md` P10).
