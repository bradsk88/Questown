# Block-rooms: a single block that registers as a room

A `RoomBlock` is a town sub-block whose *presence* is the room: placing it next
to a flag registers a one-block "block-room" (id
`questown:block_room/block.<description-id>`) with the flag's rooms handle, the
same record a walled room gets. Mineshaft, fishing station, and the job board
are block-rooms.

## Registration paths

```mermaid
flowchart TD
    Place["player places RoomBlock\n(holding item with flag NBT)"] --> GSP["RoomBlock.getStateForPlacement"]
    GSP --> Reg["roomsHandle.registerBlockAsRoom(roomId, pos)"]
    Wand["TownWand on a RoomBlock"] --> Reg
    Reg --> Match["roomRecipeCreated(metaRoom, [roomId])\n(quests, advancements, messages)"]

    Swap["sign inside detected room matches\nquestown:job_board recipe"] --> TWS["TownWorldInteraction.swapJobBoardSign"]
    TWS --> SBU["setBlockAndUpdate(job_board_block)\nBYPASSES getStateForPlacement"]
    SBU --> Reg2["registerBlockAsRoom(job-board roomId, pos)\n(explicit — placement hook never ran)"]
```

The swap path is the trap: `setBlockAndUpdate` never calls
`getStateForPlacement`, so any block-room a swap installs must be registered
explicitly at the swap site (see
`docs/solutions/conventions/setblockandupdate-side-effect-bypass-2026-04-24.md`).

## Shared entity and the orphan drop

All block-rooms share `BlockAsRoomEntity` (`TilesInit.BLOCK_AS_ROOM`); a block
joins the family via `BlockAsRoomEntity.register(...)` in `BlocksInit`, not by
declaring its own block-entity type.

When the flag is removed, orphaned sub-blocks drop themselves with the flag
position stored on the item's NBT so they can re-attach. The dropped item comes
from `RoomBlock.getOrphanDropItem()`, which defaults to the block's own item.
The job board overrides it to drop an oak sign, because the board item is born
from a sign (holding a sign near the flag converts it), so orphaning returns
the player's original investment.

## Job board facing: blockstates re-bases the model's X-axis front

The board model (`models/block/job_board_block.json`) authors the slab
**thin along X** (the two readable faces are the ±X faces; the slab leans via
a z-axis rotation), while the FACING rendering convention is Z-fronted (the
stock `south:0, west:90, north:180, east:270` variant table). Net effect:
whatever the `FACING` state says, the readable face renders **90° off** —
the 2026-10-11 and 2026-10-15 playtest reports ("rotated 90 west", "rotated
90 left"; both consistent with a constant 90° offset). The state logic is
canonical and was right all along: `JobBoardBlock.getStateForPlacement` sets
`FACING = ctx.getHorizontalDirection().getOpposite()` (face the placer — the
sign/bed convention; computed twice, both sites). The fix (2026-10-15) re-bases
the blockstates variants instead of touching the state code or re-authoring the
model: a client-side resource change.

**The rotation sign was verified against the vanilla oracle, not derived:**
villa 1.20.6 `blockstates/furnace.json` + the `block/orientable` template
(front face on the model's **−Z**) reads `north:0, east:90, south:180,
west:270` — so a `y:90` rotation carries the north face to the **east**
(clockwise viewed from above), the opposite of the stock-math sign. With the
board's readable face on **−X**, that gives `west:0, north:90, east:180,
south:270`. The first re-base attempt used the wrong sign (`south:90,
north:270`); the 2026-10-15 03:13 playtest report confirmed it: "Facing west,
placement was correct. But when I was facing south it pointed the wrong way"
— exactly one sign flip, south/north transposed. Swapped; all four facings
now satisfy `readable face == FACING == the placer's side`.

Check before assuming: other asymmetric FACING block-rooms (bread oven, food
display, fishing station, axe rack) may share the model-axis mismatch —
verify each model's authored front axis against its blockstates table.

# The bread oven: its own BE type (the shared-entity exception)

`BreadOvenBlock` is NOT a `RoomBlock` and does NOT join the
`BlockAsRoomEntity` family — it is a processing block with its own block-entity
type (`TilesInit.BREAD_OVEN`, `BreadOvenBlockEntity`):

- **Slots** (0 wheat, 1 coal, 2 bread) live in the BE's NBT-persisted
  `Container` storage; `resolveContainer` in `MinecraftWorldAccess` treats any
  `BlockEntity implements Container` as a job container, so the cook-shaped
  job slot rules (`insert_into_slot_0/1`, `questown:take_from_slot_2`) drive
  it.
- **Bake arithmetic** (`BreadOvenBake`: 1 wheat + 1 coal -> 1 bread over
  `BAKE_TICKS=1000`, partial-progress reset on any slot change) is shared by
  the realtime BE (`advanceBakeOneTick`/`advanceBake`) and the warp lane
  (`WarpWorldAccess.advanceBreadOven` → same `BreadOvenBake` instance bound to
  the in-memory slot snapshot). Keep it that way — a second copy of the
  arithmetic in either lane is the parity footgun.
- **`bake_state` visual**: content-derived. `BreadOvenBlockEntity.syncVisualState()`
  keeps the block property in sync with the slots (0 empty / 1 wheat only /
  2 filled / 3 baking / 4 bread ready); the townie's work-state index no
  longer drives it (the old `StatefulJobBlock.setProcessingState` path was
  removed in the 2026-10-10 cook-shape migration, so
  `TownWorkStatusStore`'s `instanceof StatefulJobBlock` probe skips the oven).
- **Destruction**: `BreadOvenBlock.onRemove` drops slot contents via
  `Containers.dropItemStack`; `getDrops` returns the block item only.
- **Unit-testability trap**: `BreadOvenBlockEntity` cannot be constructed in a
  plain unit test — its constructor resolves `TilesInit.BREAD_OVEN`, whose
  supplier constructs the mod's `BreadOvenBlock` (`Block.<init>` writes
  `BuiltInRegistries`, frozen after `Bootstrap.bootStrap()`). The bake
  arithmetic is therefore tested registry-free through `BreadOvenBakeTest`;
  the warp-lane branch stays pinned by the `assertThrows` marker in
  `WarpWorldAccessTest`.
