---
title: 'Job board break drops a plain sign instead of the board item with NBT'
status: done
origin: 2026-10-15 playtest — user chat 03:12:02
---

# Job board orphan drop is a sign (stale conversion-era override)

User report: "Destroyed the job board. It should have dropped a job board
item with the correct NBT data. But it dropped a plain sign instead."

Root cause: `JobBoardBlock.getOrphanDropItem()` overrides the base to return
`Items.OAK_SIGN`, with a comment about a "sign -> job board conversion at the
flag". **That conversion no longer exists anywhere** (grep: the override was
the only OAK_SIGN reference in the block/flag code) — the board is now
placed directly from the `job_board_block` item. The orphan path
(`BlockAsRoomEntity.dropWhenOrphaned` → `TownFlagBlock.StoreParentOnNBT`)
already stamps the flag NBT onto whatever item is dropped, so the NBT half
was fine; only the item was wrong.

Fix (applied 2026-10-15, same session): deleted the override so the base
`RoomBlock.getOrphanDropItem()` (`asItem()`) drops the `job_board_block`
item, NBT-stamped. Stale javadoc in `RoomBlock` updated. Server-side Java —
needs the next server restart.

## Resolved (audit 2026-10-22)

No `getOrphanDropItem` override left outside `RoomBlock` (line 82, `asItem()`); `BlockAsRoomEntity.java:59` drops it, so a broken board drops the board item.
