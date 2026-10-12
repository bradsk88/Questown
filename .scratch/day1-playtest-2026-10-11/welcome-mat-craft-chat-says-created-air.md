---
title: Crafting a welcome mat posts "Created Air" to chat
closed: 2026-10-13
created: 2026-10-11
---

## Resolved

Shipped in the fix commit of 2026-10-13: `giveAndNotify` captures
`crafted.getHoverName()` before `player.getInventory().add(crafted)`
consumes the stack. Live-verified by the user (chat reads
"Created Welcome Mat"). No unit test: the mechanism is vanilla
`ItemStack` empty-stack fallback + `ServerPlayer` inventory wiring, and
the fix is a one-line ordering change pinned by the live observation
(see ROOT CAUSE section below).

## ROOT CAUSE (confirmed 2026-10-13 18:50, live probe round 3)

**The item was always delivered correctly. "Created Air" is a display-name
timing bug in `FlagCraftMessage.giveAndNotify`.**

`giveAndNotify` read the message's display name from the stack *after*
`player.getInventory().add(crafted)` had consumed it:

1. `add(crafted)` succeeds (the mat lands in the inventory - the user's
   observed fact, 10-11: "I DID get a welcome mat in my hand") and, on
   success, shrinks the passed stack to count 0.
2. An empty stack's `ItemStack.getItem()` is vanilla fallback behavior:
   `if (isEmpty()) return Items.AIR;` (verified in the 1.20.6 bytecode -
   `getItem()`, not just NBT round-trips, does the lookup).
3. `crafted.getHoverName()` on the consumed stack therefore resolves
   through `Items.AIR` -> "Air" -> the chat line reads "Created Air".

All three recipes (mat, wand, board) went through this path, so any
successful craft with room in the inventory could say "Created X -> Air".

**The two earlier wrong verdicts are both explained by the same trap:**
- 2026-10-13 "not reproducible": the `CreatedAirProbe` read the hover name
  of a fresh, unconsumed stack -> "Welcome Mat".
- 2026-10-12/13 `[craft-give]` probe printed `item=minecraft:air`:
  it logged *after* the `add()` consumption -> the empty-stack fallback.
  Every probe that read the item post-consumption showed AIR; the
  registry/holder chain was healthy at every observation point (boot,
  pre-craft, post-craft: key `questown:welcome_mat_block`, real
  `BlockItem`, holder identity stable all session).

**Fix (`FlagCraftMessage.giveAndNotify`):** capture
`Component itemName = crafted.getHoverName()` *before* the `add()`; send
the message with the captured name. One line; covers all three craft
recipes. All DEV-DIAG probes removed (`MatDiagServerEvents` deleted).

**Verify (live, one click):** craft the mat from the flag menu on the
rebuilt server; chat must read "Created Welcome Mat".

**Residue (cosmetic, separate):** the world carries 4-5 corrupt
`ItemEntity`s from earlier broken builds (`Tried to load invalid item:
'No key id in MapLike[{}]` on every join). Harmless; delete the dropped
items in-game or leave them.

## Context

Playtest chat, 2026-10-11 17:13:09 (`run/client2.log`):

> Crafting a welcome mat causes a message to be added to chat that says
> "Created Air" - but I DID get a welcome mat in my hand.

## Verdict 2026-10-13: not reproducible on the current source

Live-server probe (DEV-DIAG, `CreatedAirProbe` on `ServerStartedEvent`,
`run/server13.log` 18:13:15) replicated the exact craft path
(`new ItemStack(WELCOME_MAT_BLOCK.get())` ± `StoreParentOnNBT`, I18n
loaded) and resolved:

```
[created_air_probe] bare hover='Welcome Mat'
[created_air_probe] stamped hover='Welcome Mat'
[created_air_probe] outer message bare='Created Welcome Mat'
[created_air_probe] outer message stamped='Created Welcome Mat'
```

Static chain confirms: item `questown:welcome_mat_block` (BlockItem) →
`block.questown.welcome_mat_block` → lang "Welcome Mat" (the key has held
that value through the lang file's whole git history). "Air" is only the
vanilla name of the empty item, and the mat item provably was in the
inventory (the placement NBT errors at 17:13:19/23 name it).

The playtest server booted 16:42:50, i.e. pre-`8f670d10`; the craft path
(`FlagCraftMessage.giveAndNotify`, unchanged since `ef9bb156`) is
byte-identical, so a stale craft code is unlikely — but the client-side
resolution at that moment is not verifiable after the fact.

## Report 2026-10-13 (recurred on a fresh build)

Playtest chat, 2026-10-13 02:41:00 (`/tmp/dev-server.log`, fresh-world
repro run for the econ-tab axe bug):

> "created air when I got a welcome mat again. We had that bug before. Thought we ffixed it?"

So the bug **recurred on the current source** on a freshly-built fresh
world — contradicting the 2026-10-13 "not reproducible" verdict above. The
`CreatedAirProbe` was removed from the branch after that verdict, so the
resolved message was not captured this time. Reopened to needs-triage.

**Next (deferred until the econ-bug run finishes, to avoid losing the
world):** re-add the `CreatedAirProbe` DEV-DIAG, rebuild, reproduce the
welcome-mat craft, and capture the client log around the craft to see
whether the resolved message is "Created Air" again.

## Ask (if it resurfaces)

Reproduce on a fresh `runServer` + `runClient` build and send the craft;
if it still says "Created Air", re-add the `CreatedAirProbe` DEV-DIAG
(server-start log of the resolved message) and capture the client log
around the craft.

## Related

`menu-crafted-welcome-mat-missing-nbt.md` (same craft, real bug, fixed).

## Triage notes

Same root as menu-crafted-welcome-mat-missing-nbt. Live probe on current source resolves to 'Created Welcome Mat' (not reproducible as 'Created Air'); the NBT stamp (530c8923) makes the craft carry the registration NBT. Kept as the investigation record.

## 2026-10-12 probe run (still open — probe did NOT fire)

User reported it again at 17:38 ("Still says 'Created Air' for welcome mat from
flag crafting menu"). The `CreatedAirProbe` **produced zero log lines this run**,
and the flag-menu craft handler's `Welcome mat crafted` log line never appeared —
so either the user did not click the mat button (the mat item came from another
surface) or the handler ran without logging (error swallowed).

**Question for the user (observed facts, per AGENTS):** exactly where did
"Created Air" appear — the server log, or chat in-game? And when: at the moment
the mat appeared in the inventory, or when the quest card completed? Which
crafting surface produced the mat item — the flag's crafting tab button, or a
vanilla crafting grid / the sign room recipe?

## 2026-10-12 user confirmation + probe armed

User confirmed: "The Created Air message is output when the player presses
'Craft' in the flag's crafting menu." So `FlagCraftMessage.craftWelcomeMat`
runs, the mat item lands (gate quest completed 17:38:33), and
`giveAndNotify`'s `message.flag_crafting.created` resolves `%s` to "Air".
Static resolution chain (BlockItem.getDescriptionId -> block id -> lang
"Welcome Mat") says otherwise; a `[craft-give]` log in `giveAndNotify`
now dumps item/descId/hoverName — next craft after the rebuilt server
identifies the broken link.

Note: the mat's craft input is 1 wooden pressure plate (the user earlier saw
"0" on that row - flag-crafting-0-in-inventory-pressure-plate.md); the board
craft input is 1 sign.
