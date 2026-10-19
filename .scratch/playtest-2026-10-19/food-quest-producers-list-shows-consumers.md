---
title: 'Clicking the "8 food" quest icon lists jobs that USE cooked food, not ones that produce it'
status: resolved
origin: 2026-10-19 playtest (dev-server-13) — user chat 04:02:05
---

"Clciking the "food" icon on the "8 food" quest takes you to the "jobs that produce this item" UI,
but it seems to only show jobs that _use_ cooked food."

## Resolved (2026-10-19)

The item-jobs screen (`TownVillagerUIs.showItemJobsUI`) listed producers through
`getAllJobsThatProduce`, which also counts a job's declared `initial_request`: cook/fuel,
cook/stock_fuel, cook/stock_ingredients and baker/fuel declare `#questown:villager_food` but only
consume food. It now uses `getJobsKnownToProduce` (results only; loot jobs limited to the town's
known loot) and logs the list (`[item-jobs] producers of ...`). Registry-bound: check in a client.
