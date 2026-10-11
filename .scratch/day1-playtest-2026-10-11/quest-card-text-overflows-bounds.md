---
title: "A new villager arrives in the morning" text overflows its card bounds
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:12:32:

> "A new villager arrives in th emorning" is bleeding outside the card's bounds.

A quest/card body line ("A new villager arrives in the morning") is not wrapped
to the card width and bleeds past the card's right edge.

## Acceptance criteria

- Card/quest body text wraps to the card's inner width (no line extends past the
  card border).
- Consistent with the other card screens (the quests screen and any
  `AbstractPagedCardScreen` descendant) so no card body overflows.

## Notes

Likely the body line is drawn with a fixed/oversized width or is not run through
the compat text-wrap helper (`Compat.splitText` / the screen's wrap util) at the
card's content width. Find where the quest card body is laid out and wrap it to
the card width. Check the other card screens for the same issue (this may be a
shared layout bug).
