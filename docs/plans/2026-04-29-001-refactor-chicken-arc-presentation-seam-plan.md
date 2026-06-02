---
title: Chicken arc presentation seam — consolidate bubble + hint + plain into a (beat, phase) table
type: refactor
status: complete
date: 2026-04-29
origin: docs/adr/0001-chicken-arc-beat-phase.md
---

# Chicken arc presentation seam

## Overview

The chicken arc has three parallel switches over `ChickenBeatState` taking overlapping inputs and producing related outputs (bubble icon, hint lang key, plain lang key). They have drifted in production — most visibly when the bubble cycled through three SUNSET_AND_MAP phases while the hint stayed frozen at "come back in the evening", and when "with-item" hint variants disagreed with placement-beat bubbles. This plan introduces `ChickenArcPresentation`: a per-(beat, phase) table where each row authors all three outputs together, with a single `BeatPhase` decision derived from `PhaseInputs`. The existing `ChickenArcBubbles.forState`, `ChickenArcController.hintKey`, and `ChickenArcController.plainTextKey` are deleted. Tests gain one new assertion (`expectedPhase`) that the result-checker compares against a live derivation, validating end-to-end behavior without re-asserting table contents.

---

## Problem Frame

Three switches, four inputs (`state`, `hasItem`, `chestSpawned`, `isNight`), three call sites — all coordinated only by author discipline. When inputs grow (e.g., SUNSET_AND_MAP gained a third phase) one switch is updated and the others lag. The bug class repeats: bubble and hint say different things to the player. The current `ChickenArcExpectation` has no field for "what should the player see," so `ChickenArcResultChecker` cannot catch any of these — beats advance, tests pass, players see drift in-game.

The root design problem is that *phase* is implicit. Each switch independently re-derives phase from inputs and risks doing so differently. The fix is to make phase explicit, decide it once, and have all three outputs read from the same row.

See origin: `docs/adr/0001-chicken-arc-beat-phase.md` and `CONTEXT.md` for terminology.

---

## Requirements Trace

- R1. A single source of truth maps `(ChickenBeatState, BeatPhase) → (bubble, hintKey, plainKey)`. Drift between the three outputs becomes structurally impossible.
- R2. `BeatPhase` is decided once from `PhaseInputs`. Bubble, hint, and plain all read from the row that decision selects.
- R3. The existing public APIs `ChickenArcBubbles.forState`, `ChickenArcController.hintKey`, `ChickenArcController.plainTextKey` are removed. No facade left behind.
- R4. `ChickenArcExpectation` gains `expectedPhase: Optional<BeatPhase>`. Result-checker derives the live phase from world state and compares.
- R5. Tests assert on `expectedPhase`, not on leaf bubble/hint/plain values. Existing leaf-coupled tests in `ChickenArcBubblesTest` and `ChickenArcHintsTest` migrate to phase assertions or to a single snapshot test of the table.
- R6. Behavior is preserved: every (beat, inputs) combination produces the same bubble/hint/plain it does today. The two intentional asymmetries (placement beats — wall_block, door — bubble ignores `hasItem` while hint honors it; plain text doesn't vary on `hasItem`) are encoded in the table, not lost.

---

## Scope Boundaries

- `HelperChickenBeatOffsets` is **not** consolidated into the presentation table — different problem (spatial, single-consumer). Bug #7 (chicken-on-campfire) is not addressed here.
- Goal/pathing assertions are **not** added to the test framework. Bugs in category (b) — coverage gaps where the chicken doesn't path correctly — are out of scope.
- Save/reload persistence assertions are **not** added.
- Multi-player bubble/clicker drift (`findNearestPlayerForFlag` vs the clicker) is **not** fixed here. Flagged in `CONTEXT.md` as a known ambiguity for follow-up.
- The existing `ChickenBeatState` enum is **not** reshaped. Beats keep their identity; only their presentation is reorganized.

---

## Context & Research

### Relevant Code and Patterns

- `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubbles.java` — current bubble switch. Source of `Bubble` record (kept) and `forState(...)` (deleted).
- `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java:110–140` — bubble-update call site (`updateChickenBubble`).
- `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java:212–321` — click-handler call site, plus `plainTextKey` and `hintKey` switches to be deleted.
- `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcConditions.java` — `playerHoldsRequiredItem(player, state)`. Both call sites already use this; no change needed.
- `src/main/java/ca/bradj/questown/commands/test/ChickenArcExpectation.java` — gains `expectedPhase` field + builder method.
- `src/main/java/ca/bradj/questown/commands/test/ChickenArcResultChecker.java` — gains live-phase derivation + comparison.
- `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubblesTest.java` — migrates to new API.
- `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcHintsTest.java` — migrates to new API.

### Institutional Learnings

- **`docs/solutions/`** — no directly applicable patterns. Closest is the `QTToolAction` closed-enum decision (`docs/decision-qttoolaction-closed-enum.md`) which similarly used a closed enum to make compile-time safety load-bearing; same spirit applies to `BeatPhase`.
- **`MEMORY.md`** — "Always Read files before Edit"; "Pre-existing warnings are normal." No relevant warp-vs-realtime parity concern (presentation is realtime-only).

### External References

- None. Pure refactor, no framework or third-party concerns.

---

## Key Technical Decisions

- **One global `BeatPhase` enum (six values)** rather than per-beat nested enums. `NEED_TO_FETCH` and `READY_TO_USE` recur across multiple beats; fragmenting them would obscure a real cross-cutting concept. Runtime nonsense (`expectedPhase=AWAITING_NIGHT` on a stick beat) is caught by the result-checker validating the (beat, phase) pair is a row in the table.
- **`Presentation` record holds all three outputs as siblings.** Every row authors `(bubble, hintKey, plainKey)` together. Authoring-time invariant: a row can declare `null` for `hintKey`/`plainKey` for terminal states (COMPLETE/FORFEIT) but otherwise all three must be present.
- **`activePhase(state, phaseInputs)` and `present(state, phaseInputs)` are separate.** `activePhase` is logic (tested directly); `present` is a table lookup (tested via the table-completeness invariant only). This is what makes the test surface meaningful — the result-checker can compare `activePhase` output to `expectedPhase` without ever touching leaf values.
- **No facades.** Old APIs are deleted, not stubbed. A facade that delegates to the new table is shallow and re-creates the drift risk by inviting new callers to use the deprecated entry point.
- **Leaf assertions stay possible but unused.** A new caller could query `present(...).bubble()` for a specific value, but tests should not. Enforced by convention, documented in `CONTEXT.md` and the ADR.
- **Migration is staged, not big-bang.** U1–U2 introduce the new module without removing anything; U3 migrates call sites; U4 adds the test surface; U5 deletes old APIs and migrates tests. Each unit leaves a green build.

---

## Open Questions

### Resolved During Planning

- **Phase enum scope** (per-beat vs global): global, six values. Resolved during grilling Q9.
- **Module shape** (record-with-fields vs phase-keyed table): table. Resolved during grilling Q6.
- **Module placement** (replace vs facade): replace, no facades. Resolved during grilling Q8.
- **Test surface** (leaf vs phase assertions): phase only; leaves are data. Resolved during grilling Q9 follow-up.
- **Phase derivation in tests** (live vs declared inputs): live, from real world state. Resolved during grilling Q10.

### Deferred to Implementation

- **Exact field name for `Presentation.bubble`** vs `Presentation.bubbleIcon`. The current `ChickenArcBubbles.Bubble` record stays; the field can be `bubble`. Implementer's call.
- **Whether to make `BeatPhase` `Comparable`** for ordered rendering or test output. Probably not needed; defer.
- **Whether `Presentation` rows should be authored as a static `Map<>` literal, a switch expression, or a per-beat list of `(PhaseGuard, Presentation)`**. The grilling settled on "phase-keyed table" conceptually; the Java realization is implementer's choice. Recommended: a switch expression on `(state, phase)` pairs is the most readable in Java 17; a `Map<EnumPair, Presentation>` is a plausible alternative.

---

## High-Level Technical Design

> *This illustrates the intended approach and is directional guidance for review, not implementation specification. The implementing agent should treat it as context, not code to reproduce.*

```
// Sketch — not implementation.

enum BeatPhase { DEFAULT, NEED_TO_FETCH, READY_TO_USE, READY_TO_PLACE, PREPARING, AWAITING_NIGHT }

record PhaseInputs(boolean hasItem, boolean chestSpawned, boolean isNight) {}

record Presentation(Bubble bubble, @Nullable String hintKey, @Nullable String plainKey) {}

final class ChickenArcPresentation {
    static BeatPhase activePhase(ChickenBeatState state, PhaseInputs in) {
        // Logic: derive phase from inputs. Single decision tree.
        // For SUNSET_AND_MAP: PREPARING / AWAITING_NIGHT / READY_TO_USE.
        // For "use X on Y" beats: NEED_TO_FETCH / READY_TO_USE.
        // For placement beats: NEED_TO_FETCH / READY_TO_PLACE.
        // For single-phase beats: DEFAULT.
    }

    static Presentation present(ChickenBeatState state, PhaseInputs in) {
        return rowFor(state, activePhase(state, in));
    }

    private static Presentation rowFor(ChickenBeatState state, BeatPhase phase) {
        // Table lookup. Authoritative source of (bubble, hintKey, plainKey) per row.
    }
}
```

The result-checker's comparison reads:

```
PhaseInputs live = new PhaseInputs(
    ChickenArcConditions.playerHoldsRequiredItem(nearestPlayer, state),
    flag.getChickenSunsetChestSpawned(),
    level.isNight()
);
BeatPhase actual = ChickenArcPresentation.activePhase(state, live);
assertEquals(expected, actual);
```

---

## Implementation Units

- [x] U1. **Introduce `BeatPhase`, `PhaseInputs`, and `Presentation` types**

**Goal:** Land the data model — three new types — with no behavior change. Nothing calls them yet.

**Requirements:** R1, R2

**Dependencies:** None

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/BeatPhase.java`
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/PhaseInputs.java`
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/Presentation.java`
- Test: `src/test/java/ca/bradj/questown/mobs/helperchicken/BeatPhaseTest.java` (minimal smoke test for enum identity)

**Approach:**
- `BeatPhase` is a closed enum with six values, ordered as listed in `CONTEXT.md`.
- `PhaseInputs` is a record of three `boolean` fields. No validation logic.
- `Presentation` is a record of `(Bubble bubble, @Nullable String hintKey, @Nullable String plainKey)`. Reuses the existing `ChickenArcBubbles.Bubble` record — does not redefine it.

**Patterns to follow:**
- `ChickenBeatState` (sibling enum) for naming and structure conventions.
- `QTToolAction` for closed-enum discipline.

**Test scenarios:**
- Happy path: all six `BeatPhase` values are distinct and have stable `name()` strings (`DEFAULT`, `NEED_TO_FETCH`, `READY_TO_USE`, `READY_TO_PLACE`, `PREPARING`, `AWAITING_NIGHT`). Locks the enum order against accidental reorder.
- Happy path: `Presentation` allows `null` for `hintKey` and `plainKey` (terminal states need this).

**Verification:**
- `./gradlew compileJava compileTestJava` succeeds.
- The three new types exist and are importable; no production code references them yet.

---

- [x] U2. **Implement `ChickenArcPresentation` with full table and `activePhase` logic**

**Goal:** Land the new module with the per-(beat, phase) table populated and the `activePhase` decision tree implemented. Old APIs (`ChickenArcBubbles.forState`, `ChickenArcController.hintKey`, `ChickenArcController.plainTextKey`) remain in place and untouched. Behavior of the system is unchanged.

**Requirements:** R1, R2, R6

**Dependencies:** U1

**Files:**
- Create: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcPresentation.java`
- Test: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcPresentationTest.java`

**Approach:**
- `activePhase(state, phaseInputs)` is a switch on `state`. For multi-phase beats, it consults the relevant fields of `PhaseInputs`. For single-phase beats and terminal states, it returns `DEFAULT`.
- `present(state, phaseInputs)` calls `activePhase`, then looks up the row.
- Row authoring: use a switch expression on `(state, phase)` pairs returning `Presentation` literals. Each row carries the same `Bubble`, `hintKey`, and `plainKey` the existing switches produce today for the matching inputs. **This is a copy job, not a redesign.**
- The two intentional asymmetries documented in `CONTEXT.md` are encoded in the table:
  - For `WAITING_FOR_WALL_BLOCK` and `WAITING_FOR_DOOR`, the bubble field is identical between `NEED_TO_FETCH` and `READY_TO_PLACE` rows; only the hint differs.
  - The `plainKey` field is identical across `NEED_TO_FETCH`/`READY_TO_USE` for use-X-on-Y beats; the action description doesn't change with player state.
- Add a private `tableCompleteness()` invariant check (or test) ensuring every `(state, phase)` reachable from `activePhase` has a non-`null` row. Catches a missing row before runtime.

**Patterns to follow:**
- `ChickenArcBubbles.forState` — the existing switch is the source-of-truth for bubble values per (beat, inputs). Mirror exactly.
- `ChickenArcController.hintKey` / `plainTextKey` — same.

**Test scenarios:**
- Happy path: for each `ChickenBeatState`, `activePhase(state, defaultInputs)` returns the documented default phase. (`defaultInputs = (false, false, false)`.)
- Happy path: `activePhase(WAITING_FOR_STICK, hasItem=true)` → `READY_TO_USE`; with `hasItem=false` → `NEED_TO_FETCH`.
- Happy path: `activePhase(SUNSET_AND_MAP, ...)` returns `PREPARING` when `chestSpawned=false`; `AWAITING_NIGHT` when `chestSpawned=true && isNight=false`; `READY_TO_USE` when `chestSpawned=true && isNight=true`.
- Happy path: `activePhase(WAITING_FOR_WALL_BLOCK, hasItem=true)` → `READY_TO_PLACE`; `hasItem=false` → `NEED_TO_FETCH`.
- Edge case: `activePhase(COMPLETE, anyInputs)` → `DEFAULT`; `present(COMPLETE, _).hintKey()` → `null`. Same for `FORFEIT`.
- Edge case (parity): for every `ChickenBeatState` value except `COMPLETE`/`FORFEIT`, and for every (`hasItem`, `chestSpawned`, `isNight`) combination, `present(state, in).bubble()` equals `ChickenArcBubbles.forState(state, in.hasItem(), in.chestSpawned(), in.isNight())`. Locks behavioral parity with the existing switch. **Delete this test in U5** when `forState` is removed.
- Edge case (parity): same shape, comparing `present(state, in).hintKey()` against the existing `ChickenArcController.hintKey(...)`, and `.plainKey()` against `plainTextKey(...)`. **Delete in U5.**
- Integration: `tableCompleteness()` — every `(state, phase)` returned by `activePhase` over the cartesian product of `PhaseInputs` produces a non-null `Presentation` row.

**Verification:**
- `./gradlew test` passes for the new test class.
- Behavior parity tests prove the table reproduces the current behavior bit-for-bit.

---

- [x] U3. **Migrate the two call sites in `ChickenArcController` to `ChickenArcPresentation`**

**Goal:** Replace the bubble-update path and click-handler path with calls to the new module. Old APIs (`ChickenArcBubbles.forState`, `hintKey`, `plainTextKey`) become unused but not yet deleted.

**Requirements:** R1, R2

**Dependencies:** U2

**Files:**
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java`

**Approach:**
- In `updateChickenBubble` (around L110–140), build a `PhaseInputs` from the existing `hasItem`, `chestSpawned`, `isNight` locals; call `ChickenArcPresentation.present(state, in).bubble()`.
- In the click handler (around L212–230), build the same `PhaseInputs`, call `present(state, in)`, and pluck `.hintKey()` or `.plainKey()` based on `clickCount % PLAIN_TEXT_CYCLE`.
- Logging at L135 and L222 stays — minor adjustments may be needed if the local `bubble` variable is replaced with `Presentation`.

**Patterns to follow:**
- The two existing call-site shapes — input gathering already happens locally, only the destination call changes.

**Test scenarios:**
- Integration: existing `ChickenArcControllerTest` continues to pass without changes (it tests transition logic, not presentation outputs).
- Test expectation: no new tests in this unit. The behavior-parity tests added in U2 are what prove the migration is safe.

**Verification:**
- `./gradlew test` passes the existing controller test.
- A grep for `ChickenArcBubbles.forState\|ChickenArcController.hintKey\|plainTextKey` in `src/main/` returns only the dying definitions in `ChickenArcBubbles.java` and `ChickenArcController.java`. No call sites remain in `src/main/`.

---

- [x] U4. **Add `expectedPhase` to `ChickenArcExpectation` and live-phase derivation to `ChickenArcResultChecker`**

**Goal:** Open the test surface for phase assertions. Result-checker derives the live phase from real world state and compares to `expectedPhase` when provided.

**Requirements:** R4

**Dependencies:** U2 (does not require U3 — could land in parallel)

**Files:**
- Modify: `src/main/java/ca/bradj/questown/commands/test/ChickenArcExpectation.java`
- Modify: `src/main/java/ca/bradj/questown/commands/test/ChickenArcResultChecker.java`
- Test: `src/test/java/ca/bradj/questown/commands/test/ChickenArcExpectationTest.java` (new — minimal builder smoke tests)

**Approach:**
- Add `@Nullable BeatPhase expectedFinalPhase` to the `ChickenArcExpectation` record. Add `Optional<BeatPhase> finalPhase()` accessor and `Builder.finalPhase(BeatPhase)`.
- In `ChickenArcResultChecker`, after the existing beat-state assertion, branch on `expectation.finalPhase().isPresent()`. When present:
  1. Find nearest player to the flag (`ChickenArcConditions.findNearestPlayerForFlag`).
  2. If player is null, fail the assertion with a descriptive message ("expectedPhase requires a nearby player").
  3. Build live `PhaseInputs` from `playerHoldsRequiredItem(player, state)`, `flag.getChickenSunsetChestSpawned()`, `level.isNight()`.
  4. Call `ChickenArcPresentation.activePhase(state, live)`. Compare to `expected`. Emit one assertion line via `AutotestLogFormatter` matching the format of existing assertions in this file.
- No existing scenarios pass `expectedPhase` yet — they continue to behave exactly as before.

**Patterns to follow:**
- `ChickenArcExpectation` builder shape — mirror the existing `finalBeat`, `flagBits`, etc.
- `ChickenArcResultChecker.check` assertion-emission pattern.

**Test scenarios:**
- Happy path: `Builder.finalPhase(BeatPhase.READY_TO_USE).build()` produces an expectation whose `finalPhase()` is `Optional.of(READY_TO_USE)`.
- Happy path: an expectation built without `finalPhase` returns `Optional.empty()`.
- Edge case: `flagBits` and other existing fields on the expectation are unchanged when `finalPhase` is set or unset (defensive immutable copy still works).
- Test expectation: result-checker behavior is exercised end-to-end by U5's scenario migration; no isolated unit test of `ChickenArcResultChecker.check` here.

**Verification:**
- `./gradlew compileJava test` passes.
- Existing chicken-arc autotest scenarios still run unchanged (no scenario has set `expectedPhase` yet).

---

- [x] U5. **Delete old APIs, migrate existing tests, add at least one scenario asserting `expectedPhase`**

**Goal:** Remove the dead code (`ChickenArcBubbles.forState`, `ChickenArcController.hintKey`, `ChickenArcController.plainTextKey`); migrate `ChickenArcBubblesTest` and `ChickenArcHintsTest` to the new API; add at least one autotest scenario that exercises `expectedPhase` end-to-end.

**Requirements:** R3, R5

**Dependencies:** U3, U4

**Files:**
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubbles.java` (delete `forState` and its overloads; keep the `Bubble` record + `SUNSET_TEXTURE` constant — both are still used)
- Modify: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcController.java` (delete `hintKey`, `plainTextKey`)
- Modify: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubblesTest.java` (rewrite around `ChickenArcPresentation.present(...).bubble()`, OR delete in favor of the parity tests in U2 — implementer's call based on what's still useful)
- Modify: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcHintsTest.java` (same — migrate or delete based on coverage value)
- Modify: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcPresentationTest.java` (delete the parity tests added in U2 — they no longer have a counterpart to compare against)
- Modify: `src/main/java/ca/bradj/questown/commands/test/ChickenArcBlueprintRegistry.java` (add one new scenario or augment an existing one with `expectedPhase`)

**Approach:**
- Delete `ChickenArcBubbles.forState` and all its overloads. The `Bubble` record and `SUNSET_TEXTURE` constant stay (both are still consumed by `Presentation` and by the bubble layer).
- Delete `hintKey` and `plainTextKey` from `ChickenArcController`. The click handler already calls `ChickenArcPresentation.present(...)` after U3.
- Migrate `ChickenArcBubblesTest`: any test that exercised invariants worth preserving (e.g., COMPLETE/FORFEIT → HIDDEN bubble, the through-walls flag for seeds delivery) is rewritten to call `ChickenArcPresentation.present(...).bubble()`. Tests that purely re-assert table contents per (beat, inputs) can be deleted — they're testing data, not logic.
- Same migration approach for `ChickenArcHintsTest`. Note: the `noItemHint_mentionsBubbleIconNoun` test (the explicit invariant added on 2026-04-27) is *not* table-content; it's a structural invariant ("every no-item hint must mention a noun tied to its bubble icon"). Keep it, ported to the new API.
- Augment one existing scenario (recommend: `f4_register_door_via_wand` or similar mid-arc scenario where a phase change is meaningful) with `expectedPhase` set. End the action sequence with the player holding (or not holding) the relevant item to land in a known phase. Verify the scenario still passes.

**Patterns to follow:**
- Existing `ChickenArcHintsTest.noItemHint_mentionsBubbleIconNoun` — structural invariants that still apply to the new module.
- Existing scenarios in `ChickenArcBlueprintRegistry` — same shape, one new field on the `expectation()` builder.

**Test scenarios:**
- Happy path: at least one autotest scenario has `.finalPhase(...)` set in its expectation, runs end-to-end via `/qt testall`, and passes.
- Happy path: the `noItemHint_mentionsBubbleIconNoun` invariant test (or its successor) still passes against the new module.
- Edge case: `present(COMPLETE, _).bubble() == Bubble.HIDDEN` — preserved from `ChickenArcBubblesTest`.
- Edge case: `present(AWAITING_WORLDLY_SEEDS_DELIVERY, _).bubble().throughWalls() == true` — preserved.
- Test expectation: the parity tests from U2 (which compared new module against the now-deleted old API) are removed in this unit because their counterpart no longer exists.

**Verification:**
- `grep -rn "ChickenArcBubbles\.forState\|ChickenArcController\.hintKey\|ChickenArcController\.plainTextKey" src/` returns nothing.
- `./gradlew test` passes.
- `/qt testall` passes in-game (or at minimum the chicken-arc test track runs cleanly with one scenario asserting `expectedPhase`).
- The `Bubble` record and `SUNSET_TEXTURE` constant remain in `ChickenArcBubbles.java` (the file shrinks to a small data-holder).

---

## System-Wide Impact

- **Interaction graph:** Two call sites change in `ChickenArcController` (bubble update at L110, click handler at L212). One test surface gains a field (`ChickenArcExpectation.expectedFinalPhase`). The `Bubble` record continues to be consumed by `HelperChickenBubbleLayer` and the entity sync path — unchanged.
- **Error propagation:** `ChickenArcResultChecker` gains one new failure mode ("expectedPhase requires a nearby player") — surfaced via the existing `AutotestLogFormatter` path.
- **State lifecycle risks:** None. This is a pure refactor of stateless presentation logic. The flag's `chickenSunsetChestSpawned` bit and the level's `isNight` are read-only inputs.
- **API surface parity:** No external API impact. All affected classes are internal to the mod.
- **Integration coverage:** Behavior-parity tests in U2 prove byte-for-byte equivalence with the existing switches before any call site changes. Removed in U5 once the old API is gone.
- **Unchanged invariants:** `ChickenBeatState` enum identity, `ChickenArcBubbles.Bubble` record shape, `ChickenArcConditions.playerHoldsRequiredItem` semantics, `HelperChickenBubbleLayer` rendering — all explicitly unchanged. The lang-key inventory in `en_us.json` is unchanged (we're consolidating *which* keys are returned per state, not adding or removing keys).

---

## Risks & Dependencies

| Risk | Mitigation |
|------|------------|
| Behavioral drift introduced during the table copy in U2. | U2 ships with parity tests that compare new module's output against the existing switches over the full cartesian product. Tests are deleted only in U5, after the old switches are gone. Any drift fails the build before U3 lands. |
| Multi-player ambiguity (bubble uses `findNearestPlayerForFlag`, click handler uses the clicker) means `expectedPhase` could be derived from a different player than the one in the click handler. | In autotest scenarios there's only one fake player — the ambiguity doesn't manifest. Flagged in `CONTEXT.md` as a known issue for follow-up; not addressed here. |
| Existing `ChickenArcBubblesTest` / `ChickenArcHintsTest` may have load-bearing structural invariants (not just table-content assertions) that get accidentally deleted in U5. | U5 explicitly calls out the `noItemHint_mentionsBubbleIconNoun` invariant as one to keep. Implementer reads each test before deleting; ports anything that's testing logic, deletes anything that's re-asserting table contents. |
| Adding `expectedPhase` to `ChickenArcExpectation` could break existing scenario authors expecting the record's positional constructor. | The record's primary constructor stays unchanged in shape — `expectedFinalPhase` is added as a new field with a `null` default at the end. The compact constructor handles the defensive copy. Scenarios using `Builder` are unaffected. Scenarios using the record's primary constructor directly (if any) need to add the new arg — grep before landing. |
| Deleting `ChickenArcBubbles.forState` may break tests that aren't in the migration list. | Grep `forState` across `src/test/` before U5's deletion step to confirm only the two known test files reference it. |

---

## Documentation / Operational Notes

- `CONTEXT.md` and `docs/adr/0001-chicken-arc-beat-phase.md` already document the new vocabulary and decision. No further docs work required.
- `docs/INDEX.md` could optionally gain a pointer to ADR-0001 once implementation lands. Defer until U5 completes.

---

## Sources & References

- **Origin document:** [docs/adr/0001-chicken-arc-beat-phase.md](../adr/0001-chicken-arc-beat-phase.md)
- **Glossary:** [CONTEXT.md](../../CONTEXT.md)
- Related code: `src/main/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubbles.java`, `ChickenArcController.java`, `ChickenArcConditions.java`
- Related test code: `src/test/java/ca/bradj/questown/mobs/helperchicken/ChickenArcBubblesTest.java`, `ChickenArcHintsTest.java`
- Bug history that motivated this refactor: ce-session-historian found 12 representative bugs across 16 sessions on 2026-04-22 to 2026-04-29; presentation-drift cluster (bugs 1, 2, 3, partially 7) is what this refactor structurally eliminates.
