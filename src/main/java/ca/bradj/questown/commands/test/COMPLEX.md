# commands/test — hold mode (visual inspection after a suite)

The autotest harness normally halts the JVM when the last scenario reports
(`/tmp/...log` shows `RESULT: N/N passed`). Hold mode keeps the world alive
afterwards so a dev client can join and *see* the end state — built for
headless screenshot verification (`grim`) of things a unit test cannot judge
(e.g. the need bubbles of ADR-0011).

## Flow

```mermaid
flowchart TD
    A[ServerStartedEvent with -Dquestown.autotest=true] --> B[run scenarios]
    B --> C{questown.autotest.hold=true?}
    C -- no --> D[halt JVM]
    C -- yes --> E[log HOLDING, keep world running]
    E --> F[register HoldModeLoginListener]
    F --> G[player joins: spectator,<br>teleported to arena, aimed at nearest needy townie]
    F --> H[every 40 ticks: re-aim to still-nearest needy townie<br>4-5 blocks out, square on its eye]
```

- **Skip the halt** — `AutoTestRunner.HOLD` branch near the end of the tick
  listener. The world keeps ticking; `session.lock` still applies, so kill the
  old held server before launching another `runServer`.
- **Interface** — the `-Pautojoin` Gradle property makes `runClient` connect
  straight to `localhost:25565` (`--server/--port` args), bypassing the title
  screen — no mouse/keyboard needed on the client, which matters because
  dev-box input injection (wtype) does not reach the GLFW window.
- **Aim matters as much as position** — the bubble focus contest is an angular
  cone (dot >= 0.9), so the tracker recomputes yaw/pitch from the villager's
  live position. A fixed yaw goes stale the moment the roster wanders.
- **Hysteresis of scenario state** — need bubbles can clear themselves as the
  world keeps living past the assertions (e.g. a villager eventually finding
  viable work). Verify soon after HOLD, don't sit on the held world for hours.
- Fake players (`[AutoTest]`) are excluded from both the login teleport and the
  per-tick re-aim via the bracketed-name check.
- **Aim preference order** — a scenario may post its own target via the System
  property `questown.autotest.aim.door` ("x,y,z" offset from the arena origin,
  set in a postSpawnAction). When present it wins over needy townies; without
  it, a post-scenario needy townie will steal the aim and the thing the
  scenario asserted on (a dead door, say) may never reach the screen. Used by
  `flag/dead_door_bubbled`.

## Launch discipline (hold mode)

- Env: `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8` — the wrong
  JDK breaks Gradle's Groovy compile; a POSIX locale fails javac with
  "unmappable character for encoding US-ASCII".
- Before relaunching: `pkill -f '[G]radlew runClient'`, `pkill -f '[G]radlew
  runServer'`, `pkill -f 'questown[.]autotest'` — always bracket one char or
  the pattern self-matches your own bash argv and kills your shell. A **held**
  server (one launched with `-Dquestown.autotest.hold=true`) **catches SIGTERM
  and survives `pkill`** — after `pkill`, re-check with `pgrep -af '[r]unServer'`
  and `kill -9 <pid>` the java child (the `gradlew` wrapper dies, the server JVM
  does not) if it is still up. Then
  `rm -rf run/world` and confirm port 25565 is free (a surviving held server
  kills the new one with "Address already in use", and holds `session.lock`).
  Note: any `runServer` launch with `-Dquestown.autotest=true` now wipes
  `run/world` itself before boot (gradle `doFirst`), so the manual wipe is
  only needed when relaunching a *non-autotest* server over a locked world.
- Order: server → wait for "HOLDING: server left running" → client
  (`runClient -Pautojoin`). Never join mid-run. Background the launch in the bash
  tool with a bare `nohup ... &` + only an `echo` (a `sleep`/`tail`/`disown` in the
  same call kills the process — see the global `~/.pi/agent/AGENTS.md` "This machine"
  note); the "wait for HOLDING" is a separate check call, not a foreground loop.
- Before using a Minecraft API in hold-mode/test code, grep an existing call
  site — 1.19.2 lacks several 1.20+ methods (`ServerChunkCache.getLoadedChunks`,
  `Level.getBlockEntities`). Scanning `level.getChunk(cx, cz).getBlockEntities()`
  over the arena works.

## Before/after repro (a scenario that bites only the fix)

To confirm a fix, run the *same* scenario twice: once with the working-tree fix (after), once without (before). A scenario only proves the fix if the two runs **differ**. Identical runs (both `throughFloor=false`, both min-Y the same) mean the layout does not exercise the mechanism, not that the fix is wrong — and that is the usual reason a repro "does not bite." A verified "the scenario did not bite" is not a verified "the bug cannot happen": the gap is usually the vehicle, not the phenomenon.

Two vehicle traps, seen this cycle:
- A job that **warps** its deliver (e.g. `organizer/fetch`) never walks its nav target, so a fetch scenario cannot confirm a `navTargetForContainer` fix. A **gatherer** walks (gate ↔ supply chests) and is the vehicle; but leavers are warp-only in the autotest, so that repro is a live / held-realtime, not a held-autotest scenario.
- A chest placed **off the room's scan level** (below it) is never registered as a container, so the job falls to `NO_SUPPLIES` and never navigates. Contained blocks are `yCoord` and `yCoord+1` only; a chest at `yCoord-1` is invisible to every job.

The before-fix run reverts the fixed file(s) to HEAD while keeping the scenario change, then restores — see "A before/after held run must not clobber the uncommitted fix" in `~/.pi/agent/references/questown-dev-launch.md` for the restore method (saved patch or stash, **never** `git checkout`).

The layout that finally bit (`nav/roof_gatherer`, 2026-09): chest at `yCoord+1` (registered) on a plank, its interact spot filled by a head-height grass ledge with a one-block roof hole above it, and a three-step staircase up to the roof. Pre-fix the target (the interact spot at chest height) has no standable interior node, and the ledge top is the only Manhattan-1 node, so the best-effort path goes over the roof and the townie stands on the grass above the chest. Post-fix the pinned target (scan level) has standable interior floor neighbours, so the path goes through the door. A raised chest alone (intact interior floor) never bites: the interior floor spot is always Manhattan-1 from the chest-height target, which beats the roof (Manhattan-2).

Two door traps hit while building it: townies cannot open wooden doors (no `InteractWithDoor` brain task; `useNearbyGates` opens fence gates only), so a closed door stalls the walk at the threshold; and an open door still leaves about 0.2 blocks of clearance beside its swung-open panel, which wedged the townie when the doorway sat on the walk line (the unstick ladder's pushes knock it into the panel). Put the door where the approach never clips the panel: the scenario uses a west-wall door facing EAST, open, hinge-left, with the welcome mat right outside.

Before/after evidence lives in the scenario's RESULT line (`[nav/roof_gatherer] RESULT: roofVisited=…, roomEntered=…`) plus the per-position tracker lines: before = `roofVisited=true, roomEntered=false` (stairs, roof, drop onto the ledge), after = `roofVisited=false, roomEntered=true` (west door, interior floor).

## Why a room-detection *gate* lives in `CrawlPerfTest`, not the perf autotest

A perf autotest scenario cannot gate the wall-walk crawl. The crawl runs inside
the flag-tick, and the flag-tick holds the single server thread. A shape that
makes the crawl O(n²) (a comb, ~12000 visited positions) stalls that thread — a
400×400 comb measured a 5641 ms / 112-tick-behind `Can't keep up` on the *fixed*
(HashSet) code — and the autotest fails on the global `TestExecutor` timeout
(`maxWaitTicks = 100 × villagerCount`, 20 townies = 2000 ticks: "Villager spawn
timed out"), *before* the scenario's own assertion runs. So the scenario fails on
correct code, not on a regression: the gate cannot bite without also failing.
The cost is real regardless of HashSet-vs-ArrayList, so an autotest budget on
this shape is structurally unsound. Keep the gate as the synchronous unit test
`CrawlPerfTest` (RoomRecipes — it drives the crawl directly, asserts `ms < 1000`,
and bites); the perf autotest's `plus_room` stays a measurement (always passes,
reports the `TickProfile`), never a gate.
