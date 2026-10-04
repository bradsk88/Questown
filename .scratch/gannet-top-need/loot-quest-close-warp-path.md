---
title: warp path closes the same LOOT quest the realtime path closes (Option B)
status: closed
closed: 2026-10-04
area: jobs/declarative, town/entity, integration/minecraft, commands/test
---

## Problem

`loot_on_extract` (realtime) closed the raw-food LOOT quest, but the warp path
did not: `TimeWarpWorldInteraction` inherited the `onLootExtracted` no-op, so a
grant that completed during a 24000-tick warp left the quest open. Dev
principle: warp and realtime must yield the same quest outcome.

## Direction (locked 2026-10-04, handoff "crake")

**Option B** over A: the warper stays *pure* — no live `QuestsHolder`/flag
reference. The close is computed from the snapshot only and applied at settle:
record in `MCTownState`, apply in `TownFlagState.warp` via the live flag's
`getQuestHandle().processLootQuests(...)`.

## Implementation (verified)

- `MCTownState.recordedLootExtractions` + `recordLootExtract` /
  `recordedLootExtractions()` / `clearRecordedLootExtractions()`; the list
  instance is shared by reference through the state copy paths (like `knowledge`).
- `TimeWarpWorldInteraction` overrides the 3-arg `postExtractHook` bridge (the
  only extract hook with a return value) and records non-empty items under the
  same `LootExtractGuard.questCheckFires` contract as realtime.
- `TownFlagState.warp` settle applies each record through the live quest handle,
  then clears (consumed once, never stored). Covers dev `warpTime` and sleep-warp.
- `MCAdvanceTime.finalizeState` had to carry the list — the terminal rebuild
  had silently dropped it (first oracle red: records present, settle saw 0).
- **Harness**: `TestExecutor.checkResults` (warp-only path) now runs the
  blueprint's `withCustomAssertion` — previously vacuous on warp-only scenarios,
  which is how `loot_on_extract_warp` first "passed" with the quest never closed.

## Tests

- `MCTownStateLootExtractTest` — 3/3 green (record order, copy survival,
  consume-once).
- Oracles: `loot_on_extract` (realtime regression) and `loot_on_extract_warp`
  — `RESULT: 2/2 passed (ALL PASS)`; warp run log shows `warp-record` per
  extraction, the settle applying them, and `quest complete=true`
  (CUSTOM assertion: PASS).

Note: the oracle entry in `TestBlueprintRegistry` (`loot_on_extract_warp` +
`loot_on_extract` refactor) is entangled with the uncommitted
`addLootQuest` dev scaffolding (see the commit-scoping trap in handoff "crake")
— it lands with that pile, not the core commit.
