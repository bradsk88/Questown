---
title: LOOT quest closes on raw-loot extraction, independent of the job's post-extract rule
status: closed
closed: 2026-10-04
area: jobs/declarative, town/entity
---

## Problem

The raw-food LOOT quest (2.5 gate, `villager_simple_furnace_food_raw`) only closed
for jobs that declared an `EXTRACTING_PRODUCT` special rule. The sword-hunter
declares none, so a hunted raw-meat grant (mutton) never closed it, and the
downstream 2.75 top-need gate (which needs cook staffed) sat closed. Field runs
looked stochastic because which extraction closed it depended on *which job*
extracted the raw food.

## Root cause (verified in code)

`Quests.processLootQuests` was nested in `RealtimeWorldInteraction`'s 5-arg
`postExtractHook`, reached only via the abstract 3-arg bridge, which short-
circuits when `specialRules.get(EXTRACTING_PRODUCT)` is empty. The hunter job
has no such rule → the check was skipped on its grant. Engine (warp vs realtime)
was a red herring — the special-rule gate is upstream of engine dispatch.

## Fix

- `AbstractWorldInteraction`: 3-arg bridge now calls a virtual
  `onLootExtracted(inputs, item)` **before** the rule gate (default no-op).
- `RealtimeWorldInteraction`: overrides it to fire
  `processLootQuests(item)` for any non-empty extraction; the check was removed
  from the rule-gated 5-arg hook (no double-fire). Warp engine inherits no-op.
- `LootExtractGuard`: Forge-free decoupling decision, unit-pinned.

## Verified

- **Unit (green):** `LootExtractGuardTest` — non-empty+no-rule fires,
  non-empty+rule fires, empty never fires. Affected slice green; strangler gate
  clean.
- **Oracle (RED→GREEN):** `loot_on_extract` autotest — before
  `quest complete=false ... [FAIL]`, after `quest complete=true ... [PASS]` with
  the `quest_completed [menu.quests.raw_food]` broadcast.
- **Untestable half (doctrine):** the live wiring (`RealtimeWorldInteraction` +
  declarative engine) is registry-bound (`<clinit>` hits Forge) → not unit-loadable;
  pinned at the pure decision + the autotest.

## Left uncommitted (entanglement — user decides keep/remove)

The `loot_on_extract` oracle + its `addLootQuest` scaffolding and the
`[LOOTTRACE]` DEV-DIAG lines (in `TownQuests`, `VisitorMobEntity`,
`TestBlueprintRegistry`) are investigation scaffolding intermingled in
`TownQuests` with the separate uncommitted `cook-skill-unlock` work. Only the
self-contained 4-file fix + the doc was committed; the scaffolding stays in the
working tree for the dev to keep (as the regression oracle) or drop.

## Resolved

Committed the LOOT-quest-close-on-extraction fix (the 4 `jobs/declarative/`
files + the `town/entity/COMPLEX.md` section). The 2.5 raw-food gate now closes
on any raw-loot extraction, which unblocks 2.75 top-need for the hunt path.
