---
title: 'Villager menu opens on the change-job tab while that tab has its red dot'
status: ready-for-human
origin: 2026-10-17 playtest round 5 — user chat 18:00:22 (dev-server-8, build 3ed88de2)
---

"If the "change job" tab has a red dot, it should take initial focus opun opening the villager UI."

## Fix

- New pseudo-type `OpenVillagerMenuMessage.INITIAL` ("the server picks"): sent by the two
  player entry points — right-clicking a villager (`Jobs.openInventoryAndStatusScreen`) and the
  flag villager-card click (`MultiStatusScreen`). Tab clicks still send their explicit type.
- `TownVillagerUIs.showUI` resolves INITIAL via the pure `VillagerTabs.initialTab(dot,
  showInventoryTab)`: CHANGE_ROOT while `wantsJobChange` (the same predicate as the dot), else
  INVENTORY, or STATS for the management view (the old defaults of the two entry points).
- Pinned by `VillagerInitialTabTest` (5).
