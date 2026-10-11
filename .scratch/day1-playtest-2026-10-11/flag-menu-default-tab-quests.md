---
title: Flag menu opens with no tab selected; should default to the quests tab
status: implemented
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

## Fix (2026-10-11 evening, session 2)

Two-part root cause + fix, build-verified in an isolated worktree
(`compileJava` green):

1. **No tab was ever highlighted.** `FlagTabs.build` set the `Tab.selected` field
   to `(fn == null)` (i.e. "is this tab disabled"), which is `false` for every
   enabled tab — so `Tabs.draw` (which uses `selected()` to pick the highlight
   art) never highlighted anything. `FlagTabs` now takes an `activeTabKey` and
   sets `selected = activeTabKey.equals(titleKey)`; each of the 5 flag screens
   passes its own tab key via a new `FlagTabs.forMenu(menu, activeTabKey)`
   overload (quests/villagers/economics/blocks_of_progress/crafting), so the open
   tab is highlighted. The 1-arg `forMenu(menu)` is kept (delegates with null →
   nothing selected, e.g. the overview).
2. **First open landed on the multi-status overview** (no content, no tab).
   `TownFlagBlock`'s first-open path now opens the **QUESTS** tab when
   `getInfo().hasIncompleteQuests()` (before the BOP pre-select, after the
   BOP-full branch), so a fresh day-1 flag opens on the quests the player can
   see. `QuestsScreen.forTown` marks "tooltips.quests" selected; `forVillager`
   is untouched (it uses `VillagerTabs`).

Needs a live client to confirm the highlight + the first-open-to-quests.
