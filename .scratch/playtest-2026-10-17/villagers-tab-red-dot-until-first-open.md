---
title: 'Flag villagers tab: red dot from the first arrival until the tab is first opened'
status: ready-for-human
origin: 2026-10-17 playtest round 5 — user chat 17:54:36 (dev-server-8, build 3ed88de2)
---

"After unlocking the first villager, and before opening the villagers tab for the firt time,
the villagers tab on the tag menu shou dhave a red dot."

## Fix

- `TownFlagBlockEntity.villagersTabSeen` (saved as `questown_villagers_tab_seen`; a newly
  placed flag starts false, a town saved before the field existed loads true = no dot).
- `FlagInfo.villagersTabAttention` (trailing wire field) = villagers tab shown && not seen;
  `FlagTabs` uses it as the villagers tab's notification.
- `TownFlagMenus.showUI` marks the tab seen when the VILLAGERS menu opens, before building the
  info, so the open screen already has no dot.
- Pinned by `VillagersTabAttentionTest` (5).

## Verify (needs a server restart to load)

Fresh world: no villagers tab before the first arrival; after it, the tab shows a red dot on
every flag screen; open it once and the dot is gone, also after quit + rejoin.
