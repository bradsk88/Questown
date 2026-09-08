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

This skill consolidates what was scattered across `AGENTS.md`,
`references/questown-dev-launch.md`, `questown-behavior-verify`, and
`commands/test/COMPLEX.md`. Before the first launch, read
`~/.pi/agent/references/questown-dev-launch.md` — it has the full OOM-137 /
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
2. Kill stale: `pkill -9 -f '[f]orgeserveruserdev'`, `pkill -9 -f 'runClient'`,
   `pkill -9 -f 'sleep 999999'`.
3. Launch the FIFO writer **first, in its own call**, verify it is alive, then
   launch the server in a **separate** call. A `sleep` or a second background job
   in the *server's* call kills the server ("no output", no process, no log — the
   global AGENTS.md launch-survival rule). A held `pkill` (teardown) kills the
   writer; recreate it on relaunch, do not share the server's call — that sharing
   is what kills the server.
   ```
   # call 1 — FIFO writer, then verify alive in a later call
   rm -f run/server-cmd.fifo; mkfifo run/server-cmd.fifo
   nohup sh -c 'sleep 999999 > run/server-cmd.fifo' &
   echo "fifo writer launched"
   # later call: pgrep -af 'sleep 999999'  (confirm the sh is alive)
   # call 2 — server alone: only nohup + echo, no FIFO writer, no sleep
   rm -f /tmp/village-server.log
   JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8 nohup ./gradlew \
     runServer -Dquestown.autotest=true -Dquestown.autotest.hold=true \
     -Dquestown.autotest.only=village/growth -Dquestown.tp.fifo=true \
     -Dquestown.village.agentDrive=true > /tmp/village-server.log 2>&1 &
   echo "server launch issued"
   ```
4. Verify it booted (separate call): `grep -a 'Flag block entity live'
   /tmp/village-server.log` and `pgrep -af '[f]orgeserveruserdev'`, and confirm the
   FIFO writer is alive — `pgrep -af 'sleep 999999'`. A dead writer blocks every
   later FIFO write, so catch it here, not on the first `qt` command. A stale
   `:compileJava UP-TO-DATE` means the edit did not compile — force with
   `--rerun-tasks` or confirm a fresh boot line, not the old timestamps.
5. **Then** launch the client (it reconnects to the live server):
   `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8 nohup ./gradlew
   runClient -Pautojoin > /tmp/village-client.log 2>&1 &`.
6. Put the dev in survival: `echo 'gamemode survival Dev' > run/server-cmd.fifo`.

Do not restart the server while the client is up — it kills the client and
wipes the world. If you must load a new command, kill the client, restart the
server alone, relaunch the client after. Full traps (OOM 137, client daemon
death, FIFO) are in `~/.pi/agent/references/questown-dev-launch.md`.

`-Dquestown.village.agentDrive=true` makes `VillageGrowthExecutor` only call
`observe()` (no `act()`), so the executor does not fight the agent's actions.

## Teardown (when the run ends — do not orphan the server)

A held `runServer` with no client is a **live** run, not a finished one. When
the run ends (the dev closes the client, the world is wiped, or the session is
done) **kill the server and the FIFO writers** — a held server catches SIGTERM
and survives `pkill`, so kill the java child directly. Leaving it running
orphans a live server plus stale `sleep 999999` writers (the FIFO tangle that
blocks the next run's console and makes the next session's `qt`/`time` commands
fail):

```sh
pkill -9 -f 'runClient'
pkill -9 -f 'runServer'
kill -9 $(pgrep -f '[f]orgeserveruserdev')   # the held server survives pkill
kill -9 $(pgrep -f 'sleep 999999')          # the FIFO writers
```

Re-check with `pgrep -af '[f]orgeserveruserdev'` and `pgrep -af 'sleep
999999'` after — a held server can outlive the `pkill`.

## The observation cadence (~10s windows)

Observe in ~10s windows — one observe cycle (the flag's `--- observe ---` line
is 200 ticks, ~10s). Start at 10s and go **up**, not down, if the state has not
changed by the next cycle. After each window, report (a) what is **clear** about
what the town/villagers need, and (b) what is **ambiguous, unknown, or
blocking**. Act on the current output, not a pre-baked plan.

**Confirm against the latest observe cycle, not the last line you grepped.**
Observe lines and the need/job lines interleave, and a line you grepped a
window ago is not the current state. Before reporting a townie's need or job
status, read the most recent `--- observe ---` cycle (or a fresh `qt quests`) so
a need that cleared a window later is not reported as still open. When the dev
(as a live observer) says a state is not what the log shows, the log is the
stale one — re-read, do not argue the old line.

**Read the chat channel, not only the `[village]` observe lines.** The town
speaks in chat (`TownMessages.broadcastMessage` sends "Quest completed: X",
"New visitors will arrive after all players sleep", "New quests are
available! Right-click the town flag"). Each window read both:
- server log: `grep -aE 'Broadcasting message' /tmp/village-server.log`
  (proof the town sent it)
- client log: `grep -aE '\[CHAT\]' /tmp/village-client.log` (what the dev sees)
- observe lines: `grep -aE '\[village\] (KNOWN|UNCLEAR)' /tmp/village-server.log`
- quest detail: `_qtdev quests` (lists the quests)

**The broadcast is not proof a quest completed.** It fires on room
re-creation, not on completion; a `recipe_created`/`quest_completed` broadcast
can appear while the quest is still open. Verify completion against the done
list / `qt quests`, not the broadcast.

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
`VillageGrowthExecutor` (or any source) to find where to place a block, what a
command closes, or what happens next — that is re-deriving the mechanism the
discipline is meant to keep out of the run. The only observation channels are
the server log (the `--- observe ---` cycles and the town's chat) and the
`_qtdev` commands. When a step cannot proceed, surface it as the blocking
question in a check-in; do not resolve it by reading code.

- `_qtdev help` - the onboarding guide the helper chicken would show a human
  player, as text an agent can read from the log. It prints the current
  chicken-arc beat as a `need` (what the arc wants), a `how` (a command or
  vanilla action the agent can use, since it can't right-click), a `teaches`
  (the legibility gap the beat exposes - the stumbling point), a `status` (is
  the arc even running and is the beat's condition met, so a stale beat is not
  mistaken for progress), plus a compact map of the whole arc. Consult it when
  stuck on first-town onboarding; it gives the next beat and why it is stuck,
  **not** the game's why (which you still learn by watching the town's
  response).
- `_qtdev registerroom <x> <y> <z>` — register a door at the position, the way a
  player right-clicking a door with the town wand would.
- `_qtdev quests` — list the flag's active and complete quests.
- `_qtdev villagers` — inspect the town's villagers: each one's need (and, for an
  UNMET_ITEM need, the item it lacks), the quests that belong to that villager,
  and the red-dot cue that marks the quests tab. Mirrors what right-clicking a
  visitor shows.
- `_qtdev deposit <item> [count]` — put an item into a registered container, the
  way a player dropping an item into a chest would.
- `_qtdev place_mat <x> <y> <z>` — place a welcome mat at the position.
- `_qtdev place_board <x> <y> <z>` - place a job board at the position.
- `_qtdev spawn_chicken <x> <y> <z>` - force the helper chicken to spawn for a
  flag, so a held run can drive onboarding on a flag that was not placed in an
  empty_town structure (and so never had its rotation detected, leaving the
  arc inert). Bypasses the rotation-detection/forfeit gates; the individual
  beats still need their real conditions.
- Vanilla (also from the FIFO/console): `tp <x> <y> <z>` (teleport),
  `time set <ticks>` (set the time of day, 0–24000), `setblock <x> <y> <z>
  <block>` (set a block), `qt tp` (jump to the nearest flag).
- **Arg types: use `ItemArgument`/`BlockPosArgument`, not `StringArgumentType
  .word()`.** From the FIFO/console, `StringArgumentType.word()` fails to parse
  with `Expected whitespace to end one argument, but found trailing data` (even
  a single token), and a chain of `IntegerArgumentType` args fails with
  `Incorrect argument for command` — while `ItemArgument` (vanilla `give` uses
  it) and `BlockPosArgument` (`registerroom` uses it) parse fine. In 1.19.2 the
  item arg is `net.minecraft.commands.arguments.item.ItemArgument` (singular
  `item`, not `items`) and needs the `CommandBuildContext` from
  `event.getBuildContext()`; resolve with `ItemArgument.getItem(ctx, "item").getItem()`.

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

## Check-ins (the only time to propose a code change)

Periodically check in with the dev: report what happened and what you plan next
based on the game's observable status. Check-ins are the **only** time code
changes can be proposed/approved. Outside a check-in, play the game; do not
propose fixes from a code read.

When a log line contradicts the expected result (a command gave the wrong
item, a log says "Created Air"), check the *effect* (what is in the world /
inventory) before trusting the log line, and ask the dev for the observation
they have rather than keep re-deriving from code.
