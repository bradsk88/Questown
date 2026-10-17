---
title: 'Clicking a "Villager Job" (JOB_CHANGE) quest card should open the villagers tab'
status: fixed
origin: 2026-10-17 playtest round 2 — user chat 05:37:56 (feature request)
---

"'Villager Job' quests should take you to the villagers tab when clicked (tooltip
should reflect that) <-- this is a feature request"

Scope:
- Click on a JOB_CHANGE quest card in the flag quests screen → switch the flag menu
  to the villagers tab (the BOP spend happens from a villager's change-job tab).
- The card tooltip gains a click hint line, same pattern as the room quests'
  click-to-craft tooltip (`menu.quests.roomblock.*`, 2026-10-14).

Entry points: `gui/QuestsScreen` card click handling + tooltip switch; the flag
menu's tab switch (`Opening flag menu: <tab>` log line).
