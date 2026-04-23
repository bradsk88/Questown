---
title: Agent-Automated Verification for Helper Chicken Arc
type: feat
status: active
date: 2026-04-23
deepened: 2026-04-23
origin: docs/plans/2026-04-22-001-feat-helper-chicken-onboarding-plan.md
---

# Agent-Automated Verification for Helper Chicken Arc

## Overview

Remove the two human-in-the-loop handoffs the helper-chicken onboarding plan (`docs/plans/2026-04-22-001-feat-helper-chicken-onboarding-plan.md`) left open: the Structure-Block round-trip that authors `empty_town.nbt`, and the `/_qtdev` in-game verification of F1–F4 flows. Both become agent-executable here — a one-shot NBT-editing Java main populates the scaffolding blocks programmatically, and a new `ChickenArcTestExecutor` rides the existing `AutoTestRunner` harness so the agent can run `./gradlew runServer -Dquestown.autotest=true`, parse the `[autotest] RESULT:` line, and loop on fix-verify-retry without asking the user to do anything in a dev client.

This plan does **not** re-open the chicken-arc product decisions captured in the prior plan. Product scope, actors, flows, acceptance examples, and the U1–U7 implementation breakdown are all preserved. This is strictly enabling work for the verification path.

---

## Problem Frame

The helper-chicken plan shipped all seven implementation units (U1–U7) but left two items in "manual / in-game verification" land:

- `empty_town.nbt` needs the scaffolding blocks (unlit campfire at CAMPFIRE_OFFSET, cobblestone room with one missing wall and no door, two gate columns) placed at the offsets `HelperChickenBeatOffsets` already encodes. The existing convention doc (`docs/conventions/editing-empty-town-nbt.md`) documents a Structure-Block round-trip that requires a human running a dev client.
- F1–F4 end-to-end verification of the 14-beat state machine, UI observations, seeds delivery, statue transform, and wand-on-campfire gate is documented in TODO_ tests (`ChickenArcControllerTest`, `CurriculumSequenceTest`, `WandLightCampfireTest`, `FirstGatherWorldlySeedsTest`) as requiring a live `ServerLevel` + spawned chicken + player. The existing `/_qtdev testall` harness covers jobs (farmer, gatherer, baker, etc.) but not entity-driven scenarios.

The user explicitly asked: "modify the nbt file yourself" + "use the existing testing tools that allow YOU to run a server and trigger test scenarios, so you can work in a loop without needing input from me". This plan delivers both.

---

## Requirements Trace

- R1. `empty_town.nbt` carries scaffolding blocks at the coordinates `HelperChickenBeatOffsets` already defines (unlit campfire, mostly-built cobblestone room, two gate columns with a gap).
- R2. The NBT mutation is reproducible from source: the authored layout lives as code, and re-running the editor produces tag-level-equivalent output from the same input (`NbtUtils.compareNbt(a, b, true)` returns true — byte-level equality is not guaranteed because gzip output varies across JVMs).
- R3. The chicken arc's F1, F3, and F4 flows are driven end-to-end inside the existing `AutoTestRunner` headless harness.
- R4. Each chicken-arc scenario reports pass/fail via the `[autotest]` log format, and `AutoTestRunner` exits with `0` when all chicken scenarios pass or `1` when any fails.
- R5. The agent can run the full verification cycle via one gradle command, read a single log file to decide pass/fail, and re-invoke after code changes without additional setup.
- R6. Existing job-centric `/_qtdev` tests continue to pass unchanged.

**Origin actors, flows, and acceptance examples** (carried from the parent helper-chicken plan and therefore from its origin brainstorm):

- Origin actors: A1 (New Player), A2 (Helper Chicken), A3 (First Villager), A4 (Returning Player) — the `ChickenArcTestExecutor` simulates A1 actions and observes A2/A3 behaviors; A4 is out of scope for automated verification (returning-player semantics are covered by the realtime / warp infrastructure the executor reuses).
- Origin flows to cover: F1 (First Arrival), F3 (Village Construction), F4 (Handoff and Closing Beat). F2 (Evening Ritual, sleep-based) is carried as a follow-up — sleep semantics under `AutoTestRunner`'s fake-player model need additional design (see Open Questions).
- Origin acceptance examples targeted by scenarios: AE4, AE5, AE6, AE7. AE1 (initial spawn + stick-peck bubble) is covered by a new scenario added in this deepening pass. AE-claims live at the per-scenario level (with `Covers AE<N>.` prefixes in test bullets), not at the unit level.

---

## Scope Boundaries

- **Renderer correctness verification** (U2 of the helper-chicken plan: bubble layer, through-walls render type, billboard math) stays in-game-only. `AutoTestRunner` has no client render pass — pixel-level validation is unreachable from a dev server. The executor asserts `SynchedEntityData` values (icon ItemStacks, through-walls boolean) rather than rendered pixels.
- **Localization review** of the hint-text strings stays human-only — the executor asserts lang-key delivery, not the English strings themselves.
- **Statue art / block model polish** stays out of scope; the executor asserts that `STONE_CHICKEN_STATUE` is placed, not that it looks good.
- **Multiplayer co-op edge cases** stay deferred — `AutoTestRunner` spawns a single fake player; concurrent-action scenarios are not exercised.
- **Legacy-world migration of `empty_town.nbt`.** Minecraft bakes structures into chunks at generation time. Players on a pre-update version who already generated chunks containing `empty_town` will retain the old (scaffolding-less) layout; the mutation only applies to newly generated chunks. The chicken arc's rotation detector and spawn controller must therefore degrade gracefully on legacy worlds (detector forfeits, no chicken ever spawns) — that's the existing production behavior when the authored anchors are missing. A one-shot server-side fixup to rewrite legacy `empty_town` instances in place is out of scope.

### Deferred to Follow-Up Work

- **F2 (sunset + sleep) scenario.** `AutoTestRunner`'s fake player does not sleep naturally; `CampfireSleepHandler` uses `player.startSleepInBed()` which depends on client-driven wake events. A fake-sleep shim that advances time and fires `CampfireSleepHandler.onWake(fakePlayer)` directly is doable but designs to a separate brainstorm, since it affects sleep semantics across other systems. This plan wires F1, F3, F4; F2 follows.
- **Rotation-detection in all four rotations.** Worldgen places `empty_town.nbt` at a random rotation via the jigsaw. `AutoTestRunner`'s test arena places a flag directly (not via worldgen), so rotation detection runs against a synthetic placement. This plan exercises rotation `NONE` and one non-`NONE` rotation. Full 4-rotation sweep is an extension.

---

## Context & Research

### Relevant Code and Patterns

- **AutoTestRunner harness** — `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java`. Triggered by `-Dquestown.autotest=true`. Subscribes `ServerStartedEvent`, spawns a fake player at origin, force-loads the origin chunk, registers a tick listener that delegates to `TestAllExecutor`, and on completion calls `server.halt(false)` + `Runtime.getRuntime().halt(passed == total ? 0 : 1)`. Logs via `QT.FLAG_LOGGER.info(...)` under the `[autotest]` prefix.
- **TestExecutor phase machine** — `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java`. Sequential phases: DESTROY_NEARBY_FLAGS → FLATTEN → SETTLE_BEFORE_PLACE → PLACE_FLAG → BUILD_ROOM → REGISTER_ROOM → SPAWN_VILLAGER → ASSIGN_JOB → SETTLE → CAPTURE_BEFORE → RUN_WARP / NATURAL_WARP_FREEZE → SETTLE_AFTER_WARP → CHECK_RESULTS → KILL_FOR_INSPECT (+ realtime phase if configured). Blueprint-driven. Tick-advanced via the listener in `AutoTestRunner`.
- **TestBlueprint** — `src/main/java/ca/bradj/questown/commands/test/TestBlueprint.java`. Record carrying room type, block placements, supply items, door/gate offsets, `TestExpectation`, warp override, villager count, realtime flags. Today's fields are job-centric.
- **TestExpectation / TestResultChecker** — `src/main/java/ca/bradj/questown/commands/test/TestExpectation.java` + `TestResultChecker.java`. Assertions are item-count deltas (before/after snapshots over villager inventories + containers). No block-state, entity-data, or log-based assertions exist today.
- **TestBlueprintRegistry** — `src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java`. Registers all 11+ job blueprints consumed by `TestAllExecutor`.
- **LogTestOutput** — `src/main/java/ca/bradj/questown/commands/test/LogTestOutput.java`. Log-only (no chat). Used by `AutoTestRunner` in headless mode. `PlayerTestOutput` is the chat+log variant.
- **Fake-player force-load pattern** — `AutoTestRunner.java` spawns via `FakePlayerFactory.get(...)` and calls `overworld.setChunkForced(...)` so tick listeners run without a real client.
- **QT logger categories** — `src/main/java/ca/bradj/questown/QT.java`. Must use `.info()` for visibility; `.debug()` is suppressed at the default log level. This is both an implementation rule (per memory) and a scenario-author rule.
- **Helper-chicken implementation surface** (the system under test, all landed in the parent plan):
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcTransitions.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcConditions.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubbles.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcLootGuarantee.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcUiObservations.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenStatueTransformHandler.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBeatOffsets.java`
  - `src/main/java/ca/bradj/questown/town/HelperChickenSpawnController.java`
  - `src/main/java/ca/bradj/questown/town/HelperChickenRotationDetector.java`
- **Target structure file** — `src/main/resources/data/questown/structures/empty_town.nbt`. Gzip-compressed. Standard Minecraft structure schema: root `CompoundTag` with `size: {x,y,z}`, `palette: ListTag<CompoundTag{Name, Properties?}>`, `blocks: ListTag<CompoundTag{pos: [int,int,int], state: int}>`, and optional `entities`. `DataVersion: int` at root.
- **Convention doc for NBT edits** — `docs/conventions/editing-empty-town-nbt.md`. Documents the current Structure-Block round-trip. This plan adds a parallel "authored in code" path without removing the manual fallback.

### Institutional Learnings

- `docs/conventions/editing-empty-town-nbt.md` — "no build-time tooling in the repo" is the pre-existing state. This plan intentionally changes that.
- `CLAUDE.md` — "DO NOT simulate core questown logic". Applies here: the chicken-arc scenarios must drive the real `ChickenArcController` via the real flag BE, not a mock. `AutoTestRunner` already does this — scenarios spawn a real flag BE and a real `HelperChickenEntity` and let the real controller tick.
- Memory / `MEMORY.md`:
  - "Log levels for in-game debugging: `QT.JOB_LOGGER.debug()` does NOT appear in `run/logs/latest.log`. Use `.info()` for diagnostic logs during in-game testing, then remove them after." — applies to scenario-author logs.
  - "Container scan requires registered rooms: Chests must be inside rooms with registered doors/gates for the warp container scan to find them." — F4's seeds-in-container observation depends on a registered room; F3 beats must run before F4 can complete.
  - "Guard block property access: Always check `blockState.hasProperty(X)` before `getValue(X)`." — applies to observation code that runs during `/_qtdev` test teardown/rebuild.

### External References

- Minecraft Structure Block file format (Wiki): root compound with `size`, `palette`, `blocks`, `entities`, `DataVersion`. The "palette" is a deduplication table of `{Name, Properties?}` — each `blocks` entry carries a `state: int` index into it. Adding new blocks requires either reusing an existing palette entry or appending a new one and pointing at its index. We need palette entries for `minecraft:campfire[lit=false]`, `minecraft:cobblestone`, and (probably) `minecraft:oak_fence` or similar for the gate columns.
- Forge 1.19.2 `net.minecraft.nbt.NbtIo` — `readCompressed(InputStream) -> CompoundTag` and `writeCompressed(CompoundTag, OutputStream)`. Works standalone without booting a `ServerLevel`; SharedConstants/Bootstrap are not required for raw NBT I/O, only for richer Minecraft type registrations.

---

## Key Technical Decisions

- **NBT edit runs as a one-shot, not at every build.** A standalone Java main (invoked via a Gradle `JavaExec` task) mutates `empty_town.nbt` once; the result is committed. Running the editor on every build would couple resource state to tool state, which is the exact problem the existing convention doc describes. A one-shot matches how the Structure-Block round-trip has always worked.
- **The NBT edit is idempotent at the tag level.** The editor first scans the `blocks` list and removes any entries at the target offsets before inserting the new ones, so re-running the editor produces tag-level-equivalent output from the same input (verified by `NbtUtils.compareNbt(a, b, true)`, not byte-level — gzip output is not deterministic across JVMs). This makes the editor safe to re-run after authored-offset changes.
- **Palette is mutated additively, never rewritten.** The editor only appends missing block-state entries to `palette` and points new `blocks` entries at the appended indices. Existing palette entries are not touched. This avoids breaking any pre-existing `blocks` entries that reference specific palette indices.
- **Chicken-arc scenarios live behind a parallel executor, not as overloaded `TestBlueprint` fields.** `TestBlueprint` is job-centric (item-delta assertions). Retrofitting it for entity-driven scenarios with scripted player actions, beat-state assertions, and UI-open observations would blur its contract and complicate every existing job test. The plan adds `ChickenArcBlueprint` + `ChickenArcTestExecutor` as a parallel track that reuses `AutoTestRunner`'s tick listener + fake-player + force-load machinery.
- **Scripted player actions run server-side directly.** F1 ("player obtains wand") and F3 ("player places chest at offset") are simulated by the scenario calling the same server-side entry points the real player triggers — adding items to the fake player's inventory, calling `TownWand.onRightClicked(...)` directly, placing blocks via `level.setBlockAndUpdate(...)`. The executor never synthesizes input packets. This matches how `TestExecutor` already drives villagers through `SpawnVisitorReward` + `changeJobForVillager(...)`.
- **Rotation in happy-path scenarios is set directly; forfeit-path scenarios exercise the real detector.** For F1/F3/F4 happy-path coverage, the scenario writes `chicken-rotation-detected=true` + `chicken-structure-rotation=<rot>` on the flag BE immediately after placement. Rationale: deterministic setup avoids spending up to `MAX_RETRY_TICKS` (~20 ticks, `HelperChickenRotationDetector.java:36`) in the detector's retry loop, which would add flake surface to every scenario. For rotation-detector coverage itself, two dedicated scenarios (`rotation_ambiguity_forfeit`, `rotation_zero_match_forfeit`) place real campfire blocks and let `HelperChickenRotationDetector.tick()` run to completion — the detector is anchor-agnostic (it scans four rotated `CAMPFIRE_OFFSET` candidates via `sl.getBlockState(...).is(Blocks.CAMPFIRE)`; no jigsaw coupling), so synthetic placements drive it naturally. This closes the "real detector" coverage hole without making every scenario pay the retry-loop tax.
- **Spawn scenarios satisfy the radius gate rather than bypassing it.** `HelperChickenSpawnController.tick(flag)` checks four gates (radius-of-player, rotation-detected, ever-spawned, arc-forfeit). Scenarios teleport the fake player to within 10 blocks of `flagPos` and invoke the real `tick(flag)` — no `forceSpawn` bypass, no `@VisibleForTesting` annotation, no reflection audit test. This tests the real gate logic, avoids weakening a production invariant, and keeps the controller's public surface unchanged.
- **Unified `[autotest]` log format across both executor tracks.** Both the existing jobs track and the new chicken track emit one shape: `[autotest] <track>:<name> [PASS|FAIL] <expectation>: <details>` where `<track>` is `jobs` or `chicken-arc`. U4 retrofits `TestExecutor.broadcastResult` to this shape so the agent's U5 log-parse regex is genuinely single-pattern. Without unification, the agent has to maintain two regexes, and the "[autotest]" contract splinters.
- **Arena preparation is shared between executors via `TestArenaPreparer` with opt-in behaviors.** The chicken executor's `RESET_ARENA`, `PLACE_FLAG`, and `waitForInit` phases duplicate `TestExecutor`'s private-ish primitives. U3 extracts a `TestArenaPreparer` parameterized by a `PreparerOptions` record — `halfWidth`, `killHelperChickens`, `clearFakePlayerInventory`, `clearRotationDetectorRetryCounters`. Jobs track passes the zero-change defaults (width 7, all flags false), preserving R6 by construction rather than by "harmless" assertion. Chicken track opts into wider arenas (≥10), chicken kill-sweep, fake-player inventory clear, and `HelperChickenRotationDetector.RETRY_COUNTERS` clear. Scenario-specific overrides (e.g. `halfWidth=25` for `two_flags_independent_arcs`) flow through the same record.
- **`ChickenArcResultChecker` composes `TestResultChecker` for item-delta assertions.** To avoid two incompatible assertion contracts over the same `ExpectedProduct` record, `ChickenArcResultChecker` delegates item-delta checks to the existing `TestResultChecker` and adds its own per-assertion reporters for beat-state, flag-bit, entity-existence, and statue-placement checks. `ChickenArcExpectation` carries an `itemDeltas: TestExpectation` sub-field (composition) rather than an inlined `List<ExpectedProduct>`.
- **UI-open observations drive directly too.** `ChickenArcUiObservations.markVillagerUiOpened(level, flagPos)` and `markFlagUiOpened(level, flagPos)` are already static server-side entry points. The executor calls them directly in F4's handoff beats rather than simulating a menu open.
- **Each scenario is one entry in a new registry matching the existing registry shape.** `ChickenArcBlueprintRegistry` mirrors `TestBlueprintRegistry` — static list with a `category()` field on each entry. `AutoTestRunner` chains `TestAllExecutor` (jobs) → `ChickenArcAllExecutor` (chicken) sequentially, gating both on the existing `QUESTOWN_AUTOTEST_CATEGORY` env-var routing: category `"chicken"` runs only chicken scenarios; `"warp"` / `"eating"` / `"worldgen"` continue to select jobs; unset runs both.
- **Scenario assertions include beat state, flag-BE bits, entity existence, statue placement, and SynchedEntityData icons.** Item-delta assertions are delegated to `TestResultChecker` via composition (see the `ChickenArcResultChecker` decision above). Each assertion type gets a `ChickenArcExpectation` field; the checker reports individual [PASS]/[FAIL] lines per assertion in the unified log format so the agent's log parser sees which expectation failed.

---

## Open Questions

### Resolved During Planning

- **NBT editor placement — gradle task vs build plugin vs test class?** One-shot Java main invoked via a Gradle `JavaExec` task. Avoids coupling resource state to every build; avoids the complexity of a build plugin; keeps the code path easy to re-run on demand. Decided above.
- **Parallel executor vs overloaded `TestBlueprint`?** Parallel executor. Decided above.
- **Fake player action simulation — packets or server-side direct calls?** Direct calls to the server-side handlers the real player would have triggered. Decided above.
- **Rotation handling in scenarios?** Happy-path scenarios write rotation state directly on the flag BE; two dedicated forfeit-path scenarios exercise `HelperChickenRotationDetector.tick()` end-to-end. Decided above.
- **`NbtUtils.writeBlockState(BlockState)` availability in 1.19.2 mapped Forge?** Confirmed present (public `CompoundTag NbtUtils.writeBlockState(BlockState)` and inverse `readBlockState(CompoundTag)`). U1 relies on it unconditionally; no hand-authored-palette fallback needed.
- **Reuse vs. duplicate `TestExecutor` arena phases?** Extracted into a shared `TestArenaPreparer` per the Key Technical Decisions. Both executors compose the preparer with configurable half-width.
- **`STONE_CHICKEN_STATUE` cleanup between scenarios?** No special cleanup — `TestArenaPreparer.flatten()` levels the arena (half-width ≥10 for chicken scenarios) and removes the statue along with everything else. Verification: ensure the statue's final position (derived from `GATE_CENTER_OFFSET` + rotation) falls inside the preparer's flatten radius.
- **Cross-scenario leakage via `HelperChickenRotationDetector.RETRY_COUNTERS`?** The detector keeps a `static Map<BlockPos, Integer>` that `finalizeDetection` only removes when a match is found or retries exhaust. Happy-path scenarios force-set `chicken-rotation-detected=true` and never call the detector, so happy-path runs cannot pollute the map. But forfeit scenarios (`rotation_ambiguity_forfeit`, `rotation_zero_match_forfeit`) do run the detector; to be safe against scenario-order dependencies, `TestArenaPreparer` clears the map when `clearRotationDetectorRetryCounters=true` (chicken-track default).
- **Warp amounts for F4 + Worldly-Seeds scenarios?** `F4_seeds_to_statue` uses 0 warp (scripted F3 preamble reaches `WAITING_FOR_VILLAGER_UI` in real ticks; seeds deposit + chicken interact advance in real ticks). `first_gather_worldly_seeds_warp` copies the existing gatherer blueprint's warp value from `TestBlueprintRegistry` as the starting point; `first_gather_worldly_seeds_realtime` uses 0 warp with enough settle ticks for one gatherer cycle. F1/F3 both use 0 warp.
- **Log format across job and chicken tracks?** Unified on `[autotest] <track>:<name> [PASS|FAIL] <expectation>: <details>`. Decided above.
- **`ChickenArcExpectation` / `TestExpectation` assertion contract?** Composition — `ChickenArcResultChecker` delegates item-delta checks to `TestResultChecker`. Decided above.
- **`QUESTOWN_AUTOTEST_CATEGORY` filter plumbing?** Reuses existing mechanism — `AutoTestRunner.java:26` already reads the env var; `TestBlueprintRegistry.getTestsByCategory(String)` already filters by a `category()` field on entries. `ChickenArcBlueprintRegistry` adopts the same shape with `category = "chicken"` on each entry.

### Deferred to Implementation

- **Exact block-state strings for the palette entries.** 1.19.2 block states serialize via `NbtUtils.writeBlockState(state)` — U1 reads the intended default state of each block at runtime (from `Blocks.CAMPFIRE.defaultBlockState()` etc.) and passes to the helper. No hand-authoring required.
- **Whether scenario assertions about the chicken's `SynchedEntityData` icons need tick-settle waits after beat-state transitions.** The controller updates the chicken's data on the same tick the transition fires; the scenario may read the data immediately, but the client-sync round-trip happens asynchronously. Direct server-side reads on the entity should be synchronous — confirm at U3 implementation.

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
┌───────────────────────────────────────────────────────────────────────┐
│   One-shot NBT edit (pre-ship, on-demand)                             │
│                                                                       │
│   ./gradlew editEmptyTownNbt                                          │
│       → runs ChickenScaffoldingNbtEditor.main(...)                    │
│           · NbtIo.readCompressed(empty_town.nbt)                      │
│           · removeExistingBlocksAt(HelperChickenBeatOffsets.*)        │
│           · appendPaletteEntries(campfire[lit=false], cobblestone,    │
│                                   oak_fence)                          │
│           · insertBlocks(walls minus gap, gate columns)               │
│           · NbtIo.writeCompressed(empty_town.nbt)                     │
│       → developer commits the updated .nbt                            │
└───────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────┐
│   Headless verification loop (agent)                                  │
│                                                                       │
│   ./gradlew runServer -Dquestown.autotest=true                        │
│       → AutoTestRunner.onServerStarted(...)                           │
│           · spawn fake player + force-load origin chunk               │
│           · TestAllExecutor runs jobs (unchanged)                     │
│           · ChickenArcAllExecutor runs chicken scenarios:             │
│                 stick_peck_and_follow_spawn       (AE1)               │
│                 F1_stick_to_campfire              (AE4)               │
│                 F3_build_room_to_welcome_mat                          │
│                 F4_seeds_to_statue                (AE5)               │
│                 F1_rotation_clockwise_90                              │
│                 rotation_ambiguity_forfeit                            │
│                 rotation_zero_match_forfeit                           │
│                 two_flags_independent_arcs       (AE6)                │
│                 skip_chicken_command             (AE7)                │
│                 forfeit_remove_command                                │
│                 first_gather_worldly_seeds_realtime (AE5)             │
│                 first_gather_worldly_seeds_warp                       │
│                 wand_on_unlit_campfire_outside_flag (AE4)             │
│           · per scenario: log unified [autotest] track:name           │
│             [PASS|FAIL] <expectation>: <details>                      │
│       → Runtime.halt(0 or 1)                                          │
│                                                                       │
│   Agent: tail run/logs/latest.log for "[autotest] RESULT:"            │
│          + exit code; fix; re-run.                                    │
└───────────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────────────┐
│   One chicken scenario phase machine (per blueprint):                 │
│                                                                       │
│   RESET_ARENA     → TestArenaPreparer.reset(halfWidth=10)             │
│                     (flatten, kill HelperChickenEntity instances,     │
│                      clear fake-player inventory)                     │
│   PLACE_FLAG      → when !blueprint.placeFlagViaCommand: set flag BE  │
│   FORCE_ROTATION  → when blueprint.forceRotationDetected:             │
│                       write chicken-rotation-detected=true            │
│                             chicken-structure-rotation=<r>            │
│                     else: let HelperChickenRotationDetector tick      │
│   PLACE_SCAFFOLDING → ChickenScaffoldingLayout.forRotation(r) blocks  │
│   TELEPORT_PLAYER → fakePlayer to within 10 of flag (radius gate)     │
│   SPAWN_CHICKEN   → real HelperChickenSpawnController.tick(flag)      │
│   RUN_ACTIONS     → ordered list of scripted actions:                 │
│                       giveWand(fakePlayer)                            │
│                       rightClickWithWandOn(campfirePos)               │
│                       placeBlock(wallOffset, COBBLESTONE)             │
│                       runCommand("/qt flag place_above ...")  (for    │
│                                   placeFlagViaCommand scenarios)      │
│                     advance ticks between actions                     │
│   WARP            → blueprint.warpAmount ticks (0 = skip)             │
│   ASSERT          → per-expectation check:                            │
│                       beat-state reached                              │
│                       flag-BE bits set                                │
│                       entity alive/discarded                          │
│                       STONE_CHICKEN_STATUE present                    │
│                       item-delta (TestResultChecker composed)         │
│                     log [autotest] chicken-arc:<name>                 │
│                         [PASS|FAIL] <expectation>: <details>          │
│   CLEANUP         → TestArenaPreparer.reset(halfWidth=10)             │
└───────────────────────────────────────────────────────────────────────┘
```

---

## Implementation Units

- [ ] U1. **NBT editor — `ChickenScaffoldingNbtEditor` + Gradle task**

**Goal:** Provide a one-shot, idempotent tool that reads `src/main/resources/data/questown/structures/empty_town.nbt`, inserts the chicken-arc scaffolding blocks at the coordinates `HelperChickenBeatOffsets` already encodes, and writes the file back. The developer (or agent) runs it once; the updated `.nbt` is committed.

**Requirements:** R1, R2.

**Dependencies:** U6 (consumes `ChickenScaffoldingLayout.forRotation(Rotation.NONE)`).

**Files:**
- Create: `src/main/java/ca/bradj/questown/devtools/ChickenScaffoldingNbtEditor.java` — core editor logic (idempotent mutation + `--check` + sibling-`.bak` write). Has a `public static void main(String[])` entry point but is primarily invoked via the JUnit harness below.
- Test / entry point: `src/test/java/ca/bradj/questown/devtools/ChickenScaffoldingNbtEditorTest.java` — includes `applyToRealStructure` test (normally `@Disabled`; enabled on demand to run the actual mutation). This is the **primary** invocation path because it inherits the existing `SharedConstants.tryDetectVersion() + Bootstrap.bootStrap()` harness used by `BeatOffsetsRotationTest`, so `Blocks.CAMPFIRE.defaultBlockState()` and `NbtUtils.writeBlockState` resolve reliably inside a ForgeGradle-aware classpath.
- Modify: `build.gradle` — add a `JavaExec` task `editEmptyTownNbt` as a thin **convenience alias** that delegates to `./gradlew test --tests "ChickenScaffoldingNbtEditorTest.applyToRealStructure" -PenableEditor=true`. ForgeGradle classpath and Bootstrap concerns flow through the existing test runtime rather than a hand-rolled `JavaExec` classpath.
- Modify: `docs/conventions/editing-empty-town-nbt.md` — add a section describing the new tool, keeping the Structure-Block round-trip as the documented fallback.

**Approach:**
- Load via `NbtIo.readCompressed(Files.newInputStream(path))`.
- Consume the authored layout from `ChickenScaffoldingLayout.forRotation(Rotation.NONE)` (U6) rather than rebuilding it here. This keeps the committed `.nbt` tag-aligned with what U3's in-world PLACE_SCAFFOLDING phase writes into the arena at scenario time.
- Modes: `--check` compares the authored layout against the current file's blocks and exits 0/1 without writing. `--apply` (or no flag, the default) performs the mutation. Before writing, the editor writes the original file to a sibling `empty_town.nbt.bak` so `git` is not the only rollback mechanism.
- Idempotency: before inserting, scan the existing `blocks` list and remove any entries whose `pos` equals one of the target offsets.
- Palette management: for each block state needed (`minecraft:campfire[lit=false, ...]`, `minecraft:cobblestone`, `minecraft:oak_fence`, and possibly `minecraft:air` if an existing filled position needs to be cleared), look up the palette index; if missing, append a new entry and use its index. Use `NbtUtils.writeBlockState(...)` to author the palette compound so we match Minecraft's exact property serialization.
- Size adjustment: if the new blocks push the structure bounds, expand the root `size` compound's `x`/`y`/`z` accordingly.
- Writeback: `NbtIo.writeCompressed(tag, Files.newOutputStream(path))`.
- Test covers the editor's logic against a small synthetic gzipped NBT — load, mutate, save, re-load, assert blocks are present at the expected offsets and palette references them correctly.
- The test that *applies the editor to the real `empty_town.nbt`* is separate and marked `@Disabled` by default; it's the tool a developer or agent runs once on demand. Running it writes a new `.nbt` which the developer commits.

**Execution note:** Write the editor test (synthetic input) first and iterate against it; apply to the real structure only once the synthetic test passes. This is a characterization-first posture for the NBT format — the synthetic test is what proves we understand the schema.

**Technical design:** *(directional — not implementation specification)*

```
main():
    tag = NbtIo.readCompressed(EMPTY_TOWN_NBT)
    palette = tag.getList("palette", COMPOUND)
    blocks  = tag.getList("blocks",  COMPOUND)

    plan = ChickenScaffoldingLayout.forRotation(Rotation.NONE)  // from U6
           // list of (BlockState, BlockPos)

    for each (state, pos) in plan:
        removeBlocksAt(blocks, pos)
        paletteIdx = ensurePaletteEntry(palette, state)   // reuse or append; entry built via NbtUtils.writeBlockState(state)
        blocks.add(newBlockEntry(paletteIdx, pos))

    expandSizeIfNeeded(tag.getCompound("size"), plan)
    NbtIo.writeCompressed(tag, EMPTY_TOWN_NBT)
```

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/town/entity/TownStateSerializer.java` for `CompoundTag` / `ListTag` iteration idioms.
- `src/main/java/ca/bradj/questown/mc/QTNBT.java` for safe NBT getters.
- `src/test/java/ca/bradj/questown/mobs/helperchicken/BeatOffsetsRotationTest.java`'s `@BeforeAll SharedConstants.tryDetectVersion() + Bootstrap.bootStrap()` pattern — the editor test needs both so `Blocks.CAMPFIRE` and friends resolve.

**Test scenarios:**
- Happy path: editor builds a synthetic NBT with a known palette + blocks, applies the scaffolding plan at well-known offsets, re-loads the result, asserts every planned block is present at its pos with a palette entry matching its state.
- Idempotency: run the editor twice on the same input; re-read both outputs via `NbtIo.readCompressed` and assert `NbtUtils.compareNbt(first, second, true)` returns true. Tag-level equality — not byte-level. Gzip output is not byte-deterministic across JVMs (header mtime field + DEFLATE implementation variation), so byte-equality is unsafe as an assertion.
- Palette-reuse: start with an NBT whose palette already contains `minecraft:cobblestone`; editor reuses the existing index rather than appending.
- Palette-append: start with a palette missing `minecraft:campfire[lit=false]`; editor appends exactly one entry and every inserted campfire block points at it.
- Overwrite existing block at a target offset: existing block at `CAMPFIRE_OFFSET` is replaced, not duplicated; the pre-existing block entry is gone from the final `blocks` list.
- Size expansion: authored offsets extend past the input's `size`; editor widens `size.x/y/z` to enclose the new coordinates.
- Edge case: `Blocks.CAMPFIRE` is null at test init → editor fails fast with a clear message (guards the Bootstrap prerequisite).
- Covers R1, R2.

**Verification:**
- `./gradlew test --tests "ChickenScaffoldingNbtEditorTest"` passes, including the idempotency and palette-reuse tests.
- After running `./gradlew editEmptyTownNbt`, the repo-tracked `empty_town.nbt` has the scaffolding blocks at the authored offsets and `git status` shows exactly one modified file (`src/main/resources/data/questown/structures/empty_town.nbt`), no other collateral.

---

- [ ] U6. **`ChickenScaffoldingLayout` shared utility**

**Goal:** Extract the per-rotation scaffolding block list into a standalone static utility so both U1's NBT editor and U3's in-world PLACE_SCAFFOLDING phase consume the same source of truth. This decouples U3's landing from U1's gradle task being complete.

**Requirements:** R1, R3.

**Dependencies:** None — reads only `HelperChickenBeatOffsets`.

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenScaffoldingLayout.java`
- Test: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenScaffoldingLayoutTest.java`

**Approach:**
- `ChickenScaffoldingLayout.forRotation(Rotation)` returns an ordered `List<BlockPlacement>` where `BlockPlacement` is a `(BlockState, BlockPos)` record relative to the flag origin.
- The `NONE` rotation is the authored base; `CLOCKWISE_90` / `COUNTER_CLOCKWISE_90` / `CLOCKWISE_180` are derived by rotating the offset via `BlockPos.rotate(Rotation)` and rotating directional block properties (e.g., `CampfireBlock.FACING`) where applicable.
- Block states are produced via `Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)`, `Blocks.COBBLESTONE.defaultBlockState()`, `Blocks.OAK_FENCE.defaultBlockState()`.

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenBeatOffsets.java` for offset-constant shape.
- `src/test/java/ca/bradj/questown/mobs/helperchicken/BeatOffsetsRotationTest.java` for the `@BeforeAll SharedConstants.tryDetectVersion() + Bootstrap.bootStrap()` pattern needed to resolve `Blocks.*`.

**Test scenarios:**
- Happy path: `forRotation(Rotation.NONE)` returns the authored layout — campfire at `CAMPFIRE_OFFSET`, cobblestone around the room perimeter minus one wall gap and one door gap, fence columns flanking `GATE_CENTER_OFFSET`.
- Rotation invariance: `forRotation(CLOCKWISE_90).size() == forRotation(NONE).size()` and every rotated block's position equals `NONE_pos.rotate(CLOCKWISE_90)`.
- Directional property rotation: campfire's `FACING` is rotated along with its position.
- Covers R1 (layout matches the authored offsets) and the R3 scaffolding-parity invariant (U1 + U3 produce identical block sets).

**Verification:**
- `./gradlew test --tests "ChickenScaffoldingLayoutTest"` passes.
- U1's editor and U3's PLACE_SCAFFOLDING phase both import this class; no duplicated offset logic exists in either.

---

- [ ] U2. **`ChickenArcBlueprint` + `ChickenArcExpectation` record types**

**Goal:** Define the data shape of a chicken-arc scenario — what actions to script, what to assert, what warp amount to apply — without coupling to `TestBlueprint`'s job-centric fields.

**Requirements:** R3, R4.

**Dependencies:** None — data types only. `ChickenArcBlueprintRegistry` in U4 pulls these together; scenarios themselves also depend on U6.

**Files:**
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcBlueprint.java`
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcExpectation.java`
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcScriptedAction.java` (sealed interface + action subtypes)
- Test: `src/test/java/ca/bradj/questown/commands/test/ChickenArcBlueprintTest.java`

**Approach:**
- `ChickenArcBlueprint` is a record carrying:
  - `name: String` (used in the log output prefix)
  - `category: String` (always `"chicken"` — matches the existing `TestBlueprintRegistry.getTestsByCategory` filter shape)
  - `startRotation: Rotation` (`NONE`, `CLOCKWISE_90`, etc.)
  - `placeFlagViaCommand: boolean` — when true, the executor skips its default `PLACE_FLAG` phase and expects a scripted `RunCommand("/qt flag place_above ...")` action to place the flag. Added for `skip_chicken_command` scenario whose whole point is command-placed flags.
  - `forceRotationDetected: boolean` — when true (the default for happy-path scenarios), the executor writes `chicken-rotation-detected=true` + `chicken-structure-rotation=startRotation` on the flag BE after placement. When false, the executor lets `HelperChickenRotationDetector.tick()` run to completion — used by `rotation_ambiguity_forfeit` and `rotation_zero_match_forfeit`.
  - `scriptedActions: List<ChickenArcScriptedAction>` — executed in order by the executor, with optional per-action tick-wait
  - `expectedFinalBeatState: Optional<ChickenBeatState>`
  - `expectedFlagBits: Map<String, Boolean>` (e.g., `"chicken-first-gather-worldly-seeds-fired" → true`, `"chicken-arc-forfeit" → true`)
  - `expectStatuePlaced: boolean`
  - `expectChickenDiscarded: boolean`
  - `expectChickenSpawned: boolean` — for `skip_chicken_command` (false) and rotation-forfeit scenarios (false)
  - `itemDeltas: Optional<TestExpectation>` — delegated to the existing `TestResultChecker` for item-count assertions. Composition, not duplication.
  - `postActionSettleTicks: int`
  - `warpAmount: int` (0 = no warp)
- `ChickenArcScriptedAction` is a sealed interface with concrete subtypes:
  - `GiveItem(item, count)` — to the fake player (unbound; for non-wand items)
  - `GiveBoundWand(flagPos)` — gives a `TOWN_WAND` stack whose NBT binds it to the given flag via `TownFlagBlock.SetParentOnNBT(stack, flag)`. Required for all wand scenarios: `TownWand.onRightClicked` derives the target flag from the item's NBT (`TownFlagBlock.GetParentFromNBT(level, itemInHand)`) and dereferences the result without a null guard — a plain `GiveItem(WAND)` NPEs. For `wand_on_unlit_campfire_outside_flag`, the wand is bound to a distant flag and clicked on a campfire outside that flag's radius; the no-op is produced by the radius check, not by the absence of binding.
  - `WandRightClick(offset)` — server-side call to `TownWand.onRightClicked` against the currently-held main-hand stack (which the preceding `GiveBoundWand` populates).
  - `PlaceBlock(offset, blockState)`
  - `RegisterDoorViaWand(offset)` — `GiveBoundWand` + `WandRightClick(offset)` composed, with the click targeting a door block.
  - `DepositIntoContainer(offset, itemStack)`
  - `SetUpRegisteredRoomWithChest(roomBounds, chestOffset, doorOffset)` — convenience action that bulk-applies the F3 preamble: places walls, door, sign (auto-converts to job board), chest, and wand-registers the door. Used by `F4_seeds_to_statue` and the two `first_gather_worldly_seeds_*` scenarios so they don't replay 14+ F3 steps inline. F3's own scenario (`F3_build_room_to_welcome_mat`) still performs the fine-grained step sequence since verifying that flow is the scenario's purpose.
  - `MarkVillagerUiOpened` / `MarkFlagUiOpened` — call the static observation helpers
  - `AdvanceTicks(n)`
  - `RightClickChickenWithHand(item)` — drives `HelperChickenEntity.interactAt(...)`
  - `RunCommand(cmd)` — for `/questown chicken remove <pos>` and similar. Dispatched via `fakePlayer.createCommandSourceStack()` (not `server.createCommandSourceStack()`) so `source.getPlayer()` is non-null — `FlagCommand.setBlock` calls `APPROACH_TOWN_TRIGGER.trigger(source.getPlayer(), ...)` which dereferences the player.
- `ChickenArcExpectation` is the checker's contract — takes a snapshot before/after, reports per-assertion pass/fail lines via the same `TestOutput` interface `TestResultChecker` uses.

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/commands/test/TestBlueprint.java` for record shape.
- `src/main/java/ca/bradj/questown/commands/test/TestExpectation.java` for the `ExpectedProduct` sub-record.
- `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenBeatState.java` + `HelperChickenBeatOffsets.java` for the enum + offset types the blueprint references.
- Sealed-interface pattern: any existing sealed hierarchies in `src/main/java/ca/bradj/questown/`? If none, establish it here with a short convention note.

**Test scenarios:**
- Happy path: blueprint construction with a minimal scripted-action list round-trips — fields are exposed, no hidden nulls.
- Edge case: blueprint with `warpAmount=0` and no scripted actions is valid (reads as "spawn chicken, advance settle ticks, assert beat is WAITING_FOR_STICK").
- Edge case: scripted actions are immutable once passed to the blueprint (record defensive-copy behaviour).
- Happy path: every `ChickenArcScriptedAction` subtype is exhaustively covered by a `switch` in `ChickenArcTestExecutor` — a compile-time test (sealed interface exhaustiveness) plus a runtime test that every known subtype has a non-null handler.

**Verification:**
- Blueprint records serialize/deserialize idempotently for test fixtures.
- Every subtype of `ChickenArcScriptedAction` is reachable in at least one blueprint in U4's registry.

---

- [ ] U3. **`ChickenArcTestExecutor` + phase machine**

**Goal:** Execute one `ChickenArcBlueprint` end-to-end inside `AutoTestRunner`'s tick loop: reset arena, place flag, force rotation-detected state, place scaffolding, spawn chicken, run scripted actions with tick-settles between them, advance warp if requested, evaluate expectations, log per-assertion pass/fail, clean up.

**Requirements:** R3, R4, R6.

**Dependencies:** U2, U6.

**Files:**
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcTestExecutor.java`
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcResultChecker.java` (composes `TestResultChecker` for item-delta assertions; adds per-assertion reporters for beat state, flag bits, entity existence, statue placement)
- Create: `src/main/java/ca/bradj/questown/commands/test/TestArenaPreparer.java` — shared between `TestExecutor` and `ChickenArcTestExecutor`. Configurable `PreparerOptions` record: `halfWidth`, `killHelperChickens: boolean`, `clearFakePlayerInventory: boolean`, `clearRotationDetectorRetryCounters: boolean`. All chicken-only behaviors are opt-in so jobs-track semantics are genuinely preserved per R6 (no "harmless by assumption").
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java` — compose `TestArenaPreparer` with `PreparerOptions(halfWidth=7, killHelperChickens=false, clearFakePlayerInventory=false, clearRotationDetectorRetryCounters=false)` for its existing `DESTROY_NEARBY_FLAGS` / `FLATTEN` / `SETTLE_BEFORE_PLACE` / `PLACE_FLAG` / `waitForInit` phases. Zero behavioral change for jobs tests. `ChickenArcTestExecutor` passes `PreparerOptions(halfWidth=10 by default, killHelperChickens=true, clearFakePlayerInventory=true, clearRotationDetectorRetryCounters=true)`; per-scenario overrides (e.g. `halfWidth=25` for `two_flags_independent_arcs`) flow through the same record.
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestResultChecker.java` — if needed, extract the per-assertion reporter format so `ChickenArcResultChecker` can compose it. The unified log shape `[autotest] <track>:<name> [PASS|FAIL] <expectation>: <details>` lands here; existing job-track callers in `TestExecutor.broadcastResult` update to match.
- Test: `src/test/java/ca/bradj/questown/commands/test/ChickenArcTestExecutorTest.java` (pure-function coverage of per-action handler dispatch and per-assertion reporter format)
- Test: `src/test/java/ca/bradj/questown/commands/test/TestArenaPreparerTest.java` (half-width configurability, kill-sweep covers `HelperChickenEntity`, inventory clear)

**Approach:**
- Phase order (reusing `TestArenaPreparer` where it maps cleanly):
  1. `RESET_ARENA` — `TestArenaPreparer.reset(PreparerOptions(halfWidth=10, killHelperChickens=true, clearFakePlayerInventory=true, clearRotationDetectorRetryCounters=true))`: flatten, destroy prior flags, kill `HelperChickenEntity` instances, clear fake-player inventory, clear `HelperChickenRotationDetector.RETRY_COUNTERS`. Per-scenario override: `two_flags_independent_arcs` passes `halfWidth=25`.
  2. `PLACE_FLAG` — set the flag block at origin and wait for `TownFlagBlockEntity` init. **Skipped entirely when `blueprint.placeFlagViaCommand = true`** — that scenario's scripted `RunCommand` action places the flag via `/qt flag place_above`.
  3. `FORCE_ROTATION_STATE` — when `blueprint.forceRotationDetected = true` (happy-path default), write `chicken-structure-rotation = blueprint.startRotation`, `chicken-rotation-detected = true`, `chicken-arc-forfeit = false`, `chicken-ever-spawned = false` via `writeTownData`. When false, skip this phase so the real `HelperChickenRotationDetector.tick()` runs.
  4. `PLACE_SCAFFOLDING` — consume `ChickenScaffoldingLayout.forRotation(startRotation)` from U6 and `level.setBlockAndUpdate(flagPos.offset(pos), state)` for each placement. For the rotation-detector forfeit scenarios, this phase places real campfire blocks at the relevant offsets so the detector has real anchors to find (or deliberately does not, for the `rotation_zero_match_forfeit` case).
  5. `TELEPORT_PLAYER_NEAR_FLAG` — teleport the fake player to within 10 blocks of `flagPos` so `HelperChickenSpawnController`'s radius gate passes naturally.
  6. `SPAWN_CHICKEN` — call the real `HelperChickenSpawnController.tick(flag)` (no bypass). Advances ticks until either the chicken spawns or the scenario's expected non-spawn outcome is realized (for `skip_chicken_command` and rotation-forfeit scenarios).
  7. `RUN_ACTIONS_LOOP` — iterate the scripted-action list; for each action, dispatch to its handler, advance N tick-settles, continue.
  8. `WARP` (optional) — call `flag.warpTime(blueprint.warpAmount)` if non-zero.
  9. `SETTLE_AFTER_WARP` — advance `postActionSettleTicks` ticks.
  10. `CHECK_RESULTS` — `ChickenArcResultChecker.check(...)` reads flag BE state, entity list, container inventories, block states at the statue candidate position; delegates item-delta assertions to `TestResultChecker`; reports per-assertion [PASS] / [FAIL] lines in the unified log format.
  11. `CLEANUP` — delegate to `TestArenaPreparer.reset(...)` again so the next scenario starts clean.
- Each action handler is a small method that calls the real server-side entry point:
  - `GiveItem` → `fakePlayer.getInventory().add(new ItemStack(item, count))`
  - `WandRightClick(offset)` → `TownWand.onRightClicked(() -> fakePlayer, level, flagPos.offset(offset.rotate(rotation)), fakePlayer.getMainHandItem())`
  - `PlaceBlock(offset, state)` → `level.setBlockAndUpdate(flagPos.offset(offset.rotate(rotation)), state)`
  - `RegisterDoorViaWand(offset)` → same as `WandRightClick(offset)` but on a door position
  - `DepositIntoContainer(offset, stack)` → resolve the container via `TownContainers.getAllContainers(flag, level)` and insert; reuses the same path the real first-gatherer deposit uses
  - `MarkVillagerUiOpened` / `MarkFlagUiOpened` → direct call to `ChickenArcUiObservations.*`
  - `RightClickChickenWithHand(item)` → find the chicken entity, stuff `item` into the fake player's main hand, call `chicken.interactAt(fakePlayer, Vec3.ZERO, InteractionHand.MAIN_HAND)`
  - `RunCommand(cmd)` → `server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd)`
  - `AdvanceTicks(n)` → yield control and resume after n ticks (phase machine handles this — the executor returns `false` from `tick()` while the counter is non-zero)
- `ChickenArcResultChecker` reports each expectation on its own log line so the agent sees *which* assertion failed, not just a composite pass/fail.
- Log format: `[autotest] <track>:<name> [PASS|FAIL] <expectation>: <details>` — unified across jobs and chicken-arc tracks (per Key Technical Decisions). Chicken scenarios use `<track>=chicken-arc`; retrofitted jobs emit `<track>=jobs`.

**Execution note:** The per-action handlers are thin wrappers over existing server-side entry points. Build them test-first against fakes where possible, and use one full end-to-end scenario as an integration check rather than mocking the whole executor.

**Technical design:** *(directional — not implementation specification)*

```
ChickenArcTestExecutor.tick():
    switch (phase):
      RESET_ARENA:          TestArenaPreparer.reset(halfWidth=10)
                            -> advance -> (blueprint.placeFlagViaCommand ? RUN_ACTIONS_LOOP : PLACE_FLAG)
      PLACE_FLAG:           setFlagBlock() -> waitForInit -> FORCE_ROTATION_STATE
      FORCE_ROTATION_STATE: if blueprint.forceRotationDetected:
                                writeBits(flag, blueprint.startRotation)
                            -> PLACE_SCAFFOLDING
      PLACE_SCAFFOLDING:    for (state,pos) in ChickenScaffoldingLayout.forRotation(startRotation):
                                level.setBlockAndUpdate(flagPos.offset(pos), state)
                            -> TELEPORT_PLAYER_NEAR_FLAG
      TELEPORT_PLAYER:      fakePlayer.teleportTo(flagPos.within(10))
                            -> SPAWN_CHICKEN
      SPAWN_CHICKEN:        HelperChickenSpawnController.tick(flag)   // real gates
                            -> settle -> RUN_ACTIONS_LOOP
      RUN_ACTIONS_LOOP:     if no actions left: -> WARP
                            else: handler.dispatch(actions.next()); settleTicks = action.wait;
                                  while settleTicks > 0: return false; settleTicks--
      WARP:                 if blueprint.warpAmount > 0: flag.warpTime(warpAmount)
                            -> SETTLE_AFTER_WARP
      SETTLE_AFTER_WARP:    advance(blueprint.postActionSettleTicks) -> CHECK_RESULTS
      CHECK_RESULTS:        resultChecker.check(flag, blueprint); logPerAssertion()
                            -> CLEANUP
      CLEANUP:              TestArenaPreparer.reset(halfWidth=10); done=true; return true
```

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java` — entire phase-machine shape, including the tick-counter-waiting idiom.
- `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java` — fake-player + force-load integration points.
- `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java` — the system under test; read it to confirm which BE bits to check.

**Test scenarios:**
- Happy path: one simple blueprint with a single `GiveItem` + `AdvanceTicks` + `CHECK_RESULTS` is driven through the full phase machine against a fake `TestOutput` and a mocked-but-realistic flag BE proxy, confirming phase transitions fire in order.
- Edge case: action handler for an unknown `ChickenArcScriptedAction` subtype logs `[FAIL] unhandled action: <class>` rather than throwing.
- Edge case: `SPAWN_CHICKEN` fails (e.g., flag BE gone after `PLACE_FLAG`) — executor logs a clear [FAIL] and advances to CLEANUP without NPE.
- Error path: scenario blueprint lists an action whose offset resolves outside the force-loaded chunk — executor warns rather than silently succeeding, so the agent can detect the force-load region is too small.
- Integration: full F1 scenario (`GiveItem(WAND)` → `WandRightClick(CAMPFIRE_OFFSET)` → `AdvanceTicks(10)` → assert beat-state `SUNSET_AND_MAP`) runs end-to-end in a real `AutoTestRunner` boot. The scenario-level AE claim (`Covers AE4.`) lives on the matching U4 blueprint's test bullet, not here — this unit's coverage is the executor machinery, not the origin behavior.

**Verification:**
- Executor's test suite passes with full phase coverage.
- A single hand-written F1 blueprint loaded into a one-off gradle run (`./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=chicken -PsingleScenario=F1_stick_to_campfire`) completes with `[PASS]` in `run/logs/latest.log`.

---

- [ ] U4. **`ChickenArcBlueprintRegistry` + scenarios + AutoTestRunner integration**

**Goal:** Register the actual scenarios — F1, F3, F4, skip-chicken, `/questown chicken remove`, rotation-CLOCKWISE_90, realtime+warp Worldly-Seeds guarantee — and wire them into `AutoTestRunner` so they run under the same `-Dquestown.autotest=true` invocation as the existing job tests.

**Requirements:** R3, R4, R5, R6.

**Dependencies:** U2, U3.

**Files:**
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcBlueprintRegistry.java`
- Create: `src/main/java/ca/bradj/questown/commands/test/ChickenArcAllExecutor.java` (parallel to `TestAllExecutor`; iterates the registry and delegates each entry to a fresh `ChickenArcTestExecutor`)
- Modify: `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java` — chain the chicken executor before `server.halt(...)` fires. Current listener calls `server.halt` the moment `executor.tick()` returns true (`AutoTestRunner.java:106–123`); extend to: run `TestAllExecutor` to completion, then run `ChickenArcAllExecutor` to completion, then aggregate + `Runtime.halt(0 or 1)`.
- Modify: `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java` — emit observability probes so the agent can distinguish "suite truncated mid-run" from "suite ran to completion with failures". At `onServerStarted`, emit `[autotest] BOOT_OK`. Before each track begins, emit `[autotest] jobs:SUITE_START` and later `[autotest] chicken-arc:SUITE_START`. The agent's U5 runbook uses these: `BOOT_OK` absent → boot failure; `BOOT_OK` present but `chicken-arc:SUITE_START` absent → jobs-track crashed before chicken ran; either `SUITE_START` present but no matching track `RESULT:` → that track was truncated mid-run.
- Modify: `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java` — extend the existing `QUESTOWN_AUTOTEST_CATEGORY` env-var routing (already read at `AutoTestRunner.java:26` and plumbed through `TestAllExecutor` at `.java:46–49`). Category `"chicken"` selects only `ChickenArcAllExecutor`; the existing `"warp"`, `"eating"`, `"worldgen"` categories continue to select jobs. Unset means "run both tracks". No new env-var mechanism is introduced — `ChickenArcBlueprint.category = "chicken"` plus `TestBlueprintRegistry.getTestsByCategory(String)`-style filtering on the chicken registry is all that's required.
- Modify: `src/main/java/ca/bradj/questown/commands/TestAllCommand.java` (the `/_qtdev testall` command) — optionally extend to run chicken scenarios too (decision deferred to implementation — may be cleaner to leave `/_qtdev` chickens behind a separate command, e.g., `/_qtdev chicken_all`).
- Test: `src/test/java/ca/bradj/questown/commands/test/ChickenArcBlueprintRegistryTest.java`

**Approach:**
- Scenarios to register (13 total):
  1. `stick_peck_and_follow_spawn` — spawns the chicken, settles ticks, asserts the chicken is alive, has `stickIcon` on its `SynchedEntityData`, and its peck goal targets the flag. *Covers AE1* (initial spawn + stick-peck bubble).
  2. `F1_stick_to_campfire` — stick beat → wand conversion → wand-on-unlit-campfire → campfire-lit (R14 branch). Asserts final beat state `SUNSET_AND_MAP`. *Covers AE4* (wand lights adjacent-to-flag campfire).
  3. `F3_build_room_to_welcome_mat` — R7 + R7b (out-of-order accepted): place wall block, place chest *before* sign, place door, wand-on-door, place sign (auto-converts to job board), place welcome mat. Asserts final beat state `WAITING_FOR_VILLAGER_UI` and all F3 flag bits set. *No origin AE.*
  4. `F4_seeds_to_statue` — mark villager UI opened, mark flag UI opened, deposit Worldly Seeds into a container, right-click chicken with seeds. Asserts statue placed, chicken discarded, beat state `COMPLETE`. *Covers AE5* (seeds → statue handoff).
  5. `F1_rotation_clockwise_90` — F1 with `startRotation = CLOCKWISE_90` to exercise the rotated-offset path in `HelperChickenBeatPeckGoal.resolveTarget`. *No origin AE* (implementation-detail coverage of rotation-aware offsets in the happy path).
  6. `rotation_ambiguity_forfeit` — `forceRotationDetected = false`. Places real campfire blocks at two rotated `CAMPFIRE_OFFSET` candidates so `HelperChickenRotationDetector.tick()` finds >1 match. Asserts `chicken-arc-forfeit = true`, no chicken spawns. *No origin AE* (exercises a product-critical forfeit branch added in deepening).
  7. `rotation_zero_match_forfeit` — `forceRotationDetected = false`. Places no campfire anywhere. Advances ≥`MAX_RETRY_TICKS`+5 ticks. Asserts `chicken-arc-forfeit = true` via timeout, no chicken spawns. *No origin AE.*
  8. `two_flags_independent_arcs` — places two flags ≥20 blocks apart. Passes `halfWidth=25` to the preparer so both flag positions are flattened, and calls `overworld.setChunkForced` on flag B's chunk during its `PLACE_FLAG` phase (flag B is outside the origin chunk; without force-load its BE would never tick and the scenario would produce a trivially-passing "stayed at WAITING_FOR_STICK" result). Spawns a chicken at each flag, advances F1 on flag A only. Asserts flag A reaches `SUNSET_AND_MAP`, flag B remains at `WAITING_FOR_STICK`, `flagB.getTickCount() > 0` (distinguishes genuine independence from chunk-unload staleness), and each flag BE carries independent `chicken-*` bits. *Covers AE6* (per-flag independence).
  9. `skip_chicken_command` — `placeFlagViaCommand = true`. Scripted action runs `/qt flag place_above <pos> skip-chicken` in place of the default `PLACE_FLAG` phase; advances ticks. Asserts no `HelperChickenEntity` spawns and `chicken-ever-spawned = true`. *Covers AE7.*
  10. `forfeit_remove_command` — spawn chicken, run `/questown chicken remove <pos>`. Asserts chicken discarded + `chicken-arc-forfeit = true` + no statue. *No origin AE* (R23 has no AE).
  11. `first_gather_worldly_seeds_realtime` — spawn villager-gatherer in a ready town, no warp, advance real ticks until first gather lands. Asserts first-gatherer's loot list included `WORLDLY_SEEDS` and `chicken-first-gather-worldly-seeds-fired = true`. *Covers AE5* (guarantee half).
  12. `first_gather_worldly_seeds_warp` — same setup; uses `warpAmount` (seeded from gatherer blueprint's existing warp) to advance a day's worth of gatherer cycles. Asserts seed-guarantee parity with the realtime path. *No origin AE prefix* (scenario 11 claims AE5; this one covers R15 realtime/warp parity).
  13. `wand_on_unlit_campfire_outside_flag` — places a campfire far from any flag; wand click is a no-op. *Covers AE4* (no-op clause).
- `ChickenArcAllExecutor` logs the unified pattern: per-scenario `[autotest] chicken-arc:<name> [PASS|FAIL] ...` lines, end-of-run totals, e.g., `[autotest] chicken-arc:SUITE [PASS|FAIL] Passed: 13/13 (100%)`. `AutoTestRunner` combines job + chicken totals into the final `RESULT:` line.
- Category filtering reuses the existing `QUESTOWN_AUTOTEST_CATEGORY` routing (see Files section above).

**Patterns to follow:**
- `src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java` for registry shape.
- `src/main/java/ca/bradj/questown/commands/test/TestAllExecutor.java` for the batch-runner pattern (tick delegation, result accumulation, `printSummary()`).
- `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java`'s `reportAndShutdown` flow.

**Test scenarios:**
- Happy path: registry contains exactly the 13 scenarios above; every name is unique; every scenario's `expectedFinalBeatState` (when set) is a valid `ChickenBeatState`; every scenario's `category` is `"chicken"`.
- Edge case: `QUESTOWN_AUTOTEST_CATEGORY=chicken` + `-Dquestown.autotest=true` runs *only* chicken scenarios; job scenarios are skipped; exit code reflects chicken-only results.
- Edge case: `QUESTOWN_AUTOTEST_CATEGORY=jobs` (or any existing job-category value like `warp`) preserves the existing behavior unchanged. Verified by **parity-on-membership**: capture the set of `(scenario_name, PASS|FAIL)` tuples from a pre-refactor autotest run and from a post-refactor run with the same category filter, and assert the sets are equal. Raw log text will differ by design (the unified `[autotest] jobs:<name>` format replaces `[autotest] [PASS] WARP: ...`), so text-diff is not a valid check — membership-diff is.
- Integration: one full `./gradlew runServer -Dquestown.autotest=true` boot runs both executors sequentially (chicken after jobs), reports pass/fail for both track sets, and exits with the combined code.
- Per-scenario AE coverage is declared on the scenario bullets above, not here, per the ce-plan convention.

**Verification:**
- `./gradlew runServer -Dquestown.autotest=true` completes within ~8 minutes, writes a `RESULT: X/Y passed` line to `run/logs/latest.log`, and exits with `0` when all scenarios pass.
- `./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=jobs` produces output identical to pre-plan runs (existing job tests unchanged).
- Breaking any helper-chicken file (e.g., flipping a beat-state transition condition) causes exactly the scenarios that exercise that transition to fail with a clear `[FAIL]` line.

---

- [ ] U5. **Agent runbook + log-parsing contract**

**Goal:** Give the agent a single doc that says "here is how you run the verification loop" — the exact command, the log-parse regex, the exit-code semantics, known failure modes, and what to do when a scenario fails. Remove the remaining human-verification TODOs from the chicken-arc test files.

**Requirements:** R5.

**Dependencies:** U4.

**Files:**
- Create: `docs/conventions/agent-chicken-verification-loop.md`
- Modify: `docs/conventions/editing-empty-town-nbt.md` — add a one-line pointer to the new runbook.
- Modify: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcControllerTest.java` — the two `TODO_` tests become standard tests that invoke the new scenarios *if* they can be reached from a JUnit environment, or stay as pointers to the agent-loop runbook if they cannot. Decide at implementation time.
- Modify: `src/test/java/ca/bradj/questown/mobs/helperchicken/CurriculumSequenceTest.java` — same treatment.
- Modify: `src/test/java/ca/bradj/questown/items/WandLightCampfireTest.java` — same treatment.
- Modify: `src/test/java/ca/bradj/questown/jobs/gatherer/FirstGatherWorldlySeedsTest.java` — same treatment.

**Approach:**
- Runbook content: launch command, log path, grep pattern for per-scenario `[PASS]`/`[FAIL]` lines, grep pattern for the final aggregate `RESULT:` line, exit-code semantics, pitfalls (server startup time, chunk force-load, log level `.info()` vs `.debug()`), troubleshooting (how to re-run a single scenario, how to keep the world directory around for post-mortem, how to enable DEBUG logging).
- **Missing-output triage flow** (included verbatim in the runbook so the agent does not hand back to a human on recoverable failures): (1) if `[autotest] BOOT_OK` is absent, the server failed to start — check `run/logs/latest.log` for `java.net.BindException: Address already in use` (port 25565 held by a zombie JVM), `session.lock` held in `run/world/`, or Gradle daemon hangs; propose killing stale processes and re-running. (2) if `BOOT_OK` is present but `[autotest] chicken-arc:SUITE_START` is absent, the jobs track crashed before chicken began — inspect the last jobs-track `[PASS|FAIL]` line and the stack trace that follows. (3) if a `SUITE_START` is present but no matching track `RESULT:` line exists, that track was truncated mid-run — look for an `OutOfMemoryError` or uncaught exception near the last per-scenario line. Each branch resolves to either "retry after fixing environment" (1) or "fix code + re-run" (2, 3) — no case asks the user to run the dev client.
- The runbook is written for an agent to consume first and a human to consume second — keep it under ~300 lines and use concrete grep strings.
- For the existing `TODO_` tests: they document what the test was going to prove if a harness existed. Now that `ChickenArcAllExecutor` is that harness, some of those TODOs collapse into "see agent runbook" pointers. Others (e.g., pure transition tests already covered by `ChickenArcTransitionsTest`) can be outright removed.

**Test scenarios:**
- Runbook includes the exact `./gradlew runServer -Dquestown.autotest=true` invocation that a fresh-clone agent can copy-paste.
- Runbook includes an example of a passing `RESULT:` log line and an example of a failing one.
- Runbook names each of the 13 scenarios and cross-links to the U4 registry.
- Every `TODO_*` test that the new harness makes executable has been updated or removed; any remaining `TODO_*` is there because the test is genuinely out of harness scope (e.g., renderer pixels).

**Verification:**
- A new agent clone can follow the runbook from scratch and produce a green run.
- `grep -rn 'TODO_' src/test/java/ca/bradj/questown/mobs/helperchicken/` returns only the items explicitly out of scope, each with a comment pointing to why.

---

## System-Wide Impact

- **Interaction graph:**
  - `AutoTestRunner` gains a second executor track — job + chicken. Both share the same fake player + force-load. The tick listener now chains the two executors sequentially (jobs → chicken) before calling `server.halt(...)`, so the current halt-on-first-completion path becomes "halt when both tracks are done".
  - No `forceSpawn` or other test-only bypasses are added to `HelperChickenSpawnController`. Scenarios satisfy the spawn controller's radius gate by teleporting the fake player near the flag, then invoking the real `tick(flag)`. The production spawn invariants are unchanged.
  - `TestArenaPreparer` is new shared infrastructure parameterized by a `PreparerOptions` record. `TestExecutor` passes the zero-behavior-change defaults (width 7; all chicken-only flags false) so R6 parity is preserved by construction. `ChickenArcTestExecutor` opts into wider arenas, chicken kill-sweep, fake-player inventory clear, and rotation-detector retry-counter clear.
  - `TownWand.onRightClicked`, `CampfireSleepHandler.beginCampfireSleep`, `ChickenArcUiObservations.*`, `/questown chicken remove` — all called from scenarios as they are, no shims, no changes.
- **Error propagation:**
  - Any scenario exception is caught inside `ChickenArcTestExecutor.tick()` and reported as a `[FAIL]` line; the executor advances to CLEANUP so the next scenario still runs. One bad scenario cannot crash the whole suite.
  - `AutoTestRunner`'s `Runtime.halt()` is only called after all scenarios finish — a scenario timeout is its own `[FAIL]`, not a process kill.
- **State lifecycle risks:**
  - Scaffolding blocks placed in one scenario must be cleaned in CLEANUP; otherwise later scenarios start from a dirty arena. The existing `DESTROY_NEARBY_FLAGS` levels a 15×15 area — confirm during U3 that the chicken arena fits.
  - Fake player's inventory persists across scenarios; the executor clears it at RESET_ARENA.
  - `STONE_CHICKEN_STATUE` after F4 is at the chicken's final position; DESTROY_NEARBY_FLAGS levels it along with everything else — no special cleanup.
- **API surface parity:**
  - `/_qtdev test <job>` continues to work for jobs.
  - New `/_qtdev chicken <scenario>` command (optional, deferred to U4 implementation) would let a human trigger one scenario in a dev client for debugging. Not required by the agent loop, but useful escape hatch.
- **Integration coverage:**
  - The scenarios *are* the integration coverage for U4 + U6 + U7 of the parent plan. They drive the real controller, the real state machine, the real conditions observer, the real statue handler, the real loot wrapper.
- **Unchanged invariants:**
  - Existing `/_qtdev testall` behavior when `QUESTOWN_AUTOTEST_CATEGORY=jobs` (or the env var is unset and the user wants only jobs via a new `-DQUESTOWN_AUTOTEST_CATEGORY=jobs` override) is unchanged.
  - `empty_town.nbt`'s jigsaw placement, biome filter, and template pool remain untouched — U1 only mutates the internal `blocks` / `palette` / `size` tags.
  - The Structure-Block round-trip documented in `docs/conventions/editing-empty-town-nbt.md` remains a valid fallback; U1 adds an alternative, not a replacement.
  - `HelperChickenBeatOffsets` constant values are read, never written.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| The NBT format has a subtlety (e.g., palette-index order dependencies, size-bounding-box semantics) that the editor gets wrong and corrupts `empty_town.nbt` | U1 is built characterization-first against synthetic NBT before touching the real file. The synthetic tests assert tag-level idempotency and round-trip via `NbtUtils.compareNbt(a, b, true)` (not byte-level — gzip output is not deterministic across JVMs). The real file is committed only after a manual `/locate structure questown:empty_town` confirms worldgen still places the structure correctly (one-time human check in a dev client, then the agent never touches NBT again). |
| Chicken scaffolding extends past the existing 15×15 `TestExecutor.flatten` radius (rotated placements at `(2,0,9)` exceed the 7-block half-width), leaking blocks between scenarios | `TestArenaPreparer` exposes a configurable half-width; chicken scenarios pass ≥10. Test `TestArenaPreparerTest` asserts half-width coverage reaches rotated scaffolding bounds. |
| Sequential executor chaining means a failing job scenario could block chicken scenarios from running | Per-scenario exceptions are caught and reported as `[FAIL]`; the suite continues. `server.halt(...)` only fires after both track sets complete. A hard process crash in jobs would still kill chicken, but that's identical to today's behavior for jobs themselves. |
| Scenarios depend on tick counts that vary with server load, causing flakes | `postActionSettleTicks` is generous by default (60 ticks = 3 seconds) and per-scenario overridable. Scenarios that depend on warp use explicit `warpAmount` rather than real-time waits. If real-time flake surfaces during U4, bump the settle counts — the existing job tests faced the same issue and resolved it the same way. |
| Running chicken scenarios after job scenarios leaves dirty world state | Each scenario runs its own RESET_ARENA at start and end. Verified by running scenarios back-to-back with deliberately dirty arenas in U3 tests. |
| `./gradlew runServer -Dquestown.autotest=true` takes 5+ minutes per iteration, making the agent loop slow | Acceptable for v1. Category filter (`QUESTOWN_AUTOTEST_CATEGORY=chicken`) narrows to ~1-2 minutes for chicken-only runs. Single-scenario mode (implementation detail in U4) narrows further for debugging. |
| `F4_seeds_to_statue` depends on a registered room with a chest (per the `MEMORY.md` container-scan rule), which adds a lot of F3 setup to F4 | F4's scripted actions include the F3 preamble (place walls, door, sign, etc.) so the test preconditions are satisfied. Alternatively, introduce a `SetUpRegisteredRoomWithChest` convenience action that bulk-applies the F3 steps — decision during U3. |
| `AutoTestRunner`'s fake player can't sleep, blocking F2 (sunset + sleep) verification | F2 is explicitly deferred in Scope Boundaries. F1/F3/F4 do not require sleep — the sleep observation only gates SUNSET_AND_MAP → WAITING_FOR_WALL_BLOCK, which F3 skips by directly setting `flag.setChickenObservedSleepSinceSunset(true)` via a new scripted action type. |
| Forge mod-registered types (ItemsInit.WORLDLY_SEEDS, BlocksInit.STONE_CHICKEN_STATUE) are null in unit tests (same issue the parent plan's TODO_ tests hit), breaking U2 + U3 test scenarios | U2/U3 tests stay at the contract level (shape, dispatch, format) where mod-registered items are not required. Full item-resolving coverage lives in the `AutoTestRunner` loop itself, not JUnit. |
| Editor consumes `BlockState` objects that depend on Bootstrap-initialized registries, which may not be available in a bare `JavaExec` classpath | The primary invocation path is the JUnit harness (`applyToRealStructure` test) which inherits the existing SharedConstants + Bootstrap setup. The `editEmptyTownNbt` Gradle task is a convenience alias that delegates to the same test — no independent ForgeGradle classpath to manage. |

---

## Documentation / Operational Notes

- `docs/conventions/editing-empty-town-nbt.md` adds a section "Agent-authored path" pointing at U1's gradle task.
- `docs/conventions/agent-chicken-verification-loop.md` is the new runbook (U5).
- `MEMORY.md` may want a note that `/_qtdev` coverage now includes chicken scenarios, and that the category filter is `chicken`. Deferred to the user — not a plan responsibility.
- No CI wiring in scope. If CI is added later, the agent loop's single `./gradlew runServer -Dquestown.autotest=true` command + exit code is already CI-ready.

---

## Sources & References

- **Parent plan (origin):** `docs/plans/2026-04-22-001-feat-helper-chicken-onboarding-plan.md`
- **Parent plan's origin brainstorm:** `docs/brainstorms/2026-04-22-helper-chicken-onboarding-requirements.md`
- **Existing convention doc:** `docs/conventions/editing-empty-town-nbt.md`
- **Key code surfaces referenced in Context & Research:**
  - `src/main/java/ca/bradj/questown/commands/test/AutoTestRunner.java`
  - `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java`
  - `src/main/java/ca/bradj/questown/commands/test/TestAllExecutor.java`
  - `src/main/java/ca/bradj/questown/commands/test/TestBlueprint.java`
  - `src/main/java/ca/bradj/questown/commands/test/TestExpectation.java`
  - `src/main/java/ca/bradj/questown/commands/test/TestResultChecker.java`
  - `src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java`
  - `src/main/java/ca/bradj/questown/commands/test/LogTestOutput.java`
  - `src/main/java/ca/bradj/questown/QT.java`
  - `src/main/java/ca/bradj/questown/mobs/helperchicken/*` (controller, conditions, bubbles, transitions, loot guarantee, UI observations, statue handler, beat offsets)
  - `src/main/java/ca/bradj/questown/town/HelperChickenSpawnController.java`
  - `src/main/java/ca/bradj/questown/town/HelperChickenRotationDetector.java`
  - `src/main/resources/data/questown/structures/empty_town.nbt`
- **External references:**
  - Minecraft Structure Block file format (community wiki, 1.19.2 schema)
  - Forge 1.19.2 `net.minecraft.nbt.NbtIo` and `net.minecraft.nbt.NbtUtils.writeBlockState(BlockState)`
