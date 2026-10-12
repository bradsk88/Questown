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

## Decision (2026-10-13, walkthrough F2)

**A day-2 quest — "post your first work request"** on the job board
(completes on any posted request; small reward; tooltip points at the board).
Quests are the game's native CTA; P10 tease copy stays for the board's
in-menu copy only. The onboarding quest (craft + place the board, shipped
2026-10-13 `75e8b081`) already handles object discovery.

Related re-scope (same walkthrough): `auto-request-root-initial-request` is
NOT a town-wide default — it becomes an **OPTION on the job board**: a
toggle checkbox in the board UI. When on, acquiring a root job whose
`initial_request` is set auto-posts that request. Default state: ON (the
board reads alive; the player can silence it) — confirm.
