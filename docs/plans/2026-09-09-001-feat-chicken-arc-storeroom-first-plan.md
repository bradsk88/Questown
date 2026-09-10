---
title: Chicken arc — build the storeroom before the sleep
type: feat
status: active
date: 2026-09-09
origin: held village/growth play-test 2026-09-09 + docs/adr/0001-chicken-arc-beat-phase.md
---

# Chicken arc — build the storeroom before the sleep

## Overview

Re-sequence the helper-chicken onboarding arc so the player **builds and
registers a room before sleeping by the campfire**, and so the **chicken — not
the player — drops the "come back later" chest (filled map + wooden axe) into
that room**, turning it into a storeroom and completing the storeroom quest.
The job-board lesson then moves to *after* the sleep, into a **second, fresh
room**, so the chest and the job board live in two different rooms and can never
collide in one.

This is a re-sequence of the existing arc, not a new one. The `ChickenBeatState`
enum is a persisted flag-NBT field but has no live onboarding in the wild, so the
order may be changed freely (open decision O1).

## Problem Frame

A held `village/growth` play-test (2026-09-09) surfaced that the chest beat can
never be completed in a way that does not also break the job board. Verified in
code and in the run log:

- The chest beat and the job-board beat are two conditions on **the same
  matched room**. `ChickenArcConditions.isChestPlaced` and
  `isSignConvertedToJobBoard` both delegate to `anyMatchedRoomContains(...)`,
  and a room matches exactly one recipe (the top-weighted one). A room cannot be
  both a store and a job board.
- The only matched room at the chest beat was the job-board room. Dropping a
  chest into it (by any means) made it stop matching `job_board`, matched
  nothing, and `room_destroyed` / `quest_lost job_board` fired. The chest had
  **no room it was allowed to be in.**
- The one *worked example* of a chest in the world was the chicken's own
  `HelperChickenSunsetChestGoal`, which spawns the map+axe chest **in the open**
  (near the player), never in a room. `isChestPlaced` needs a chest in a
  *recipe-matched* room, so that example actively demonstrated the wrong thing:
  the player matched it and put a chest in the open, which satisfies nothing.
- The guidance pointed the player at "the registered room" (singular) — which was
  the job board's — so every signal said "the one full room," and the only
  example said "chests go in the open." Neither is "a chest in a room."

The dev's resolution is to re-sequence the arc so the chest room and the
job-board room are two different rooms, and the chicken builds the storeroom
(drops the chest into the player's registered shell), which both completes the
chest beat and *shows* the player a chest in a room. The player then learns the
"build a room → it becomes something" lesson on the storeroom and re-applies it
for the job board.

## The new arc

Player-facing sequence (beats in brackets are `ChickenBeatState`; the sleep and
the villager arrival are the existing consequences, not new beats):

1. Player discovers flag and chicken.
2. `WAITING_FOR_STICK` — player gets the town wand.
3. **quest intro** — the chicken encourages the player to right-click the flag
   (open the flag / quest tab); the agent inspects it (`_qtdev inspect <flag>`).
   Resolved O2: this is the flag UI, not a new beat.
4. `WAITING_FOR_WALL_BLOCK`, `WAITING_FOR_DOOR`, `WAITING_FOR_WAND_ON_DOOR` —
   player builds and registers a room shell (the store shell).
5. `WAITING_FOR_CHEST` — **chicken walks into the shell, waits for the player to
   be in it, and drops a chest holding the filled map + wooden axe; the shell
   matches the `store_room` recipe and the beat completes** (chicken-driven, not
   player-driven; resolved O3: smallest storeroom recipe).
6. `SUNSET_AND_MAP` — chicken says "come back at sunset"; player returns and
   sleeps. **No chest spawns here anymore** (the map+axe is what step 5 dropped).
7. *Villager arrives* (existing "visitors arrive after players sleep" mechanic;
   not a beat, lands right after the sleep).
8. **quest re-nudge** — the chicken re-encourages the player to right-click the
   flag (the flag UI, same as step 3; resolved O2 follow-up), then the player
   builds a **fresh** room by re-running the wall/door/register flow (resolved
   O5: not a new beat), and `WAITING_FOR_SIGN` — job board in the new room.
9. `WAITING_FOR_PRESSURE_PLATE`, then the existing later beats, unchanged.

### Why this dissolves the trap

- The store shell (step 4) and the job-board room (step 8) are **two different
  registered rooms**. The chest lives in the store shell; the job board lives in
  the new room. They never compete for one matched room.
- `isChestPlaced` becomes true because the chicken's chest is in the store
  shell, which matches a storeroom recipe **because the chest is in it** —
  exactly parallel to the sign making the job-board room match `job_board`.
- The player sees a chest *in a room* (the chicken's worked example), never a
  chest in the open, so the "chests go in the open" inference is gone.

## Phases

### Phase 1 — Re-sequence the beats (enum + transitions)

Reorder `ChickenBeatState` and update `ChickenArcTransitions` /
`ChickenArcConditions` to the new order. The `chickenSunsetChestSpawned` bit and
the `HelperChickenSunsetChestGoal` are the map+axe spawner; in the new arc they
move to the chest beat (step 5), not the sleep.

- Reorder the enum: `… WAND_ON_DOOR (store shell) → CHEST → SUNSET_AND_MAP →
  WAND_ON_DOOR#2 (job-board room) → SIGN → …`. (Persisted field, safe to reorder
  per O1.)
- `SUNSET_AND_MAP` condition stays `sleepHappenedToday`; remove the chest-spawn
  coupling from it.
- `CHEST` condition stays `isChestPlaced` (a chest in a recipe-matched room),
  but it is now satisfied by the *chicken's* drop, not the player's.
- Add the second room/door/register set (step 8) after the sleep.

### Phase 2 — Chicken drops the storeroom chest (new/repurposed goal)

Repurpose `HelperChickenSunsetChestGoal` (or add a sibling) so that on the chest
beat the chicken walks into the **registered store shell**, waits for the player
to be in the room, and drops a chest holding the filled map + wooden axe.
The drop makes the shell match a storeroom recipe, so `isChestPlaced` goes true
and the beat completes. Keep the existing arrival / stuck-teleport logic
(`hasArrived`, `pickChestPos` on the chicken's ground Y) from
`HelperChickenSunsetChestGoal`; change the target from "a cell near the player"
to "a cell inside the registered store shell."

- Target = an interior cell of the registered store shell (not the open floor).
- Contents = filled map of the flag area + wooden axe (the current sunset-chest
  contents; confirmed "map + axe").
- The `chickenSunsetChestSpawned` bit and the sunset-texture bubble flip move to
  the chest beat.

### Phase 3 — Job board in a fresh room (post-sleep)

The job-board lesson moves after the sleep into a new room. The player builds a
second shell and registers it by **re-running the existing wall/door/register
flow** (no new beat; resolved O5), then places the job board (sign) in it. The
sign beat's `isSignConvertedToJobBoard` then keys on the *new* room.

- No second `WAND_ON_DOOR` beat; the player re-runs the door/register actions on
  a new shell after the sleep.
- `WAITING_FOR_SIGN` keys on the new room's sign, not the store shell.

### Phase 4 — Guidance (chicken narration + observe)

Update the in-world guidance so it never points at the wrong room again, and
shows a chest in a room rather than in the open. This is the `QtDevInspectCommand`
`whereToAct` / `chickenBehavior` and the `VillageGrowthExecutor` UNCLEAR line.

- Chest-beat `where` no longer says "the registered room" (which was the job
  board's); it reads as the chicken's worked example (a chest in a room).
- `VillageGrowthExecutor` UNCLEAR: "the villagers need a chest in a room" instead
  of "no registered container exists."
- No behind-the-curtain terms (container / recipe / room detection) in any
  player-facing line.

### Phase 5 — Tests (ledger)

- Re-point the chicken-arc autotests (`ChickenArcTestExecutor`) at the new order;
  the chest beat is now chicken-driven, so its assertion is "the chicken dropped
  the chest into the store shell and `isChestPlaced` is true," not "the player
  placed a chest."
- Add a case that the job-board room is a *different* registered room from the
  store shell and that a chest in the store shell does **not** destroy the
  job-board room.
- Keep the pure `ChickenArcTransitions` unit tests (they assert the logic, not
  leaf data — ADR-0001).

## Vocabulary → code

| Domain term | Code |
|---|---|
| beat | `ChickenBeatState` (persisted enum; `TownFlagBlockEntity.chickenBeatState`) |
| advance / "condition met" | `ChickenArcTransitions.advance` + `isSatisfied` |
| observer of world conditions | `ChickenArcConditions` (`isChestPlaced`, `isSignConvertedToJobBoard`, `anyMatchedRoomContains`) |
| drives the arc each tick | `ChickenArcController` |
| chicken's worked-example chest | `HelperChickenSunsetChestGoal` (today spawns in the open; repurpose to the store shell) |
| the (beat, phase) presentation | `ChickenArcPresentation` (ADR-0001) |
| chicken pecks a beat target | `HelperChickenBeatPeckGoal` + `HelperChickenBeatOffsets` |
| a chest in a matched room | `TownContainers.getAllContainers` / `anyMatchedRoomContains` |
| storeroom recipes | `store_room` … `storeage_facility` (chain; onboarding superset — see `.scratch/storeroom-quest-completion`) |
| agent-facing guidance | `QtDevInspectCommand` (`whereToAct`, `chickenBehavior`), `AgentBeatGuidance` |
| agent observe lines | `VillageGrowthExecutor` (KNOWN / UNCLEAR) |

## Test ledger

| Case | What it proves | Status |
|---|---|---|
| chicken-arc beats advance in the new order | `ChickenArcTransitions` unit (logic, not data) | update |
| chest beat completes on a *chicken*-dropped chest in the store shell | `ChickenArcTestExecutor` | new |
| a chest in the store shell does not destroy the job-board room | held run / room-detection | new |
| job board completes in a *second* registered room | held run | new |
| `SUNSET_AND_MAP` no longer spawns the chest | `ChickenArcTestExecutor` | update |
| no player-facing line names container/recipe/detection | lint of `QtDevInspectCommand` + `VillageGrowthExecutor` | new |

## Open decisions

- **O1 — reordering the persisted enum.** Settled: no live onboarding; reorder
  freely, no migration.
- **O2 — "player learns about quests" (step 3).** Settled: the chicken
  encourages the player to right-click the flag; the agent inspects it. This is
  the flag UI, not a new beat. Follow-up settled: the *second* "player learns
  about quests" (after the villager arrives) is the same flag-UI nudge — the
  chicken re-encourages the player to right-click the flag; the quest tab does
  not open on its own (keeps the arc's established chicken-driven pattern).
- **O3 — which storeroom recipe the shell matches.** Settled: `store_room`
  (smallest of the chain; onboarding superset per
  `.scratch/storeroom-quest-completion`). The chicken's single chest tips the
  shell into `store_room`.
- **O4 — "come back later" contents.** Settled: map + axe (the current
  sunset-chest contents).
- **O5 — how the player registers the second room.** Settled: re-run the
  existing wall/door/register flow on a new shell; no new beat. Phase 3 keys the
  sign on that new room.

## Progress (held run, 2026-09-10)

- The re-sequence is implemented and the unit tests pass (the deliberate
  `ChickenArcHintsTest.todo_` marker is the only failure).
- A held `village/growth` run advanced the arc through the new order
  (`WAITING_FOR_STICK` -> `WAITING_FOR_WALL_BLOCK` -> `WAITING_FOR_DOOR` ->
  `WAITING_FOR_WAND_ON_DOOR` -> `WAITING_FOR_CHEST`) and reached the chest beat.
- **Follow-up (unconfirmed):** at the chest beat the goal stood down with
  `no registered rooms` and `registered-doors=1 rooms=0` — the registered door
  was not an enclosed room in `getAllRooms()`. A second run hit
  `no passable cell inside the registered store shell` instead (the room was
  detected but the interior cell at the chicken's Y was not passable). The two
  failures point at the registered door not reliably yielding an enclosed room
  at the chicken's ground Y; not yet root-caused. The re-sequence itself is not
  in doubt.

## Progress (2026-09-10, room-build beat)

- Decision resolved: the chicken builds the room itself, on a **new
  `WAITING_FOR_ROOM_BUILD` beat** inserted after `WAITING_FOR_WAND_ON_DOOR`
  and before `WAITING_FOR_CHEST` (not absorbed into the register beat).
- Implemented: `HelperChickenRoomBuildGoal` + `ChickenRoomShellPlan` (pure
  layout), the beat/condition/presentation, agent guidance, `_qtdev inspect`
  narration, the `roomBuilt` observation, and lang keys. The chicken walks to
  the registered door and lays a 3×3×3 cobbleshell around it (door row squared
  to a single-cell opening, two-cells-deep interior, no ceiling), one block per
  peck; the beat advances when the room scan detects an enclosed room at the
  door.
- Unit tests pass (the deliberate `ChickenArcHintsTest.todo_` marker is the
  only failure). `compileJava --rerun-tasks` is green.
- Confirmed (2026-09-10, held `village/growth` run): the chicken lays the
  blocks, the room registers (`room_created` broadcast, beat advanced to
  `WAITING_FOR_CHEST`). Three failures found and fixed on the way: the peck
  goal starved the room-build goal at the same tier (`shouldPeckRun`), the
  shell layout was a plus not a closed box (rebased on
  `InclusiveSpace`/`getWallPositions`, `frameFor` made `along`/`inward`
  perpendicular), and the door was half-placed (the run now uses `_qtdev
  place_door`, not `setblock`). See helperchicken/COMPLEX.md.
