---
name: questown-behavior-verify
description: >-
  Verifies a Questown behavior (navigation, jobs, movement) on a live server when a visual play-test cannot run (this box has no capturable window). TRIGGER when asked to "watch" a Questown behavior, run a before/after, or "see" a villager/townie do something live, or when a unit test cannot settle a broken-behaviour report. SKIP when a unit test or the existing autotest suite already proves it. The key fact: this box's runClient opens no window (grim captures 0 bytes), so the evidence must come from the server log, not a screenshot.
---

# Questown behavior verification (no window)

A visual play-test cannot run on this box: `runClient` connects but opens no
window (`grim` captures 0 bytes, `xdotool search --name Minecraft` finds
nothing). So the evidence for a live behavior must come from the **server log**,
not a screenshot. See `references/questown-dev-launch.md`.

## The recipe

1. **Prove the objective is loggable.** The autotest harness already writes
   results to `QT.FLAG_LOGGER` via `PlayerTestOutput`/`LogTestOutput`. A
   scenario's `customAssertion` (or `TestResultChecker`) can read live state
   (entity positions, blockstates, needs) and log it. If the thing you want to
   see is not expressable as an assertion, add one — it becomes the objective
   read.

2. **Add a scenario to `TestBlueprintRegistry`.** Model it on an existing
   entry (e.g. `organizerFetchBlueprint`). Give it a `customAssertion` that
   logs the outcome you care about (the before/after signal).

3. **Run it from a console source.** `_qtdev test` cannot be driven from the
   FIFO — it calls `getPlayerOrException()`, which throws on a console source,
   so it silently does nothing. Two ways to run from a headless box:
   - The `AutoTestRunner` hold path: `runServer -Dquestown.autotest=true
     -Dquestown.autotest.hold=true -Dquestown.autotest.only=<scenario>`. It
     runs the scenario and keeps the world alive, logging results.
   - Or register a console command that builds the same `TestExecutor` from a
     console source (origin from a flag scan) with `LogTestOutput`, and send it
     through the FIFO.

4. **Read the server log**, not the client. Grep for the scenario's
   `QT.FLAG_LOGGER` output (e.g. `[nav] RESULT: ...`). That is the evidence.

## Traps

- **Baseline runs: stash only your change's files.** A before/after comparison
  needs a baseline run on the pre-change code, but a full `git stash` also
  reverts unrelated tracked edits (this tree often carries them) that
  untracked in-progress files depend on — the baseline then fails to compile
  with `cannot find symbol` in files you did not touch, and the run is wasted.
  Stash explicitly: `git stash push -- <only the files your change touched>`.
- Scenario-name matching is on the scenario name only; chicken-arc names have
  no `chicken/` prefix (`F3_build_room_to_welcome_mat`, not
  `chicken/F3_build_room_to_welcome_mat`). A 0-match run prints the full
  `available scenarios` list — a cheap way to get exact names.

- `setblock`/`/give` resolve **item** ids, not block ids. Questown blocks have
  a different item id (flag block `questown:cobblestone_flag_base`, not
  `questown:cobblestone_town_flag`). Place mod blocks via the `_qtdev`/`qt`
  harness, not vanilla `setblock`.
- `getblockstate` is unsupported in this build — read block/entity state via the
  assertion's level access, not a console command.
- A held server is a `java` process; match with `pgrep -af '[r]unServer'` or
  `pgrep -af forgeserveruserdev`, not `pgrep -af 'runServer'`.

## When a unit test is enough

If the behavior is a pure function of inputs (like a walk-target computation),
a unit test that fails on the old code and passes on the new is stronger
evidence than a live run and is faster. Reserve this skill for behavior that
only settles on a live world (interaction timing, pathfinding end-state,
real-time effects).
