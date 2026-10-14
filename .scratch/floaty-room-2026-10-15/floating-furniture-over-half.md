---
title: flag/floating_furniture_over_half — over-halved room accepted, door not dead
status: fixed-pending-verification (root cause settled 2026-10-15 afternoon)
created: 2026-10-15 (morning full-suite run; new [FAIL] not in the 2026-10-10 baseline)
---

# flag/floating_furniture_over_half: over-halved room was NOT rejected

Full autotest suite 2026-10-15 06:48–07:50 (log `/tmp/autotest-full-20261013-r2.log`,
now gone from /tmp): 58 scenarios, 50 PASS, 8 FAIL. 7 of the 8 in the known-fail
baseline; this one is **new**.

## Expectation (blueprint, `TestBlueprintRegistry.floatingFurnitureOverHalfBlueprint`)

4×4 footprint, two-tall cobble walls, open interior — except **3 of the 4
interior cells carry a floating chest at head height** (air at feet). Door
registered via the real wand path. `MIN_ROOM_WALKABLE_FRACTION` = 0.5 →
`required = ceil(4 * 0.5) = 2` standable cells; only 1 is → the
`MultiLevelRoomDetector.hasWalkableInterior` filter must REJECT the room → the
door ends in `deadDoors` with `DoorTrouble.NOT_ENCLOSED`.

Failing run: `deadDoors` empty, the room matched `empty_room` — i.e. the room
was ACCEPTED.

## Probe state (carried from the morning session, uncommitted in
`MultiLevelRoomDetector.java`)

Temporary `[WALKPROBE]` instrumentation: enter/fraction, per-cell closed/open,
floorCells==0 early-return, room@ verdict, and `handleNewRooms` kept/not-kept.

- **Un-instrumented runs FAIL deterministically (2/2)**: the first pass accepted
  the room; the room then re-detects as "new" on every flag tick (75
  `handleNewRooms` passes, all kept) and matches `empty_room`.
- **Fully-instrumented runs PASS (2/2)** with
  `floorCells=4 walkable=1 required=2 -> false` on every pass.
- Chest VoxelShape verified non-empty (0.0625..0.9375) → `isPassable(chest)` =
  false; the filter MATH is correct.

Working hypothesis: in the failing runs the first pass takes an early-return
accept path (floorCells==0 suspected — interior chunks not yet solid when the
filter reads them), and the instrumented build's extra log I/O shifts the
timing enough for the chunks to be solid on every pass. Not yet confirmed:
the failing runs have no probes, so the early-return path is inferred, not seen.

## Root cause (2026-10-15, settled)

**The room's interior foot level was un-flattened random terrain — the scenario
was seed-dependent.**

- The floaty room is built only in `postSpawnAction`, so it is NOT part of
  `blueprint.blocks()` — and `TestArenaPreparer.buildVolume` (the flatten
  region) is derived from `blueprint.blocks()` ∪ the base 15×15 square
  (halfWidth=7). The room footprint (x -9..-6, z 12..15) sits OUTSIDE that
  region; the interior foot cells at y=64 were whatever the random-seed
  terrain gave.
- Solid terrain at the feet → `IsFloorOpen` false everywhere → `floorCells=0`
  → the day-1 storeroom escape hatch ("fully furnished interior, no open
  floor cells") ACCEPTS the room → door not dead → FAIL. Air terrain →
  floorCells=4 walkable=1 required=2 → REJECT → PASS.
- Each autotest boot wipes `run/world` and regenerates with `level-seed=`
  EMPTY (random), `level-type=minecraft: normal`. The morning's "un-instrumented
  FAIL 2/2 vs instrumented PASS 2/2" split was two different world seeds,
  not a build effect — the probes were never causative.
- WALKPROBE evidence (this afternoon): failing run → `floorCells=0 -> accept`
  with zero interior iterations (the 2D walk's spaces carried no INTERIOR_ONLY
  cells at all when the feet level was solid); passing run →
  `spaces=[InclusiveSpace{aa=(-9,12), bb=(-6,15)}]`, all 4 interior cells
  "open at feet but unwalkable (head y+1)", `floorCells=4 walkable=1
  required=2 -> false`, `filterKept=false`, `deadDoors={…=NOT_ENCLOSED}`.
  Pinned seed runs: seed=1 PASS (lucky air interior).

## Fix (fixture, not production code)

`TestBlueprintRegistry.buildFloatingFurnitureOverHalfAndRegister` now clears
the 4×4 footprint at feet (y) and head (y+1) to air before building the room
(there is no `Level.fill` on this branch — explicit `setBlockAndUpdate`
loops). The filter's input is now deterministic: floorCells=4 on every seed.

**Same latent flake fixed in `buildTooShortRoomAndRegister`** (5×5 room at
offset (-9,0,-2), also outside the flatten region; also a REJECTION test that
needs the floorCells>0 path — a solid terrain foot level would have flipped
it by seed too). The two ACCEPT-expected siblings (`floating_chest_room`,
`filled_storeroom_alive`) pass under either terrain (the floorCells==0
escape hatch accepts them too) — not seed-fragile for pass/fail, left as-is.

WALKPROBE instrumentation reverted (file back to HEAD). `run/server.properties`
`level-seed` restored to empty.

## Verification

- 3 consecutive random-seed runs of the flag room set must PASS
  (`only=room`, `only=flo`, `only=too_short` — substring match).

## Logs (some now gone from /tmp after reboot)

`/tmp/autotest-full-20261013-r2.log` (full suite), `/tmp/autotest-floaty-rerun.log`
(un-instrumented fail), `/tmp/autotest-floaty-probe2.log` + `/tmp/floaty-p1.log`
(instrumented passes).
