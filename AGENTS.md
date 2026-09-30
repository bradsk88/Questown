Try to avoid asking the user to test something themselves. You have access to a tool called "autotest". When unsure whether to run a heavy check (an autotest, a dev client, a build), default to running it yourself rather than offering it as the user's choice or deferring it.

When the user reports a behaviour that does not work ("X sits idle after Y", "this does nothing"), that is a *verification* question, not a code investigation — the first move is an autotest, not reading code to speculate. "Understanding the code" is not "does it work"; a code reading that cannot settle a broken-behaviour report is a signal to set up the test, not to keep re-reading the same files.

A perf/lag report ("grinds to a halt", "can't keep up", "half-waiting for server") is the same kind of verification question. Before asserting which code costs the tick, run the perf autotest: `./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.only=perf/town_large` — its `TickProfile` reports the flag-tick average vs the 50ms budget and the per-phase µs (which phase is hot). A perf test that disproves a suspected cause *is* the finding, not a nuisance to work around; do not commit a perf fix before running it.

GUI pixel-oracle work (the 1.19.2↔1.20.6 screen banks, `capture.sh` stages, `rebank.sh`, the drift/sidecar checks) lives in `tools/porttest/` — read `tools/porttest/PORTTEST.md` before the first capture in a GUI-parity session. It carries the capture-state traps (gamemode match, fixture age, hover/cursor parking, the fresh-world rebank limit) that each cost a re-run when re-derived from scratch.

## Running the dev client (`./gradlew runClient`)

The dev is a secondary observer via `runClient`; the agent is the main driver. When a held `runServer` autotest or play-test needs a verification pass, run `runClient -Pautojoin` and hand control back — never ask, offer, or defer it, and do not decline because the box has no capturable window (grim captures 0 bytes; the client still renders). The one skip is when a unit test or the existing autotest suite already settles the question.

- A background client task that reports exit code 1 / 143, or "Gradle build daemon disappeared unexpectedly", is the user closing the Minecraft window, not a crash or regression. Only treat it as a real failure if the log shows an actual exception before the window opened.
- Do not restart the server while the client is up (it kills the client and wipes the world). Keep the client up while the dev is watching; if you must load a new command, tell the dev, then relaunch the client after the server is back.
- The full launch sequence (server first, then client, so the compile does not OOM-kill the server), the FIFO, the pgrep traps, and the 10-30s observation cadence are in `~/.pi/agent/references/questown-dev-launch.md` and `.pi/skills/questown-held-run/SKILL.md`. Read those before relaunching `runServer`/`runClient`.

When writing unit tests, DO NOT "simulate" core questown logic. If a situation comes up which causes testing to be impossible due to insufficient interfaces on the real code: 1) make a note of that, 2) Add a failing assertion to the test so it is possible to understand what tests are failing due to the code not being testable, 3) continue working on what is possible to test.

A class whose static initializer touches Forge registries (`Items`, `Blocks`, `ForgeRegistries`) cannot be loaded in a plain unit test — it throws `ExceptionInInitializerError` / `NoClassDefFoundError` at `<clinit>` (the class just won't load, not a failing assertion). To test its logic, extract the pure part to a Forge-free class and test that (e.g. `RecipeItemConfig`'s encode/decode → `ItemWeightCodec`).

A test that uses vanilla bootstrap (`ItemStack` NBT, `Bootstrap.bootStrap()`) must call `SharedConstants.tryDetectVersion()` **before** `Bootstrap.bootStrap()` in `@BeforeAll`; without it the boot dies with `ExceptionInInitializerError` at `Util.doFetchChoiceType` / `Registry.<clinit>`. Pattern: `PostDowntimeWarperNoOpGuardTest`.

Do not make design decisions without first consulting the local docs/ files. There is an index that can help with doing that efficiently.

Before asserting a domain fact as a constraint or a negative — "villagers can't eat X", "no gate exists for Y", "job Z works this way" — grep `CONTEXT.md` and `docs/adr/` for the term first. The two times a session asserted one of these from a single code read (raw food, the `canFit` start-gate), the correct model was already in `CONTEXT.md` or the selection path, and the dev had to correct it. A single-file or single-call-site read is not grounds for "X doesn't exist."

Do not build large functions unless there is a compelling reason to do so.

Do not choose nesting over early returns.

Do not use deprecated methods in new code. If there is no other option, prompt the user to confirm.

Before calling a raw Minecraft API for a common integration (text splitting, components/styling, tooltip text, sounds, item names, on-screen text), scan `mc/Compat`, `mc/Util`, and `gui/RenderUtil` first and reuse the existing wrapper (e.g. `Compat.splitText` not `font.split`; `Compat.translatableStyled` not `.withStyle`). Add one to `Compat` if none fits. See `docs/solutions/conventions/prefer-compat-util-renderutil-wrappers.md`.

**Version migrations go through the strangler seam.** `mc/Compat` exists specifically so forward/back-porting between MC versions is cheap: when a migration (e.g. 1.19.2 -> 1.20.1) changes a vanilla API that several call sites use, route it through a `Compat` wrapper (e.g. a vanished `GuiComponent.fill`/`blit` becomes `Compat.fill`/`Compat.blit`) rather than scattering the change. Mechanical-only edits (a moved import like joml `com.mojang.math.*` -> `org.joml.*`, or `Entity.level` field -> `level()`) are fine inline; the strangler applies to *functions that changed or disappeared* between versions, so the next port touches one place.

**1.20.x GUI pose: never mix the global modelview stack into GUI code.** In 1.20.6 `GuiGraphics.pose()` is its own `PoseStack` (not the joml `RenderSystem.getModelViewStack()`), and the `fill`/`drawString` pipeline (RenderType.gui) applies the global modelview uniform on top of the gui pose. 1.19.2-era patterns — `RenderSystem.getModelViewStack().pushMatrix()` + `applyModelViewMatrix()` around `drawContents`, plus the `depthFunc`/`colorMask` z±950 hacks in advancement-style trees — clip or misplace every fill/drawString (sprite `blit` keeps drawing, which makes it look like "content vanished but the window frame is there"). Port such screens with `guiGraphics.pose().pushPose()` / `translate()` / `popPose()` only, and delete the depth/colorMask juggling (vanilla 1.20.6 `AdvancementTab.drawContents` is the oracle: gui-pose only, zero RenderSystem calls).

**Ported-GUI parity: diff the golden call site before deriving any API formula.** When a 1.20.6 GUI element renders wrong against 1.19.2, first `diff -u` the wip file against the golden repo (`/workspace/IdeaProjects/Questown-119`, same relative path) and read the golden's **exact argument values** — not just call shape. 1.20.6 `GuiGraphics.blit` overloads changed semantics from 1.19.2 `GuiComponent.blit`, and the port silently rewrote call arguments in passing (verified 2026-09-30: the golden's stats/skills tab-icon blits carry one extra arg the wip's `Util.blit` call drops — `0, 0, 0, 9, 9, 256, 256` vs wip `0, 9, 9, 9, 256, 256`; `0, 17, 7, 30, 30, 256, 256` vs wip `0, 17, 7, 30, 256, 256`). Do not derive the 1.20.6 blit UV math from `javap` — the overloads delegate three layers deep and the arithmetic is split across private variants, so each dump yields a different "formula." If the semantics is genuinely in question, settle it with a capture probe: blit a known-good region, `shot`, check the pixels. To pin a golden call's semantics, `javap` the 1.19.2 mapped jar in Questown-119's `build/fg_cache/net/minecraftforge/forge/1.19.2-43.3.0_mapped_official_1.19.2/`; this repo's 1.20.6 classes are under `build/neoForm/neoFormJoined1.20.6*/steps/recompile/classes`. This repo's `build/fg_cache` can hold stale 1.20.1 jars, so a version-less `find` match is not proof it is the 1.20.6 code path.

## Build environment (this machine)

- **Gradle 7.2 here runs on JDK 17, not the system JDK 21.** The system default `java` is 21, which makes Gradle 7.2 fail to compile `build.gradle` with `Unsupported class file major version 65`. `gradle.properties` pins `org.gradle.java.home=/home/retro/.gradle/jdks/jdk-17.0.20+8`; ensure a new repo/branch has that line (RoomRecipes does, Questown may not).
- **`unzip` is not installed.** To locate/verify a class inside a jar use `jar tf <jar> | grep ...`, not `unzip -l`. `javap -classpath <jar> FQN` works for classes that exist but returns nothing for inner classes (`X$Y`) or for classes that were *removed* in the target version — so a `javap` "class not found" can mean the tool quirk OR a genuinely-gone class. Confirm a class's location with `jar tf` before concluding it moved; the ForgeGradle mapped jar lives at `build/fg_cache/net/minecraftforge/forge/<ver>_mapped_official_<mc>/<ver>_<...>_mapped_official_<mc>.jar`.
- **A version migration is a compile loop, not a memory exercise.** Change `build.gradle` (forge/jei/patchouli/roomrecipes + `mappings`), then `./gradlew build` and let the compiler name each change; fix per category and re-run. Do not pre-decide a 1.20 API change from memory. The `RoomRecipes` mod (only Forge dep) migrates first and is `publishToMavenLocal`-ed to `~/.m2` (Questown resolves it via `mavenLocal()`); Questown cannot compile until that jar exists.
- **Dependency availability is a hard gate.** Before choosing an MC target, probe the reachable maven (maven.minecraftforge.net, maven.blamejared.com, dvs1.progwml6.com — modrinth/terraform/modmaven are NOT reachable). For 1.20.x: Forge + JEI resolve for base 1.20 but **Patchouli only has 1.20.1+** (no base-1.20 Patchouli anywhere reachable), so **1.20.1 is the only buildable 1.20.x target**, not 1.20.

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

## A dev command is a thin wrapper; domain logic lives on the owner

When a held/agent run needs an action a player would do with a mouse (place a
flag-linked block, place a welcome mat, open a menu), the `qt`/`_qtdev`
command should be a **thin wrapper** that delegates the action to the entity
that owns it, not re-implement the action in the command. E.g. placing a
welcome mat: the command calls
`TownFlagBlockEntity.placeWelcomeMat(pos)` (which sets the block and registers
it), not re-implement the item-use path + NBT in the command. The command
re-implementing domain logic is the failure mode: it duplicates a path that the
owner already has, drifts from it, and misses the owner's registration side
effects. If the owner has no method for the action, add the method to the owner
first, then wrap it.

A command that takes a `pos` should take the `pos` as a literal argument
(`qt place_mat <x> <y> <z>`), not derive it (nearest flag, manhattan
distance to the player, the player's look vector). Deriving a position is the
kind of "fancy" logic that should be on the owner, and it makes the command
harder to test and reason about.

## Ask the user for an observed fact instead of re-deriving it

When you are stuck on why something produced the wrong result (a command
gave the wrong item, a log line is wrong, a value is AIR), stop re-deriving the
cause from code and **ask the user for the observation they have that you do
not**. The user is a live observer; you are not. In this session the craft
command's log said "Created Air" but the user noticed the welcome mat was in
their hand — the craft *worked*, the log was cosmetic-wrong, and the real
problem (the mat needed to be *placed*, not just given) was only visible from
the user's observation. Re-deriving a registry issue for several turns when the
user could tell you "it's in my hand" in one line is the failure mode. When a
log line contradicts the expected result, check the *effect* (what is in the
world / inventory) before trusting the log line, and ask the user rather than
keep re-reading code.

## Build verification

Run gradle compile/test tasks with `LANG=C.UTF-8` (the hold-mode env, not just hold mode): a POSIX locale fails javac with "unmappable character for encoding US-ASCII" on sources with unicode. A cached `up-to-date` task can mask this — it only bites when a task actually recompiles.

A Gradle `BUILD SUCCESSFUL` with the task `up-to-date` did not recompile your edits — it only re-ran cached outputs, so it proves nothing about the code you just changed. After changing code, check the task says `executed` (or pass `--rerun-tasks`) before trusting a build as a pass. Applies to `compileJava`/`test`/`build`.

`./gradlew build` is **red by default**, independent of your changes: `ChickenArcHintsTest.todo_…` is a deliberate `Assertions.fail` marker (chicken-arc mod-icon beats that need a Forge mod-loading harness) and always fails. A `:test` failure on a `todo_…` test is **not a regression** — verify `compileJava`/`jar` `executed` (or run `./gradlew build -x test`) before treating the suite as a gate. `publishModrinth` does not run tests, so a red `:test` never blocks a release.

When verifying a build output, identify the newest artifact, not an alphabetical match. The version is the git branch, so several versioned jars coexist in `build/libs`; `find build/libs/*.jar | head -1` returns the first by name (often a stale version), so you can verify a jar you did not just build. Use `ls -t build/libs/*.jar | head -1` (newest by mtime) instead.

`publishModrinth` (and `publish`) declare no outputs, so they are not up-to-date-cached: the task runs — and POSTs — on every invocation. Do not run a publish task a second time to "check the exit code"; that creates a duplicate Modrinth entry. Verify the result via the API (`GET https://api.modrinth.com/v2/project/<id>/version`, singular), not another run.

Check that same API **before** publishing, not only after. The version is the git branch, so a branch may already be published from an earlier commit, and a new commit on the same branch re-POSTs the same version number — Modrinth keeps it as a duplicate (it already holds two `alpha.6-unstable` entries). If the branch's version already exists, bump the branch (`alpha.N` → `alpha.N+1`) rather than re-publishing; do not assume the branch is un-published because it is a new commit.

Two concrete traps hit when actually running a publish (the version was shipped this session by working around both):
- Run `./gradlew publishModrinth`, not `./gradlew publish`. `publish` (the maven task) has a circular dependency (`publish` → `publishModrinth` → `reobfJar` → `publish`) and fails immediately with "Circular dependency between the following tasks"; `publishModrinth` is the task that POSTs.
- `publishModrinth` **truncates a long changelog**. Its `curl -F "data=${data}"` drops the `data` field after ~270 chars, so a long changelog 400s with Modrinth `{"error":"invalid_input","description":"Error while parsing JSON: EOF while parsing a string at line 1 column N"}` (N near the changelog; `curl` exit 22). The JSON is valid locally; the failure is in transit, not the content — a `--trace-ascii` shows the `data` field cut off mid-string. Send the payload from a file (`curl -F "data=@<file>"`) instead of inline, or fix `build.gradle`'s `publishModrinth` to do so.

Running a gradle task that recompiles `build.gradle` (after editing it) fails on the machine's default Java 21 with `java.lang.IllegalArgumentException: Unsupported class file major version 65` — Gradle 7.2's Groovy can't parse Java 21 bytecode. Set `JAVA_HOME=/home/retro/.gradle/jdks/jdk-17.0.20+8` for that run. `compileJava`/`test` pass on Java 21; only a `build.gradle` recompile hits it.

## Looking up an API / method name (dot completion)

When you need to call a method or read a field on a type you are not sure of, do not guess the name and do not read the Java source to find it. Use `jmembers` (`~/.local/bin/jmembers`), the dot-completion equivalent for agents: it lists the type's real members from the compiled symbols via `javap`, the same list an IDE completion menu shows.

```
jmembers <Type> [anchor]            # FQ or simple name; optional substring filter
jmembers --all <Type> [anchor]      # include protected/private members
```

It resolves the classpath itself (own `build/classes`, the branch's mapped Forge/MC jar from `build.gradle`, and dep jars in the Gradle cache), so it hands back the deobfuscated names you actually use (`useOn`, not the SRG `m_41661_`). If a simple name is ambiguous (`Item`), it lists the candidates; re-run with the fully-qualified name. Run from the project root or set `JMEMBERS_PROJECT`. `jmembers` gives names and signatures, not behavior: for what a method does, still read the source or a doc. Full detail: `references/jmembers-dot-completion.md`.

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
-Dquestown.autotest.only=<scenario>` + `runClient -Pautojoin` cycle. For the full
held-run sequence (server-first launch, 30s observation cadence, chat/observe
channels, the console command set) load the `questown-held-run` skill.

Checking for a held server: `pgrep -af 'gradlew runServer'` **returns nothing** for a
running held server — the held server is a `java` process (its `runServer` is in `-D`
args, not a `gradlew runServer` string), and so is the gradle wrapper. Use
`pgrep -af '[r]unServer'` instead (matches both the wrapper and the `java` server). A scenario
asserting on a non-townie thing (e.g. a dead door) should post its aim target via
the `questown.autotest.aim.door` system property or a needy townie steals the camera.

## Injecting a console command / TP into a headless runServer

RCON is broken in this build, and the dev box has no reachable GLFW window, so feed the `runServer` task's stdin from a FIFO. The working setup (the `tasks.matching { it.name == 'runServer' }` block with `standardInput = new java.io.FileInputStream(project.file('run/server-cmd.fifo'))`, the `mkfifo` + `nohup sh -c 'sleep 999999 > run/server-cmd.fifo' &` writer, the `-PperfFifo`/`questown.tp.fifo` gates, the `info`-not-`debug` console level, and the dead-server-when-a-write-blocks trap) is in `~/.pi/agent/references/questown-dev-launch.md`. These are throwaway perf/TP plumbing, not features — re-apply them for a headless perf/TP run and revert before committing.

Note: `TownTickRadius` in `questown-server.toml` is compared against **squared** distance (`distSqr`) in `TownFlagTicker`, so its effective radius is `sqrt(value)` (default 10000 = ~100 blocks, not 10000). Widening it keeps the flag awake but does **not** move the player onto the room — that is not equivalent to teleporting the player to the flag.

## Agent skills

Project skills live under `.pi/skills/` and are **tracked in git** — commit a new skill with the work that created it. An untracked skill exists only in your working tree and vanishes on a clean checkout.


### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles using default strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`), written as `status:` frontmatter on issue files. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context. `CONTEXT.md` lives at the repo root (the domain vocabulary: jobs, food, economy, rooms). ADRs go in `docs/adr/` going forward (legacy `docs/decision-*.md` files are pre-existing and not moved). See `docs/agents/domain.md`.

### Plans

Implementation plans go in `docs/plans/` (phases, a vocabulary→code map, a test ledger, and an "open decisions" list). When a plan or doc has a "verified"/status column, **verified** means confirmed in code (grep the class or the declaration); a claim seen only in a `COMPLEX.md` or an ADR is "referenced in a doc," not "verified" — re-confirm it in code before it earns the label.
