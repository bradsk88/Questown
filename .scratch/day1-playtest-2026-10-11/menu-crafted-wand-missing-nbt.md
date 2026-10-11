---
title: Menu-crafted town wand is missing the NBT it needs to work
status: resolved
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 21:59:22 + 22:03:09:

> I can't seem to sleep at the campfire...
> Ah, it's because the wand was constructed via the flag's crafting menu. Its
> missing the NBT. Same bug as before with the welcome mat.

A `TownWand` crafted through the flag's crafting menu comes out **without the
parent NBT** (`TownFlagBlock.StoreParentOnNBT`), so it doesn't know which flag
it belongs to and its actions (place welcome mat, register door, sleep at the
campfire, ...) don't work. This is the same class of bug as
`menu-crafted-welcome-mat-missing-nbt.md` (the welcome-mat variant is resolved)
— the flag-craft path was dropping the item's required NBT.

## Acceptance criteria

- A `TownWand` crafted via the flag's crafting menu carries the correct parent
  NBT, so it works out of the hand (place mat / register door / sleep).
- Ideally the flag-craft path stamps the required NBT on **every** item it
  crafts that needs it (not a per-item whitelist), so the next NBT-bearing item
  doesn't re-hit this.

## Notes

The welcome-mat fix is the reference for the shape of the NBT and where the
craft path should apply it. Grep for how the welcome mat got its NBT back and
generalise it. Verify by crafting a wand in the flag menu and using it (or a
unit test that asserts the crafted ItemStack's NBT matches the expected
parent-POD).

## Fix (2026-10-11 evening)

`FlagCraftMessage.craftWand` now stamps `TownFlagBlock.StoreParentOnNBT(wand,
flagPos)` on the crafted wand (same parent-stamping the welcome mat already
carried), so a menu-crafted wand resolves its parent flag and its campfire-sleep
/ door-register / mat actions work out of the hand. Build-verified (no dedicated
unit test yet); needs a live client to confirm sleep-at-campfire.
