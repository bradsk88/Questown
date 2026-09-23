---
title: held-run console command set can't perform the real player actions for sign/villager-UI/flag-UI beats, so the agent substitutes a code-derived action that hides the gap
status: needs-triage
created: 2026-09-08
closed: 2026-09-23
priority: p2
---

## Closed 2026-09-23

Every beat in this set is now driveable the real-player way (each cross-checked
in code 2026-09-23):

- **sign / job-board** - `_qtdev place_block <index> sign` places the sign a
  player would (commit 818381a8); the game's own conversion carries it into the
  job board (`TownFlagBlock` SIGNS->job-board, `isSignConvertedToJobBoard`
  observation per the 2026-09-11 note).
- **villager UI / flag UI** - `_qtdev inspect villagers` / `_qtdev inspect
  <flag>` advance the beats via the game's own UI-opened observation
  (`adb4d1be`; the same callback a real UI open fires through
  `TownVillagerUIs`).
- **campfire-sleep** - `_qtdev clickheld <campfire bed head>` at night fires the
  real sleep path; the clock skip is the agent's `time set` action
  (campfire-sleep-freezes.md, superseded 2026-09-09).
- **direct deposit** - `_qtdev deposit` removed (01b1e2be), per the 2026-09-11 note.

The skill documents all four, so the gap is no longer fillable with a
substitute the docs would present as a real action.

# Held-run command set gaps for the chicken-arc beats that need a real player action

Observed while driving a held `village/growth` run (`agentDrive=true`). The
agent drives the player actions via the FIFO console command set
(`_qtdev ...`), but several beats need a **real player action** the command set
can't perform. When a beat can't be driven, the agent is pushed into one of two
bad things: a manual hand-off to the live player, or a **code-derived
substitute** that reaches the same internal state but is not the action a new
player would do. The substitute is worse: it advances the arc, so the gap is
hidden.

## The beats that can't be driven from the console

- **campfire-sleep** (`SUNSET_AND_MAP`) — filed separately in
  `.scratch/chicken-arc-campfire-sleep/campfire-sleep-freezes.md`. Fired by
  `PlayerInteractEvent.RightClickBlock`; `_qtdev click` uses `useItemOn`, which
  doesn't fire that event; the sleep also requires the *paired* wand and freezes
  the player through a real night.

- **sign / job-board** (`WAITING_FOR_SIGN`) — the real action is "hold a sign
  and place it; the game converts the sign to a job board." The console can't
  place a sign *as a player would* through the conversion path. The agent
  instead used `_qtdev place_board` (which calls `flag.placeJobBoard` and drops
  a `JOB_BOARD_BLOCK` directly) — the *wrong action for a new player* (you can't
  place a job board block; you place a sign and the game converts it), but it
  satisfies the underlying condition (`isSignConvertedToJobBoard` checks for a
  job board block in a matched room), so the arc advanced and the gap was
  hidden. The agent "discovered" the job board by reading three code paths
  (`TownFlagBlock.convertItemInHand` SIGNS->JOB_BOARD_BLOCK,
  `ChickenArcTestExecutor` placing JOB_BOARD_BLOCK directly, and the recipe
  `job_board.json` needing `questown:job_board_inputs`), which is exactly the
  re-derive-from-code the held-run discipline forbids.

- **villager UI** (`WAITING_FOR_VILLAGER_UI`) — advances when a villager status
  UI is opened (`TownVillagerUIs` -> `markVillagerUiOpened`). A real
  villager-menu interaction; the console can't open it (`_qtdev inspect
  villagers` reads the tab but doesn't open the real UI).

- **flag UI** (`WAITING_FOR_FLAG_UI`) — likely the same class (a flag-menu
  open observation the console can't drive).

## The failure mode

A blocked step is the run's open question, not a lookup. When the agent can't
drive a beat, it should surface it, not re-derive the mechanism. But the command
set's own framing makes the wrong substitute look legitimate: `_qtdev place_board`
is documented as "the way a player's item-use would, but driven from the
console," so using it to place a job board felt like a real player action, even
though a new player places a sign, not a job board. The substitute reaches the
same internal state, advances the arc, and hides the gap.

## Root cause

The held-run console command set doesn't cover the *real* player actions for
these beats (place a sign, open a villager/flag menu, right-click a campfire).
When it doesn't, the agent fills the gap with a manual hand-off or a
code-derived substitute that satisfies the underlying condition instead of
performing the action a new player would.

## Direction

Make the command set drive the **real action**, not a substitute that reaches
the same state:
- a command that places a **sign** (which the game converts to a job board),
  not one that drops a job board block directly;
- a command that opens a villager's status UI (and the flag's UI);
- a command that fires `PlayerInteractEvent.RightClickBlock` with the paired
  wand for the campfire (and that the sleep advances the clock to dawn, see the
  campfire-sleep issue).

The test of "did the agent do the right thing" is not "did the arc advance," it
is "did the agent perform the action a new player would." A beat advancing is
necessary but not sufficient.

## Notes

- This is the same class as the campfire-sleep bug; filed as a set because the
  shared cause is the command-set gap, not one beat.
- Verified during the run: `_qtdev place_board 5 64 12` advanced
  `WAITING_FOR_SIGN -> WAITING_FOR_PRESSURE_PLATE` with
  `signPlacedAsJobBoard=true`, but the action placed a job board block, not a
  sign. The real action (place a sign) was never performed.
- Log: /tmp/village-server.log (2026-09-08).
- 2026-09-11: one instance of this class is now fixed - `_qtdev deposit` was
  removed (commit 01b1e2be). It wrote an item into any registered container and
  returned success regardless of whether the `UNMET_ITEM` need it was meant to
  clear actually cleared, so an item quest could "complete" the game-state way
  while the supply-chain lesson (a job produces the item into its
  job-block's container) was never played - the gap hidden, exactly the
  failure mode this issue names. This closes the deposit case only; the
  sign/villager-UI/flag-UI/campfire-sleep beats in the set are still open.
- 2026-09-11: the "where" half of the sign case is now fixed - `_qtdev place_block`
  lists a registered room's interior cells as indices, so the agent places a sign
  in a cell the room actually contains rather than a guessed coordinate (which
  before destroyed the room). The agent's `place_block <index> sign` now advances
  the sign beat end-to-end (the sign->job-board conversion registers the job board
  in the work handle, which `isSignConvertedToJobBoard` reads; that observation fix
  is the same commit). This addresses the sign case's "where" gap only; the
  villager-UI/flag-UI/campfire-sleep beats in the set are still open, and the
  sign case's "code-derived substitute" half (the beat needed the board placed
  directly, `place_board`, not the sign conversion) is closed by the same commit's
  `isSignConvertedToJobBoard` fix. Still open overall: villager-UI, flag-UI,
  campfire-sleep.
