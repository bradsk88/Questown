---
title: '"Take a Block of Progress" decrements the flag count but the item never arrives (all BOPs lost)'
status: needs-info
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
