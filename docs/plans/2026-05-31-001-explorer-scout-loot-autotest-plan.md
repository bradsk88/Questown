# Plan: Automated coverage for the explorer's ScoutLootSpecialRule (warp path)

Date: 2026-05-31
Status: Implemented (compiles clean) — in-game autotest run still pending
Related: ADR-0004 (scouting as special rule), ADR-0002 (warp does not model hunger),
ADR-0008 (explorer job block)

## Problem

`ScoutLootSpecialRule` (the explorer's "learn a loot drop for this map's biome"
outcome) is the one path in the meta-item-removal refactor with **no automated
coverage**. The explorer is not in the in-game autotest, and its payoff —
knowledge-state registration via `knowledgeUpdater()` — is invisible to the
autotest harness, which only asserts on inventory deltas. A future refactor of
`afterExtract` could silently break scouting with zero test signal.

This plan adds durable regression coverage by (a) teaching the autotest harness to
assert on town knowledge growth and (b) adding an explorer test blueprint that
exercises the warp path end-to-end.

## Why the warp path is the right (and sufficient) target

- Leaver jobs (gatherer/hunter) are **warp-only** in the harness
  (`welcomeMatBlueprint` sets `realtimePhase=false`). The explorer follows suit.
- Warp is also the *riskier* path: `beforeTick`/`UnsafeVillagerData` do not run in
  warp, so warp-relevant logic must live at `afterExtract`. Covering warp covers
  the gotcha that motivated the refactor.
- The realtime updater stays covered by the existing no-op unit test
  (`ScoutLootSpecialRuleTest`). Acceptable for now.

## Feasibility (verified)

- The explorer **self-stamps** a biome on the map it produces
  (`ExplorerWork.getFromLootTables`, lines ~75-87): random from 7 hardcoded biomes
  (`dark_forest, desert, forest, jungle, mushroom_fields, savanna, taiga`), none of
  which is `plains` — so the flat test arena's actual biome is irrelevant.
- All 7 biomes have a `gatherer_notool` loot table, and `gatherer_notool/default.json`
  guarantees ≥1 item. So `rollScoutedLoot` returns non-null and `knowledgeUpdater`
  fires deterministically enough.
- **Timing is safe**: `tfbe.warpTime()` (`TestExecutor:357`) is synchronous; the
  merge-back `registerFoundLoots(newState.knowledge())` runs *inside* it
  (`TownFlagState:438`, in `warp()`); then 40 ticks of `SETTLE_AFTER_WARP` elapse
  before `checkResults` (`TestExecutor:413→419`). The live knowledge store reflects
  the warp-learned loot by the time we read it.
- **Clean baseline is free**: each test destroys+recreates the flag
  (`TestArenaPreparer:103` → `TestExecutor:199`), so `knowledgeHandle` is born fresh
  with only `baseKnowledge` (wheat seeds). No reset code needed.
- `cooked_beef` ∈ `VILLAGER_FOOD` tag (confirmed in `villager_food.json`).

## Locked design decisions

1. **Goal**: durable regression coverage (extend the framework), not a one-off
   in-game run.
2. **Assertion semantics**: starting from a clean/empty knowledge baseline, after
   the explorer completes one cycle, learned (non-base) knowledge count grows by
   **exactly 1**. Clean baseline guaranteed by arena teardown (no new reset code).
3. **Read approach**: read via `tfbe.getKnowledgeHandle()` using the **no-arg**
   `getAllKnownGatherResults()` (returns all learned entries + base, counted once),
   snapshot `.size()` before and after, assert the delta.
4. **Interface change**: promote the existing concrete no-arg
   `getAllKnownGatherResults()` (already on `KnowledgeStore:57`) onto the
   `KnowledgeHolder` interface so the harness can call it through the getter.
   Blast radius is minimal — only `TownKnowledgeStore extends KnowledgeStore`
   (inherits for free); zero direct implementers, zero test doubles.
5. **Framework representation**: follow the `minExpectedFullnessAfter` precedent —
   add `@Nullable Integer minKnowledgeGrowth` to `TestBlueprint`, asserted and
   broadcast in `TestExecutor`. `TestResultChecker` stays purely item-delta.
6. **Blueprint assertion composition**: keep **both** tripwires — the inherited
   `wildcardExpectation()` (≥1 product → the map is deposited, proves the job ran)
   **and** `minKnowledgeGrowth=1` (proves scouting learned loot). They fail
   independently, localizing a future break to "job didn't run" vs "scouting
   didn't learn".
7. **Determinism knobs**: `warpAmountOverride=2500` (≥ the ~2000-tick NEED_ROAM
   timed state, < 2 cycles), supply **exactly 1 paper + 1 cooked_beef** (a second
   cycle is impossible — it would starve at NEED_FOOD/NEED_PAPER), so knowledge
   growth is exactly 1. ADR-0002 (warp doesn't model hunger) removes the
   eat-for-hunger starvation risk on the warp-only path. Assert `after - before == 1`.
8. **Record-edit strategy**: append `minKnowledgeGrowth` as the **last** record
   component + a **21-arg compatibility constructor** delegating `null`, so all ~14
   existing full-ctor call sites compile unchanged. Explorer sets the field via a
   tiny **`withMinKnowledgeGrowth(int)` wither**.
9. **Pass/fail folding**: gate `warpPassed` on the knowledge check **only when
   `minKnowledgeGrowth != null`** (so every other warp test is byte-for-byte
   unaffected). A scouting regression turns the explorer entry red in the
   `testall` X/Y summary.

## Implementation checklist

### New/changed production interface
- [ ] `town/interfaces/KnowledgeHolder.java` — add
      `ImmutableSet<ITEM_IN> getAllKnownGatherResults();` (no-arg). `KnowledgeStore`
      already implements it (line 57); `TownKnowledgeStore` inherits it. No other
      implementer exists.

### TestBlueprint record (record-edit strategy = decision 8)
- [ ] `commands/test/TestBlueprint.java`:
  - Append `@Nullable Integer minKnowledgeGrowth` as the **last** component (after
    `boolean useNaturalWarp`).
  - Add a **21-arg compatibility constructor** with the *old* canonical signature
    that delegates to the 22-arg one with `minKnowledgeGrowth = null`. This keeps
    the ~14 edge-case full-ctor call sites in `TestBlueprintRegistry` compiling
    unchanged, and the two convenience ctors (7-arg, 8-arg) auto-route through it.
  - Add `public TestBlueprint withMinKnowledgeGrowth(int n)` returning a copy with
    the field set (used only by the explorer blueprint).

### TestExecutor knowledge gate (decision 9)
- [ ] `commands/test/TestExecutor.java`:
  - Add field/snapshot: capture `beforeKnowledgeCount =
    tfbe.getKnowledgeHandle().getAllKnownGatherResults().size()` in
    `captureBefore()` (alongside `beforeCounts`).
  - Add `private boolean checkKnowledgeGrowthIfNeeded()` mirroring
    `checkFullnessIfNeeded()`'s null-guard shape: if
    `blueprint.minKnowledgeGrowth() == null` return `true`; else read the after
    count, compute `grown = after - beforeKnowledgeCount`, broadcast a `KNOWLEDGE`
    line, return `grown == blueprint.minKnowledgeGrowth()` (exact match; explorer
    sets 1).
  - In `checkResults()` (the warp path, ~line 428-431): after the item check,
    `warpPassed = result.passed() && checkKnowledgeGrowthIfNeeded();`
    (the helper short-circuits to `true` for all non-explorer blueprints).

### Explorer blueprint + registration (decisions 6, 7)
- [ ] `commands/test/TestBlueprintRegistry.java`:
  - Add `private static TestBlueprint explorerBlueprint()`:
    ```
    return welcomeMatBlueprint(List.of(
            new ItemStack(Items.PAPER, 1),
            new ItemStack(Items.COOKED_BEEF, 1)
    )).withMinKnowledgeGrowth(1);   // keeps inherited wildcardExpectation()
    ```
    Then wrap in the full-ctor form (or extend the wither) to set
    `warpAmountOverride=2500`. Simplest: build the base via `welcomeMatBlueprint`,
    then re-emit through the full 22-arg ctor with `warpAmountOverride=2500` and
    `minKnowledgeGrowth=1` — OR add a second small wither for warp override if
    cleaner. (Implementer's call; prefer the wither for both to avoid a 22-arg
    literal.)
  - In `get(JobID)`: inside the existing `"gatherer".equals(rootId())` branch,
    **before** the generic `return gathererBlueprint();`, add
    `if ("explore".equals(jobId.jobId())) return explorerBlueprint();`
    (mirrors the `crafter`/`isArmorerJob` pattern).
  - In `getTestableJobs()`: add
    `jobs.add(entry(new JobID("gatherer", "explore"), explorerBlueprint()));`
    after the `gatherer:axe` entry (~line 85).

### Verify
- [ ] `./gradlew compileJava` (expect ~11-16s; pre-existing deprecation warnings OK).
- [ ] In-game: `/_qtdev test gatherer:explore 2500 destroy` — expect a `WARP [PASS]`
      line for items (wildcard) AND a `KNOWLEDGE [PASS]` line (growth == 1).
- [ ] `/_qtdev testall <warp> destroy` — explorer entry appears in the X/Y summary.
- [ ] Negative check (optional, do not commit): temporarily no-op
      `ScoutLootSpecialRule.afterExtract` and confirm the explorer entry goes red on
      the KNOWLEDGE line while the item/wildcard line stays green — proves the
      tripwire actually catches a scouting regression.

## Notes / watch-outs
- `TownKnowledgeStore.registerFoundLoots` skips the GATHERER_MAP for *knowledge*
  (`KnowledgeStore:84`) but the map is still a deposited inventory product — that's
  what satisfies the wildcard tripwire.
- If `warpAmountOverride` needs to be set without a 22-arg literal, prefer adding a
  `withWarpAmountOverride(int)` wither rather than expanding the explorer to a full
  positional constructor call.
- This plan adds production-interface surface (`KnowledgeHolder`); consider a brief
  ADR if the team wants the public knowledge-read API documented (the no-arg getter
  becomes part of the interface contract).
