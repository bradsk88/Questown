---
title: 'Villager menu (via flag menu) still shows the un-openable inventory tab'
status: fixed
origin: 2026-10-15 playtest — user chat 03:06:38
---

# Villager menu from flag: inventory tab should be gone

User report: "The flag menu -> villager menu screen still shows the
inventory tab, even though its not openable. It should be gone from that
view."

Context: the management-mode villager UI (opened from the flag menu) hides
the inventory interaction (see gui/COMPLEX.md "MultiStatusScreen: full-card
click opens the villager UI in management-only mode (2026-10-14)"), but the
TAB itself still renders. Find where the villager screen builds its tab list
and drop the inventory tab when the screen is in management-only mode (the
direct-right-click-villager path keeps it).

Fixed: the gap was the SKILLS pane. Every tabbed villager menu filtered `getEnabledTabs()` on the management-only flag except `VillagerAdvancementsScreen`, whose embedding returned `VillagerTabs.all()`. The `showInventoryTab` flag now rides `TownVillagerUIs` SKILLS router -> `OpenVillagerAdvancementsMenuMessage` (new wire field) -> `ClientAccess.openVillagerAdvancements` -> screen ctor -> `VillagerTabs.hideInventory(...)` when false. See gui/COMPLEX.md "Playtest 2026-10-15". Committed 2026-10-16 on branch pt-ui.
