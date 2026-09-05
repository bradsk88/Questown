---
title: Make the job board a room-block
status: ready-for-human
created: 2026-09-05
closed: 2026-09-05
priority: p2
---

# Make the job board a room-block

From `ideas.txt` (bradsk88): "Make the job board a room-block." Gloss from the
handoff that queued it: `JobBoardBlock` should behave like `MineshaftBlock` /
`FishingStationBlock` — placing it registers a "job board room" for villager
bookkeeping.

## Current state (verified in code)

Two acquisition paths, both end at `TownFlagBlockEntity.registerJobsBoard(pos)`,
which only feeds `TownWorkHandle.jobBoards` (a position list) used solely as a
boolean "town has a job board" gate (`TownWorkHandle:92`). No villager pathing,
quest matching, or UI consumes the board position today.

- Held-sign conversion: holding any sign near the flag converts it to the
  `job_board_block` item (`TownFlagBlock:247`); placing it with flag NBT calls
  `registerJobsBoard` (`JobBoardBlock.getStateForPlacement`).
- Retroactive swap: a sign inside a detected room matches room recipe
  `questown:job_board` (`data/questown/recipes/job_board.json`, one ingredient:
  tag `job_board_inputs` = signs + the job board block itself), and
  `TownWorldInteraction.swapJobBoardSign` replaces the sign with the block and
  calls `registerJobsBoard`.

So a job board in an enclosed room already matches the `questown:job_board`
recipe. What it lacks is what block-rooms get: a registered single-block room
record (`RoomsHolder.registerBlockAsRoom`), wand re-registration support
(`TownWand:272` handles any `RoomBlock`), and `RoomBlock`'s orphan handling.

## Options

A. **`JobBoardBlock extends RoomBlock`** (the letter of the idea).
   Placement auto-registers `block_room/block.questown.job_board_block` via
   `RoomBlock.getStateForPlacement`; wand support comes free. Cost: `RoomBlock`'s
   constructor hardcodes `TilesInit.BLOCK_AS_ROOM`, and `JobBoardBlock.Entity`
   uses `TilesInit.JOB_BOARD` with custom `runWhenOrphaned` (drops a sign, since
   the item is born from a sign). Needs a small refactor: let `RoomBlock` take
   the entity type/factory, or fold the sign-drop into `BlockAsRoomEntity` as an
   overridable hook. The swap path (`setBlockAndUpdate`) bypasses placement, so
   it must also register the room explicitly.

B. **Keep the class; register the room in `registerJobsBoard`.**
   `TownFlagBlockEntity.registerJobsBoard` also calls
   `roomsHandle.registerBlockAsRoom(<id>, pos)`. Both acquisition paths get the
   room record with a two-line change and no refactor, but the job board stays
   outside the `RoomBlock` family (no wand support, divergent orphan behaviour).

## Resolution

Option A, with the RoomBlock-convention id. Brad: the "what does the village
need it for" is answered in the docs (the `questown:job_board` room match
already drives the `first_job_board` advancement and the onboarding beat), and
the id follows the RoomBlock convention
(`questown:block_room/block.questown.job_board_block`).

What changed:

- `JobBoardBlock` extends `RoomBlock`; its bespoke block-entity class and the
  `TilesInit.JOB_BOARD` type are gone — it uses the shared `BlockAsRoomEntity`
  like the mineshaft and fishing station, and `BlocksInit` wraps it in
  `BlockAsRoomEntity.register`.
- Placement registers the block-room via `RoomBlock.getStateForPlacement`;
  the job board keeps its own `registerJobsBoard` call on top.
- The sign-swap path (`TownWorldInteraction.swapJobBoardSign`) bypasses
  placement (`setBlockAndUpdate`), so it registers the block-room explicitly.
- Orphan drop: the job board is born from a vanilla sign, so
  `RoomBlock.getOrphanDropItem()` (new overridable hook, defaults to the
  block's item) returns `Items.OAK_SIGN` for it.

Verified: `compileJava`/`compileTestJava` green; autotest
`chicken/F3_build_room_to_welcome_mat` reaches the same beat with and without
the change (its pre-existing WAITING_FOR_FREE_FOOD stall is tracked in
`.scratch/autotest-suite/f3-stalls-at-waiting-for-free-food.md`).

## Superseded questions

1. **What does the village need the room for?** No consumer of block-room
   records was found beyond quests/wand (jobs do not reference even the
   mineshaft room id). If a near-term consumer exists (work seekers gather at
   the board? onboarding checks?), name it; it decides the id and whether
   option A's uniformity is worth the refactor.
2. **Room id.** `questown:block_room/block.questown.job_board_block` (RoomBlock
   convention) vs reusing `questown:job_board` (merges block-placed boards with
   sign-room matches in any room-list consumer).
