---
title: 'Empty-room quest card: oak door icon + shorter tooltip'
status: done
---

# Empty-room quest card: oak door icon + shorter tooltip

Captured 2026-10-13 (playtest, in-game chat):

> "The icon for the 'empty room' quest should be an oak door. Its tooltip
> should be about 50% as long with no em dashes."

## What it was

- **Icon**: `empty_room.json` has zero ingredients (it matches any room), so
  `QuestsScreen.renderRecipeCardIcons` drew an empty strip — no icon at all.
- **Tooltip**: the card shows the quest's flavor text
  (`TownQuests.addEmptyRoomBatch`): "Build a small room - walls and a door.
  Then use your Town Wand on the door to register the room with the town.
  Villagers need registered rooms to work in." — 143 chars, one dash, half of
  it background.

## Fix (implemented this turn)

- `QuestsScreen.renderRecipeCardIcons`: when the strip is empty and the
  wanted id is `SpecialQuests.EMPTY_ROOM`, render a single
  `Ingredient.of(Items.OAK_DOOR)` — the block you actually point the wand at.
- Flavor text halved, dash-free: "Build a small room, then use your Town Wand
  on its door to register it." (70 chars).

Note: the flavor text is set at quest-post time, so the live world's already
posted (and completed) quest keeps the old copy; fresh worlds get the new.

## Resolved (audit 2026-10-22)

Icon: `QuestsScreen.java:540` renders `Ingredient.of(Items.OAK_DOOR)` for the empty-room card. Tooltip: the flavor in `TownQuests.addEmptyRoomBatch` is now "Build a room: four walls and a door. Use your town wand to register the door." (78 chars, no em dash; lang key `menu.quests.roomblock.empty_room` matches).
