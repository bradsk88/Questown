---
title: Flag menu opens with no tab selected; should default to the quests tab
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 21:50:15:

> When I first opened the flag, no tabs were selected. It should have opened
> quests since it had stuff to see.

(Note: this message predates the crosshair message and was missed when the
evening batch was first logged; added here so it isn't lost.)

## Acceptance criteria

- Opening the flag menu selects a sensible default tab — the quests tab when it
  has content (incomplete quests), falling back to another populated tab if not.
- No "no tab selected" empty state on first open.

## Notes

Find where the flag menu's initial selected-tab is decided (the tab
`FlagTabs`/`FlagTabsEmbedding` wiring or the screen init) and default it to the
quests tab when there's an incomplete quest, matching the player's expectation
that the tab with "stuff to see" is open.
