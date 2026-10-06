# Questown dev server/client launch on this box

Symptom strings and the working sequence for `./gradlew runServer` / `runClient`
during a Questown play-test (profiling, lag repro). Read before launching, not
after the failure.

## Environment

- `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8` — the wrong JDK
  breaks Gradle's Groovy compile; a POSIX locale fails javac with "unmappable
  character for encoding US-ASCII". `compileJava`/`test` pass on Java 21, but a
  `build.gradle` recompile hits `Unsupported class file major version 65`.
- Launch in a **separate** bash call: bare `nohup CMD > log 2>&1 &` + only an
  `echo`. A `sleep`/`tail`/`disown` in the same call leaves the process dead
  ("no output", no process, no log). Verify in a later call.
- **A launch call can return empty output entirely (even the `echo` missing)
  and still have half-run.** Seen twice in one session: the call died after
  the kill but before or during the rm+launch, leaving the PREVIOUS server's
  log in place. Verify the generation, not just the line: the `Flag block
  entity live` timestamp must be AFTER your launch time and the log file's
  mtime must be fresh; a flag-live line older than your launch means you are
  reading the dead server's log. A client stuck at `Connecting to localhost`
  for minutes with no join is the same tell from the other side - check
  `pgrep -f forgeserveruserdev` before assuming the server is up.
- **`pkill -f <pattern>` self-kills (and the "no output" launch is its symptom).**
  `pkill -f` matches the *full command line* of every process, and the `bash -c`
  string that runs it contains the literal pattern — so the shell dies before
  the `nohup … runServer` launch on the same line executes, returning empty
  output. Worse, a broad pattern takes down a *live held server*: a "quick"
  `pkill -9 -f runServer` demo this session killed the held server with it
  (it was still writing at 05:41:51, gone by 05:42:09). The safe form brackets
  one char so the shell's own text no longer matches the regex: `[r]unServer`
  or `forgeserveruserde[v]` — verified: `bash -c 'echo START; pkill -9 -f
  "zz[9]x"; echo END_SAFE'` prints both lines (plain `zz9x`-style tokens print
  only START). Use bracket patterns for both `pkill` and `pgrep`, or kill in a
  separate call and confirm the target is gone with
  `pgrep -af 'forgeserveruserde[v]'` before launching.
- **A held server can die silently minutes after a clean boot** (log just
  stops mid-tick, no exception, pid gone). Observed when a second gradle
  `runServer` (an autotest) overlapped the held one; run autotests only
  after tearing the held server down.

## A visual play-test cannot run on this box — but the client CAN be run for a user

Two different things get conflated here; keep them apart:

- **Screenshot/automation capture is out.** `grim 2>/dev/null | wc -c` returns 0
  bytes and `xdotool search --name Minecraft` finds no window (confirmed
  2026-09-04, re-confirmed 2026-09-05). **UPDATE 2026-10-13:** `grim` works in
  the user's Sway session (`grim shot.png` → a full 1280x720 PNG), and the
  window rect comes from `swaymsg -t get_tree` (Sway, wayland-2; no xdotool/
  wmctrl). Crop the client window out of the capture and pixel-check with
  `PYTHONPATH=~/.local/cvlib python3` + PIL. Re-probe `grim` before assuming
  the 2026-09 state; it was fine today after failing in September.
  So `cv screenshot` and window automation do not work on this box. The evidence for *automated* before/after
  must come from the server log.
- **But the client still renders and a user can drive it.** On 2026-09-05 the
  user ran `runClient`, saw it, clicked a quest, and reproduced a client-side
  crash (report landed in `run/crash-reports/`). The `grim`/`xdotool` probe
  measures *this box's* ability to capture a window, not whether the client
  renders for the user. Do not treat a 0-byte `grim` / no `xdotool` window as
  a reason to decline running the client for a reproduction — run it, and have
  the user click.

So: for a reported client-side crash or "does X work" question, the first move
is to run `runClient` and hand the user a client to click, not to rule out
mechanisms by reading code and ask for a crash report. Read automated
outcomes from the server log (below); read a *click reproduction* outcome from
the user + the client log / crash report.

**And the user wants `runClient` run — never ask, offer, or defer it.** When a
held `runServer` autotest or play-test needs a verification pass, run the client
(add `-Pautojoin` for a live view) and hand control back. Do not ask the user
whether they want it run, do not offer it as a choice, and do not defer it to a
later session or handoff. The one skip is when a unit test or the existing
autotest suite already settles the question; then the run adds nothing.

Read the outcome from the **server log**, not the client. The autotest harness
(`AutoTestRunner`, `TestExecutor`, `TestBlueprintRegistry`) already writes
results to `QT.FLAG_LOGGER` via `PlayerTestOutput`/`LogTestOutput`, and a
scenario's `customAssertion` can read live state (entity positions,
blockstates) and log it — an objective read with no window and no
`getblockstate` (unsupported in this build).

**`_qtdev test` cannot be driven from the FIFO** — it calls
`getPlayerOrException()`, which throws on a console source. To run a scenario
from a headless box, register a console command that builds the same
`TestExecutor` from a console source (origin from a flag scan) with
`LogTestOutput`. The `AutoTestRunner` hold path is the alternative.

Also: `setblock`/`/give` resolve **item** ids, not block ids — Questown blocks
have a different item id (flag block `questown:cobblestone_flag_base`, not
`questown:cobblestone_town_flag`). Use the `_qtdev`/`qt` harness to place
mod blocks, not vanilla `setblock`.

## Launching the client can reclaim keyboard focus from the IDE (reported 2026-10-01)

User-reported: a `runClient` launched from the agent session held the dev's
keyboard focus hostage — they could not move the text cursor back to the IDE
and had to exit the game to reply to the agent. Cause not yet established
(headless box: the focus swap is only observable from the dev's desktop).
Suspect: the client JVM staying in the terminal's session (the Questown-119
porttest launcher detaches with `setsid nohup sh -c …`). Experiment: on the
next agent-launched client, put the client chain under `setsid` and have the
dev confirm focus returns to the IDE before calling it fixed until then it is a
report, not a verified mechanism. Until settled: tell the dev a client window
is about to open so they know why the IDE goes quiet.

## Restarting the server kills the client — launch the server first, then the client

Restarting the held `runServer` (to load a new command or edit) **kills the client**
and wipes the world state the client was watching. `pkill -f 'runClient'` (or a
server restart that the client is connected to) drops the user's screen. Do not
restart the server while the client is up: launch the server **alone** first
(it compiles; the compile + client + server together OOM-kill the server JVM,
exit 137), let it boot and stabilize, *then* launch `runClient -Pautojoin`. The
client reconnects to the live server; the server's FIFO writer is independent
of the client. If you must restart, expect the client to drop and reconnect it
after the server is back. **Never `pkill -f 'runClient'`/`'runServer'` to tear
down your own run — it also matches the dev's gradle task in another terminal
tab.** Kill the pids you launched, by pid (see `questown-held-run` teardown).
The same trap bites inside one session: a `pkill -f`/`pgrep -f` pattern typed
verbatim anywhere in the tool call's own command line matches the wrapper shell
and kills it mid-call — the call returns with *zero* output, terminator `echo`
included (twice in the 2026-10-03 wigeon session). Bracket the pattern
(`[r]unServer`, `[s]leep 999999`) or kill by explicit pid, then confirm the
outcome in a fresh call — a dead call proves nothing.

## Client + server + recompile together OOM-kill the server (exit 137)

Symptom: `./gradlew runServer` dies with `non-zero exit value 137` and
`BUILD FAILED` after a few minutes; `pgrep -af '[f]orgeserveruserdev'` shows
nothing. 137 is SIGKILL — the server JVM was OOM-killed, not a crash. The
trigger: the client (a second JVM, ~2-3G) + the server JVM + a `compileJava`
that recompiled the source in the same run. Memory shows `free -h` with tens of
GB free, so it is not system-wide OOM — it is the per-process budget. The fix:
launch the server **alone** (precompiled, so no compile spike) first, let it
boot, then launch the client. Keep the client down while the server compiles.

## Parallel confirmation sweep: N standalone servers from one captured command

For N parallel held runs (a confirmation sweep), do not re-derive the JVM
command from build.gradle — capture the exact one gradle runs and replicate it:

1. Launch one server the normal way; `pgrep -f BootstrapLauncher` for its pid.
2. Dump the args (null-separated tokens; `-p` and `-cp` are single long args):
   `rm -rf /tmp/sweep-cmd; mkdir /tmp/sweep-cmd; i=0; while IFS= read -r -d '' a; do i=$((i+1)); printf '%s' "$a" > /tmp/sweep-cmd/arg$(printf '%02d' $i); done < /proc/<pid>/cmdline`
3. Per run N: fresh gameDir `/tmp/ksweep-N` with `server.properties` (`server-port=2556<N>`, `level-name=world`, `online-mode=false`), replace the `--gameDir .` value with that dir, prepend `-Xmx3G -Xms1G`, and **skip arg01** (it is the java binary, already the command). Launch from the gameDir so relative paths land inside it.

Facts that cost a session to find (2026-10-06):

- **The classpath split is structural, not incidental**: `-p` (module path)
carries exactly 8 loader-infra jars (bootstraplauncher, securejarhandler,
5×asm, JarJarFileSystems) and **everything else, including modlauncher-11,
sits on `-cp`**. If modlauncher lands module-path-only, the FML loader dies
`java.lang.NumberFormatException: Empty version string` (`Package
.isCompatibleWith("4.0")` — a named-module package version is null; from the
classpath it reads the jar manifest, `11.0.2+...`). Replicate the captured
split verbatim; do not "clean it up".
- **`MOD_CLASSES` is an env var the gradle *daemon* injects. Export it for
any standalone java or the mod silently vanishes**:
  `export MOD_CLASSES='questown%%<repo>/build/resources/main:questown%%<repo>/build/classes/java/main'`
  (verify the live value with `tr '\0' '\n' < /proc/<pid>/environ | grep MOD_CLASSES`).
  Without it, discovery still logs `Found mod file "main"` but the mod never
  constructs: empty registry (tag/recipe parse errors for `questown:*`), zero
  `[questown]` log lines, and the autotest scenario never starts (no
  `[autotest] BOOT_OK`). Loaded-marker: `[questown] bound N deferred registries
to the mod bus`.
- A fresh empty gameDir equals a wiped world: the `run/world` wipe is a
  build.gradle `doFirst`, and the scenario executor builds the world
  programmatically (flag/mat/rooms) — a standalone start is simply pristine.
- Kill sweep servers by **explicit pid** (`kill -9 <pid>`), never a pattern —
a broad `java` pkill has crashed the box once.
- **Parallel load is the independent variable**: the 2026-10-06 sweep got 1/3
  runs through the raw-meat chain (a `LOOTTRACE extract 'Raw Mutton' vs … ->
  true` line) while 2/3 sat days in `COLLECTING_SUPPLIES` with **zero**
  extraction lines. A stall under load is the confirmation data the sweep
  exists to catch (a timing-dependent extraction), not a launcher failure —
  tell them apart by the log (extraction lines present or absent), not by the
  process still running.

## The held server is the verification path; a held run has no client

A held `runServer` autotest is the verification path, not a play-test for a
spectator. The user is a *secondary* observer via `runClient`; the agent is the
main driver. Do not wait for the user to place a block or drive the run —
place the blocks, register the rooms, and drive the clock yourself via the
FIFO; the user watches. If you find yourself asking the user to place something
or to confirm a step, you have stopped driving.

## Server console goes to a pipe; redirect to a file to read it

The `runServer` task's console is unreadable through the gradle pipe. Redirect to
a file to read `QT.FLAG_LOGGER` output:
`nohup ./gradlew runServer -PperfFifo > run/serverN.log 2>&1 &`. Use a **fresh
log name** each relaunch (`serverN.log`) so you do not overwrite prior crash/log
evidence — do not delete the logs you are investigating from.

## Command injection (RCON is broken)

RCON does not work in this build. Feed a command to the server through a FIFO:

- THE ONE recipe on this box is **`run/ffeed.sh`** (bounded write + delivery
  confirm; never hand-roll the FIFO):
  `run/ffeed.sh setup` (fifo + idempotent holder) then
  `run/ffeed.sh feed "/tick rate 200" --expect "Set the target tick rate" <log>`.
- Root cause of every historical hang: a bare `echo … > fifo` **blocks the writer
  until something holds the fifo's READ end open**. The holder must therefore open
  the fifo **read-write**: `nohup sh -c 'sleep 999999 <> run/server-cmd.fifo' &`
  (`<>`, not `>` — a write-only holder opens *no* read end, and is indistinguishable
  from none at all). The 2026-10-04 200-t/s battery worked because its holder was
  `<>`; minutes later, probes with `>`-style holders (or a server whose stdin was a
  plain pipe, not the FIFO — case 3 below) hung the caller 7532 s, server up the
  entire time. `run/ffeed.sh` bounds the write to 15 s regardless.
- **Pace, not echo, is delivery truth:** at 20 t/s one in-game day takes ~20 min of
  wall time, at 200 t/s ~60 s. `run/pace-guard.sh <log> <claimed-rate>` enforces
  exactly this (threshold = expected day gap × 1.5 + 30 s). An unapplied `/tick rate`
  is SILENT — a day gap of ~10 min is rate-20 data wearing a rate-200 label. Do not
  cite a log as rate-R data until pace-guard says GREEN on it.
- **Verify a command exists in this build before sending, and treat a parse failure as *not-executed*.** The `_qtdev`/`qt` set differs across branches. Before a first send, grep the leaf literal — e.g.
  `grep -rl 'literal("prepare")' src/main/java/ca/bradj/questown/commands/` → `PrepareWorkCommand.java`, so it is `qt jobs prepare <pos>`, **not** `prepare_work`. To enumerate the whole current leaf set run `grep -rho 'literal("[a-z_]*")' src/main/java/ca/bradj/questown/commands/*Command.java | sort -u` (run it, don't trust a doc's table — the set changes per branch). If you echoed into the FIFO and the server log then shows the command followed by `Incorrect argument for command`, that command **did not run** — the absence of follow-up output is your probe having failed, not evidence about the system. Fix the command and resend before reading behaviour from the log. (The `.scratch/*/*.log` transcripts can contain *failed* sends, i.e. an echo + `Incorrect argument`, so a command's presence in an old log is not proof it works in this build.)
- **DEV-DIAG scaffolding convention:** when you add a dev-only diagnostic command or log line for an investigation, mark it `// DEV-DIAG: <what, date> — keep/remove per handoff` so it is grep-enumerable (`grep -rn 'DEV-DIAG' src/main/java`), and list each tag in the handoff with a keep/remove recommendation. Six tags exist from the 2026-10-02 raw_food hunt investigation (DevLootFireCommand, DevWorkScoreCommand, LOOTTRACE ×2, [jcscan], scoreDump).
- A blocked FIFO write (the `echo` never returns) or an aborted command usually
  means the **server process is dead** or the **reader is not open** — check
  `pgrep -af '[r]unServer'` before assuming the pipe is backed up. The reader is the
  task's stdin; it exists only while the gradle task is up **and** its worker has the
  fifo open. It was **proven to work on an autotest hold run** (the 2026-10-04
  200-t/s battery: days landed ~60 s apart — impossible at 20 TPS — so `/tick rate 200`
  delivered). It **blocked indefinitely on the same kind of run** minutes later, the
  bash tool call hanging ~2 h (7532 s) until aborted, server up and healthy the whole
  time. **The concrete difference is who holds the read end** (see the holder bullet
  above), not autotest-vs-dev or daemon warmth. **Rule: use `run/ffeed.sh` (bounded
  write + `--expect` confirm); and confirm by in-game day pace with
  `run/pace-guard.sh` — nothing in the log proves the rate landed on its own.**
- **Third block case (verified 2026-10-13): the server's stdin is a plain pipe,
  not the FIFO** — `ls -l /proc/<pid>/fd` shows fd 0 → `pipe:[NNN]` instead of
  the FIFO path. The server was launched from a terminal shell without the FIFO
  discipline, so the FIFO has no reader and every open-for-write blocks forever
  (including a persistent `sleep 999999 > fifo` writer, which hangs in open and
  must be killed, not waited on). Check fd 0 before writing; the workaround is a
  chat command from the dev's client (`qt`/`_qtdev` work from the game chat) or
  relaunching the server with the FIFO writer first. Prefer driving it yourself —
  ask the user for a command only as a last resort (the 2026-10-03
  "don't rely on me, use FIFO" correction); when client+console are both dead,
  see the next bullet (1.20.6: injection goes through scenario code / forwarded
  properties).
- **Launch the FIFO writer in a *separate* call, before `./gradlew runServer`**
  (verify the `sh -c 'sleep 999999 > …'` is alive, then launch the server in its
  own call). A `sleep`/second background job in the *server's* call kills the
  server ("no output", no process, no log — see the Environment section above and
  the global AGENTS.md launch-survival rule). A held `pkill` (teardown) kills the
  `sleep 999999` writer and a FIFO write then blocks (nothing reads it); recreate
  the FIFO and relaunch the writer before each relaunch, not by sharing the
  server's call. The same "no process, no log" result has a third shape
  (2026-10-03): a failing command earlier in the launch chain
  (`rm -f run/world` on a directory) aborts before the server starts while a
  pattern match on the chain's own text reads as "running" — after any
  "launch issued" line, confirm in a fresh call that the server pid exists
  *and* the log file exists.
- **1.20.6-neoforge branch (verified 2026-10-03, runs 12/12b): the forked
  server doesn't carry `standardInput` regardless** — `ls -l /proc/<pid>/fd/0`
  → `/dev/null` even though the `questown.tp.fifo` gate provably fired (an
  init-script probe read the task's `standardInput = FileInputStream(run/server-
  cmd.fifo)`) and `--no-daemon` doesn't change it (the sibling `-Dquestown.autotest`
  gate fired in the same build, so the -D is visible to build scripts). Command
  injection on this branch is scenario/executor code or a `server{}`-whitelisted
  system property; note the two bullets above are not proved dead by this — the
  hold-mode console-poll question was never reached (no live stdin to poll).
- **A `build.gradle` edit is not applied by a cached `:compileJava UP-TO-DATE`.**
  After editing `build.gradle`, the run re-reads it, but a stale `UP-TO-DATE`
  task skips recompiling the source — the edit is not in the class. Force with
  `--rerun-tasks`, or confirm a fresh boot in the log (a new `BOOT_OK` line,
  not the old timestamps) before trusting the run.
- **A *source* edit can also leave a stale `:compileJava UP-TO-DATE` that skips
  whole packages — and the crash is a `ClassNotFoundException`, not a compile
  error.** On this box `:compileJava UP-TO-DATE` can skip recompiling a package
  (it happened to `ca/bradj/questown/town/rooms`), so the run boots and fails
  later at tick with `NoClassDefFoundError`/`ClassNotFoundException` for a class
  that *is* in source and *does* compile (`MultiLevelRoomDetector$DetectorWithLevel`,
  `RoomRecipes`). `ls build/classes/java/main/.../` shows the class is absent
  even though `compileJava` reported SUCCESS. The fix is `./gradlew compileJava
  --rerun-tasks` (force it, do not trust the cached `UP-TO-DATE`), and verify
  the previously-missing classes are back in `build/classes/java/main/` before
  launching. Do **not** `./gradlew clean` to force this — `clean` wipes the
  ForgeGradle-decompiled Minecraft classes and a plain `compileJava` cannot
  re-fetch them (`package net.minecraft.core does not exist`); `--rerun-tasks`
  reuses the decompiled classpath and only recompiles the mod.
- **Do not run the held server in the background while the dev is an observer,
  and do not `pkill`/restart the server while `runClient` is up.** The dev is a
  secondary observer via `runClient`; launch the client *after* the server boots
  and keep it up while the run goes, so the dev sees the world. A server restart
  or `pkill -f runClient` drops the dev's screen (this bit when the dev was
  told to watch and the agent kept relaunching the server blind). If you must
  load a new command, tell the dev the client will drop, then relaunch it after
  the server is back.
- **No console path for a wand right-click** — the town wand's `onRightClicked`
  (door registration, room detection) cannot be driven from the console, and a
  held run has no real player to right-click. The `qt registerroom <x> <y> <z>`
  dev command is the console enabler: it calls `RoomsHolder.registerDoor` (the
  same path a wand click on a door takes), so a room is detected and its recipe
  quest completes. It takes any `DoorBlock` at the flag's height (no radius gate;
  a first version gated on `CAMPFIRE_SEARCH_RADIUS`=10 and silently rejected doors
  19+ blocks out). A `campfire` block placed by `setblock` completes the campfire
  quest on its own (the flag auto-detects it; no registration needed).

## Two branches' servers can look identical in a process listing

`pgrep -af '[r]unServer'` matches *both* the 1.20.6 wip server and the
1.19.2 worktree (`Questown-119`) server. Which project a `java` server
belongs to is in its classpath: `tr '\0' '\n' < /proc/<pid>/cmdline | grep
Questown` shows the project path (`Questown-119` vs `Questown`). A worktree
server also sits on its *own* port (the 1.19.2 one ran on 25571 for hours
while sessions assumed 25565 was wip-only). Before killing, stopping, or
re-launching a "server", pin pid → project → port from cmdline, not from
position in the pgrep output.

**Client and server are both `--launchTarget` javas, and broad patterns hit both.** `pgrep -f "[M]inecraft"` also matches the *server*: its `-DlegacyClassPath.file=` argument contains `writeMinecraftClasspathServer`. Discriminate on the exact launch-target tokens - `pgrep -f "forgeclientuserdev"` (client) vs `pgrep -f "forgeserveruserdev"` (server) - verified 2026-09-30 when a client restart left the live server running only because the pkill pattern was narrow.

## A held / relaunched server is a `java` process, not a `gradlew` string

`pgrep -af 'runServer'` returns nothing for a running (relaunched) server — the
server is a `java` process. Match with `pgrep -af '[r]unServer'` or
`pgrep -af 'forgeserveruserdev'`. A `pkill -f '[G]radlew runServer'` may not kill
the `java` child; re-check and `kill -9` the `forgeserveruserdev` pid if it
survives.

## `pgrep -f` matches its own bash invocation (phantom counts)

When a verify command runs `pgrep -f 'forgeclientuserdev'` (or `'sleep 999999'`,
`'runClient'`) inside a `bash -c '...'` whose expanded command line *contains*
that literal, `pgrep -f` matches the bash process itself. The count reads
`1` or `2` and never drops to `0` even after the target is dead, so it looks
like a straggler that you keep trying to kill. Tell-tale: the reported pid is
the same across different patterns, or `ps -o pid,cmd -p <pid>` prints nothing.

Don't chase the phantom. Verify teardown by the *server* (the one you actually
care about): `pgrep -f '[f]orgeserveruserdev'` returning nothing, and port
`25565` free (`netstat -ltnp | grep 25565` → nothing). The client and FIFO-writer
phantoms don't hold the port, so a free port + dead server means the next run
will launch clean.

The dangerous variant is `pkill -f`, not just `pgrep -f`: a pattern that
appears in the invoking shell's own command line (e.g. `pkill -f 'runClient'`
run *inside* a compound command that also mentions `runClient`) kills the
shell mid-flight, and every command after the pkill in that compound
**never runs** with zero output. Tell-tale: a command that must print
saying nothing, followed by a state where the thing you expected it to
start is absent. Use bracket patterns (`[r]unClient`) for kills too, or
verify each step of a compound command landed.

## `pgrep -af java` prints the full classpath (context pollution)

`pgrep -af java` / `pgrep -af forgeserveruserdev` prints the entire multi-kilobyte
`-cp` classpath, which floods the bash output and can push a turn into context
pressure. Filter it: `pgrep -af forgeserveruserdev | grep -v pgrep | head -1`
and, if you only need the pid, `pgrep -f forgeserveruserdev`.

## Profiling the real world's flag tick (dev-only TickProfile)

`TickProfile` is enabled only by the `perf/*` autotests, and those **wipe
`run/world`** on boot (gradle `doFirst`) — so profiling the real town that way
destroys the world. To profile the real world on a plain server:

- Boot a **plain** (non-autotest) server — `-Dquestown.autotest=true` wipes
  `run/world` on boot, so do not use it for the real world.
- A dev-only console command `qt tickprofile` (and `qt tickprofile reset`)
  enables/dumps it: `qt tickprofile` enables on the first call and dumps
  `snapshotServer()` + `snapshot()` + `describePhases()` on later calls; `reset`
  clears the rolling samples so you can re-baseline and see whether a stall
  recurs vs was a one-time spike.
- `snapshotServer()` is the whole-server-tick (the 2s "Can't keep up" lives here,
  invisible in the flag-tick bucket); `snapshot()`/`describePhases()` split the
  flag tick into phases.
- `Can't keep up` = 0 does NOT mean no stall — a single ~1s server tick can be
  well under the cumulative threshold Minecraft uses for the message.

## A before/after held run must not clobber the uncommitted fix

A before/after repro runs the scenario twice: once with the working-tree fix, once
without ("before-fix"). The before-fix run reverts the fixed file(s) to HEAD while
keeping the scenario change, then restores. Do **not** restore with `git checkout`:

- `git checkout HEAD -- <file>` **destroys** the uncommitted working-tree change
  (the fix was never staged, so the index holds HEAD, not the fix). Running
  `git checkout <file>` afterward restores HEAD, not the fix — the fix is gone.
- Restore with a saved diff, not `git checkout`. Sequence: `git diff -- <fixed files>
  > /tmp/fix.patch`, run the before-fix (`git checkout HEAD -- <fixed files>`,
  recompile, launch), then `git apply /tmp/fix.patch` to bring the fix back
  (recompile). `git stash` works too, but a patch is explicit and re-applyable if a
  run half-fails. `git checkout` has no path back to an uncommitted edit; the patch
  or stash does.

This bit once: the before-fix run used `git checkout HEAD -- ProductionJob.java` to
reach the dormant state, then `git checkout <file>` to "restore" — which re-confirmed
HEAD, and the fix had to be re-applied from the diff captured earlier. Recoverable
(because the diff was captured), but the edit was lost between the two.
