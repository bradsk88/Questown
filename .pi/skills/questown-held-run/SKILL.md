---
name: questown-held-run
description: >-
  Drives a held `runServer` autotest (village/growth or any hold-mode scenario)
  as the agent is the main driver and the dev is a secondary observer via
  `runClient`. TRIGGER when asked to run/observe a Questown held run, drive the
  village through onboarding, "play the game", watch a live held autotest, or
  when a behavior needs a live world a unit test cannot settle (then also load
  `questown-behavior-verify`). SKIP when a unit test or the existing autotest
  suite already settles the question. Consolidates the launch sequence, the
  30s observation cadence, the chat/observe channels, and the console command
  set so a session does not re-derive the OOM / client-kill traps.
---

# Questown held run (agent drives, dev observes)

The held `runServer` autotest is the verification path. The agent is the **main
driver**; the dev is a **secondary observer** via `runClient`. The agent places
blocks, registers rooms, supplies items, and flips the clock; the dev watches
in survival. Do not ask the dev to place blocks or drive the run.

**Pick the scenario from the behavior, not the convenience.** If the behavior
is cook / food / leaver timing (only ticks in realtime), the scenario is
`village/self_sufficiency` (realtime, held), **not** `village/growth` (that one
warps the clock with `time set` and cannot exercise realtime job work, and does
not staff a cook). The full mapping, the `warp` != tick-rate distinction, and
the "stuck at roster 0" read-out live in
`questown-behavior-verify` under *Scenario selection*.

**The observation standard.** In this context the agent plays as a new player:
it may use only what the game surfaces (the server log, the `inspect` tabs,
the chicken's narration, broadcasts, chat). This skill documents what each
command does and how it fails - never recipes, mechanics, or strategy the game
is meant to teach. That standard applies to the skill's own command
documentation too: describe what an output shows, never what to conclude
from it or what to do next. When a run stalls on knowledge the game does not surface,
the fix belongs in the game or in a dev command's output, not in this skill.
(Other contexts may allow an omniscient agent; a held run is not one of them.)

This skill consolidates what was scattered across `AGENTS.md`,
`references/questown-dev-launch.md`, `questown-behavior-verify`, and
`commands/test/COMPLEX.md`. Before the first launch, read
`references/questown-dev-launch.md` — it has the full OOM-137 /
client-daemon-death / FIFO-survival detail this skill only summarizes, and a
fresh session that skips it will hit the launch traps. The rest of this skill is
the sequence a session needs up front.

## Launch (server first, then client — order matters)

Launch the server **alone** first (it compiles; the client + server + recompile
together OOM-kill the server JVM, exit 137). Let it boot, then launch the
client (it reconnects to the live server).

Env for both: `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8`
(the wrong JDK breaks Gradle's Groovy compile; a POSIX locale fails javac with
"unmappable character for encoding US-ASCII").

1. **Compile guard (after any source edit, before launching):**
   `./gradlew compileJava --rerun-tasks` — force a full recompile (do not trust
   a cached `UP-TO-DATE`; it can skip a package and the run then crashes with a
   `ClassNotFoundException` at tick — see the reference). Then assert the
   classes are present: `ls build/classes/java/main/ca/bradj/questown/town/rooms/MultiLevelRoomDetector\$DetectorWithLevel.class
   build/classes/java/main/ca/bradj/questown/logic/RoomRecipes.class`. Do
   **not** `./gradlew clean` (it wipes the ForgeGradle-decompiled Minecraft
   classes; a plain `compileJava` cannot re-fetch them).
2. Kill *stale, by pid* (not by the shared `runClient`/`runServer` pattern —
   that also matches the dev's own gradle tab in another terminal). The held
   server and its FIFO writer are the stale bits from a prior run:
   `kill -9 $(pgrep -f 'sleep 999999')` (the writer) and, if a held server is
   still up, `kill -9 $(pgrep -f '[f]orgeserveruserdev')`. Do **not**
   `pkill -f 'runClient'`/`'runServer'` here — step 5's client is *this*
   session's, and the dev's tab is not yours to kill.
3. Launch the FIFO writer **first, in its own call**, verify it is alive, then
   launch the server in a **separate** call. A `sleep` or a second background job
   in the *server's* call kills the server ("no output", no process, no log — the
   global AGENTS.md launch-survival rule). A held `pkill` (teardown) kills the
   writer; recreate it on relaunch, do not share the server's call — that sharing
   is what kills the server.

   The same quirk kills the **writer**: a `sleep` (even one waiting for step 2's
   killed writer to die) or a `kill`/`pkill` in *call 1* leaves the backgrounded
   `sh` dead. So step 2's kill (and any wait to confirm it's gone) is its own
   call, and call 1 below holds *only* `rm; mkfifo; nohup &; echo` — nothing
   before the `nohup`, nothing after the `echo`. Verify the writer in a later
   call, not the same one.
   ```
   # call 1 — FIFO writer, then verify it HOLDS the fifo fd in a later call.
   # CRITICAL: use `<>` (O_RDWR), NOT `>` (O_WRONLY). The runServer task config
   # opens the fifo O_RDONLY (new FileInputStream); an O_WRONLY `>` open blocks
   # until a reader opens, and an O_RDONLY read-open blocks until a writer opens
   # — so a `>` writer + the build's read-open DEADLOCK (build hangs forever at
   # "Configure project", daemon idle in FileInputStream.open0). `<>` opens
   # non-blocking and holds the write fd before the build opens the read end.
   # (Verified 2026-10-01: `<>` writer held the fd, console write unblocked, the
   # build's O_RDONLY completed and BOOT_OK fired; `>` deadlocked until killed.)
   # Do NOT kill the one fd-holding writer to replace it (removing it left only a
   # still-pending writer and re-created the deadlock).
   # Keep ONLY these lines here: no kill/sleep/pkill, or the sh dies on call exit.
   rm -f run/server-cmd.fifo; mkfifo run/server-cmd.fifo
   nohup sh -c 'sleep 999999 <> run/server-cmd.fifo' &
   echo "fifo writer launched"
   # later call: confirm it HOLDS the fifo fd, not just "process present"
   # (present-but-blocked is still not ready):
   #   ls -la /proc/<pid>/fd | grep server-cmd.fifo
   #   (pgrep -af 'sleep 999999' lists it, but that only proves the sh is alive)
   # call 2 — server alone: only nohup + echo + pid capture, no FIFO writer, no sleep
   rm -f /tmp/village-server.log
   JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8 nohup ./gradlew \
     runServer -Dquestown.autotest=true -Dquestown.autotest.hold=true \
     -Dquestown.autotest.only=village/growth -Dquestown.tp.fifo=true \
     -Dquestown.village.agentDrive=true > /tmp/village-server.log 2>&1 &
   echo "server launch issued"
   # capture the pids IN THIS SAME CALL, right after nohup — the held server's
   # java pid is NOT the gradle wrapper pid, and the file goes stale the instant
   # a new server launches. A capture done later (after other work) has already
   # gone stale and the next teardown kills the wrong pid.
   sleep 3   # let the java child spawn before pgrep sees it
   # THIS BOX RUNS TWO REPOS: Questown-119 carries its own `sleep 999999` fifo writer
   # and a similar process tree, so `pgrep -af 'sleep 999999'` / 'forgeserveruserdev'
   # can list foreign matches. Disambiguate by the fifo fd held (ls /proc/<pid>/fd must
   # point at THIS repo's run/server-cmd.fifo), and the server pid is the forge java
   # (cmdline has --launchTarget forgeserveruserdev), not the bash wrapper that launched
   # it — **filter the pgrep on `launchTarget`**, because an unfiltered
   # `pgrep -af 'forgeserveruserdev' | head -1` twice in the 2026-10-15 session
   # picked a non-java pid and the first teardown killed nothing, leaving the live
   # server behind (caught only by a later `netstat 25565` check). (2026-10: first
   # capture wrote the bash wrapper's pid and a 119 writer pid.)
   pgrep -af 'forgeserveruserdev' | grep 'launchTarget' | awk '{print $1}' > run/hold-server.pid
   pgrep -af 'questown.autotest.hol[d]' | grep -v 'bash\|pgrep' | head -1 | awk '{print $1}' > run/hold-wrapper.pid
   pgrep -af 'sleep 999999' | grep -v pgrep | head -1 | awk '{print $1}' > run/hold-fifo.pid
   ```
4. Verify it booted (separate call): `grep -a 'Flag block entity live'
   /tmp/village-server.log` and `pgrep -af '[f]orgeserveruserdev'`, and confirm the
   FIFO writer HOLDS its fd — `ls -la /proc/<writer-pid>/fd | grep server-cmd.fifo`
   (not just `pgrep -af 'sleep 999999'`: present-but-blocked-in-open is the FIFO
   O_WRONLY deadlock signature from call 1). A dead or fd-less writer blocks every
   later FIFO write, so catch it here, not on the first `qt` command. A stale
   `:compileJava UP-TO-DATE` means the edit did not compile — force with
   `--rerun-tasks` or confirm a fresh boot line, not the old timestamps.
5. **Then** launch the client (it reconnects to the live server):
   `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8 nohup ./gradlew
   runClient -Pautojoin > /tmp/village-client.log 2>&1 &`.
   **Client focus-steal (reported 2026-10-01, unverified fix):** a client
   launched this way held the dev's keyboard focus hostage — they could not
   move the cursor back to the IDE and had to exit the game to reply. The
   suspected cause is the client JVM staying in the terminal's session;
   the porttest 119 launcher detaches with `setsid nohup sh -c '…'`. Try
   launching the client through `setsid` and let the dev confirm focus
   returns to the IDE on the next client launch before treating it as fixed. The
   agent's own `echo`/pid-capture lines are fine to keep in the call; only the
   client chain goes under `setsid`.
   The client joins in survival mode with the world set to peaceful
   (deterministic, set by `VillageWatchListener.onLogin` on the server) —
   no FIFO `gamemode`/`difficulty` commands needed. A dead player is the
   silent blocker every session hits: the chicken-arc gates on a *live*
   nearest player's inventory, so a dead player silently stalls every
   interaction beat no matter what the clickwand log says, and `tp`/`give`
   do not reach a dead player. Peaceful setup removes the whole class of
   dead-player stalls; do not rely on checking liveness in-run. If the
   world still has a dead player, it cannot progress until a live player
   holds the required item — ask the dev to respawn, do not keep feeding
   a dead player.
6. **Start the onboarding arc if the chicken is not guiding.** The arc can be
   inert (its chicken rotation was never detected), so no beat drives. Do not
   infer this from where the flag came from. "Hand-placed" vs "worldgen" is not
   a fact you can read off a flag, and a structure name in the worldgen data is
   not proof the flag came from it, so a matching `empty_town` file is not a
   verified origin. Check the arc directly with `_qtdev inspect chicken <pos>`
   (the flag's position, from `qt findflag`): if the chicken is not guiding (no
   beat, or "the chicken has stopped guiding you"), run `_qtdev spawn_chicken
   <flag pos>` once to start the arc; it clears the forfeit, reads the rotation
   once, and spawns the chicken. The individual beats still need their real
   conditions, so this only starts the arc, not fakes it.

Do not restart the server while the client is up - it kills the client and
wipes the world. If you must load a new command, kill the client, restart the
server alone, relaunch the client after. Full traps (OOM 137, client daemon
death, FIFO) are in `references/questown-dev-launch.md`.

`-Dquestown.village.agentDrive=true` makes `VillageGrowthExecutor` only call
`observe()` (no `act()`), so the executor does not fight the agent's actions.

## Per-logger DEBUG on a held run (log-capture mode, client-free)

To read a DEBUG-level trace that INFO hides (e.g. a recruit trail:
`Spawned visitor …` / `Applying quests: [ … visitorUUID=… ]`), bump ONE logger
to DEBUG rather than the whole run. A held run can run **client-free** — the
flag ticks in realtime without a nearby player — so for pure log capture you do
not need the `runClient` Dev at all; just launch the server and read the
console capture.

- **Hook (committed to `build.gradle`, `server {}` block):** an optional
  `-Dquestown.log4j.config=<file>` is forwarded to the forked server JVM as
  `-Dlog4j2.configurationFile=<file>`. No effect when the flag is absent.
- **Config recipe — two traps that `run/log4j2.debug.xml` fell into.** That file
  is a **never-loaded decoy** (its `AsyncLogger` + `File`+`filePattern` are both
  wrong on this classpath), so do **not** copy it as a template:
  - Use a **synchronous** `<Logger>`, not `<AsyncLogger>` — async needs the LMAX
    disruptor (`com.lmax.disruptor`), which is absent; log4j logs
    `NoClassDefFoundError: com/lmax/disruptor/EventHandler`, falls back to the
    vanilla config, and your DEBUG silently never fires.
  - Use a **plain** `<File>` appender — `File` does **not** accept `filePattern`
    or `<Policies>` (that is `RollingFile`-only); log4j logs `invalid element
    "filePattern"` and rejects the appender.
  - Root at `info` so everything else stays quiet; only the target logger at `debug`.
  Minimal working config (bumps `Questown:Quests`):
  ```xml
  <Configuration status="WARN">
    <Appenders><Console name="sysout" target="SYSTEM_OUT">
      <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] [%logger{36}/]: %msg%n"/></Console>
    </Appenders>
    <Loggers>
      <Logger name="Questown:Quests" level="debug"><AppenderRef ref="sysout"/></Logger>
      <Root level="info"><AppenderRef ref="sysout"/></Root>
    </Loggers>
  </Configuration>
  ```
- **Launch (verified; on the 1.20.6/neoforge branch the pinned JDK is 21,
  `java-21-openjdk-amd64` — the JDK-17 lines elsewhere in this skill are the
  1.19.2 track, and `gradle.properties` pins the branch's own JDK):**
  ```
  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 LANG=C.UTF-8 nohup ./gradlew \
    runServer -Dquestown.autotest=true -Dquestown.autotest.hold=true \
    -Dquestown.autotest.only=village/self_sufficiency \
    -Dquestown.log4j.config=file://$PWD/run/log4j2.quests-debug.xml > /tmp/log 2>&1 &
  ```
- **Verify it loaded:** the target logger's DEBUG lines appear in the console
  capture (e.g. `Questown:Quests/]: Spawned visitor …`). If they do not, log4j
  fell back to the vanilla config — the config has one of the two traps above.

## Teardown (when the run ends — do not orphan the server)

A held `runServer` with no client is a **live** run, not a finished one. When
the run ends (the dev closes the client, the world is wiped, or the session is
done) **kill the server and the FIFO writers** — a held server catches SIGTERM
and survives `pkill`, so kill the java child directly. Leaving it running
orphans a live server plus stale `sleep 999999` writers (the FIFO tangle that
blocks the next run's console and makes the next session's `qt`/`time` commands
fail).

**Kill the pids *you* launched, never by the shared pattern.** The dev runs

**A teardown call that returns empty output did not prove the kill** - it may have died mid-command (seen twice this session: `kill <pids>` + verify in one call printed nothing, and the server/writers were still alive minutes later; `bash run/capture.sh state` exposed it). Verify teardown in a *later* call with fresh evidence: `pgrep -f forgeserveruserdev` empty **and** `netstat -ltnp | grep 25565/25571` empty. `bash tools/porttest/sweep.sh` is the safe cleaner for stragglers (matches by cwd + main class; cannot match the invoking shell).
their *own* `runClient`/`runServer` gradle task in a *separate terminal tab*
that you are not watching. `pkill -9 -f 'runClient'` / `'runServer'` matches
*any* process with that string — including the dev's tab — and drops it. The
held `runServer` gradle wrapper (and the `runClient` wrapper you launched) may
also already be dead by teardown time, so the pattern only ever finds the
dev's tab. Kill by pid instead:

```sh
# server/fifo/wrapper pids are captured at launch (see the Launch step, call 2).
# The client pid is captured after the client launch (step 5):
pgrep -af 'runClient' | grep -v pgrep | head -1 | awk '{print $1}' > run/hold-client.pid
# teardown: kill only those pids, never pkill -f 'runClient'/'runServer':
kill -9 $(cat run/hold-server.pid 2>/dev/null)   # the held java server (survives SIGTERM)
kill -9 $(cat run/hold-wrapper.pid 2>/dev/null)  # the gradle wrapper (keeps living if the server dies)
kill -9 $(cat run/hold-fifo.pid 2>/dev/null)
kill -9 $(pgrep -f 'sleep 999999' | head -5) # parent sh AND the orphaned `sleep 999999` child (its cmdline is bare, no fifo path); the group kill (-PID) silently leaves the child alive, and a live child holds the fifo open and blocks the next run's commands
kill -9 $(cat run/hold-client.pid 2>/dev/null)   # this session's client, not the dev's
# verify, then kill stragglers by pid: pgrep -af 'sleep 999999'; pgrep -af 'runClient' - the client java can outlive its wrapper pid; kill any remaining match whose cmdline holds -Pautojoin (this session's client), never a bare pkill -f 'runClient' (the dev's tab)
# a stale hold-server.pid (wrong pid) means the capture wasn't done at launch — re-capture live: pgrep -af 'forgeserveruserdev'
```

The held server catches SIGTERM, so it can outlive a `pkill`; kill it by the
captured pid (or `pgrep -af '[f]orgeserveruserdev'` and `kill -9 <pid>`). The
`runClient`/`runServer` *gradle wrappers* for *your* launch are ordinary
processes a `kill -9` reaches — no need to `pkill` them by name, and that is
what kills the dev's tab. Re-check with `pgrep -af '[f]orgeserveruserdev'`
and `pgrep -af 'sleep 999999'` after; confirm the java child is actually gone
before the next run.

## The observation cadence (~10s windows)

**Timeout discipline: a 60s hard cap on every command.** No batched
multi-minute polling loops in one tool call. Drive the run in short
steps — launch, then a bounded wait, then check state in a fresh
call — and keep each `timeout`/wait under a minute. A long held wait is
wasted money if it stalls, and it hides the moment something wedged.
If you need to poll a long-running boot, loop *short* sleeps (each
command ≤ 60s) instead of one big sleep.

Observe in ~10s windows — one observe cycle (the flag's `--- observe ---` line
is 200 ticks, ~10s). Start at 10s and go **up**, not down, if the state has not
changed by the next cycle. After each window, report (a) what is **clear** about
what the town/villagers need, and (b) what is **ambiguous, unknown, or
blocking**. Act on the current output, not a pre-baked plan. **Waiting for
production is a blocked step**: two observe windows with no change is a
stall, not patience - inspect the villager (status, carrying), do not
schedule a third window of waiting. Likewise, a yes/no "will this townie
start working?" is settled by `_qtdev inspect villagers <flag>` (its
job + need + status line) on the first probe; reserve time-elapsed windows
(daily report, milestone) for questions that actually need time to pass.

**Hunting (and all leaver work) is off-town — the townie "disappears."**
A hunter/gatherer/miner/fisher sent to work is teleported to an imaginary
off-town location and the "work" is a **biome loot-table roll**
(`data/questown/loot_tables/jobs/<job>/<biome>.json`, gated by known loot),
not collecting entities in the world. A town with **zero animals in the
world still hunts** and brings back meat. So: never `summon` pigs/rabbits
near the flag to "give the hunter prey", never chase a hunting townie
off-map, and never read "no animals in the world" as a reason the townie
won't work. If a leaver comes back with nothing, suspect **missing known
loot / biome knowledge** (a gatherer map / explorer), not missing mobs.
CONTEXT.md "leaver job" is the source of truth.

**Confirm against the latest observe cycle, not the last line you grepped.**
Observe lines and the need/job lines interleave, and a line you grepped a
window ago is not the current state. Before reporting a townie's need or job
status, read the most recent `--- observe ---` cycle (or a fresh `_qtdev inspect <pos>` / `_qtdev inspect villagers <pos>`) so
a need that cleared a window later is not reported as still open. When the dev
(as a live observer) says a state is not what the log shows, the log is the
stale one — re-read, do not argue the old line.

**After any action, read the broadcast first.** The broadcast is the
immediate feedback a player sees in chat (`TownMessages.broadcastMessage`).
It fires when something changes: a quest completes, a room is destroyed or
created, a new quest appears, visitors arrive. After performing a command
(`place_mat`, `place_board`, `place_door`, `clickwand`, `clickheld`,
`registerroom`, `free_food`, `tp`, `time`), check the broadcast channel before
the observe cycle:

```
grep -aE 'Broadcasting message' /tmp/village-server.log | tail -5
```

The observe cycle (`[village] KNOWN/UNCLEAR`) is the steady state; the
broadcast is the event. A `room_destroyed` or `quest_lost` broadcast after
`place_mat` means the mat destroyed the room; the observe cycle showing
`rooms=0` is the consequence, not the cause. Read the broadcast to find
what just happened; read the observe cycle to see what is true now.

The broadcast is not proof a quest completed. It fires on room re-creation,
not on completion; a `recipe_created`/`quest_completed` broadcast can appear
while the quest is still open. Verify completion against the done list /
`_qtdev inspect quests <pos>`, not the broadcast. A `ROOM` quest needs its
own room (put the recipe block in a room built for that quest); the quests
tab says so, but a new player can miss that each quest wants a distinct room.

**A room broadcast means the room is registered - do not argue it from the
observe line.** `room_created` / `recipe_created` broadcast from
`TownMessages`, which only fires when a room is registered in
`TownRoomsMap`. If you see that broadcast, the room IS registered; the
observe line's `rooms=N` (often 0, because it counts a different set, e.g.
walled/recipe-matching rooms) is a *second* counter and does not
overrule the broadcast. Do not conclude "the room is not registered"
from `rooms=0` when the broadcast says it was. The recipe-change events
(`ActiveRecipes` created/changed - the ones that drive block
conversions like sign->job-board) run only on a registered room, so a
broadcast you read as "not registered" will make you skip the very
mechanism you were debugging.

To read what a broadcast *says*, the English strings live in
`src/main/resources/assets/questown/lang/en_us.json` under
`messages.building.*` and `messages.town_flag.*` (e.g.
`messages.building.room_created`, `messages.building.recipe_created`,
`messages.town_flag.quest_completed`, `messages.town_flag.quest_lost`).
Do not build a second dictionary in this skill - grep the lang file for
the key the log printed, and read the `messages.*` / `dialog.*` / `room.*`
translation keys it carries.

Also read each window:
- client log: `grep -aE '\[CHAT\]' /tmp/village-client.log` (what the dev sees)
- observe lines: `grep -aE '\[village\] (KNOWN|UNCLEAR)' /tmp/village-server.log`
- quest/villager detail: `_qtdev inspect <pos>` / `_qtdev inspect quests <pos>` / `_qtdev inspect villagers <pos>` (the tab literal comes *before* the pos; `_qtdev inspect <pos> villagers` does not parse)

## Verdict (declaring the run a pass)

A held run verifies the **mechanism**, not a literal scenario detail. When the
fix's signature moment (e.g. *a specific item* being deposited) is not forced by
the chosen watchtower — the hunter never hunts, so no mutton is ever carried — the
pass is on the observable mechanism (work output deposited to the store, `consumed==0`,
the town healthy/growing), and the notes must say the literal moment was not
forced and that the mechanism is identical for any carried output. Do **not**
report the literal moment as observed (false pass), and do **not** re-run the
watchtower expecting it to appear (it will not, in a scenario that can't produce
it). The honest "mechanism verified, literal detail not forced" is the correct
verdict.

## The console command set (the agent's hands)

The agent has no mouse/keyboard; these console commands (via the FIFO) are the
player actions. All run from the console (no permission gate) and are registered
in `CommandInit.java`.

Each is written at the level a brand-new player would know it: what the action
does in the world. The agent is playing a new player being onboarded by the
game, so it does **not** carry the why into the run — which quest a command
closes, which need it clears, what a job does with a placed block. It performs
the action a player would, then *observes* the town's response (a quest closing,
a need appearing or clearing, a villager moving). The why is something the agent
learns by watching, not something the docs hand it ahead of time.

A blocked step is the run's open question, not a lookup. Do **not** open
`VillageGrowthExecutor` (or any source, or a `.scratch`/handoff note) to find
where to place a block, what a command closes, or what happens next — that is
re-deriving the mechanism the discipline is meant to keep out of the run. The
first move when a step cannot proceed is `_qtdev inspect chicken <pos>` (the
flag's position): the chicken's narration (to do / how / where) is the in-world
guidance and names the next action without a doc. It also prints the player's
**held** item (the hand a new player looks at); when a `clickheld` no-ops, read
that line to see the held item is the wrong one (the town wand instead of a
sign, say) and correct the hand, rather than retrying `clickheld` at new spots.

A blocked step is **not** automatically a check-in. Before surfacing it, run
the tools the skill gave you, the console command set (`registerroom`,
`free_food`, `place_mat`, `clickwand`, `clickheld`, `place_board`,
`build_room`, `place_block`, `look_room`, `place_player`, `change_job`,
`spawn_chicken`, `setblock`, `tp`, `time`, and the `inspect` variants), in the
context of the chicken's guidance, reading each response. The only observation
channels are the server log (the `--- observe ---` cycles and the town's chat)
and those `_qtdev` commands. "Blocked" is not a check-in until you have run the
tools you have and the response confirms the precondition still isn't met. For
example, `registerroom` returns the door but the room still won't register:
*that* is a real dead end, not "the first line looked blocked." Checking in on a
blocked-looking line before running your tools defers the run; the agent is the
main driver, the dev a secondary observer. Trying a tool is not a peek. The
tools are your hands, and reading each response is how you learn the game, so do
not read code or a stale doc to decide which tool to try.

**An `inspect` can *advance* a beat, not just read one.** `_qtdev inspect
villagers <flag>` opens the villager UI and `_qtdev inspect <flag>` opens the flag
menu - each calls `markXUiOpened`, so the `WAITING_FOR_VILLAGER_UI` /
`WAITING_FOR_FLAG_UI` beats advance when you read the tab, the way a player
right-clicking the villager/flag would. A blocked "look at a villager" / "look at
the flag" beat is **not** a dead end and **not** a reason to try `clickheld` on a
mob (a mob is not a block - `clickheld` is `useItemOn`, a block right-click, and
never acts on a mob). Read the tab with `inspect`; the beat advances on the next
tick.

**A `UNMET_ITEM` need is the job's domain, not a command - except the one item
a player supplies.** A need for an item (mutton, food, and most others) clears
because a townie's job *produces* it into the container its job block points at.
Let that job run and watch whether the need clears, the same way a player sees it
clear or not. The exception is an open sword *quest* the player is meant to supply
(wooden sword in onboarding, stone sword after): a new player clears it by leaving
the item in the storeroom the game shows you, and that has a hand -
`_qtdev complete_sword_quest` (see below).
`_qtdev free_food` covers the free-food beat only; it is not a general deposit.
Do not invent other shortcuts: there is no `_qtdev deposit`, and dropping an item
into a container for a *need* (not a player-supplied quest) overwrites the very
lesson the quest is teaching (the supply chain) and reads as success in a log that
was never the right one. When the chicken's cue is "put X in the world," place the
block the matching hand places it.

- `_qtdev inspect <flag>` - the flag menu a player would see by right-clicking the flag: which tabs are shown (villagers, quests, economics, block of progress, crafting), with red-dots, and the static content. `_qtdev inspect <tab> <pos>` returns one tab (`villagers`, `quests`, `economy`, `blocks`, `crafting`).
- **`inspect villagers` also shows what a player's eyes see on the villager's body**: a `carrying: Nx item` line for the items in their hands, a `holding a Block of Progress` line (the icon a player sees above their head), and a `cannot deposit: ...` line when they are holding goods the town's chests cannot take (every chest full, or no chest) - what a player learns by watching the villager at a chest and opening it themselves. `_qtdev inspect chicken <pos>` narrates the current onboarding beat as the chicken's observed behavior (e.g. "The chicken looks at your town wand, then back to the flag base [Use the town wand on the flag base]"), keyed off the player's held item AND the actual world state so the same beat reads differently in different states. This is the new-player vantage: it returns what a right-click shows, not the gate behind it - no internal state name, no condition line, no arc map. Use it to decide the next action; do not reach for `_qtdev help` (which prints the internal beat/`status`/map - the cheatsheet) or the removed `qt quests` / `_qtdev villagers` (which reach the quest board without going through the menu).
- **The chicken's hint reflects what it sees.** Its wording changes with the actual world state, so read it literally rather than assuming a beat is stuck. The chicken observes the world, not the internal beat state.
- `_qtdev registerroom <x> <y> <z>` — register a door at the position, the way a
  player right-clicking a door with the town wand would.
- `_qtdev place_door` (no coordinate) or `_qtdev place_door <x> <y> <z>` — place
  a **full** door (both halves). With **no coordinate** it drops the door at the
  chicken's door target (the door spot the door beat checks), so an agent that
  can not see the chicken's peck can still land the door in the right place; with
  a coordinate it places at that block, the way a player using a door item on a
  block would. Use this for a door, **not** `setblock`.
- `_qtdev place_mat` (no coordinate) or `_qtdev place_mat <x> <y> <z>` — place a
  welcome mat. With **no coordinate** it drops the mat a block beside the flag, so
  an agent that can not see where the chicken's "gate" cue points can still place the
  mat in a safe spot; with a coordinate it places at that block, the way a player
  using a mat item would. Prefer the bare form: the welcome mat has no fixed spot in
  a hand-placed town.
- `_qtdev clickwand <x> <y> <z>` — right-click the flag with a stick so it
  converts to the town wand (the real flag interaction; the agent's stand-in
  for the wand beat a player does by hand).
- `_qtdev clickheld <pos>` — right-click a block with the player's **held**
  item, keeping that item's NBT (the way a player would). It does whatever the
  held item does: for the paired town wand from `clickwand`, for an action the
  current moment asks the *player* to perform with what they hold. It is **not
  a block-placement tool** — if the held item isn't what the moment needs (the
  town wand from `clickwand`, say), it places nothing, and retrying it won't
  help; that is the wrong hand, not a failed placement. A player too far gets
  `TOO_FAR_AWAY`.
- **Putting a block in the world is its own hand, never `clickheld`.** To put a
  block in the world use `setblock` or the matching command (`place_door`,
  `place_board`, `place_mat`) — the same hand that placed the door and the
campfire. When the chicken's hint is "put X in the world" (a door, a campfire,
  a sign, a chest, a mat), place X as a block this way; do not
  route it through `clickheld`, whose held item may be the wrong one. Only use
  `clickheld` when the moment asks the player to *use the held item* on a
  block (the wand on a door, an item's action on a block).
- `_qtdev place_board <x> <y> <z>` - place a job board at the position.
- `_qtdev build_room <x> <y> <z>` - build a room around a door spot the way the helper
  chicken does: it places its own door, lays the shell, and registers the door so the room
  scan detects it. Use it for a room quest ("each room quest needs its own room"). It is
  **atomic**: if the door spot is not clear (an existing wall at wall-level intrudes the
  footprint, or the door cell is not open) it backs off and logs why rather than half-building -
  so read the `[build_room] refusing to build …` line and pick a clear spot, do not assume it
  built. The room is detected by the scan on a later tick (not synchronously), so read the
  next observe cycle / `inspect doors`, not an instant `recipe_created`, to confirm.
  **A clean spot can still yield a leaky shell.** `build_room` lays the chicken's
  `ChickenRoomShellPlan` perimeter, which at some footprints is not a closed box -
  the room then reads `NOT_ENCLOSED` on `look_room` and never registers, even though
  the `[build_room] laid N shell blocks` line logged success. The log line says the
  shell was *laid*, not that the room *closed*. Confirm with `look_room`; if it is
  `NOT_ENCLOSED`, the shell shape is the problem (not the spot) - the workaround is a
  hand-laid rectangular shell (5x5 outer walls, single door, 3x3 interior, the shape
  the wall walk closes cleanly) plus `_qtdev registerroom <door pos>`.
- `_qtdev place_block [index] <block>` - place a block inside a registered room's interior
  (the way a player placing a block on the floor would). With no index it lists the room's
  empty interior cells (index, x, y, z); with `<index> <block>` it places the block at that
  cell. Use it to put a quest's recipe block *inside* the room -
  a block placed at a coordinate you guessed may land outside the room footprint and never
  register. It resolves a **block** id (not an item id), so it where a vanilla `setblock` (which
  resolves an item) would not. List first, pick a cell, then place - do not guess a coordinate.
  If `place_block` lists no cells for a room that should have open floor, do not guess -
  read the room with `look_room` first.
- `_qtdev look_room <door pos>` - the agent's eyes for a room, the way a player
  standing in front of it sees walls and floor. Prints the room (or, for a door with
  no detected room, a probe around the door) as an ASCII grid at floor level: `#` a
  wall cell, `D` the door, `.` an open interior cell, and a letter per contained
  block (legend printed below). Use it before placing anything inside a room by
  coordinate, and whenever a door shows `NO_RECIPE` (where is the open floor?) or
  `NOT_ENCLOSED` (which wall cell is open?).
  **Reading the grid to a coordinate:** each row is one `z`, and the columns run
  west-to-east from the printed `x min..max` — so the Nth character of a row is
  `x = minX + (N-1)`. E.g. `x 5..8`, row `z=2: #c.#` puts `c` at `6,64,2`, not
  `5,64,2`. Do not guess a contained block's coordinate from the glyph's visual
  position; count from minX.
- `_qtdev place_player` - teleport the player into an open interior cell of the
  largest registered room, the way the player would walk in. This is the hand for
  a cue that asks the player to be inside a room.
- `_qtdev change_job <villager uuid>` - the change job tab's action as a console
  hand: spends one Block of Progress from the flag's store and requests a job
  change for the villager, which takes effect overnight. Reports clearly when
  the flag holds no Block of Progress. The villager's uuid is in
  `inspect villagers`, which also names this command.
- `_qtdev inspect quests <pos>` also prints `room needs: ...` for a ROOM quest -
  the blocks the room must contain, the way the quest UI shows the recipe as
  icons. Read it before furnishing a
  room quest; do not guess a room's recipe from its name.
- `_qtdev spawn_chicken <x> <y> <z>` - force the helper chicken to spawn for a
  flag whose arc is inert (its chicken rotation was never detected). Run it when
  `_qtdev inspect chicken <pos>` shows the chicken is not guiding; do not decide
  from where the flag came from. Bypasses the rotation-detection/forfeit gates;
  the individual beats still need their real conditions.
- `_qtdev complete_sword_quest` (alias `supply_sword`) - clear the one kind of
  item quest a *player* supplies: an open sword quest (wooden or stone). It
  deposits the wanted sword into a registered container (the storeroom) - the
  real player path of leaving the item in the chest the game shows you, not a
  magic clear, so the supply-chain lesson is preserved. One time per quest: once
  the quest closes, a re-run finds no open sword quest. Scoped to swords only:
  every other item need is a townie's job, not a command, so let those run
  instead. Use it only when an open item *quest* (not a
  townie's `UNMET_ITEM` need) is the blocking thing and no townie job will produce
  it; if it returns a "no registered container" line, the town has no storeroom yet
  - that is a real dead end, not a no-op. After it, read the broadcast and confirm
  completion against the done list / `_qtdev inspect quests <pos>`, not the
  broadcast.
- Vanilla (also from the FIFO/console): `tp <player> <x> <y> <z>` (teleport the
  named player), `time set <ticks>` (set the time; absolute ticks past 24000 work -
  37000 is day 1 night, 61000 day 2, so use them to reach a *later* day's night),
  `setblock <x> <y> <z> <block>` (set a block), `qt tp` (jump the player to the
  nearest flag).
  **A campfire sleep works once per in-game day, and a failed sleep is silent.**
  Sleeping at the campfire at night then `time set 13000` again puts you back in
  the SAME night: `clickheld` logs `-> PASS` but no `SleepFinishedTimeEvent`
  follows, with no error line. Advance to a later day's night (37000, 61000,
  85000, ...). Also: the first `clickheld` after a tp can no-op with a `Trying
  to drop too quickly` warn - retry it once before concluding anything.
  **The client can drop at the moment a campfire sleep starts** (`Dev left
  the game` right after the first sleep heartbeat, client back at the menu;
  seen twice in one session, cause unknown). The sleep dies with it: relaunch
  the client, `tp` back, and `clickheld` once - a rapid second click can
  interrupt a sleep that did start (`OTHER_PROBLEM`), so give one click ~15s
  before retrying.
  **A bare `tp <x> <y> <z>` (no player target) silently no-ops from a console
  source** — it has no player to move, so the command appears to run but the
  player never moves, and there is no log line to tell you it failed. Always
  target a player (`tp dev <x> <y> <z>`, or `tp @s`). This bit the chest beat:
  the chicken's "stand in the room" guidance was unmet the whole time because
  the teleport never happened, and it looked like an unbreakable dead end. For a **door**, use `_qtdev place_door`, not `setblock` (see above).
- **Arg types: use `ItemArgument`/`BlockPosArgument`, not `StringArgumentType
  .word()`.** From the FIFO/console, `StringArgumentType.word()` fails to parse
  with `Expected whitespace to end one argument, but found trailing data` (even
  a single token), and a chain of `IntegerArgumentType` args fails with
  `Incorrect argument for command` — while `ItemArgument` (vanilla `give` uses
  it) and `BlockPosArgument` (`registerroom` uses it) parse fine. In 1.19.2 the
  item arg is `net.minecraft.commands.arguments.item.ItemArgument` (singular
  `item`, not `items`) and needs the `CommandBuildContext` from
  `event.getBuildContext()`; resolve with `ItemArgument.getItem(ctx, "item").getItem()`.

- **A held-item beat: the held item is whatever the player is holding, and `give` does not change it.** `give <player> <item>` puts the item in an empty inventory slot, **not** the hand, so `clickheld` (which uses `getMainHandItem`) still uses the old held item — the `give` looks like it worked ("Gave 1 [X]") but the hand is unchanged, and the beat won't advance. To use a held-item action (a campfire sleep, an item's use-on a block), the right item must be **in the hand**: `clickwand` puts a stick in the main hand and converts it to the town wand on the flag, or `clickheld <flag pos>` with a stick in hand consumes the stick and leaves the wand held. There is no `select`/`hand_to` command; do not `give` then `clickheld` expecting the give to set the hand.
- **Smoke-test a command before the long boot cycle.** After registering/editing
  a command, send it once and confirm the log shows **no** `Incorrect argument`
  / `Expected whitespace ... trailing data` / `Cannot parse` line for it (a
  `No player was found` or the command's own log line means it parsed and ran).
  A broken registration or a wrong arg type (see above) shows up as a parse
  error, not an execution error — catch it with one test command, not after a
  full boot + onboarding.

Before re-implementing a player action in a command, check this set — a command
may already exist. A command that takes a `pos` takes it as a literal argument,
not a derived one (nearest flag, manhattan distance).

## Past onboarding is not "done"

The chicken-arc (the helper-chicken onboarding) finishing is **not** the end of
the run. When the last onboarding beat is done, the town keeps running: townies
keep cycling jobs and surfacing needs, quests stay open, the flag keeps ticking.
Do **not** report "done" when the arc ends.

- The arc's last beat (`AWAITING_WORLDLY_SEEDS_DELIVERY`) is a **gap** the
  command set cannot perform: it needs a live player to hand the Worldly Seeds
  to the chicken. But the chicken no longer tells the agent to wait — its
  `_qtdev inspect chicken` line now points back at the flag and names the open
  room quest ("a room quest is still open - build a room for it"), so the gap
  reads as a concrete next action, not a dead end. A gap pauses the *arc*, not
  the *run*. Keep observing the world past it: read the observe cycles, the
  town's chat, and `_qtdev inspect` for new needs, jobs, and quest changes. The
  open `ROOM` quests each need their own room; a townie's `UNMET_ITEM` need
  clears through its job, while the sword *quests* a player supplies (wooden,
  stone) clear with `_qtdev complete_sword_quest` - keep observing
  and driving what you can (build the room, supply the real way), not the
  onboarding.
- "The chicken stopped guiding" is a hand-holding cue ending, not a stop signal.
  Switch to observing the town's own observable state (roster, jobs, needs,
  quests open/done) and keep driving what you can with the command set.
- Only stop (or check in as a true dead end) after the **town** shows no
  progress across several observe cycles, not after the **arc** ends.

## Check-ins (the only time to propose a code change)

Periodically check in with the dev: report what happened and what you plan next
based on the game's observable status. Check-ins are the **only** time code
changes can be proposed/approved. Outside a check-in, play the game; do not
propose fixes from a code read. A check-in is a last resort after you have run
the tools you have (see the blocked-step rule): a blocked-looking line you have
not driven with your own tools is not a check-in.

When a log line contradicts the expected result (a command gave the wrong
item, a log says "Created Air"), check the *effect* (what is in the world /
inventory) before trusting the log line, and ask the dev for the observation
they have rather than keep re-deriving from code.
