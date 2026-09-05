Try to avoid asking the user to test something themselves. You have access to a tool called "autotest". When unsure whether to run a heavy check (an autotest, a dev client, a build), default to running it yourself rather than offering it as the user's choice or deferring it.

When the user reports a behaviour that does not work ("X sits idle after Y", "this does nothing"), that is a *verification* question, not a code investigation — the first move is an autotest, not reading code to speculate. "Understanding the code" is not "does it work"; a code reading that cannot settle a broken-behaviour report is a signal to set up the test, not to keep re-reading the same files.

A perf/lag report ("grinds to a halt", "can't keep up", "half-waiting for server") is the same kind of verification question. Before asserting which code costs the tick, run the perf autotest: `./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.only=perf/town_large` — its `TickProfile` reports the flag-tick average vs the 50ms budget and the per-phase µs (which phase is hot). A perf test that disproves a suspected cause *is* the finding, not a nuisance to work around; do not commit a perf fix before running it.

## Running the dev client (`./gradlew runClient`)

**When the background client task reports exit code 1 / 143, or "Gradle build daemon disappeared unexpectedly", that is just the user closing the Minecraft window.** It is NOT a crash, a launch failure, or a regression. Do not investigate it, do not re-read the log looking for the cause, and do not relaunch — the user closed it on purpose. Only treat it as a real failure if the log shows an actual exception/crash BEFORE the window opened.

Launch the client with `run_in_background: true` and then **immediately hand control back to the user**. Never follow a launch with a foreground wait-loop (`for ... sleep ...` polling the log for "Sound engine started") — that holds the turn open and blocks the user from typing. If you need the boot/gameplay log later, just read the task's output file on a later turn.

When writing unit tests, DO NOT "simulate" core questown logic. If a situation comes up which causes testing to be impossible due to insufficient interfaces on the real code: 1) make a note of that, 2) Add a failing assertion to the test so it is possible to understand what tests are failing due to the code not being testable, 3) continue working on what is possible to test.

A class whose static initializer touches Forge registries (`Items`, `Blocks`, `ForgeRegistries`) cannot be loaded in a plain unit test — it throws `ExceptionInInitializerError` / `NoClassDefFoundError` at `<clinit>` (the class just won't load, not a failing assertion). To test its logic, extract the pure part to a Forge-free class and test that (e.g. `RecipeItemConfig`'s encode/decode → `ItemWeightCodec`).

A test that uses vanilla bootstrap (`ItemStack` NBT, `Bootstrap.bootStrap()`) must call `SharedConstants.tryDetectVersion()` **before** `Bootstrap.bootStrap()` in `@BeforeAll`; without it the boot dies with `ExceptionInInitializerError` at `Util.doFetchChoiceType` / `Registry.<clinit>`. Pattern: `PostDowntimeWarperNoOpGuardTest`.

Do not make design decisions without first consulting the local docs/ files. There is an index that can help with doing that efficiently.

Do not build large functions unless there is a compelling reason to do so.

Do not choose nesting over early returns.

Do not use deprecated methods in new code. If there is no other option, prompt the user to confirm.

Before calling a raw Minecraft API for a common integration (text splitting, components/styling, tooltip text, sounds, item names, on-screen text), scan `mc/Compat`, `mc/Util`, and `gui/RenderUtil` first and reuse the existing wrapper (e.g. `Compat.splitText` not `font.split`; `Compat.translatableStyled` not `.withStyle`). Add one to `Compat` if none fits. See `docs/solutions/conventions/prefer-compat-util-renderutil-wrappers.md`.

## Codebase scope

"the codebase" for this project spans **two repos**: Questown and the adjacent
**RoomRecipes** (`/workspace/IdeaProjects/RoomRecipes`). Questown imports
`ca.bradj.roomrecipes.*` heavily (200+ imports) — room/wall/recipe detection, the
resumable `LevelRoomDetector`, `MultiLevelRoomDetector`, etc. all live there. When a
task asks about a pattern "in the codebase," **search RoomRecipes too**, or say you
only searched Questown — do not answer "not present" from a Questown-only search.

Before calling a pattern "established" / "production," check its **actual call sites
and trigger**, not just that a matching class exists. A class can be shared between a
synchronous production path and a spread-across-ticks debug/test path (e.g. the
resumable `LevelRoomDetector.proceed()` is used synchronically by
`LevelRoomDetection.findRooms()` but only spread across ticks by the debug-task
pump). Shape match ≠ status; verify the trigger.

When building or changing a `gui/` screen, follow `docs/solutions/conventions/gui-layout-flow-and-linter.md` (flow layout, not hardcoded positions) and verify with the dev-only `gui-lint` oracle — open the screen in a dev client and confirm `[gui-lint] <Screen> — OK, no layout violations`.

## Build verification

A Gradle `BUILD SUCCESSFUL` with the task `up-to-date` did not recompile your edits — it only re-ran cached outputs, so it proves nothing about the code you just changed. After changing code, check the task says `executed` (or pass `--rerun-tasks`) before trusting a build as a pass. Applies to `compileJava`/`test`/`build`.

`./gradlew build` is **red by default**, independent of your changes: `ChickenArcHintsTest.todo_…` is a deliberate `Assertions.fail` marker (chicken-arc mod-icon beats that need a Forge mod-loading harness) and always fails. A `:test` failure on a `todo_…` test is **not a regression** — verify `compileJava`/`jar` `executed` (or run `./gradlew build -x test`) before treating the suite as a gate. `publishModrinth` does not run tests, so a red `:test` never blocks a release.

When verifying a build output, identify the newest artifact, not an alphabetical match. The version is the git branch, so several versioned jars coexist in `build/libs`; `find build/libs/*.jar | head -1` returns the first by name (often a stale version), so you can verify a jar you did not just build. Use `ls -t build/libs/*.jar | head -1` (newest by mtime) instead.

`publishModrinth` (and `publish`) declare no outputs, so they are not up-to-date-cached: the task runs — and POSTs — on every invocation. Do not run a publish task a second time to "check the exit code"; that creates a duplicate Modrinth entry. Verify the result via the API (`GET https://api.modrinth.com/v2/project/<id>/version`, singular), not another run.

Check that same API **before** publishing, not only after. The version is the git branch, so a branch may already be published from an earlier commit, and a new commit on the same branch re-POSTs the same version number — Modrinth keeps it as a duplicate (it already holds two `alpha.6-unstable` entries). If the branch's version already exists, bump the branch (`alpha.N` → `alpha.N+1`) rather than re-publishing; do not assume the branch is un-published because it is a new commit.

Two concrete traps hit when actually running a publish (the version was shipped this session by working around both):
- Run `./gradlew publishModrinth`, not `./gradlew publish`. `publish` (the maven task) has a circular dependency (`publish` → `publishModrinth` → `reobfJar` → `publish`) and fails immediately with "Circular dependency between the following tasks"; `publishModrinth` is the task that POSTs.
- `publishModrinth` **truncates a long changelog**. Its `curl -F "data=${data}"` drops the `data` field after ~270 chars, so a long changelog 400s with Modrinth `{"error":"invalid_input","description":"Error while parsing JSON: EOF while parsing a string at line 1 column N"}` (N near the changelog; `curl` exit 22). The JSON is valid locally; the failure is in transit, not the content — a `--trace-ascii` shows the `data` field cut off mid-string. Send the payload from a file (`curl -F "data=@<file>"`) instead of inline, or fix `build.gradle`'s `publishModrinth` to do so.

Running a gradle task that recompiles `build.gradle` (after editing it) fails on the machine's default Java 21 with `java.lang.IllegalArgumentException: Unsupported class file major version 65` — Gradle 7.2's Groovy can't parse Java 21 bytecode. Set `JAVA_HOME=/home/retro/.gradle/jdks/jdk-17.0.20+8` for that run. `compileJava`/`test` pass on Java 21; only a `build.gradle` recompile hits it.

## Adding a value to a switch-covered enum

Adding a value to an enum that has exhaustive `switch` expressions (e.g. the
chicken-arc `ChickenBeatState` and `BeatPhase`) ripples to every `switch` over it
AND to count/ordering assertions in tests. Before compiling, grep for all the
sites up front — don't discover them one compile-error at a time:

- switches: `grep -rln 'case WAITING_FOR_' src/main/java` (adjust the prefix to the
  enum's value names; an exhaustive `switch` over an enum is the usual break)
- count/ordering: `grep -rln 'values().length\|canonicalOrdering' src/test`

This session added a beat and hit 3 switches (2 in `main/`, 1 in a test) plus a
beat-count and a canonical-ordering assertion across three failed compiles
before finding them all up front.

## Hold-mode autotest (visual verification on a live server)

See `src/main/java/ca/bradj/questown/commands/test/COMPLEX.md` ("Launch discipline")
for the kill-order, env (`JAVA_HOME` JDK 17 + `LANG=C.UTF-8`), world wipe and port
check that precede every held `runServer -Dquestown.autotest.hold=true
-Dquestown.autotest.only=<scenario>` + `runClient -Pautojoin` cycle.

Checking for a held server: `pgrep -af 'gradlew runServer'` **returns nothing** for a
running held server — the held server is a `java` process (its `runServer` is in `-D`
args, not a `gradlew runServer` string), and so is the gradle wrapper. Use
`pgrep -af '[r]unServer'` instead (matches both the wrapper and the `java` server). A scenario
asserting on a non-townie thing (e.g. a dead door) should post its aim target via
the `questown.autotest.aim.door` system property or a needy townie steals the camera.

## Injecting a console command / TP into a headless runServer

To drive the forked server (e.g. `tp <player> <x> <y> <z>`) without RCON (broken in this
build) and without a reachable GLFW window (the dev box is headless), feed the `runServer`
task's stdin from a FIFO:

- Wire it in `build.gradle` via a `tasks.matching { it.name == 'runServer' }` block, **not**
  the `runs { server { ... } }` block. The `runs` block has no `standardInput` property
  (adding `standardInput = ...` there fails configuration with `> standardInput`). And
  `standardInput` wants an **`InputStream`, not a `File`** (passing `project.file(...)`
  fails with `Cannot cast ... File ... to java.io.InputStream`). The working form:
  `tasks.matching { it.name == 'runServer' }.configureEach { standardInput = new
  java.io.FileInputStream(project.file('run/server-cmd.fifo')) }`, gated by `-PperfFifo`.
- Hold a writer open on the FIFO before launch, or the JVM's open-for-read blocks:
  `mkfifo run/server-cmd.fifo; nohup sh -c 'sleep 999999 > run/server-cmd.fifo' &`. Then
  `echo 'tp Dev 240 64 -1' > run/server-cmd.fifo`.
- Keep the server's `forge.logging.console.level` at **`info`, not `debug`** for these
  runs: debug output backs up the console appender (the log freezes, ~bytes/min, while the
  server ticks fine — `jstack` shows `Server thread` in `waitUntilNextTick`), so
  `Can't keep up` and flag/room lines never flush. Revert the build.gradle edits after.
- If a FIFO write blocks (the `echo '...' > run/server-cmd.fifo` never returns) or a
  console command aborts, the server process may be **dead**, not slow. Check
  `pgrep -f forgeserveruserdev` before assuming the pipe is backed up — a blocked
  FIFO read is the dead-server signal, and the server's console went to a pipe, so
  the death is only visible in `run/crash-reports/`, not in the log you were reading.

Note: `TownTickRadius` in `questown-server.toml` is compared against **squared** distance
(`distSqr`) in `TownFlagTicker`, so its effective radius is `sqrt(value)` (default 10000 =
~100 blocks, not 10000). Widening it keeps the flag awake but does **not** move the player
onto the room — that is not equivalent to teleporting the player to the flag.

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles using default strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`), written as `status:` frontmatter on issue files. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context. `CONTEXT.md` not yet present at repo root; ADRs go in `docs/adr/` going forward (legacy `docs/decision-*.md` files are pre-existing and not moved). See `docs/agents/domain.md`.
