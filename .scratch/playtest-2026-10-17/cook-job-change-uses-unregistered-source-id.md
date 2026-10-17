---
title: 'Onboarding cook job change targets the unregistered template source id cook/simple_furnace_food'
status: fixed
origin: 2026-10-17 playtest round 4, server log 06:24:35 (no user report yet)
---

Log (/tmp/dev-server-6.log):
```
06:24:35 Blessed BOP dictates job root change to: JobID{rootId='cook', jobId='simple_furnace_food'}
06:24:35 Changing villager from gatherer to JobID{rootId='cook', jobId='simple_furnace_food'}
06:24:35 [ERROR] No work found for job ID: JobID{rootId='cook', jobId='simple_furnace_food'}. Falling back to any job in the same root ID.
06:24:35 Job changed to JobID{rootId='cook', jobId='stock_ingredients'} for f1e15fb0 [from gatherer:gather]
06:24:35 Broadcasting message: messages.jobs.changed [cook:simple_furnace_food, Villager 3]
```

`cook/simple_furnace_food` is the simple-furnace TEMPLATE's source id: `ResourceJobLoader`
expands it into per-item cook jobs and never registers the source (hard-coded at its
line ~92, TODO "make this a special rule"). The onboarding cook beat names the source id
(`TownQuests` cookJobQuest, ~line 1427) and the blessed BOP carries it, so:
- `ServerJobsRegistry` logs an ERROR and falls back to "any job in the same root"
  (lands on cook/stock_ingredients — works).
- `TownQuests` quest completion already matches on root as a fallback (its line ~1911
  comment) — works.
- **Player-facing:** the `messages.jobs.changed` broadcast carries the raw id
  `cook:simple_furnace_food` (design-principles "Nothing raw reaches the player").

Fix shape: the onboarding beat / blessed BOP should name a registered cook job (the
template's starter, e.g. `cook/mutton` per CONTEXT.md "Starter job"), or the change-root
path should resolve a template source id to the root before broadcasting. Check what the
chat line actually rendered first.

## Consequence found + partial fix (2026-10-17, user chat ~06:30)

The fallback picked `cook/stock_ingredients` (first same-root entry). It needs
`kitchen_stocked` (furnace + chest); the player's kitchen was `kitchen_small` (furnace),
so the cook sat in COLLECTING_SUPPLIES with need=NONE while raw meat of every kind was in
the store-room chest (`_qtdev inspect villagers` 06:27:47; economy tab only
`#questown:villager_food x12`). User: "stock kitchen should not be a job that gets
unlocked initially (unless it was randomly selected, which I guess would be fine)".

Fixed: the fallback prefers a job unlocked with the root (`Work.preferInitiallyUnlocked`;
cook → extract/fuel). **Still open:** the onboarding beat should name a registered job
(stops the ERROR) and the broadcast should not carry the raw id.

## Fixed 2026-10-17 (overnight)

The onboarding cook quest names `TownQuests.ONBOARDING_COOK_JOB` (cook/extract, registered); the card and the "changed job" broadcast name the profession (`jobs.<root>`). village/day1 run: no "No work found" line, broadcasts `jobs.hunter` / `jobs.cook`, cook on cook/extract, 29/29.
