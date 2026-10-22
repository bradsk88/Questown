---
title: '"A new villager arrives in the morning" telegraphs villagers before the player knows them'
status: done
origin: 2026-10-17 playtest round 6 — user chat 19:35:29 (dev-server-9, build fe1b7238)
---

""A new villager arrives in the morning" should say "A reward will be granted in the morning". -
at this point, the player might not actualy know abot villagers, so we shouln't telegraph it."

`gui.status.villager_arriving_morning.first` (MultiStatusScreen, zero villagers + an arrival
queued) now reads "A reward will be granted in the morning". The later "...and one more in the
morning" line (villagers already met) is unchanged.

## Resolved (audit 2026-10-22)

en_us.json:556 `gui.status.villager_arriving_morning.first` = "A reward will be granted in the morning".
