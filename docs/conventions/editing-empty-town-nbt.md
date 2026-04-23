---
title: Editing empty_town.nbt
type: convention
status: active
date: 2026-04-23
---

# Editing `empty_town.nbt`

`src/main/resources/data/questown/structures/empty_town.nbt` is the jigsaw template spawned by worldgen around every new town flag. Editing it requires a Structure Block round-trip — there is no build-time tooling in the repo.

## Round-trip workflow

1. Run a dev client (`./gradlew runClient`).
2. Open a creative flat world. Give yourself a structure block: `/give @s minecraft:structure_block`.
3. Place the structure block. Set it to **Load** mode and load `questown:empty_town` with the default position offset.
4. Let it place in the world. Build or delete whatever you need to change.
5. Switch the same structure block to **Save** mode. Name it `questown:empty_town`. Set the size to match your edits (the structure block's selection box must enclose every block you want captured).
6. Save. The exported file lands in the world's `saves/<world-name>/generated/minecraft/structures/questown/empty_town.nbt`.
7. Copy that file into `src/main/resources/data/questown/structures/empty_town.nbt`, overwriting the existing one.
8. Rebuild the mod. Verify by `/locate structure questown:empty_town` in a fresh world.

## Offsets live in code, not the structure

`HelperChickenBeatOffsets` mirrors the authored layout as structure-local `BlockPos` constants. Keep the two in sync:

- Edits to `empty_town.nbt` that move a beat target (unlit campfire, doorway gap, sign spot, etc.) require matching edits to the offset constants in the same commit.
- Offsets are authored in the structure-local frame (rotation `NONE`). The rotation detector at runtime applies `BlockPos.rotate(Rotation)` using the flag BE's persisted rotation.

## Rotation-detection anchor

The unlit campfire placed by the structure doubles as the anchor for `HelperChickenRotationDetector`. It must live at a structure-local offset that is unique under all four rotations. A position like `(3, 0, 5)` works; `(3, 0, 3)` does not (symmetric under 180° rotation).

## Format notes

- 1.19.2 Structure Block exports uncompressed `.nbt`. The file in the repo may be gzipped from an earlier era — Minecraft reads either format at load.
- Binary diffing won't show meaningful hunks. Track authored changes in the git commit message.
- Do not hand-edit the binary with NBTExplorer unless you enjoy silent breakage. The Structure Block round-trip is the only supported path.
