---
title: "Mirror item-use side-effects when placing blocks via setBlockAndUpdate in test harness code"
date: 2026-04-24
category: docs/solutions/conventions/
module: autotest harness / ChickenArcTestExecutor
problem_type: convention
component: testing_framework
severity: high
applies_when:
  - "Test harness code places blocks via Level.setBlockAndUpdate(BlockPos, BlockState)"
  - "The block type has registration or state-init logic in BlockItem.place(), Block.setPlacedBy(), or an item-use handler"
tags: [setblockandupdate, test-harness, block-placement, side-effects, doors, welcome-mat, job-board]
---

# Mirror item-use side-effects when placing blocks via setBlockAndUpdate in test harness code

## Context

The chicken-arc autotest harness (`ChickenArcTestExecutor`) builds rooms and town structures programmatically using `Level.setBlockAndUpdate(BlockPos, BlockState)`. This API writes the block state directly to the world chunk and schedules a lighting/neighbor update — it does not invoke `BlockItem.place()`, `Block.setPlacedBy()`, or any item-use handler. Three block types in the registered-room setup path each carry side-effects that live exclusively in those item-use paths:

- **WelcomeMatBlock** calls `flag.registerWelcomeMat(pos)` in its item-use / placement path. Without it, `flag.getWelcomeMats()` stays empty and the F3 welcome-mat beat never advances.
- **Oak sign → job-board conversion** — placing an oak sign near a flag normally fires a sign-conversion item-use handler that swaps `OAK_SIGN` for `BlocksInit.JOB_BOARD_BLOCK`. A raw `setBlockAndUpdate(pos, OAK_SIGN.defaultBlockState())` skips the swap entirely and the job board never appears.
- **DoorBlock** — the item-use path places two halves (lower + upper, via `DoubleBlockHalf`). `setBlockAndUpdate` with a door's `defaultBlockState()` writes only a lower-half block; the room-recipe detector rejects it as a half-door and the room is never registered.

These bugs are silent: no exception is thrown, but downstream assertions fail because POI maps, room recipes, and block-entity lookups all operate on state that was never initialized.

## Guidance

When placing a block in test harness code via `setBlockAndUpdate`, check whether that block type carries item-use side-effects. Apply the appropriate fix:

1. **Blocks with registration side-effects** (e.g. `WelcomeMatBlock`): call `setBlockAndUpdate` then immediately invoke the registration API manually.
2. **Blocks produced by item-use conversion** (e.g. oak sign → job board): skip the intermediate block and place the final target block directly.
3. **Multi-half blocks** (e.g. `DoorBlock`): place both halves explicitly with `setBlockAndUpdate`, setting `DoorBlock.HALF` to `LOWER` and `UPPER` respectively.

All three fixes are consolidated in `handlePlaceBlock` in `ChickenArcTestExecutor`.

## Why This Matters

`setBlockAndUpdate` is the natural API for programmatic block placement in server-side code, and it silently bypasses an entire category of initialization. Because the failure mode is "state not registered" rather than an exception, the gap can be invisible until an unrelated assertion (e.g. `flag.getWelcomeMats().isEmpty()`) fails with a non-obvious root cause. Codifying this as a convention ensures contributors know to audit item-use handlers whenever they add a new block type to the test harness.

## When to Apply

- Any time test harness code calls `Level.setBlockAndUpdate` for a block that is normally placed by a player via an item.
- When adding a new block type to `handlePlaceBlock`, `handleSetUpRegisteredRoomWithChest`, or any equivalent harness builder method.
- When a harness-placed block is correctly visible in the world but a downstream POI lookup, room registration, or entity scan returns empty unexpectedly.

## Examples

### DoorBlock — place both halves explicitly

**Before (broken — only lower half written, room detector rejects the door):**
```java
level.setBlockAndUpdate(pos, Blocks.OAK_DOOR.defaultBlockState());
```

**After (correct — both halves present):**
```java
// handlePlaceBlock — ChickenArcTestExecutor.java
if (a.blockState().getBlock() instanceof DoorBlock) {
    level.setBlockAndUpdate(pos,
            a.blockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
    level.setBlockAndUpdate(pos.above(),
            a.blockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    return;
}
```

### WelcomeMatBlock — mirror the registration side-effect manually

**Before (broken — flag.getWelcomeMats() stays empty):**
```java
level.setBlockAndUpdate(pos, BlocksInit.WELCOME_MAT_BLOCK.get().defaultBlockState());
```

**After (correct — registration mirrored immediately after placement):**
```java
// handlePlaceBlock — ChickenArcTestExecutor.java
level.setBlockAndUpdate(pos, a.blockState());
if (a.blockState().is(BlocksInit.WELCOME_MAT_BLOCK.get()) && flag != null) {
    flag.registerWelcomeMat(pos);
}
```

### Oak sign → job-board conversion — place the final block directly

**Before (broken — oak sign placed, item-use conversion handler never fires):**
```java
level.setBlockAndUpdate(signPos, Blocks.OAK_SIGN.defaultBlockState());
```

**After (correct — target block placed directly, no conversion path needed):**
```java
// placeRegisteredRoomFurnishings — ChickenArcTestExecutor.java
handlePlaceBlock(new ChickenArcScriptedAction.PlaceBlock(
        signOffset, BlocksInit.JOB_BOARD_BLOCK.get().defaultBlockState(), 0));
```

## Related

- `src/main/java/ca/bradj/questown/commands/test/ChickenArcTestExecutor.java` — `handlePlaceBlock`, `placeRegisteredRoomFurnishings`, `registerRoomFixtures`
- Verified green: `[autotest] RESULT: 41/41 passed (ALL PASS)` in commit `9120ac06`
- Discovery commits: `fcbebf0e`, `85eb26da`, `fa231523`
- Parent plan: `docs/plans/2026-04-23-001-feat-chicken-arc-agent-automation-plan.md`
