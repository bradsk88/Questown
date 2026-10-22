---
title: 'RoomBlock quests should have a custom "Place a [X] ..." tooltip'
status: done
created: 2026-10-11
---

## Context

Design walkthrough, 2026-10-11 (from the job-board RoomBlock discussion):

> Should probably have custom tooltip for quests that utilize a RoomBlock.
> They should say "place a ___ anywhere in town" or something.

Quests that require **placing a RoomBlock** (job board, welcome mat, mineshaft,
fishing station) currently show the generic item-quest text. They should get a
custom tooltip that leads with the verb **place** and the location, so the player
understands the quest is about *placing a functional block*, not crafting/collecting
an item to hold.

## Decision (2026-10-11)

- RoomBlock quests get a custom "Place a [X] …" tooltip.
- The **location clause differs per block** — do not hardcode "anywhere in town":
  - Job board / mineshaft / fishing station → *"Place a [X] in your town"*
    (free placement; registers to the flag via the item's NBT).
  - Welcome mat → *"Place the welcome mat at your town's entrance"* (specific
    role; "anywhere" would mislead).
- Applies to **every** RoomBlock quest, so codify it as a quest-text convention,
  not a one-off.

## Acceptance criteria

- Each RoomBlock quest shows a "Place a [X] …" tooltip with the correct location
  clause for that block.
- Non-RoomBlock quests are unaffected.

## Notes

Find where quest card text/tooltip is generated (`UIQuest` / `MCQuest` / the
per-quest i18n keys) and add a RoomBlock-specific text branch. The block's item
name feeds the "[X]". Ties into `job-board-should-be-roomblock.md` (the board
becomes a craft-at-flag → place-a-block quest). Open flags to verify at
implementation: does placement need a radius check, and is "in your town" purely
cosmetic?

## Implementation (2026-10-13)

`QuestsScreen.renderCardTooltip` ROOM case now branches on the wanted ID before the
generic room line:
- `SpecialQuests.TOWN_GATE` → `menu.quests.roomblock.welcome_mat`: "Place the welcome mat at
  your town's entrance - it marks where new villagers arrive."
- `SpecialQuests.JOB_BOARD` or `RoomBlock.isBlockRoom(id)` (mineshaft, fishing station, any
  `block_room/*`) → `menu.quests.roomblock.town` with `RoomRecipes.getName(wantedId)`:
  "Place a %s in your town - this registers it with the town flag automatically."
- campfire keeps its existing branch; everything else keeps `menu.quests.room_quest`.

New lang keys added to en_us.json. Compile-verified; visual confirmation pending held run.

## Resolved (audit 2026-10-22)

`QuestsScreen.java:335` uses `menu.quests.roomblock.town` ("Place a %s in your town - this registers it with the town flag automatically.", en_us.json:478); the welcome-mat branch uses `menu.quests.roomblock.welcome_mat`.
