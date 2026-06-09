---
title: "feat: Organizer/fetch autotest — realtime baseline + known-red warp spec"
type: feat
status: active
date: 2026-06-08
---

# feat: Organizer/fetch autotest — realtime baseline + known-red warp spec

## Overview

Use the in-game autotest harness to lock down the `organizer/fetch` job in two phases:

1. **Phase A — realtime baseline (ship green):** prove the existing realtime fetch
   behavior works and pin it as a regression guard before anyone touches it.
2. **Phase B — warp gap (ship known-red xfail):** an executable spec documenting
   exactly how the job fails under time-warp, wired so it cannot silently rot.

Building the actual warp relocation model is **out of scope** here — autotest can
*expose* the gap but cannot *shrink* it. That is a separate follow-up track.

This plan is build-ready for a future session. All design decisions are settled
(captured in Key Technical Decisions). Branch context:
`1.19.2-0.0.10-alpha.3-unstable` — un-merged, so a standing-red xfail is acceptable.

---

## Problem Frame

`organizer/fetch` is the only organizer job. It is **archived** (lives in
`src/main/resources/data/questown/questown_job_archive/organizer_fetcher.json`, a
folder no Java loads — `ResourceJobLoader` scans only `questown_jobs/`), so it does
not exist at runtime today. It was archived because it has **no time-warp support**.

Its realtime behavior is bespoke and hard-coded: `FetcherHack` (deprecated, reads
live containers via `town.getServerLevel()`) + `WorkSpotFromHeldItemSpecialRule` +
villager **pathfinding** between chests. `FetcherHack` is invoked only from
`DeclarativeJob`'s realtime entity-driving methods (`setupForGetSupplies`,
`getDropTargetForLoot`, `getItemsForDrop`).

Two structural facts make this tricky to test:
- **Warp has no fetch model.** `TimeWarpWorldInteraction` never calls `FetcherHack`
  or those methods. Its loop drives a generic state machine ("does *any* container
  have the ingredient/tool for this state? → advance → `add_item_to_container` into
  *some* non-full container"). No source/target routing, no `StockRequestItem` NBT
  target, no relocation concept. Warped, the job misroutes / no-ops.
- **The existing oracle can't see a fetch.** `TestResultChecker.snapshotItemCounts`
  flattens all villagers + all containers into one town-wide `Map<String,Integer>`;
  `ExpectedProduct` asserts the *delta* of those totals. A fetch **relocates** an
  item (town-wide delta ≈ 0), so the existing oracle cannot distinguish success from
  a no-op. This applies to **both** the realtime path (`checkRealtimeResults` →
  `checkDeltas`) and the warp path (`checkResults` → `check`).

---

## Requirements Trace

- R1. An autotest verifies `organizer/fetch` realtime behavior green: the requested
  ingredient is relocated from a source chest to the requested target chest.
- R2. The success oracle asserts **both-sides conservation per position** (source
  loses N of the ingredient AND target gains N) — the town-wide production-delta
  model is insufficient and must not be the gate.
- R3. The scenario is deterministic: the requested ingredient is a distinctive item
  guaranteed absent from the base arena, present in exactly one source location,
  with a decoy non-full container so warp has somewhere else to dump. The harness
  asserts zero pre-existing count of the ingredient as a setup-sanity guard.
- R4. Warp behavior is an executable spec, true-red via an `expectedFailure` (xfail)
  flag; the assertion is the *correct* conservation (source→target), and an
  unexpected pass (XPASS) counts as a suite failure.
- R5. The job is loadable by moving its JSON into `questown_jobs/`; the
  broken-in-warp window is minimized by landing the move with the Phase B wiring.
- R6. The full autotest suite stays green with Phase A passing and Phase B as XFAIL.

---

## Scope Boundaries

- Not building warp relocation for the organizer (see Deferred to Follow-Up Work).
- Not exercising `SwitchToOrganizerFetcheSpecialRule` (the job-switch path). The
  harness assigns the job directly via `changeJobForVillager`, and no JSON declares
  that rule anyway. Job-switching is a separate concern.
- Not supporting organizer requests in non-chest containers (existing TODO in
  `TownContainers`).
- Not de-deprecating or rewriting `FetcherHack`.

### Deferred to Follow-Up Work

- **Warp relocation feature** (separate track, separate PR): port `FetcherHack`'s
  directed source→target routing into the warp path (`onWarpTick` / the
  `TimeWarpWorldInteraction` insertion logic), operating on the **warp town state**
  instead of live `ServerLevel`. Then flip the Phase B test from xfail to green and
  physically confirm the un-archive. Use the conservation autotest from this plan as
  the green gate. Mirror the arborist un-archive (fix warp first; treat as its own
  feature), per ADR-0005.
- **Move `WorkSpotFromHeldItemSpecialRule` routing off `beforeTick`** (does not fire
  in warp) onto `beforeInit`/`onWarpTick` — part of the warp-relocation track, not a
  decoupling task (the rule has no MC-world coupling).

---

## Context & Research

### Relevant Code and Patterns

- `src/main/resources/data/questown/questown_job_archive/organizer_fetcher.json` —
  the archived job definition to un-archive.
- `src/main/java/ca/bradj/questown/jobs/ResourceJobLoader.java:73` — loads only
  `questown_jobs/`; the gate on loadability.
- `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java` — phase machine:
  realtime path (`CAPTURE_BEFORE_REALTIME → START_MONITOR → MONITORING →
  CHECK_REALTIME_RESULTS`) and warp path (`CAPTURE_BEFORE → RUN_WARP →
  CHECK_RESULTS`). `passed()` at line 121; `checkRealtimeResults` at 568
  (`realtimePassed = itemsPassed && fullnessPassed && heldPassed`).
- `src/main/java/ca/bradj/questown/commands/test/TestBlueprint.java` — blueprint
  record; existing `supplyDoorOffset` second-room mechanism (registered via
  `registerDoor`, `TestExecutor.java:264`), `realtimeExpectation`, `skipWarp`.
- `src/main/java/ca/bradj/questown/commands/test/TestExpectation.java` — current
  `ExpectedProduct` (town-wide delta) model to extend.
- `src/main/java/ca/bradj/questown/commands/test/TestResultChecker.java` —
  `snapshotItemCounts` (town-wide flatten), `check`, `checkDeltas`, `computeDeltas`.
- `src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java` —
  `get(JobID)` dispatch + entry list; the arborist entries are the closest template.
- `src/main/java/ca/bradj/questown/commands/test/TestAllExecutor.java` —
  `recordResult(name, executor.passed())` aggregation; `AutotestLogFormatter` for
  result lines (`TRACK_JOBS`).
- `src/main/java/ca/bradj/questown/jobs/fetcher/FetcherHack.java` — realtime fetch
  logic + `StockRequestItem` NBT usage (the shape the setup hook must mimic).
- `src/main/java/ca/bradj/questown/items/StockRequestItem.java` — `writeToNBT`
  (request, room, job block) used to fabricate the request item.

### Institutional Learnings

- Arborist un-archive (ADR-0005, `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`):
  fix warp first, move JSON last; deterministic seeding beats vanilla RNG; scrub the
  shared arena of residue or stale items create phantom state (the `TreeFeature` flake).
- Gatherer warp-loot flake: randomness in warp produces run-to-run flakes; neutralize
  by controlling the world (single candidate) rather than loosening assertions.
- Targeted runs: `-Dquestown.autotest.only=<substring>` runs one scenario; new `-D`
  knobs must be added to `build.gradle`'s jvmArg allowlist or they're silently dropped.
- Container scan requires registered rooms (door-based); meta-rooms don't count.

### External References

- None. In-repo Minecraft Forge 1.19.2 mod patterns are sufficient; no external
  research warranted.

---

## Key Technical Decisions

- **Realtime baseline before warp.** Establish a green regression guard, then expose
  warp as red. (Settled.)
- **Make loadable by moving the JSON** into `questown_jobs/`; risk accepted on the
  unstable branch. The physical move is the final step, landed with the Phase B
  wiring to minimize the broken-in-warp window. (Settled.) → R5
- **Extend the standard harness**, not the ChickenArc scripted one — only the
  standard harness does job-production realtime/warp parity. (Settled.)
- **Post-placement setup hook** on `TestBlueprint`: runs after placement (positions
  known), fabricates the `StockRequestItem` (target `BlockPos`/room) and deposits it
  into the target chest. (Settled.) → R1
- **Per-position conservation oracle (both sides).** Assert source loses N AND target
  gains N. Also guards the dupe/loss class of warp-parity bugs. (Settled.) → R2
- **Determinism via single distinctive candidate** + zero-pre-existing sanity assert
  + decoy non-full container. (Settled.) → R3
- **Phase A green; Phase B true-red via `expectedFailure` flag.** (Settled.) → R4, R6
- **xfail semantics:** `expectedFailure && warp fails → XFAIL (pass)`;
  `expectedFailure && warp passes → XPASS (fail)`. `passed()` becomes
  `expectedFailure ? !warpPassed : (skipWarp ? realtimePassed : warpPassed)`. The
  Phase B assertion is the correct conservation, so the spec encodes intended
  behavior, not the bug. (Settled.) → R4

---

## Open Questions

### Resolved During Planning

- Which harness? → Standard `TestExecutor`/`TestBlueprint`.
- How to assert a relocation? → New per-position conservation expectation.
- How to keep the suite green with a documented warp failure? → xfail flag with
  XPASS-is-failure semantics.
- How to neutralize `take_random_ingredient`? → Single distinctive candidate + decoy.

### Deferred to Implementation

- **Realtime tick budget.** The fetch is multi-hop (locate target chest → fetch
  clipboard → fetch ingredient → return → deposit). The default
  `effectiveRealtimeTicks` may be too short; tune `realtimeTicks` after observing a
  run. Knowable only by running the harness.
- **`store_room` registration via the harness.** Confirm `RoomType`
  (FARM/INDOOR/WELCOME_MAT/BLOCK_ROOM) + `roomId = questown:store_room` register a
  valid store room the container scan accepts; pick the right `RoomType`. Resolve by
  running `BUILD_ROOM`/`REGISTER_ROOM`/`WAIT_FOR_ROOM`.
- **`require_two_free_spots`.** Target chest must keep ≥2 free slots; final slot
  sizing confirmed against runtime behavior.
- **Exact warp red shape.** Whether warp no-ops or misroutes; the xfail only needs
  "correct conservation does not hold," so the precise failure mode is observed, not
  predicted.

---

## Implementation Units

- [ ] U1. **Per-position container content oracle**

**Goal:** Add an assertion primitive that checks per-`BlockPos` chest contents so a
relocation (source loses N, target gains N) can be verified — in both realtime and
warp checks.

**Requirements:** R2

**Dependencies:** None

**Files:**
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestExpectation.java`
  (add an `ExpectedContainerContent(BlockPos chest, String item, Integer minDelta, Integer maxDelta)` record + collection field)
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestResultChecker.java`
  (per-`BlockPos` snapshot keyed by container pos; a `checkContainerContents(before, after, expectation)` that returns pass/detail)
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java`
  (capture per-position snapshots in `captureBefore` and `captureBeforeRealtime`;
  AND the per-position result into `warpPassed` and `realtimePassed`)
- Test: `src/test/java/ca/bradj/questown/commands/test/TestResultCheckerTest.java`

**Approach:**
- Keep the existing town-wide snapshot; add a parallel per-`BlockPos` map so existing
  product-delta expectations keep working unchanged.
- Resolve chest positions by absolute `BlockPos` (the setup hook in U2 knows them).
- `realtimePassed` is currently `itemsPassed && fullnessPassed && heldPassed`; add a
  `containerContentsPassed` term (default true when no per-position expectation set).

**Patterns to follow:**
- `TestResultChecker.snapshotItemCounts` / `computeDeltas` for the snapshot+delta shape.

**Test scenarios:**
- Happy path: before {chestA: 4×X, chestB: 0×X}, after {chestA: 0×X, chestB: 4×X},
  expectation {chestA delta −4, chestB delta +4} → passes.
- Error path: after shows chestB +4 but chestA unchanged (a dupe) → fails (conservation broken).
- Error path: after shows chestA −4 but chestB +0 (loss / wrong target) → fails.
- Edge case: no per-position expectation present → `containerContentsPassed` true, no effect on existing behavior.
- Edge case: expectation references a `BlockPos` absent from the snapshot → fails with a clear "container not found" detail.

**Verification:**
- New unit test green; existing job autotests unaffected (per-position term defaults true).

---

- [ ] U2. **Post-placement request setup hook + two-chest scenario scaffolding**

**Goal:** Let a blueprint fabricate a `StockRequestItem` pointing at the resolved
target chest and seed a separate source chest + decoy container, after placement.

**Requirements:** R1, R3

**Dependencies:** U1

**Files:**
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestBlueprint.java`
  (add an optional post-placement setup callback field; a builder/`with…` accessor consistent with existing `with*` methods)
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java`
  (invoke the hook after `REGISTER_ROOM`/`WAIT_FOR_ROOM`, before `ASSIGN_JOB`; assert
  zero pre-existing count of the distinctive ingredient at that point)
- Test: `src/test/java/ca/bradj/questown/commands/test/OrganizerSetupHookTest.java`
  (where unit-testable; otherwise covered end-to-end in U6)

**Approach:**
- Hook signature receives resolved origin + chest positions and the town handle, so
  it can `StockRequestItem.writeToNBT(...)` with the target `BlockPos`/room and
  deposit into the target chest; deposit N of the distinctive ingredient into the
  source chest; leave a decoy non-full container elsewhere.
- Reuse the existing `supplyDoorOffset` second-room mechanism to register the source
  chest's room so the container scan finds it.
- The zero-pre-existing assertion fails fast (clear message) if arena residue or a
  bad ingredient choice introduces a phantom candidate.

**Execution note:** Some of this is only provable in-game; per CLAUDE.md, where a
real interface is missing for a unit test, add a failing assertion documenting the
gap rather than simulating core logic, and rely on U6 for end-to-end proof.

**Patterns to follow:**
- `FetcherHack` for which NBT fields the request needs (`StockRequestItem.getJobBlock`/`getRoom`/`getRequest`).
- Existing supply-room blueprints (gatherer/hunter/miner/fisher) for `supplyDoorOffset` usage.

**Test scenarios:**
- Happy path: after the hook runs, the target chest holds exactly one `StockRequestItem`
  whose NBT job-block equals the target chest pos and whose request ingredient is the distinctive item.
- Happy path: source chest holds N of the distinctive ingredient; decoy container present and non-full.
- Error path (setup guard): distinctive ingredient already present pre-hook → zero-pre-existing assert fails with a clear message.
- Edge case: target chest retains ≥2 free slots after the request item is placed (`require_two_free_spots`).

**Verification:**
- Hook produces the expected chest contents; pre-existing guard trips when seeded with residue.

---

- [ ] U3. **`expectedFailure` (xfail) flag + reporting**

**Goal:** Allow a scenario to run the warp path, report true-red, and not break the
suite gate — while flagging an unexpected pass (XPASS) as a failure.

**Requirements:** R4, R6

**Dependencies:** None

**Files:**
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestBlueprint.java`
  (add `boolean expectedFailure` + `with*` accessor)
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestExecutor.java`
  (`passed()` → `expectedFailure ? !warpPassed : (skipWarp ? realtimePassed : warpPassed)`)
- Modify: `src/main/java/ca/bradj/questown/commands/test/AutotestLogFormatter.java`
  (XFAIL / XPASS labels)
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestAllExecutor.java`
  (surface XFAIL/XPASS in the result line; aggregation already keys off `passed()`)
- Test: `src/test/java/ca/bradj/questown/commands/test/TestExecutorXfailTest.java`
  (unit-test `passed()` truth table if extractable; otherwise assert the boolean logic in isolation)

**Approach:**
- Pure boolean logic in `passed()`; no phase-machine changes (warp phases still run).
- Reporting must visibly distinguish XFAIL (expected, green) from XPASS (alarm, red)
  so the standing-red can't be mistaken for an ordinary pass.

**Test scenarios:**
- Happy path: `expectedFailure=true`, warp fails → `passed()` true, label XFAIL.
- Error path: `expectedFailure=true`, warp passes → `passed()` false, label XPASS.
- Edge case: `expectedFailure=false` → behaves exactly as today (`skipWarp ? realtimePassed : warpPassed`).

**Verification:**
- Truth table holds; an XPASS shows up red in the suite summary.

---

- [ ] U4. **Un-archive the job + registry wiring**

**Goal:** Make `JobID("organizer","fetch")` resolvable at runtime and mapped to the
new blueprint.

**Requirements:** R5

**Dependencies:** U5 (author blueprints first; land the move in the same change to
minimize the broken-in-warp window per the Key Decision)

**Files:**
- Move: `src/main/resources/data/questown/questown_job_archive/organizer_fetcher.json`
  → `src/main/resources/data/questown/questown_jobs/organizer_fetcher.json`
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java`
  (add `organizer` dispatch in `get(JobID)` + the entry-list registration)

**Approach:**
- No JSON content change needed for Phase A/B (the work_states + special rules already
  encode the fetch). Confirm the icon/result fields load cleanly from the new path.

**Patterns to follow:**
- Arborist registry entries in `TestBlueprintRegistry` (un-archived job precedent).

**Test scenarios:**
- Integration (via U6): assigning `organizer/fetch` no longer errors with "unknown job".

**Verification:**
- `ResourceJobLoader` loads the job; `changeJobForVillager(vuid, organizer/fetch, …)` succeeds.

---

- [ ] U5. **Phase A + Phase B blueprints**

**Goal:** Author the realtime green scenario and the warp xfail scenario.

**Requirements:** R1, R2, R3, R4, R6

**Dependencies:** U1, U2, U3

**Files:**
- Modify: `src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java`
  (add `organizerFetchBlueprint()` and `organizerFetchWarpUnsupportedBlueprint()`)

**Approach:**
- `organizer_fetch` (Phase A): `roomType`/`roomId = questown:store_room` target room
  with a chest; `supplyDoorOffset` source room with a chest; post-placement hook
  (U2) stamps the request + seeds the distinctive ingredient + decoy; `skipWarp=true`;
  `realtimeExpectation` uses the U1 per-position conservation (source −N, target +N)
  plus a zero-pre-existing distinctive-item guard.
- `organizer_fetch_warp_unsupported` (Phase B): same setup; warp enabled;
  `expectedFailure=true`; expectation = the **correct** conservation (source −N,
  target +N) — currently fails because warp has no fetch model. Comment references
  the deferred warp-relocation track.

**Patterns to follow:**
- Arborist `cut_trees` blueprint pairing (realtime + warp) and `wildcardExpectation`
  usage for how loose/strict expectations are expressed.

**Test scenarios:**
- Covers AE/R1+R2 (Phase A): realtime run relocates N distinctive items source→target; conservation holds → green.
- Covers R4 (Phase B): warp run does not achieve source→target conservation → XFAIL (green at suite level).
- Error path (Phase B alarm): if warp ever achieves conservation → XPASS → suite red.
- Edge case: distinctive ingredient absent from base arena (zero pre-existing guard passes at setup).

**Verification:**
- Phase A passes; Phase B reports XFAIL; both appear in the jobs-track summary.

---

- [ ] U6. **End-to-end verification run**

**Goal:** Confirm the two scenarios behave as designed in the real game harness.

**Requirements:** R1, R4, R6

**Dependencies:** U4, U5

**Files:**
- Modify (if a new `-D` knob or scenario filter substring is introduced):
  `build.gradle` jvmArg allowlist

**Approach:**
- Run targeted: `-Dquestown.autotest.only=organizer_fetch` (matches both scenarios).
- Observe realtime green; tune `realtimeTicks` if the multi-hop fetch doesn't
  complete in the default window; confirm `store_room` registers and the container
  scan finds both chests.
- Confirm Phase B logs XFAIL (not a hard failure) and the suite stays green.

**Execution note:** This is the authoritative test for U2/U4/U5 behavior that can't
be unit-tested without simulating core logic.

**Test scenarios:**
- Integration: `only=organizer_fetch` run → Phase A PASS, Phase B XFAIL, suite green.
- Integration: full `testall` (or jobs track) → overall pass count unchanged-plus-one-green, Phase B shown as XFAIL.

**Verification:**
- Both scenarios green/XFAIL as designed; no regression in the other 46 scenarios.

---

## System-Wide Impact

- **Interaction graph:** U1 and U3 modify shared harness types (`TestExpectation`,
  `TestResultChecker`, `TestExecutor`, `TestBlueprint`, `TestAllExecutor`,
  `AutotestLogFormatter`) used by **all 46 scenarios**. Additions must be
  backward-compatible (new optional fields default to no-op).
- **Error propagation:** the zero-pre-existing setup guard (U2) and the per-position
  oracle (U1) must produce clear, specific failure messages so a future builder can
  diagnose without re-reading this plan.
- **State lifecycle risks:** shared-arena residue is a known flake source (memory:
  `TreeFeature` flake). The distinctive-ingredient choice + zero-pre-existing guard
  is the mitigation; teardown/`flatten` must leave no copies of the distinctive item.
- **API surface parity:** the per-position oracle is wired into both
  `checkRealtimeResults` and `checkResults` so realtime and warp assert consistently.
- **Unchanged invariants:** existing `ExpectedProduct` town-wide delta behavior is
  untouched; existing blueprints keep passing with no edits.

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Moving the JSON ships a warp-broken job to players | Accepted on the unstable branch; land the move with Phase B wiring (U4 depends on U5); resolve warp before upstream merge |
| Realtime fetch doesn't complete in default tick budget | `realtimeTicks` is tunable; deferred to U6 observation |
| `take_random_ingredient` / warp dump randomness flakes the assertion | Single distinctive candidate + decoy container + both-sides conservation (U2/U1) |
| `store_room` doesn't register via harness room types | Validate in U6; fall back to an `INDOOR` room with the `store_room` `roomId` if needed |
| Shared harness changes regress other scenarios | New fields default to no-op; U6 runs the full suite to confirm |
| Standing-red xfail rots / is ignored | XPASS-is-failure semantics force a red suite the moment warp is fixed |

---

## Sources & References

- Origin: grilling session 2026-06-08 (no `docs/brainstorms/` requirements doc).
- Related plan: `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md` (un-archive precedent).
- Related ADRs: `docs/adr/0005-trees-grow-in-warp-via-in-memory-worldgen.md`,
  `docs/adr/0006-warp-two-timelines-productive-and-wall-clock.md`.
- Key code: `TestExecutor`, `TestBlueprint`, `TestResultChecker`, `TestExpectation`,
  `TestBlueprintRegistry`, `FetcherHack`, `StockRequestItem`, `TimeWarpWorldInteraction`,
  `ResourceJobLoader` (all under `src/main/java/ca/bradj/questown/`).
