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
  inventory - the flag-level `showMultiStatusUI` menu has no villager UUID
  and the hook cannot tab it) for the connected player.
- **Same fixture age**: the fixture state is time-dependent - townies
  work, jobs complete, quests and job unlocks progress while the world is
  up. A re-capture against a world that has been running for hours will
  diff against a bank shot minutes after `prep`, independent of any code
  change (observed: the golden skills pane and quest pages drift within
  the hour). Re-bank a screen from a freshly `prep`-ed world, or from a
  world at the same age as the bank it is compared against. Both
  branches can run **in parallel** (separate worktree + port, e.g.
  1.20.6 on 25565, 1.19.2 on 25571); keep both servers up and switch
  clients per branch instead of killing a server to free its port.
- **Content-verified before banking**: each captured PNG is vision-checked
  (recipe in `references/vision-check-local-images.md`) to confirm the
  expected tab is active. Distinct file sizes / md5s prove the screens
  differ from *each other*; only a content check proves they show the
  *right* thing.

## Components

- `QtDiagClient` (client, dev-gated by `-Dquestown.diag=true`):
  - command file channel: `<gameDir>/.qtdiag-cmd`, one line per command
    (the 1.20.6 hook also accepts the same commands as `[qtdiag] <cmd>`
    whispers; the 1.19.2 hook is file-channel only).
    **The client gameDir is per-branch `build.gradle`** (`workingDirectory`
    of the `client` run) — currently `run-client/` on both branches, not
    `run/`. If a `shot` "never lands", check the client process's CWD
    (`readlink /proc/<pid>/cwd`) and write the command file there.
  - `shot <name>` → screenshot to `<gameDir>/diags/<name>.png`
  - `tab <type>` → `OpenVillagerMenuMessage` (same message a tab click sends;
    types: inventory|quests|stats|skills|economics|bop)
  - `connect <host> <port>` → quick-connect from the title screen
    (1.19.2: `ConnectScreen.startConnecting` + `ServerData`; the
    `--server/--port` game args do not reliably auto-connect there). **1.19.2-only**: the 1.20.6 hook logs `[qtdiag] unknown
    command: connect` (observed 2026-09-30) — the 1.20.6 path is
    `-Pautojoin` (`--quickPlayMultiplayer`), which only takes effect when
    the server is already LISTEN.
  - `cursor <x> <y>` → park the pointer at a fixed window-pixel position
    (reflection into `MouseHandler.xpos/ypos`). The `shot` stage sends it
    before every screenshot, so hover-dependent render state (the
    advancements-pane tooltip fade dims the pane to 70% when the cursor is
    over it; hovered card highlights) is identical across captures
  - `inquire <x> <y> <z> [item]` → send `AddWorkFromUIMessage(INQUIRED)` for
    a job block at the coords (the same message a work-board card click
    sends), opening the WorkRequestConfirmScreen — the console-command
    stand-in for "click this work card" (added for the G1 gui migration)
  - `flagtab <type> [x y z]` → `OpenFlagMenuMessage(FlagInfo.dumb(pos,false,false),
    type)` at a flag pos (default **0 64 0 — pass the real flag pos**, the
    server refuses a pos with no flag block): opens the FLAG-scoped menu tab —
    `quests`/`villagers` → QuestsScreen/MultiStatusScreen, `economics` →
    TownEconomicsScreen, `bop` → the block-of-progress screen (added for
    the paged-screen migration; the console path may be a dead pipe on a
    fresh launch, the diag file is not)
  - `vopen <type>` → a villager's own tab menu from the open flag
    villagers-menu context: reads `MultiStatusMenu.getFlagInfo().flagPos()` +
    the first villager UUID synced to the screen (`MultiStatusScreen
    .syncedData`, public static), sends the same `OpenVillagerMenuMessage`
    the console's `_qtdev openvillager <type>` does. Sequence per screen:
    `flagtab villagers <x y z>` → `vopen <type>` → `cursor 1 1` → `shot
    <name>` (types: inventory|quests|stats|skills|economics|bop). Added this
    session because the server stdin bridge (fifo console) was a dead pipe
    and the `_qtdev openvillager` opener had no diag equivalent
  - `cmd <line…>` → sends a `ServerboundChatCommandPacket` (a `/`-prefixed
    player chat command) so the server dispatches the line with the player
    as source — the bridge for ANY console command when the stdin fifo is a
    dead pipe (e.g. fixture `cmd _qtdev townplace <x y z>`, `cmd _qtdev
    townspawn <x y z>`, `cmd _qtdev open_jobs`). The 1.20.6 `Player` API has
    no `chat(String)` (it died in the port; `ServerboundChatCommandPacket
    (String)` is a working player-source command packet — verified by
    effect, the command dispatches as the player) — do not invent a
    player send-chat method
  - **`dual_cmd` (fixture + shot stages): every console command is now
    sent BOTH through the diag `cmd` hook and the console FIFO.** Whichever
    bridge is live per branch/environment lands; the dead one just doesn't
    respond. A double delivery is harmless (a screen re-opens; list/data
    re-log and the readers take `tail -1`). `cmd` is unknown to the 1.19.2
    hook (it logs it), where the console is the live half; on 1.20.6 the
    `cmd` hook is the live half when the console is a dead pipe. If you drive
    by hand, the loop above (flagtab/vopen/cursor/shot) still works and was
    used for the 1.20.6 re-bank 2026-10-02 (all 7 screens: check PASS,
    run.sh diff PASS).
  - **`shot` is async** — the mover polls up to 8s; `diags/<name>.png` may
    appear a few seconds after the send. `ls` once and miss it, don't
    re-capture. A capture that shows the "Connection Lost / Server closed"
    screen means the client was mid-reconnect at grab time - wait for the
    client to settle and re-capture; do not read it as a GUI failure
- `capture.sh` — one-branch driver, STAGED. Every stage is a fast
  command (≤60s); launch stages return immediately, wait stages poll
  ≤60s and are idempotent (re-invoke until they pass):
  `prep` → `launch-server` → `wait-server` → `launch-client` →
  `wait-client` → `connect` → `joined` → `fixture` → `shot <name>` ×6
  → `collect` → `check`.
  - **`run/logs/latest.log` is shared across sessions and a prior run's
    server can still be writing it.** A `joined`/`fixture`/`townspawn` line
    you grepped may belong to the previous session, not yours — read the
    generation, not the line: the timestamp must be AFTER your own
    launch/writer start. `prep` now handles this: after `sweep.sh` it also
    wipes `run/logs` and the client's `diags/` + stale `.qtdiag-cmd` (the
    sweep already killed the repo's JVMs, so no live writer to race) — the
    log and the shot bank are single-run by construction. If you drive
    manually without `prep`, apply the reset yourself: `sweep.sh` + kill any
    old FIFO writer + `rm -rf run/logs`, then relaunch. A `state.py`
    fresh-shot/server list is the cheapest pre-check that you aren't
    intermixed with a live prior run.
- `check.sh` — content gate: vision-verifies each captured screen against
  its `expect` block in `manifest.json` (menu open, active tab,
  not-death, not-title) BEFORE banking. `check.sh <dir> [screen]`.
- `run.sh` — the diff stage: `run.sh check` (content gate on both dirs),
  `run.sh diff` (asserts identical canvas dims, then `cv diff` per screen
  against the manifest threshold).
- `sweep.sh` — kills any JVM tied to the Questown repos or gradle daemons
  (matched by cwd + main class, so the invoking shell cannot match itself).
- `state.py` (`capture.sh state`) — verified state for both branches in one
  printout: server pid → LISTEN port (from /proc fds), client CWD (= its
  gameDir), gradle wrappers, FIFO-writer, stale `.qtdiag-cmd`, fresh-shot
  mtime. Run it before assuming anything about what is running — process
  listings alone cannot tell the two branches' servers apart.
- `rebank.sh [wip|a11|both]` — full deterministic bank refresh: per branch
  `prep → launch → join → fixture → shot ×6 → gate → bank`, both branches
  in parallel (wip on 25565, a11 on 25571; ~45 min). Wipes both worlds.
- Banks carry **sidecars**: every banked `<screen>.png` gets a
  `<screen>.png.json` (captured-at, world-age-min, cursor state, branch,
  commit) written by `capture.sh bank`. `run.sh diff` compares the two
  sidecars' `world-age-min` and prints a DRIFT line + fails when they
  differ by >30 min, so fixture drift is visible without pixel forensics.
  Banks shot before sidecars existed have none; `rebank` creates them.
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

This guard applies to **manual drives too** (hand-written FIFO/`.qtdiag-cmd`
sequences): `capture.sh shot` is the only place it is automatic, so skipping
that stage means re-applying it by hand — grep the server log for a death,
confirm `list` shows a live player, and vision-check the capture before
trusting it.

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

2026-10-01: a zombie at spawn killed the player between join and the first
menu open (`Dev was slain by Zombie` in the client log); a manual `shot` then
landed the **You Died** screen, which was initially misread as the work board.
Fixes: `difficulty peaceful` from the FIFO right after `wait-server`
(`echo 'difficulty peaceful' > run/server-cmd.fifo`; server log: "The
difficulty has been set to Peaceful"; persists in `run/server.properties`,
which is dev-only and never committed), and never treat a successful `_qtdev`
server-log line as proof the screen is up — the vision content check is the
proof.

- **Launch server/client with `setsid nohup ... &`** - the tiling-WM
  session kills the whole process group on disconnect.

### Stuck death loop (verified 2026-10-01)

A player death (e.g. the 02:35 zombie) leaves the player in the
"waiting for respawn" state - `Health: 0.0f` plus a large `DeathTime`
tag - and the server *replays the death screen on every rejoin*: `data
merge entity Dev {Health: 20.0f}` does not clear it, a `tp` does not,
and deleting `playerdata/Dev.dat` while a server that saw the death is
still running does not (it can re-save the state on disconnect, and
old auto-join client javas keep the loop alive - kill every
`bootstraplauncher` java whose cwd is `run-client` first). Working
sequence, in order: kill all client javas, stop the **server
process**, `rm run/world/playerdata/Dev.dat*`, verify the file is
gone while the server is down, boot the server, then boot exactly one
client. A fresh world sidesteps it entirely. `difficulty` set via the
FIFO is in-memory only until a graceful `stop` rewrites
`server.properties`; a `kill -9` loses it - and hostiles spawned
before the peaceful setting keep acting, so set peaceful in
`server.properties` (or before world gen) for a no-death fixture.
This exact sequence is now a stage: `capture.sh unstick <proj> <port>
<game> <pname>` (server-first, one client, dat-verification included).
One guard note from 2026-10-01: the post-join `Health` read must be a
POLLED 0.0f (capture.sh polls ~25s) - a single read at ~8s can race the
modern client's ~20s auto-respawn and FATAL on a player who was about to
recover; the polling kills a genuinely stuck death but tolerates the
auto-respawn window. The 1.19.2 golden side's "You Died on every join"
overlay had the SAME root cause - a latched death state (Health 0.0f +
DeathTime) persisted in the playerdata .dat, but that .dat is named by
PLAYER UUID on this box, and the old unstick wiped/verified a literal
`Dev.dat` that never existed (false success; the 380df991-…-9527.dat
sat untouched through three "fresh playerdata" attempts). unstick now
wipes all player .dat files and verifies none survive; the latch is
cleared by the normal sequence (root cause 2026-10-01, verified clean:
Health 20.0f on rejoin, no death overlay, work board captured).

### Cursor / hover drive (WorkScreen)

`cursor <x> <y>` writes `mouseHandler.xpos/ypos` in **window/canvas
pixels** (634x675). The screen receives
`floor(xpos * guiScaledWidth / screenWidth)` (1.20.6 `Gui.java`,
`int i = ...xpos() * window.getGuiScaledWidth() / window.getScreenWidth()`),
so the hover box the screen tests is *not* at the canvas position of the
same numbers - for this window the scale is ~0.96 px per GUI unit and
the first card's icon hover box sits at ~canvas x 250-265, y 305-320
(park `cursor 255 310` to trigger it). The highlight fill is
`0x80FFFFFF` over the card gray, which **blends to ~215, not 255** - a
probe threshold of 245+ misses a perfectly good hover box (this cost
6+ probe cycles).

Two measurement guards from the same pass:

- `data get block <x> <y> <z>` only reads **block entities**; a plain
  block answers "The target block is not a block entity" - that reply is
  not an error and tells you nothing about the block state (10 queries
  in the 2026-10-01 burial check learned this the slow way).
- Vision on a sub-~50px crop is not a ruler: it reported a "60px wide
  icon" inside a 42px crop on 2026-10-01. Use vision to confirm gestalt
  (face present, tooltip text, blur vs gray) and pixel masks (a
  `crop.py`-style grid, the hover_probe band-scan) for size and
  position - and when a probe returns "absent" three times in a row,
  audit the probe (threshold, coordinate space) before trusting the
  absence. A card icon only exists when the request's ingredient
is non-null, so a blank board has nothing to hover: post one first with
`_qtdev board_request wheat_seeds` (request still posts even though a
listener NPEs in `updateWorkersAfterRequestChange` on a downtime
townie - pre-existing golden-identical code; tracker:
`.scratch/work-board-request/downtime-townie-listener-npe.md`).
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
  mysterious failure. Same trap when the ad-hoc command contains a pattern
  as literal text (e.g. `*--username*` in the case statement itself — the
  shell's own cmdline then matches it). First keystroke for any kill:
  `tools/porttest/sweep.sh`, or `kill <exact-PID>` from a `/proc` scan in a
  separate command. Run the sweep from a script file: the script's own
  cmdline is `bash sweep.sh` and cannot match.
- **Name the image you are measuring.** After a capture the FRESH shot is
  in `<QT_DIAG_DIR>/diags/<screen>.png`; the banked copy in
  `tools/porttest/live/<ver>/` is only refreshed after the gate banks it.
  Measuring the banked copy after a code fix reports the pre-fix pixels and
  makes a working fix look broken (this session: "still (41,33,35)" was the
  old bank; the new capture already showed the stone texture). Check the
  path and mtime before reporting a pixel verdict.
- **All captures are 634x675 RGBA.** Any hand-rolled pixel scan must use
  `x*4` stride into the row; `x*3` (RGB) produces fake checkerboards and
  fake missing-texture patterns.
- **Sample the pixel distribution before cross-correlating.** A pane that
  "matches the texture at factor 1.0" can still be wrong: a solid color
  equals one stretched texture pixel, and correlation against the claimed
  source will say "it matches" for the wrong reason. First dump the
  Counter of top colors in the region and check the mottle values are
  actually present (stone.png: 127/116/143/104); a single dominant value
  means the tile is sampling one pixel, not the texture.
- **The `fixture` stage assumes a fresh world.** Its success marker,
  `Running initializer`, is logged once when a *new* flag's module chain
  initializes. Re-running `townplace` on top of an existing flag replaces
  the flag but the new town's modules start uninitialized: `townspawn`
  throws `Town has not been initialized on class …SleepModule`, and the
  stage's 50s retry loop dies with "flag never initialized" even though
  the log shows `[townplace] flag placed` repeating. On an already-set-up
  world, use the *initialized* flag's coordinates (from the original
  fixture log lines) for `townspawn`/`openvillager`, or `prep` first.
- **Match `gamemode` across the two sides before diffing.** The HUD and
  hotbar render differently in creative vs survival (creative: no hearts/
  hunger, different XP bar + hotbar). The wip side had drifted to
  `gamemode=creative` in `run/server.properties` while golden stayed
  `survival`; both must be survival for a comparable HUD. `state` prints
  each side's gamemode, and `rebank.sh` dies fast on a mismatch before
  generating any world. (Dev-only config; never commit a world's
  `server.properties`.)
- **A fresh-world rebank does NOT converge the data screens.** 2026-09-30
  run: after `rebank.sh both` (both worlds fresh, same fixture, same
  canvas, ~same age), the full-frame fresh-vs-fresh diff was parent 77%,
  stats 79%, skills 79%, economics 72%, quests 72% — against bop at 2.9%
  — while the mature bank pair sits at 7-13% full-frame / 2.2-22% center.
  Those screens render live town data (villager inventories, stats, quest
  lists, item-panel pages); two different worlds hold different data, and
  the fixture is deterministic *per world*, not across worlds. A useful
  bank comes from a *mature* fixture world where the data has settled;
  `rebank` from a fresh world is useful for chrome/layout-only screens
  (bop) and for regenerating sidecars, not for the data screens.
  Also learned the same run: `connect` issued while `-Pautojoin` is
  still mid-handshake opens a second connection and can stall the client
  forever (hit on 1.19.2); `rebank.sh` now only falls back to `connect`
  when no `logged in with entity id` line exists yet.
- **The WorkScreen (job board) is not one of the six canonical tabs.** It is a
  standalone container menu, opened on the wip side with `_qtdev open_jobs`
  (thin wrapper over `TownFlagBlockEntity.openJobsMenu`, added to
  `QtDevClickCommand` this session; smoke-tested via FIFO: "[open_jobs] work
  board for Dev"). `_qtdev clickheld <board pos>` does NOT open it: `runHeld`
  refuses an empty main hand ("Dev has nothing in the main hand") and the
  board's real path (`JobBoardBlock.useWithoutItem`) only fires on a genuine
  empty-hand click. Cards on the board are the town's work requests
  (`TownWorkHandle.requestedResults`; server logs "Request added to job
  board") — a board with zero requests renders zero cards, so confirm one
  exists in the server log before reading a blank board as a render bug.
  The golden (1.19.2) repo carries the same thin `_qtdev open_jobs`
  command (mirrored to Questown-119) and `_qtdev board_request <item>`
  to post a card; the work board itself is a standalone panel ("Page 1 / 1"
  title bar, request-card rows like "An item was requested") with NO tab
  strip, so the content gate's MENU/TAB lines use its own description from
  the manifest `expect.menu` (added 2026-10-01: the tab-icon wording
  misfired on both the golden and the wip work capture). The work capture is
  banked on both sides (goldens/1.19.2/work.png, live/1.20.6/work.png);
  its center diff (26.9%) is fixture content - the two worlds hold 3 vs 1
  posted requests - with the card structure confirmed identical by vision. The tiling WM gave the two
  clients different widths (wip 634, a11 420) in the same run, and the
  banked PNG is whatever width the window had. `run.sh diff`'s canvas
  check only compares the two *current* sides, so both sides changing in
  unison sails through; a later 634-vs-420 diff is silent garbage. The
  `bank` stage now dies when a fresh shot's canvas differs from the
  same-named banked PNG. The `manifest.json` `canvas` field pins the
  capture size (634x675, the bank's reference) and the `shot` stage dies
  at capture time when the client window is a different width, so a
  wrong-size shot never reaches the bank. `cv info <png>` before
  banking is the ten-second check; a resize is a capture-state change,
  record it when it happens.
- **`cv diff` + targeted crops is the oracle; vision is not a differ.**
  Asked to enumerate the differences between two captures, the vision
  model (OpenRouter) swapped and merged features across the two images
  and its "structural vs data" verdict was wrong. Use vision for
  "what is on this one screen" (menu state, active tab, content sanity)
  one image at a time; use `cv diff` on the full canvas, the center crop,
  and small self-diffs (old-bank vs new-bank of the *same* branch — the
  cheapest way to separate world-data drift from a real regression).
  Self-diff first, diff the sides second.
- **GUI render regression: probe, don't speculate.** Add a 30×20 red
  `guiGraphics.fill` probe at the top of `Screen.render` (after
  `renderBackground`) AND inside the suspected method; measure the
  red-pixel fraction in the capture. A probe that draws at top level but
  fails in-method pins the corruption to in-method state. Log
  `RenderSystem.getModelViewMatrix()` translation and `gui.pose()`
  alongside; guard with `-Dquestown.diag` and remove both when verified.
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
  bites without the persistent writer. `tellraw/say` to zero online
  players prints *nothing and no error* - with the client dropped (e.g.
  after the Connection-Lost kick) a successful probe is indistinguishable
  from an empty world; use `data get entity` (non-destructive, needs the
  exact id/uuid) or the server log, and remember `kill @e[type=...]`
  reports presence by killing it - fine only in a re-bankable fixture
  world.
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
- **The menu needs the townie inside the client's tracking range.**
  `VillagerMenus.doFromNetwork` resolves the payload's raw entity id via
  `level().getEntity(id)`; out of range it is null, the server-side write
  and the six `init*` calls NPE, the client opens the menu with "Failed
  to open a screen with advanced data" and DROPS the connection (all six
  shots then capture the Connection-Lost dialog, every gate fails with
  "no menu on screen"). A live/mature world's townies wander, so this
  hits only a world hours old. Fresh fixture drive = townie at spawn = in
  range (the golden side never saw this). If you must use a live town,
  `tp <uuid> <player>` the townie next to the player first - never
  `tp` the player, that is the desync/death trigger.
- **Vision gate backend = the live Nova GPU proxy**,
  `http://localhost:42827/v1/chat/completions`, model `qwen3.8:27b`
  (`check.sh` defaults + the manifest `check` block agree). The vast-era
  box 154.37.220.220 answers TCP but returns empty HTTP (dead box); the
  manifest pinned its model name `qwen38-nvfp4`, which Nova 404s.
  Reasoning level is **medium**: lower budgets spend the completion
  tokens on reasoning and return `content: null`; pure-yes/no MENU
  questions misfire on dimmed-world captures, so the question names
  concrete visuals (centre panel over a dimmed world, tab-icon row).
- **A world that failed the empty-ItemStack save (1.20.6:
  `ItemStack.save` throws on EMPTY slots) loses its townies across chunk
  cycles** - "An Entity type entity.questown.visitor ... will not
  persist" in the autosave log, then `data get entity <uuid>` finds
  nothing while `kill @e[type=questown:visitor]` still finds a
  half-registered one. That made "mature the live wip world" a dead end
  (no stable townie for the menu); the fix (commit `7785c651`, air tag
  per empty slot) only takes effect on a server RESTART, so any
  pre-fix world keeps bleeding townies. A fresh `rebank` drive is the
  reliable fixture.

## Usage

```
tools/porttest/capture.sh <stage> <proj> <port> <gamedir> [out] [screen]
tools/porttest/iterate.sh <stage> <screen> <proj> <port> <gamedir> [version]   # shoot|gate|bank|all
tools/porttest/check.sh <screens-dir> [screen]    # content gate (vision)
tools/porttest/run.sh [check|diff|all]            # gate + canvas + cv diff
cv diff goldens/1.19.2/stats.png live/1.20.6/stats.png   # ad-hoc one-screen
```

## Parity verdict (where `diff green` stops being enough)

`run.sh diff` green is a threshold pass *inside the manifest's center
rects*, not proof of parity. The rects can exclude a screen's failing
rows — real case 2026-09-30: on the 1.20.6 bank the stats tab-strip
rendered its tool/leftmost icons blank and the skills tab lost its slot
frames + `{ }` braces (icon-area background a near-black (24,15,7) where
1.19.2 has the light-grey pane, (222,222,222)), yet the oracle rows
passed and the ledger row was committed as "verified". It was flipped
back to open after the dev named the breakage. So before calling parity
settled: (a) confirm the center rect covers the rows that tab's content
actually draws in, not just its body; (b) vision-compare a central-pane
crop (x ~296-640, y ~200-450) of each tab, with both sides described in
one prompt, not each half in isolation; (c) ask the dev what specifically
looks broken on the tab they are watching, before re-deriving the cause
from code or defending a passing test.

A `Connection-refused` client (`Caused by: java.net.ConnectException`)
means the server is not LISTEN — verify the port (wait-server /
netstat) before relaunching the client; the second launch will not fix
it, and a perf-server that died without autosave leaves no world.

## Known residual differences (PASS-able)

- **Diff elements may be native client chrome, not mod UI.** The 1.19.2
  client draws a `ControlsScreen` WASD hint panel ("Move with w,a,s and
d", "Jump with Space", d-pad icon, ~x477-630 y31-143 in a centered
  634x585 capture); 1.20.6 does not. Confirm with the dev which side an
  element belongs to before hunting mod assets for it — a whole probe
  loop was burned looking for that panel in Questown/RoomRecipes/
  Patchouli resources (2026-09-30).

- Text antialiasing edges differ between renderers (expect ~0.1–0.5%
  changed pixels on text-heavy screens).
- The two sides run different worlds, so the background visible around the
  menu differs; judge the menu region, not the skybox.
- Villager face pixels are data-driven (seeded per entity UUID) — the two
  townies are different entities; the face region is expected to diff.
- The skills pane content is fixture-driven: the two towns unlock different
  job sets, so the widget arrangement *and* the scroll offset (which shifts
  the 16px stone-tile phase) differ. Expect a multi-digit % center diff even
  when the rendering is pixel-correct; judge the pane by texture values and
  widget frames, not by full-pixel match.
