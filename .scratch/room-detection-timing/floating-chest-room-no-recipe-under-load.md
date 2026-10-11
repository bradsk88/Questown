---
title: floating_chest_room autotest is load-dependent — NO_RECIPE under concurrency, passes alone
status: ready-for-agent
created: 2026-10-13
---

## Context

The `floating_chest_room` autotest scenario (flag category, jobs track) is flaky.
A parallel confirmation sweep (4 concurrent standalone servers, 2026-10-13)
characterised the flake as **load-dependent**, not random.

## Reproduction (deterministic under load)

`run/psweep.sh` parallel sweep, `-Dquestown.autotest.only=floating_chest_room`:

- **4-way concurrent: 4/4 FAIL**, all with the identical signature:
  `[autotest] deadDoors after monitor = {BlockPos{x=-5, y=64, z=8}=NO_RECIPE}; rooms=1`
- **Serial / alone: mostly PASSES.** A lone run in the same session came back
  `RESULT: 1/1 passed (ALL PASS)` with `rooms=2` and a `recipe_created [room.store_room, [-5, 8]]`.
  An earlier serial probe was 1 pass / 3 fail.

The `Can't keep up! ... Running 2040ms or 40 ticks behind` lines that appear in
the parallel runs are boot-time contention from 4 simultaneous JVM startups; the
room/recipe verdict settles afterward and is the real signal (not a boot artifact).

## What the signature means

- `rooms=1` (not 2): only ONE recipe match registers. The pass case has two — the
  floating-chest room at (-5,8) **and** the base room both match `store_room`.
- `[-5,8]=NO_RECIPE` (not `NOT_ENCLOSED`): the floating-chest room **is detected**
  as a room; what fails is the **recipe match** — the room does not match
  `store_room` under load.

So room *detection* works; **recipe matching for a room whose chest sits at head
height (y+1, a multi-level / "floating" chest) is timing-dependent and loses
under concurrent server load.**

## Acceptance criteria

- Root-cause why the floating-chest (multi-level) room fails the `store_room`
  recipe match under load — is the chest not yet in the room's block set when the
  match runs, or does the resumable multi-level detector lose state, or does the
  recipe-match monitor window close too early under a tick stall?
- `floating_chest_room` passes a 4-way `psweep.sh` sweep (4/4), not just serially.
- No regression to the base-room match (still 2 matches when both rooms are valid).

## Root-cause investigation (read-only, 2026-10-13)

### What is verified (read in code + confirmed in the sweep logs)

1. **`NO_RECIPE`** = a room object exists at the door but is not in the current
   recipe-match set. Set in `TownRoomsMap.recomputeDeadDoors()`
   (`TownRoomsMap.java:489-500`): `NOT_ENCLOSED` if no room at the door, else
   `NO_RECIPE` if the room is not in `matchedRooms`.
2. **deadDoors does NOT lag the match.** A `ChangeListener` is registered on
   `ActiveRecipes` and calls `recomputeDeadDoors()` on
   `roomRecipeCreated/Changed/Destroyed` (`TownRoomsMap.java:118-150`). So the
   moment a match is computed, the door clears. The failure is therefore that the
   match is **never computed**, not that the verdict lags it.
3. **The match only sees blocks in `room.getSpaces()`.**
   `RecipeDetection.getActiveRecipes` → `getBlocksInRoom` →
   `RecipeDetectionClean.getBlocksInRoom(level::getBlockState, room.getSpaces(), …)`
   (`RoomRecipes: ca.bradj.roomrecipes.recipes.RecipeDetection:73-88`). The
   head-height chest (y+1) is only in the room's spaces if the **multi-level
   extension** has pulled that level in.
4. **The pipeline is resumable and flag-ticker-driven.** `TownRoomsMap.tick`
   (driven by `TownFlagTicker.tick` → `roomsHandle.tick`, `TownFlagTicker.java:108`)
   creates a `MultiLevelRoomDetector` when idle and advances it one step per tick
   via `proceed()` (`TownRoomsMap.tick:203-244`). `proceed()` does **at most one
   level-detection step and one room recipe-match per call**; unfinished detectors
   are requeued (`roomDetectors.add`), rooms matched one-per-tick via `roomsToScan`
   (`MultiLevelRoomDetector.proceed`).

### Empirical signature (from the 2026-10-13 sweep logs)

- **Passing (alone):** build+door-register at `21:33:36` →
  `recipe_created [store_room, [-5,8]]` in the **same second** → `deadDoors={};
  rooms=2` at assert. The match is immediate.
- **Failing (4-way load):** build+door-register at `21:39:06`
  (`deadDoors={[-5,64,8]=NOT_ENCLOSED}`) → **no `recipe_created` for `[-5,8]`
  ever** → `deadDoors={[-5,64,8]=NO_RECIPE}; rooms=1` at assert
  (`21:39:22`, 16s later). The room is detected (NOT_ENCLOSED → NO_RECIPE) but the
  match is never created. The base room `[7,3]` matches in **both** cases.
- No flag-tick-halt lines (`stopped ticking` / `No players`) in the failing log —
  so it is not a no-players/distance pause; the flag kept ticking.

### Hypothesis (leading, needs empirical confirm)

The floating-chest room's `store_room` match requires the chest (at head-height
y+1, air at its feet) to be inside the room's `getSpaces()`. That only happens once
the **multi-level extension** attaches the upper level to the room. Under concurrent
server load that extension does not complete (the room stays single-level, no chest
in its contents), so the match is never created → the door stays `NO_RECIPE`. The
base room needs no such extension (its matching block is at the scan level), so it
matches in both cases. The "immediate-or-never" pattern (not "slightly late")
points to the extension stalling under load, not merely taking a few extra ticks.

Open sub-question: *why* the multi-level extension stalls under load — candidate
causes to test: (a) the wall/door predicates (`WallDetection.IsWall`/`IsDoor`) read
stale/incomplete block state while the server is behind on chunk ticking; (b) the
resumable detector re-creates/aborts its upper-level pass under load; (c) a
`MAX_ROOM_SCAN_ITERATIONS` / `MAX_ROOM_DIMENSION` budget is hit differently when the
upper level is present.

### Verification plan (needs gradle — run after the live playtest ends)

- Add a dev-only diagnostic on `MultiLevelRoomDetector.proceed()` / the
  `newRoomsHandler`: per detection cycle, log the door's room, its `getSpaces()`
  y-range, and whether the head-level chest cell is present. Run the 4-way sweep;
  confirm the failing runs show the chest cell absent from `getSpaces()` while the
  passing runs show it present.
- Run a 1/2/4-way concurrency gradient to find the tipping point (is it purely
  concurrent-load, or does even serial occasionally fail?).
- Under load, check chunk-tick/chunk-load state for the room's column when the match
  is missing (does `level.getBlockState(chestPos)` read CHEST or AIR at the moment
  `getBlocksInRoom` runs?).

### Candidate fixes

- If the extension stalls under load: complete the multi-level pass for a bounded
  footprint synchronously (don't spread a 5×5 two-tall room across ticks), or make
  the autotest monitor wait for pipeline quiescence instead of a fixed tick count.
- If the match runs before the upper level is attached: re-run the recipe match on
  the room when the multi-level extension adds a level (not only on the first pass).
- Keep the `room-3x3` walkability fix intact; this is a distinct recipe-matching /
  multi-level-timing path.

## Notes

- Sweep tooling gap hit while reproducing: `psweep.sh capture` dumps the raw
  cmdline, but injecting `-D` props (the autotest selector) requires splicing them
  into the **JVM-options zone (before `-p`)**, not appending to the end — the end
  is the main-class-arg zone and dies with `joptsimple UnrelatedOptionException:
  D is not a recognized option`. Filed for the tooling retro; a `psweep.sh
  inject -D…` / autotest-selector flag on `launch` would make the sweep a one-liner.
- Related but distinct: `room-3x3-chest-instantly-destroyed.md` (day-1 playtest)
  was a *different* root cause (`hasWalkableInterior` counting a feet-level block
  as a low roof) and is fixed; this one is recipe-matching timing under load.
