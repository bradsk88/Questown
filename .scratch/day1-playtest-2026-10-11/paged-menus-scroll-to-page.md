---
title: Paged menus no longer scroll between pages (worked in 1.19.2)
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:31:25:

> In 1.19 you could scroll on pages menus like the quest menu and it would go
> between pages. Now that doen't work.

The paged menu screens (quests, etc.) used to advance pages with the scroll
wheel in 1.19.2; on 1.20.6 that no longer works.

## Acceptance criteria

- Scrolling (wheel) on a paged menu screen moves between pages (next page on
  scroll-down, previous on scroll-up), as in 1.19.2.
- Consistent across the paged screen family (quests + the other
  `AbstractPagedCardScreen` descendants).

## Notes

The scroll→page binding likely lived on the 1.19.2 screen base and was dropped
in the port. Check `AbstractPagedCardScreen` / `PagedCardScreen` for a
`mouseScrolled`/`doScroll`/`charTyped` handler (1.19.2 may have used a key or a
`mouseScrolled` override). `WorkScreen` already has a `mouseScrolled` override
(next/previousPage on scroll delta) — the quests/paged family may be missing the
equivalent. Wire scroll to the page increment/decrement on the paged base.
