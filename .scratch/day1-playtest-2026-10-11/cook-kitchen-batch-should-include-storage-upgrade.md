---
title: Add a storage-room upgrade to the cook/kitchen quest batch
status: resolved
created: 2026-10-12
---

## Context

Playtest chat, 2026-10-12 19:08:37 (`/tmp/dev-server-7.log`):

> We've reached the "cook" and "kitchen" quest batch. I think we should
> probably also add a storage room upgrade to this batch because this is
> about the point that storage becomes an issue.

The world at that moment already had a `store_room` at [90,-71]
(`messages.building.room_size_changed` 19:09:18) — storage pressure starts
right around the kitchen stage.

## Acceptance criteria

- The cook/kitchen quest batch includes a storage-room (upgrade) quest so the
  player builds/extends storage in the same beat the kitchen arrives.

## Notes

Design item — decide whether this is a NEW store room or an UPGRADE of the
existing one, and which recipe (store_room → store_room_medium? the log shows
both recipes exist).

## Resolved (2026-10-18)

Decided (dev, 2026-10-18 playtest): an UPGRADE, Small Storeroom (`store_room`) -> Storeroom (`store_room_medium`), in the cook batch (`addQuestsForJobChangeAndFood`). The old storage-upgrade phase 6 is removed (dev: include it in phase 2.5, "thus removing phase 6"). See `town/entity/COMPLEX.md` "Cook batch carries the storage upgrade; phase 6 removed".
