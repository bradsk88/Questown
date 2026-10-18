---
title: 'Onboarding bed quest: use the standard "place these blocks in a room" tooltip, not custom text'
status: resolved
origin: 2026-10-18 playtest (dev-server-12, build dd7a43f5) — user chat 21:35:33
---

"The onboarding bed quest seems to have custom text. But the standard "place these blocks i na
room" tooltip would be better."

Find the onboarding bedroom quest's tooltip override (quest flavor / custom description) and
compare with the standard room-recipe tooltip used by other room quests.

## Resolved (2026-10-18)

The bedroom quest (`TownQuests.addHunterGatherer`) no longer sets flavor text, so `QuestsScreen` shows the standard `menu.quests.room_quest` tooltip ("Build a room containing the blocks shown here"). Pinned by `TownQuestsHunterGathererOnboardingTest.bedroomQuest_usesTheStandardRoomTooltip`.
