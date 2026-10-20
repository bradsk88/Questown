# core/network COMPLEX.md

## `FlagCraftRecipes.giveAndNotify` (was FlagCraftMessage) — capture display names before `add()`

The "Created X" chat line must name the item **before**
`player.getInventory().add(crafted)` runs. On success `add` shrinks the
passed stack to count 0, and vanilla `ItemStack.getItem()` on an empty
stack returns `Items.AIR` (`if (isEmpty()) return Items.AIR;` — verified
in the 1.20.6 bytecode, not just an NBT round-trip). Reading
`getHoverName()`/`getItem()` after the delivery therefore resolves the
name through vanilla Air: the 2026-10-11 "Created Air" report
(`.scratch/day1-playtest-2026-10-11/welcome-mat-craft-chat-says-created-air.md`).
The item itself always landed correctly; only the post-consumption name
was wrong. Any new craft/give path that reports a name must capture it
first.

Diagnostic corollary: a log line showing `item=minecraft:air` for a
stack that was just built from a mod holder does **not** mean the
registry/holder is broken — check whether the log runs after an
`Inventory.add`/`drop`/`shrink` on that stack.

## FlagCraftMessage: mineshaft recipe and WandGet (2026-10-19)

Index 3 crafts a flag-stamped mineshaft from stone stairs (dev's recipe), replacing the removed
item-on-flag stamping (`blocks/COMPLEX.md`). Crafting a wand now fires the `WandGet` advancement
(journal entry 003), which only the removed conversion fired before.

## Flag crafting recipes are one table (2026-10-19)

`FlagCraftRecipes.ALL` (input, icon, output, description key, optional advancement) is the single
definition both `FlagCraftMessage` (crafts `ALL.get(recipeIndex)`: consume one input, stamp the
output with the flag, give, fire the advancement) and `gui/FlagCraftingScreen` (one row, button and
tooltip per entry) read — dev: "No duplicate code. Common implementations." Entries: wand
(fires WandGet), welcome mat, job board, mineshaft (stone stairs) and fishing station (fishing rod,
dev's recipe 2026-10-19: the fishing station is a flag-stamped room block like the mineshaft).
Adding a recipe is one table entry plus its `menu.flag_crafting.*_desc` string.

## The craft lives on the recipe (2026-10-19)

`FlagCraftRecipes.craft(player, flagPos)` owns the craft (consume one input, stamp, give and
notify, advancement); `FlagCraftMessage.handle` only validates the index and calls it. The
village/growth driver calls the same method for a block-as-room quest (the mineshaft), so the
player path and the agent path cannot drift (AGENTS.md "A dev command is a thin wrapper").

## UnlockJobMessage names the villager, not its UUID (2026-10-20 playtest)

The unlock broadcast passed the raw villager `UUID` to `Compat.translatable`; 1.20.6
`TranslatableContents` accepts only Component/Number/Boolean/String and threw
(`IllegalArgumentException ... Was given <uuid> for messages.jobs.unlocked_by_mod`), so the
unlock happened but the message task died. It now passes `VillagerHolder.labelFor(uuid)` (display
name, or a shortened UUID), pinned by `VillagerHolderLabelTest` (the static `VillagerHolder.label` rule); the crash itself is
TranslatableContents-bound (the live stack trace was the red).
