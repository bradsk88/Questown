# Port-test (GUI pixel oracle)

Catches GUI regressions when porting between Minecraft versions: capture the
canonical screens on the golden branch and the target branch, pixel-diff them.
Built for the 1.19.2 → 1.20.6 migration; version-generic beyond that.

## Invariants (why a diff is meaningful)

- **Same canvas**: the tiling WM on this box forces both clients to 634×675
  (the `--width/--height` args do not stick). The invariant is *identical
  dimensions on both captures*, not a specific size.
- **Same fixture**: a fresh **normal** world, a town flag placed at the world
  spawn, and one townie spawned for it — driven by the dev commands
  `_qtdev townplace <pos>` / `_qtdev townspawn <pos>` (owner paths:
  `SpawnVisitorReward.spawnNew`). No village/growth arc, no gameplay.
  **No `tp` across distance** — see "player death" below.
- **Zero-click menu open**: the console command `_qtdev openvillager <type>`
  opens the per-villager tabbed menu (`showUI` for every type, including
  inventory — the flag-level `showMultiStatusUI` menu has no villager UUID
  and the hook cannot tab it) for the connected player.
- **Content-verified before banking**: each captured PNG is vision-checked
  (recipe in `references/vision-check-local-images.md`) to confirm the
  expected tab is active. Distinct file sizes / md5s prove the screens
  differ from *each other*; only a content check proves they show the
  *right* thing.

## Components

- `QtDiagClient` (client, dev-gated by `-Dquestown.diag=true`):
  - command file channel: `<gameDir>/.qtdiag-cmd`, one line per command
    (the 1.20.6 hook also accepts the same commands as `[qtdiag] <cmd>`
    whispers; the 1.19.2 hook is file-channel only)
  - `shot <name>` → screenshot to `<gameDir>/diags/<name>.png`
  - `tab <type>` → `OpenVillagerMenuMessage` (same message a tab click sends;
    types: inventory|quests|stats|skills|economics|bop)
  - `connect <host> <port>` → quick-connect from the title screen
    (1.19.2: `ConnectScreen.startConnecting` + `ServerData`; the
    `--server/--port` game args do not reliably auto-connect there)
- `capture.sh` — one-branch driver, STAGED. Every stage is a fast
  command (≤60s); launch stages return immediately, wait stages poll
  ≤60s and are idempotent (re-invoke until they pass):
  `prep` → `launch-server` → `wait-server` → `launch-client` →
  `wait-client` → `connect` → `joined` → `fixture` → `shot <name>` ×6
  → `collect` → `check`.
- `check.sh` — content gate: vision-verifies each captured screen against
  its `expect` block in `manifest.json` (menu open, active tab,
  not-death, not-title) BEFORE banking. `check.sh <dir> [screen]`.
- `run.sh` — the diff stage: `run.sh check` (content gate on both dirs),
  `run.sh diff` (asserts identical canvas dims, then `cv diff` per screen
  against the manifest threshold).
- `sweep.sh` — kills any JVM tied to the Questown repos or gradle daemons
  (matched by cwd + main class, so the invoking shell cannot match itself).
- `cv` (see references/cv-tool.md) — the diff engine (PSNR/SSIM/% changed).

## Branch pinning (do not trust names)

- **Golden 1.19.2**: `Questown-119` worktree of
  `1.19.2-0.0.10-alpha.11-unstable` (Forge 43.3.0, Gradle 7.2). The branch
  `1.19.2-0.0.10-alpha.12-unstable` is **not 1.19.2** — it was reused for
  NeoForge 1.20.6 WIP; treat it as history, not a 1.19.2 state.
- **RoomRecipes golden**: `1.19.2-0.0.7-alpha.1` published to `~/.m2`
  (`ca.bradj:RoomRecipes:1.19.2-0.0.7-alpha.1`) before building Questown-119.
- **Target 1.20.6**: main worktree (`1.20.6-neoforge-wip`, Gradle 8.14).

## The fixture drive (per side, ~10 short commands)

```
C=tools/porttest/capture.sh; P=<project-dir>; PORT=<port>; G=run-client
$C prep          $P $PORT $G            # sweep, wipe world, FIFO writer
$C launch-server $P $PORT $G
$C wait-server   $P $PORT $G            # re-run until 'server up' (≤60s each)
$C launch-client $P $PORT $G
$C wait-client   $P $PORT $G
$C connect       $P $PORT $G
$C joined        $P $PORT $G            # prints the spawn coords
$C fixture       $P $PORT $G            # townplace + townspawn at spawn
for s in parent quests stats skills economics bop
do $C shot $P $PORT $G /tmp/collect $s; done   # each runs the death guard
$C collect       $P $PORT $G <out-dir>
$C check         $P $PORT $G <out-dir>  # content gate BEFORE banking
```
The `wait-*` stages exit 0 with a "re-run" note after 60s; just call them
again. `shot` refuses to capture when the log tail shows a death/desync
signal or `list` doesn't show the player. Bank to `goldens/<ver>/` or
`live/<ver>/` only after `check` passes. `wait-server` also verifies the
gradle version in the log matches the project's wrapper (the wrong-project
launched detector) and that the port is actually LISTEN (hex-checked).

## Death and desync guard (run between every open and shot)

The 1.19.2 side desyncs client/server Y by ~64 in this box (observed:
server y=110, client reports y=45; on a superflat world the server spawn
itself is y=-60). The desync ends with "moved too quickly" log lines and a
**death** — and the capture then silently records the *death screen*.
So between `_qtdev openvillager` and its `shot`:

- `grep 'moved too quickly\|died\|left the game' run/logs/latest.log`
  (plus `zgrep -a 'died' run/logs/*.log.gz` — plain grep does not read .gz)
- console `list` → must say 1 player
- if the client log shows `[qtdiag] tab: no tabbed villager menu open`
  after an `openvillager`, the menu is not on screen: stop, diagnose, do
  not bank the next shot.

Place the fixture at spawn and **never `tp` the player across distance** —
a long tp is what triggers the desync here.

## Operational pitfalls (learned the hard way)

- **Launch server/client with `setsid nohup ... &`** — the tiling-WM
  session kills the whole process group on disconnect.
- **`cd X && setsid nohup ./gradlew ... &` backgrounds the `cd` too** —
  the whole list runs in the background subshell and the foreground `echo
  $(pwd)` reports the *old* directory. Use `./gradlew -p <project-dir>` with
  absolute paths, and verify which side actually launched by the log header
  (a11 prints "Gradle 7.2", wip "8.14").
- **Port hex**: `/proc/net/tcp6` shows ports in hex. 25565 = `0x63DD`
  (wip), 25571 = `0x63E3` (a11). Compute with `printf '%X' <port>`; never
  hand-roll (0x63F7 ≠ 25571 — that cost a full misdiagnosis).
- **Kill loops match their own shell.** A `case "$cmdline" in *java*)` scan
  matches the scanning shell (its cmdline contains the pattern text);
  killing it yields a bare `(no output)` from the harness that looks like a
  mysterious failure. Run the sweep from a script file
  (`tools/porttest/sweep.sh`): the script's own cmdline is
  `bash sweep.sh` and cannot match.
- **One client window at a time** on the tiling WM; piling up windows
  wrecked the WM layout and once crashed the agent harness.
- **Process identity**: runServer's java cmdline uses relative classpath
  entries, so `grep Questown` misses it. Find by `/proc/PID/cwd` or port;
  kill by exact PID.
- **Netty in this sandbox**: both sides' server AND client JVMs need
  `-Dio.netty.transport.noNative=true` (epoll native stalls handshakes).
- **FIFO console**: keep a persistent writer open
  (`setsid nohup sh -c 'sleep 999999 > run/server-cmd.fifo'`); with the
  writer holding the fd, repeated `echo cmd > fifo` writes are fine
  (verified: 15+ commands on the 1.19.2 side, no wedge). The wedge only
  bites without the persistent writer.
- **`Screenshot.grab` takes a *directory* on both versions** (bytecode:
  `new File(file, "screenshots")`). The hooks pass `diags/` + a token and
  move the result to `diags/<name>.png`.
- **1.19.2 worktree specifics**: EULA (`run/eula.txt`), client
  `workingDirectory` `run-client/` (shared log4j files wedge the join),
  autojoin port via `-Pautoport`.
- **Brigadier footguns** (dev command trees):
  - a `.then()` chain nests — re-read the edit; a subcommand added under
    the *wrong parent literal* parses as "Incorrect argument for command"
    and the handler never runs. Verify with the console before driving.
  - one `Commands.argument("pos", ...)` builder per subcommand — reusing a
    builder object across subcommands shares its `executes` target.
- **Timeouts: 60s hard cap.** No batched multi-minute polling loops; check
  state between short commands.

## Usage

```
tools/porttest/capture.sh <stage> <proj> <port> <gamedir> [out] [screen]
tools/porttest/check.sh <screens-dir> [screen]    # content gate (vision)
tools/porttest/run.sh [check|diff|all]            # gate + canvas + cv diff
cv diff goldens/1.19.2/stats.png live/1.20.6/stats.png   # ad-hoc one-screen
```

## Known residual differences (PASS-able)

- Text antialiasing edges differ between renderers (expect ~0.1–0.5%
  changed pixels on text-heavy screens).
- The two sides run different worlds, so the background visible around the
  menu differs; judge the menu region, not the skybox.
- Villager face pixels are data-driven (seeded per entity UUID) — the two
  townies are different entities; the face region is expected to diff.
