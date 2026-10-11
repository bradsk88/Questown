---
title: The job board should be a RoomBlock, not a room you have to build
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 23:10:39 (evening from-zero run, `/tmp/dev-server.log`):

> It still feels odd that I have to build a room for the job board. I really
> think it should be a RoomBlock.

The job board (the work-request board the player posts requests to) currently
requires the player to build a whole room to get it. The player finds that odd —
they think it should be a **RoomBlock** (a single placed block, like the other
room blocks), not a multi-block room to construct.

## Acceptance criteria

- The job board is available as a single RoomBlock (place one block → job board
  is present), consistent with how the other room blocks work.
- No longer requires building a room to access the job board.

## Notes

Design discussion. "RoomBlock" vs "room": the other work sites (kitchen, smithy,
etc.) are rooms detected from built geometry; the job board is more of a fixed
town fixture. Investigate whether a single-block "RoomBlock" representation fits
the room/recipe detection model (RoomRecipes' `LevelRoomDetector` / room recipes)
or whether the job board needs its own block type that registers a room by
itself. Ties into `job-board-organic-introduction.md` (how the player discovers
it) — a RoomBlock would make first access much cheaper, which supports organic
discovery.

## Decision (2026-10-11, design walkthrough with the player)

**The board is crafted in the flag's crafting menu and placed as a block.**

- `JobBoardBlock` *already* extends `RoomBlock` (placing it registers a
  one-block room). The friction was **acquisition**: today the only path is
  build-a-walled-room-with-a-sign → room detection matches the `job_board`
  recipe (`#minecraft:signs`) → `swapJobBoardSign` converts it. So the room was a
  vessel to trigger a sign→board conversion, not the thing itself.
- **New telegraphed path:** add the job board to the flag's crafting menu
  (a `craftBoardButton` beside the existing `craftMatButton` / `craftWandButton`,
  same recipe + flag-NBT machinery). The player crafts it there, then **places
  the block** near the flag — it self-registers as a block-room via the normal
  `getStateForPlacement` path (no explicit registration needed, unlike the swap
  path).
- **Keep the sign→board-room path** as an optional alternative (players who want
  to "build a board room" can), but it is **not** the telegraphed/correct way.
- **Feeds the organic-intro ask** (`job-board-organic-introduction.md`): the
  onboarding beat becomes "craft the job board at the flag and place it" — a
  low-friction, natural first action.
- Verify during implementation: can the board be placed anywhere in-town (the
  flag-NBT-on-item should let it register to the right flag regardless of spot),
  and whether placement needs a radius check.
