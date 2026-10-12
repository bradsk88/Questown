---
status: ready-for-human
origin: 2026-10-12 playtest — user chat 17:39:17 ("The job board quest shows a vanilla sign as the 'needed block' it should show the job board")
---

# Job-board quest card shows a sign, not the job board block

The day-1 "Job Board" quest card (basics batch) renders the **vanilla sign**
as its "needed block" icon — not the job board block the player actually
crafts and places.

## Root-cause analysis (in code, not yet changed)

The card icon resolves the quest's `wantedRecipe` (`questown:job_board`) in
the recipe registry. That name belongs to the **blueprint room recipe**
(`data/questown/recipes/job_board.json`, `roomrecipes:room`, ingredient tag
`questown:job_board_inputs` — a room of 2 signs + 1 log, the old sign-room
path). So the card shows the blueprint's ingredient (a sign).

The board is now crafted as a single block at the flag and placement is the
canonical quest completion (see `job-board-should-be-roomblock.md`), so the
card's recipe should be the board block itself:
`JobBoardBlock.asRecipe()` (a `RoomRecipes.standard` single-ingredient recipe
of the board item) — or point the quest's `wantedRecipe` at a
`block_room/block.questown.job_board_block`-style recipe registered at
placement time, whichever the icon pipeline actually consults (verify the
`UIQuest`/`QuestsScreen` recipe lookup before choosing).

The store-room quest in the same batch (chest room) is the correct shape to
diff against: its card shows the room's real block.

## Notes

- Companion to `roomblock-quests-custom-place-tooltip.md` (placement
  tooltip) and the flavor-text fix that now points at the crafting tab.

## Implementation (2026-10-13)

`UIQuest.fromLevel` now overrides the `SpecialQuests.JOB_BOARD` recipe in the rMap with
`RoomRecipes.standard(JOB_BOARD, NonNullList.withSize(1, Ingredient.of(JOB_BOARD_BLOCK)))`,
so the card icon is the board item, not the blueprint's sign ingredient. The map builder was
switched from `build()` to `buildKeepingLast()` to tolerate the deliberate override.
Compile-verified; visual confirmation pending held run.
