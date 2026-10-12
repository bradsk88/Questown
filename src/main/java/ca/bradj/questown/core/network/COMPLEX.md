# core/network COMPLEX.md

## `FlagCraftMessage.giveAndNotify` — capture display names before `add()`

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
