---
title: '"Take a Block of Progress" decrements the flag count but the item never arrives (all BOPs lost)'
status: ready-for-agent
created: 2026-10-11
---

## Context

Playtest chat, 2026-10-11 22:30:27 + 22:30:52:

> I pressed "take a block of progress", but it didn't show up in my invemtoyr.
> Even though the count on the flag DID reduece.
> Tried again. Now all BOPs on flg are gona dna I have none.

Pressing "take a block of progress" on the flag decrements the flag's BOP count
but the BOP item is never given to the player. Repeating it burned through the
entire BOP balance with nothing received — **the BOPs are lost**.

## Acceptance criteria

- "Take a block of progress" gives the player a BOP item **and** decrements the
  flag count atomically (never decrement-without-give).
- No path where the count drops but the item is lost (full-inventory, NBT, or
  give-failure).

## Notes

High-value bug — it destroys player progress. Find the BOP-take handler (the
flag's BOP tab / `TownBlockofProgressMenu` action → server message → the flag
BE's `takeBop`/give path). The likely failure: the count is decremented but the
item give fails silently (inventory full? wrong item id? NBT? `add` returning
false with no drop fallback — the same class of bug the flag-craft "Created X"
full-inventory fallback addressed). Add a drop-at-feet fallback so a failed give
never loses the item, and a log line when a give fails.

## Code investigation (2026-10-11 evening, session 2)

The take path is `TownBlockofProgressScreen` "take" button →
`QuestownNetwork.sendToServer(new GiveBOPMessage(flagPos))` →
`GiveBOPMessage.handle` → `flag.ejectBlockOfProgress(sender)` →
`TownFlagBOPItemHandler.eject(flag, recipient)`:

```java
ItemStack v = flag.bopStacks.remove(0);      // decrement
...
if (recipient.getInventory().add(v)) {       // try inventory
    ...broadcastChanges(); return;
}
// fallback: drop at flag ± 1 random horizontal block
ItemEntity item = new ItemEntity(level, ...); level.addFreshEntity(item);
```

So there **is** a drop-at-feet fallback (added in the migration close-out
`782a0ede`) — no clean consume-without-give path in the current source. Two
leading causes for the observed loss:
1. **Inventory full → BOP dropped at the flag** (`bp.relative(getRandomHorizontal)`),
   one block off in a random horizontal direction, easy to miss behind/inside
   the flag geometry. The count drops (correct), the item is on the ground, not
   in the inventory. The player saw "count reduced, nothing in inv" and, repeating,
   "all BOPs gone, I have none" — the drops just weren't collected.
2. A client-side inventory-sync gap (the item was added but not shown).

To settle: catch a live `took_bop` (`messages.player.took_bop`) in the server log
while the player takes a BOP, and check whether the item landed in inventory or
as an `ItemEntity` at the flag. Improvement regardless: drop at the **player's**
position (or use `ItemEntity` at their feet) instead of the flag, so a full-inv
drop is visible; and add a log line distinguishing add-vs-drop.

## ROOT CAUSE (confirmed live, 2026-10-11 23:18:06)

Caught live in `/tmp/dev-server.log` (the user reproduced it in the from-zero
run). The `took_bop` broadcast **throws**, and because it sits **between the
remove and the give**, the BOP is removed from the flag and then lost:

```
[23:18:06] Broadcasting message: messages.player.took_bop [literal{Dev}, 1 questown:block_of_progress, [-193, 40]]
[23:18:06] [ERROR] java.lang.IllegalArgumentException:
  TranslatableContents' arguments must be either a Component, Number, Boolean, or a String.
  Was given 1 questown:block_of_progress for messages.player.took_bop
```

`TownFlagBOPItemHandler.eject()` runs, in order:
1. `ItemStack v = flag.bopStacks.remove(0);` — BOP removed from the flag (count already decremented).
2. `flag.messages.broadcastMessage("messages.player.took_bop", recipient.getName(), v, ...)` —
   passes the raw `ItemStack v` as a translatable argument.
3. `recipient.getInventory().add(v)` / drop fallback — **never reached**.

`broadcastMessage` → `Compat.translatable(key, args)` → `Component.translatable(key, args)`.
In 1.20.6 the `TranslatableContents` arguments must be a `Component`, `Number`,
`Boolean`, or `String` — a raw `ItemStack` is rejected and throws
`IllegalArgumentException`. (1.19.2 auto-converted item arguments to
hoverable-text components; 1.20.6 does not.) The exception aborts the
`GiveBOPMessage` network task at step 2, so the give (step 3) never runs → the
BOP is gone from the flag and never reaches the player or the ground. **This is
the loss** — not an inventory-full drop.

## Fix

Wrap the BOP in a `Component` before the broadcast: pass `v.getName()` (the item
display-name component) instead of the raw `ItemStack v` in step 2 (or
`ComponentUtils.getItemHoverEvent` if a hover tooltip is wanted). The give
already has the drop-at-feet fallback, so once the broadcast stops throwing the
BOP reaches the inventory. Verify: a live `took_bop` with no
`IllegalArgumentException` and the BOP appearing in the player's inventory.

**Note:** other `broadcastMessage(...)` call sites that pass an `ItemStack`
argument have the same latent bug — grep for `broadcastMessage` callers that
pass an `ItemStack` (not a `Component`) and wrap those too.
