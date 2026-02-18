# FishingStationBlock.getAttachPoint crash during warp

**Status:** FIXED

## Symptom

`/_qtdev testall` fisher test fails with "Warp returned null state". The warp engine
catches and swallows the exception, returning null.

## Root Cause

`FishingStationBlock.getAttachPoint()` unconditionally called
`bs.getValue(FACING)` on whatever block was at the work position. During the
testall sequence, the block at the fishing station position could be something
else (e.g. deepslate from world generation) because testall reuses the same
area. The non-fishing block lacks the `FACING` property, causing
`IllegalArgumentException`.

## Fix

Added `if (!bs.hasProperty(FACING)) return null;` guard in `getAttachPoint()`,
matching the existing pattern in `getRandomHookPos()` which already checked
`instanceof FishingStationBlock`. The caller in `DeployFishingHookRule.deployHere()`
already handles a null return from `getAttachPoint()`.

## Files Changed

- `src/main/java/ca/bradj/questown/blocks/FishingStationBlock.java`