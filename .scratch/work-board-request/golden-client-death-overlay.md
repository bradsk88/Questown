# 1.19.2 client "You Died" overlay on every join (no server-side death)

status: needs-triage

## Symptom

Every `runClient` (golden, 1.19.2, this headless box) joined this evening renders a
"You Died!" death screen over a living world (hearts visible, world rendering, score
visible). The client is then unable to show game menus for capture; the `shot` hook
captures the death overlay.

## What is proven

- The server logs NO death event (no "slain/died/killed" for any world tried).
- `data get entity Dev Health` reports 0.0f on the server, yet the server applies
  menu-opens to the player fine (`_qtdev open_jobs` -> "[open_jobs] work board for
  Dev" - the server treats the player as functional).
- Reproduced on THREE world states: the 09-30 fixture world (warp state, easy),
  a peaceful-converted copy, and a FRESH world (new `run/world`, fresh playerdata).
- Re-join does not clear it (no auto-respawn path in the 1.19.2 client; the hook
  has no respawn command; 1.19.2 has no `ServerPlayer.forceRespawn()`).
- The 09-30 golden bank (parent/stats/skills/economics/bop) passes its manifest
  checks today, so this client CAN render menus - the overlay state entered during
  this evening's sessions (first observed ~05:00) and persists across world swaps,
  so it is not world state and not player data.
- Client log around join: narrator libflite load fail (benign), missing textures
  (benign), `Missing NBT tag on 1 town_wand` at 05:13:48 (the dev kit grants a
  town_wand; unconfirmed whether the missing NBT is involved).

## Tried without success

- `difficulty peaceful` (server + properties), fresh playerdata (dev.dat wiped x3),
  fresh world (renamed the old one), re-join, waiting 60s+.
- `effect give Dev minecraft:instant_health` was NOT reachable in time (the player
  overlay blocks client-side; the server-side effect would need the same test - not
  done, parked by budget).

## Cheap next probes (a later session)

1. Server-side: `effect give Dev minecraft:instant_health 1 255 true` right after
   join, then check the `Health` probe and whether the overlay clears (tests
   "server health is 0 and the overlay is a truthful render of it").
2. The `town_wand` missing-NBT grant: try joining with an empty dev profile / the
   dev-kit grant disabled (if such a flag exists) to see if the overlay stops.
3. A 1.19.2 vanilla client (no Questown) on the same world: overlay present?
   (isolates mod vs launcher/box).
4. `crash-reports/` in run-client for this box at join time.

## Why this matters

The work-screen golden capture (the 1.19.2 half of the parity pair) is the one
blocked item; the wip half is banked and oracle-verified, and the other six golden
screens still stand.

## RESOLVED 2026-10-01 evening

Root cause: the "latch" WAS the documented stuck-death state (Health 0.0f +
DeathTime persisted on the server-side entity), NOT a separate client-side
overlay. It lived in the playerdata .dat — which is named by PLAYER UUID on
this box (380df991-f603-344c-a090-369bad2a924a.dat, mtime 05:17 from the
zombie-death session). `capture.sh unstick` wiped and verified a LITERAL
`Dev.dat` that never existed, so it reported "dat gone - clean" while the
real dat sat untouched; every fresh client then re-loaded the death state
and the server replayed the death screen, with `data get entity Dev Health`
= 0.0f and DeathTime = 24833 ticks.

Fix: `capture.sh unstick` now wipes ALL `playerdata/*.dat{,_old}` and
verifies the directory is clear (fixture worlds hold one human player).
After a manual wipe of that one dat (server down), the rejoin was clean:
Health 20.0f, zero death lines in the client log, no overlay on the
vision-checked capture, and the golden work board was captured, gate-passed
and banked (goldens/1.19.2/work.png).

status: resolved
closed: 2026-10-01
