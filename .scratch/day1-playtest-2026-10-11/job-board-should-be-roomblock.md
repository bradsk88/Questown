---
title: The job board should be a RoomBlock, not a room you have to build
status: ready-for-human
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
