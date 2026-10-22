---
status: done
origin: 2026-10-12 playtest — user chat 17:41:25 ("it outputs the 'Created' message twice for some reason")
---

# Messages print twice: flag-craft "Created X" chat and recipe_created broadcast

Two independent duplications observed in the same session:

1. **Flag-craft "Created X" chat line printed twice** for one Craft-button
   click (user: 17:41:25). `FlagCraftMessage.giveAndNotify` sends the chat
   exactly once per message — so suspect the button click firing
   `FlagCraftMessage` twice (client-side: button listener registered twice,
   or the screen re-sending on a held/double-click).
2. **`recipe_created` broadcast logged twice per placement** — the server log
   shows the identical `Broadcasting message: messages.building.recipe_created`
   line back-to-back at 17:39:53 and 17:41:03 (board placement). The generic
   `registerBlockAsRoom` path and/or the block-room registration both fire
   `roomRecipeCreated` for the same event.

The E2 broadcast-debounce queue (5s spacing, dedupe consecutive identical
lines) will mask symptom 2 for players; both still need a real root cause so
the underlying double-firing doesn't also double-apply quest completion or
room registration (verify idempotency, not just the chat).

## Shipped (2026-10-13)

- **Recipe_created double broadcast (root cause):** the legacy double
  `roomRecipeCreated` call in `registerBlockAsRoom` (present in the 1.19.2
  golden) both broadcast. Now the meta-room call is broadcast-free
  (`broadcast` flag on `roomRecipeCreated`); `broadcastBlockRoomCreated`
  remains the single "Created X" line. Scan-driven room notifications are
  unchanged. Idempotent side effects (swapBlocks, quest completion,
  advancements) keep running on both calls as before.
- **Flag-craft chat doubling (guard):** server path verified clean (one
  `sendSystemMessage` per `FlagCraftMessage`, single registration); the
  doubling is a fast double-click racing the onClose. The craft button now
  deactivates itself before sending (`btn.active = false` — 1.20.6 has no
  `setActive`).
- In-game confirmation of both pending. Docs: `town/entity/COMPLEX.md`.

## Resolved (audit 2026-10-22)

Both halves in code: `TownFlagBlockEntity.roomRecipeCreated(room, match, broadcast)` (line 1096) keeps the meta-room call silent; `FlagCraftingScreen.java:148` deactivates the Craft button before sending. Since a73a1401, `BroadcastPacer` also drops an identical line just sent.
