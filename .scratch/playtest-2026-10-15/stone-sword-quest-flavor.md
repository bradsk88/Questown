---
title: 'Stone sword quest flavor should drop the "your villagers will need them" line'
status: done
origin: 2026-10-15 playtest — user chat 03:07:31
---

# Sword-quest flavor: drop the "villagers will need them" clause

User report: "Stone sword should drop the '- your villaers will need them'
text."

Source: `TownQuests.java:1389` —
`"Craft 4 stone swords and place them in your store room chest. Your
villagers will need them - each hunter carries one."`

Proposed: `"Craft 4 stone swords and place them in your store room chest.
Each hunter carries one."` (the clause the user quoted is the "why" payoff
they don't want yet, same spirit as the store-room flavor ruling of the
same session; "each hunter carries one" is the fact that makes the ×4 count
readable and is kept). Confirm with the user if they want the whole second
sentence gone. Flavor is post-time-baked (existing worlds keep old copy).

## Resolved (audit 2026-10-22)

`TownQuests.java:1423`: "Craft 4 stone swords and place them in your store room chest. Each hunter carries one." (bbbdf88f). Existing worlds keep the old baked flavor.
