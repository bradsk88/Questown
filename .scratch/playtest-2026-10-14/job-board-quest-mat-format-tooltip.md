---
title: 'Job-board quest card: use the welcome-mat tooltip format (click to open crafting)'
status: done
---

2026-10-14: FIXED `582d26c1`.

# Job-board quest card: mat-format tooltip

Captured 2026-10-14 (fresh-world playtest, server-18, in-game chat 03:48:39):

> Job board quest's tooltip should use the same format as the welcome mat's
> (click to see crafting)

## Current

The job-board quest card tooltip goes through the flavor-text path (single
line, "Craft a Job Board at the town flag (flag > crafting tab) and place it
near the flag") and the card click does NOT deep-link to the crafting tab.

## Wanted (the TOWN_GATE / EMPTY_ROOM pattern, QuestsScreen)

- Two-line tooltip ABOVE the flavor check:
  - line 1: new key `menu.quests.roomblock.job_board` (copy ~ "Craft a job
    board at the town flag and place it near the flag.")
  - line 2: `menu.quests.roomblock.click_crafting` in gray
- `openLessonForClickedCard`: add `SpecialQuests.JOB_BOARD` to the
  TOWN_GATE/EMPTY_ROOM deep-link condition so clicking the card opens the
  crafting tab.
- Retire the now-dead `jobBoardQuest.setFlavorText(...)` in
  `TownQuests.addBasicsBatch` (single source of copy: the lang key).

## Resolved (audit 2026-10-22)

`QuestsScreen.java:292-301`: JOB_BOARD joined the mat/room tooltip format and the deep-link condition.
