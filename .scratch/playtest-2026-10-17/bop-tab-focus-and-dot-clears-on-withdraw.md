---
title: 'BOP tab: initial focus over quests when unseen; red dot clears after the first withdrawal'
status: ready-for-human
origin: 2026-10-17 playtest round 5 — user chat 17:58:52 (dev-server-8, build 3ed88de2)
---

"If both the "quests" and "BOPs" tab have a red dot (they have not yet been seen), the "bops"
tab should get the initial focus. Also, the BOPs red dot should clear once any player has
withrdrawn a bop"

## Fix

- `FlagTabs.initialFlagTab(bopFull, bopTabUnseen, questsTabAttention)` (pure): full flag or an
  unseen BOP tab → BOP; else incomplete quests → QUESTS; else the overview. `TownFlagBlock.use`
  calls it. Before, incomplete quests outranked the unseen BOP tab.
  "Unseen" = the BOP dot is showing AND the player lacks `first_bop_view` (the old pre-select).
- `TownFlagBlockEntity.bopWithdrawn` (saved as `questown_bop_withdrawn`), set by
  `TownFlagBOPItemHandler.eject` (the player's take). `FlagInfo.bopTabAttention = !bopWithdrawn`
  replaces the hardcoded `true` dot.
- Pinned by `BopTabAttentionTest` (7).

## Verify (needs a server restart to load)

With a BOP in the flag and incomplete quests, right-click the flag: opens on the BOP tab. Take a
BOP out: the BOP tab's dot is gone, also after quit + rejoin.
