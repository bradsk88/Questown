---
title: 'Raw-food quest item icon: click opens the "jobs that make this item" screen for the tag'
status: fixed
origin: 2026-10-17 playtest round 2 — user chat 05:42:57 (feature request)
---

"Clicking on the item icon on the raw food quest should take you to the 'jobs that
make this item' screen for the tag."

The ItemJobsScreen (`ClientAccess.openItemJobs(Ingredient, jobs, flagPos)`) already
exists for requested items; the quest card's ingredient strip (`renderRecipeCardIcons`)
renders the LOOT quest's tag (`questown:villager_simple_furnace_food_raw`) as an icon.
Wire a click on that icon to the same producer lookup the job board uses.
