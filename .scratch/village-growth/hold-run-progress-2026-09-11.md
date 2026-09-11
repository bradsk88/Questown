# Held run — 2026-09-11 (place_block verified, chicken chest not broken, sign->job bug fixed)

## Verdict (both questions the dev approved)

### 1. `_qtdev place_block` works end-to-end (verified live)
- Booted the held run, drove the arc: wand -> door -> register -> room build ->
  chest -> campfire -> sleep -> sign.
- `_qtdev place_block 1 oak_sign` placed a sign at interior cell 1 (6,64,3), a cell
  the room *actually contains* (not a guessed coordinate). This is the real
  agent-hand gap the agent thrashed on before (guessed coordinates, destroyed the
  room). `place_block` closes it.

### 2. The chicken's chest beat is NOT broken (verified live)
- The chicken's chest beat dropped its chest at 6,64,2, the `store_room` recipe
  matched, `containers=1`, and `_qtdev free_food` deposited into it.
- The earlier `containers=0` was an *agent* gap (the player never entered the room
  to trigger the chicken's `playerInStoreShell()` gate). Not a chicken-goal bug,
  not a room-detection gap. The chest beat is the chicken's, and it works.

## Sign->job board conversion bug — FIXED (dev approved: "sign->job")
### The bug
- `_qtdev place_block <index> sign` placed a sign the game converted to a job
  board block (via the `job_board` recipe match -> `swapJobBoardSign`), and the
  `job_board` quest *completed* — but the chicken's `WAITING_FOR_SIGN` beat did
  **not** advance.
- Root cause: the sign->job-board conversion installs the board via
  `setBlockAndUpdate` and registers it (work handle + its own block-room), but the
  board also becomes its own block-room, so it is not in the matched *walled*
  room's contained blocks. The beat's `isSignConvertedToJobBoard` read only
  `anyMatchedRoomContains(JOB_BOARD_BLOCK)` (the walled room's contained blocks),
  so it missed the board and the beat stalled even though the board was placed and
  the quest completed. `_qtdev place_board` (direct placement) advanced the beat
  because it re-runs the placement side effect.
- The welcome-mat beat does not have this bug because it observes via
  `!flag.getWelcomeMats().isEmpty()` (a registered list the placement path
  populates). The job-board beat had no equivalent registered-list check.

### The fix
- `ChickenArcConditions.isSignConvertedToJobBoard` now checks
  `flag.getWorkHandle().hasAtLeastOneBoard()` first (mirroring `isWelcomeMatPlaced`
  reading `getWelcomeMats`), then falls back to the recipe-match contained-blocks
  check. The conversion path (`swapJobBoardSign -> registerJobsBoard ->
  registerJobBoard -> jobBoards.add`) populates the work handle, so the sign->
  job-board conversion *via the recipe path* now advances the beat.
- Verified: `compileJava` passed; `ChickenArcTransitionsTest` (exercises the sign
  beat) passed; `compileJava` clean.
- Two pre-existing test failures, not from this change (confirmed by stashing the
  change and re-running): `ChickenArcHintsTest.todo_…` (deliberate `fail` marker,
  needs a Forge mod-loading harness — AGENTS.md) and
  `ChickenArcConditionsTest.shouldPeckRun_itemBeat_nullPlayer_returnsFalse`
  (a separate method, `shouldPeckRun`, this change did not touch; fails on the
  committed code too).

## Tear down for a fresh agent — DONE
- Killed server (java child, survived pkill), client, FIFO writers, and the
  runServer gradle wrapper. Re-verified: all gone. World is clean for a fresh agent.

## State of the work
- Uncommitted (per the dev's "consult before changing"): `place_block` command
  (`FlagPlaceBlockCommand.java` + `CommandInit.java` + `TownFlagBlockEntity`
  interiorCells/placeBlockInInterior), `AgentBeatGuidance` (sign beat ->
  `place_block`; chest beat -> "the chicken's beat"), and the `isSignConvertedToJobBoard`
  fix in `ChickenArcConditions.java`.
- `HelperChickenRoomBuildGoal.java` has a pre-existing uncommitted change
  (PECK_TICKS 10->5) that is not mine.

## What a fresh agent will now find
- The sign beat: `_qtdev place_block <index> sign` now advances it (the conversion
  registers the job board in the work handle, which the beat check reads).
- The chest beat: the chicken's, not the agent's (guidance updated).
- `place_block` gives the agent the room's interior cells (its missing "where").
