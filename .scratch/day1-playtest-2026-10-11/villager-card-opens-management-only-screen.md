---
title: Villager screen (from the flag villager menu) should open management-only, no inventory tab
status: needs-info
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:19:34 + 23:20:08 (evening from-zero run, `/tmp/dev-server.log`):

> Clicking on a villager's card from the flag villager's menu should take me to
> their villager screen
> It should be in a "management only" mode. Where I can not see their inventory
> tab.

Two related asks about the villager screen reached from the flag's villager
menu:
1. Clicking a villager's **card** in the flag's villager menu should open that
   villager's (individual) villager screen.
2. That screen should be a **"management only" mode** — the **inventory tab is
   not shown**.

## Acceptance criteria

- Clicking a villager's card in the flag's villager menu navigates to that
  villager's screen.
- The screen opened this way is management-only: no inventory tab (the player
  manages jobs/status, not the villager's item inventory).

## Notes

Verify the current behaviour first: does clicking a card in the flag's villager
menu (`MultiStatusScreen` / the villager tab) do anything today, and which
screen does it open? Does that screen have an inventory tab? The villager screen
is likely the same one that has an inventory tab in normal play; this ask is to
gate the inventory tab off when the screen is reached via the flag's
management view (a "management only" flag/mode on the screen). Ties into the
flag-menu tab work (`flag-menu-default-tab-quests.md`) and the villager status
surfaces (`villager-menu-needs-job-board-during-evening-idle.md`,
`stats-page-show-didnt-eat-didnt-sleep.md`).
