---
title: Crafting a welcome mat posts "Created Air" to chat
status: resolved
created: 2026-10-11
---

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

## Ask (if it resurfaces)

Reproduce on a fresh `runServer` + `runClient` build and send the craft;
if it still says "Created Air", re-add the `CreatedAirProbe` DEV-DIAG
(server-start log of the resolved message) and capture the client log
around the craft.

## Related

`menu-crafted-welcome-mat-missing-nbt.md` (same craft, real bug, fixed).

## Triage notes

Same root as menu-crafted-welcome-mat-missing-nbt. Live probe on current source resolves to 'Created Welcome Mat' (not reproducible as 'Created Air'); the NBT stamp (530c8923) makes the craft carry the registration NBT. Kept as the investigation record.
