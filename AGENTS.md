Try to avoid asking the user to test something themselves. You have access to a tool called "autotest". When unsure whether to run a heavy check (an autotest, a dev client, a build), default to running it yourself rather than offering it as the user's choice or deferring it.

When the user reports a behaviour that does not work ("X sits idle after Y", "this does nothing"), that is a *verification* question, not a code investigation — the first move is an autotest, not reading code to speculate. "Understanding the code" is not "does it work"; a code reading that cannot settle a broken-behaviour report is a signal to set up the test, not to keep re-reading the same files.

A perf/lag report ("grinds to a halt", "can't keep up", "half-waiting for server") is the same kind of verification question. Before asserting which code costs the tick, run the perf autotest: `./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.only=perf/town_large` — its `TickProfile` reports the flag-tick average vs the 50ms budget and the per-phase µs (which phase is hot). A perf test that disproves a suspected cause *is* the finding, not a nuisance to work around; do not commit a perf fix before running it.

GUI pixel-oracle work (the 1.19.2↔1.20.6 screen banks, `capture.sh` stages, `rebank.sh`, the drift/sidecar checks) lives in `tools/porttest/` — read `tools/porttest/PORTTEST.md` before the first capture in a GUI-parity session. It carries the capture-state traps (gamemode match, fixture age, hover/cursor parking, the fresh-world rebank limit) that each cost a re-run when re-derived from scratch.

## Running the dev client (`./gradlew runClient`)

- **Before any gradle invocation** (build, compile, version-swap, decompile), check for a live dev session: `pgrep -af '[r]unServer|[r]unClient'`. A build racing a live server/client contests the same project dir and shared caches, and a dead-client report will look like a build regression. If the user's session is up, ask to kill it or wait — never run the build and discover the session afterwards (this bit the 1.19.2 strangler probe: it ran an FG build beside the user's client and had to interrupt the probe for the kill).

The dev is a secondary observer via `runClient`; the agent is the main driver. When a held `runServer` autotest or play-test needs a verification pass, run `runClient -Pautojoin` and hand control back — never ask, offer, or defer it. This box is headless (external screen tools `grim`/`xdotool` fail), so it is **not** "no capturable window": the game's own window **is** capturable from inside — the `tools/porttest` oracle uses the client hook (`loader/neo/QtDiagClient.shot` → vanilla `Screenshot.grab`), which works headless, proven by the real goldens in `tools/porttest/goldens/1.19.2/` (all 6 canonical screens are real PNGs, captured this box 2026-09-30; the 1.20.6 target captures are the sibling `tools/porttest/live/1.20.6/`). GUI parity is the oracle (`run.sh diff`, vision via the Nova vllm proxy at `localhost:42827`); behavior state is the server log (server-side, not pixels); a user can still drive a reproduction from a live client. The one skip is when a unit test or the existing autotest suite already settles the question.

- A background client task that reports exit code 1 / 143, or "Gradle build daemon disappeared unexpectedly", is the user closing the Minecraft window, not a crash or regression. Only treat it as a real failure if the log shows an actual exception before the window opened.
- Do not restart the server while the client is up (it kills the client and wipes the world). Keep the client up while the dev is watching; if you must load a new command, tell the dev, then relaunch the client after the server is back.
- The full launch sequence (server first, then client, so the compile does not OOM-kill the server), the FIFO, the pgrep traps, and the 10-30s observation cadence are in `references/questown-dev-launch.md` and `.pi/skills/questown-held-run/SKILL.md`. Read those before relaunching `runServer`/`runClient`.

When writing unit tests, DO NOT "simulate" core questown logic. If a situation comes up which causes testing to be impossible due to insufficient interfaces on the real code: 1) make a note of that, 2) Add a failing assertion to the test so it is possible to understand what tests are failing due to the code not being testable, 3) continue working on what is possible to test.

A class whose static initializer touches Forge registries (`Items`, `Blocks`, `ForgeRegistries`) cannot be loaded in a plain unit test — it throws `ExceptionInInitializerError` / `NoClassDefFoundError` at `<clinit>` (the class just won't load, not a failing assertion). To test its logic, extract the pure part to a Forge-free class and test that (e.g. `RecipeItemConfig`'s encode/decode → `ItemWeightCodec`).

A test that uses vanilla bootstrap (`ItemStack` NBT, `Bootstrap.bootStrap()`) must call `SharedConstants.tryDetectVersion()` **before** `Bootstrap.bootStrap()` in `@BeforeAll`; without it the boot dies with `ExceptionInInitializerError` at `Util.doFetchChoiceType` / `Registry.<clinit>`. Pattern: `PostDowntimeWarperNoOpGuardTest`.

Do not make design decisions without first consulting the local docs/ files. There is an index that can help with doing that efficiently.

Before asserting a domain fact as a constraint or a negative — "villagers can't eat X", "no gate exists for Y", "job Z works this way" — grep `CONTEXT.md` and `docs/adr/` for the term first. The two times a session asserted one of these from a single code read (raw food, the `canFit` start-gate), the correct model was already in `CONTEXT.md` or the selection path, and the dev had to correct it. A single-file or single-call-site read is not grounds for "X doesn't exist." The same standard applies to a *cause*: a diagnosis ("oak_wood appeared because the gate room is missing") is a hypothesis until you have checked the disconfirming lines of the same log — the `All rooms registered …` line, the `[WHY]`/`Lacking` dump. (On 2026-10-09, the "no gate room" reading was overturned by the dev, and that very line right there had already refuted it.)

The same rule for **job mechanics and item producers/consumers** (three false assertions were user-caught on this in one session: "hunts need live mobs", "swords have no producer", "top need = swords shortage"): the data layer is the oracle, and the job *name* is not the schema — `hunter/sword` is a hunter that *carries* a sword, not one that makes them. First move: `python3 scripts/job-producers.py <item>` (OUTPUT = `initial_request`+`result`, biome_loot = the union of its tables; INPUT = `work_states`; tools are held, not produced), then read that job's JSON in `data/questown/questown_jobs/` (in this repo: `src/main/resources/data/questown/questown_jobs/` — the `data/` root is not at the repo top) and the Leaver section of `CONTEXT.md`. Skill: `.pi/skills/questown-item-producers`.

Do not build large functions unless there is a compelling reason to do so.

Do not choose nesting over early returns.

Do not use deprecated methods in new code. If there is no other option, prompt the user to confirm.

Before calling a raw Minecraft API for a common integration (text splitting, components/styling, tooltip text, sounds, item names, on-screen text), scan `mc/Compat`, `mc/Util`, and `gui/RenderUtil` first and reuse the existing wrapper (e.g. `Compat.splitText` not `font.split`; `Compat.translatableStyled` not `.withStyle`). Add one to `Compat` if none fits. See `docs/solutions/conventions/prefer-compat-util-renderutil-wrappers.md`.

**Version-specific code goes through the strangler seam.** The shrinking work (de-grandfathering `tools/strangler-grandfather.txt` to zero) is **shelved** — treat the list as frozen, don't start shrinking unless the user revives it. The seam itself is a standing invariant, not a one-off tactic. The gate is `tools/strangler-check` (gradle: `check.dependsOn stranglerCheck`): it fails the build when a file outside the seam family (`mc/`, `gui/RenderUtil`, `gui/RenderContext`, `loader/`, `core/network/`) carries a 1.20+-only API, so route new version-specific calls through a `Compat` wrapper rather than scattering the change. New leaks are grandfathered in `tools/strangler-grandfather.txt` (origin: `docs/plans/strangler-1.19.2-probe-report.md`) and that list must not grow; **before removing a path, run `sh tools/strangler-check`** - the gate, not a subset grep, is the source of truth for "de-grandfatherable". Keep 99% of files version-free when porting; mechanical-only edits (a moved import like joml `com.mojang.math.*` -> `org.joml.*`, `Entity.level` -> `level()`) are fine inline. Full port loop: the `questown-mc-version-migration` skill.
**1.20.x GUI pose: never mix the global modelview stack into GUI code.** In 1.20.6 `GuiGraphics.pose()` is its own `PoseStack` (not the joml `RenderSystem.getModelViewStack()`), and the `fill`/`drawString` pipeline applies the global modelview uniform on top of the gui pose. 1.19.2-era `RenderSystem.getModelViewStack().pushMatrix()` + `applyModelViewMatrix()` patterns (plus the `depthFunc`/`colorMask` z-hacks in advancement-style trees) clip or misplace fills and drawStrings. Port such screens with `guiGraphics.pose().pushPose()` / `translate()` / `popPose()` only, and delete the depth/colorMask juggling. Oracle: vanilla 1.20.6 `AdvancementTab.drawContents` (gui-pose only, zero RenderSystem calls).

**Ported-GUI parity: diff the golden call site before deriving any API formula.** When a 1.20.6 GUI element renders wrong against 1.19.2, first `diff -u` the wip file against the same relative path in the golden repo (`/workspace/IdeaProjects/Questown-119`) and read the golden's **exact argument values** - the port has silently rewritten blit arguments in passing. Blit anything not 256x256 with its true size: `Util.blit(gui, tex, x, y, u, v, w, h, widthOfFile, heightOfFile)`, since 1.20.6 scales UV by declared maxU/maxV and the 256-sheet default stretches the art. **The two `Util.blit` overloads are an active trap: the 8-arg form assumes a 256×256 sheet and the 10-arg form takes the art's own size (32×32 status art passes `32, 32`) - use the 8-arg form on 32×32 art and it samples 4 px stretched into the box ("flat grey box" status-icon bug; the 10-arg javadoc in `mc/Util.java` carries the full warning).** Do not read the 1.20.6 blit UV formula out of the public overloads - the normalisation lives in the innermost package-private `blit`, and is only relevant when a *normalisation question* (not an arithmetic rewrite) is in play. Settle genuinely unclear semantics with a capture probe (blit a known-good region, `shot`, check the pixels). Pin golden semantics with `javap` on Questown-119's 1.19.2 mapped jar (`build/fg_cache/net/minecraftforge/forge/1.19.2-43.3.0_mapped_official_1.19.2/`); this repo's 1.20.6 classes live under `build/neoForm/neoFormJoined1.20.6*/steps/recompile/classes` (this repo's `build/fg_cache` can hold stale 1.20.1 jars, so a version-less `find` match proves nothing). Concrete incident examples (the jobs-pane grey icon, `stone.png`, the stat-bar end-cap): `gui/COMPLEX.md` "1.20.6 blit UV" and "VillagerStatsScreen stat bars" sections.

## Build environment (this machine)

- **Gradle 7.2 here runs on JDK 17, not the system JDK 21.** The system default `java` is 21, which makes Gradle 7.2 fail to compile `build.gradle` with `Unsupported class file major version 65`. `gradle.properties` pins `org.gradle.java.home=/home/retro/.gradle/jdks/jdk-17.0.20+8`; ensure a new repo/branch has that line (RoomRecipes does, Questown may not). **Track-specific:** the 1.20.6/neoforge branch instead pins Gradle 8.14 + `org.gradle.java.home=/usr/lib/jvm/java-21-openjdk-amd64`; the JDK-17 rule applies to the Forge 1.19.2/1.20.1 branch only.
- **`unzip` is not installed.** To locate/verify a class inside a jar use `jar tf <jar> | grep ...`, not `unzip -l`. `javap -classpath <jar> FQN` works for classes that exist but returns nothing for inner classes (`X$Y`) or for classes that were *removed* in the target version — so a `javap` "class not found" can mean the tool quirk OR a genuinely-gone class. Confirm a class's location with `jar tf` before concluding it moved; the ForgeGradle mapped jar lives at `build/fg_cache/net/minecraftforge/forge/<ver>_mapped_official_<mc>/<ver>_<...>_mapped_official_<mc>.jar`.
- **`git log --all` can die on a dangling pi-checkpoint ref:** `fatal: bad object refs/pi-checkpoints/turn-…` (reproduced 2026-10-09 on this box). The scoped form works — use `git log -- <pathspec>` (or `git log HEAD`) for commit archaeology instead of `--all`.
- **A version migration is a compile loop, not a memory exercise.** Change `build.gradle` (forge/jei/patchouli/roomrecipes + `mappings`), then `./gradlew build` and let the compiler name each change; fix per category and re-run. Do not pre-decide a 1.20 API change from memory. The `RoomRecipes` mod (only Forge dep) migrates first and is `publishToMavenLocal`-ed to `~/.m2` (Questown resolves it via `mavenLocal()`); Questown cannot compile until that jar exists.
- **Dependency availability is a hard gate.** Before choosing an MC target, probe the reachable maven (maven.minecraftforge.net, maven.blamejared.com, dvs1.progwml6.com — modrinth/terraform/modmaven are NOT reachable). **Old Forge track (historical):** Forge + JEI resolved for base 1.20 but **Patchouli only had 1.20.1+**, which made **1.20.1 the only buildable 1.20.x Forge target** — that conclusion does not describe the current branch.
- **Current track (1.20.6-neoforge-wip, verified 2026-09-30):** **MC 1.20.6 on NeoForge 20.6.141**; maven.neoforged.net and JEI 18.0.0.66 (1.20.6-neoforge) resolve; RoomRecipes `1.20.6-neoforge` is published to `~/.m2`; Patchouli is intentionally NOT on this classpath (the book API was replaced locally). The "1.20.1 only" gate above is irrelevant here.

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
not**. The user is a live observer; you are not. When a
log line contradicts the expected result, check the *effect* (what is in the
world / inventory) before trusting the log line, and ask the user rather than
keep re-reading code.

## Build verification

- Run gradle compile/test with `LANG=C.UTF-8` (the hold-mode env): a POSIX locale fails javac with "unmappable character" on unicode sources.
- On the 1.20.6 branch `check` runs `importCheck` (an unused-import gate) and it can fail at HEAD independent of your changes. It is a **fixable red, not a regression**: apply the exact command in its error (`/home/retro/.local/bin/import-clean apply src/main/java src/test/java` — AST-based, only drops imports whose name is unused), then re-run. (Caught 12 at HEAD on 2026-10-09, right before the migration close-out would have claimed "build green".)
- Trust a green build only when the task says `executed`, not `up-to-date`: a cached task re-ran old outputs and proves nothing about the code you just changed. Pass `--rerun-tasks` to force.
- For a filtered `:test --tests <Class>` run, confirm the named suite actually ran before claiming "tests green": `build/test-results/test/TEST-<Class>.xml` must exist with `tests=\"N\"` matching the test methods and `failures=\"0\"` (the 2026-10-02 work-board fixes read this XML twice; `BUILD SUCCESSFUL` + `executed` alone does not name the suite).
- **An empty result — or a result that comes back with no output — on a verification command is NOT a pass.** The command fired and the harness dropped the output; do not read that as success or failure. Re-run the check as its own single-purpose call and read the suite's `TEST-<Class>.xml` independently of the gradle stdout. (Bitten 2026-10-09: a green `:test` call returned no result; the standalone re-run + XML read showed 13/13 — the drop, not a failure, was the cause. Claiming green from the first call would have been a false "done".)
- `./gradlew build` is **red by default**, independent of your changes: `ChickenArcHintsTest.todo_…` and `TestScenarioNamesTodoTest.todo_…` are deliberate `Assertions.fail` markers (chicken-arc beats / the scenario-name surface need a Forge mod-loading harness) and always fail. A `:test` failure on a `todo_…` test is **not a regression**; gate on `./gradlew build -x test` with `compileJava`/`jar` executed. A red `:test` never blocks a release - `publishModrinth` does not run tests.
- Identify the newest artifact by mtime, not name: `ls -t build/libs/*.jar | head -1`. The version is the git branch, so several versioned jars coexist in `build/libs` and an alphabetical match can be a stale jar.
- A gradle run that recompiles `build.gradle` (after editing it) fails on the machine's default Java 21 with `Unsupported class file major version 65`; set `JAVA_HOME=/home/retro/.gradle/jdks/jdk-17.0.20+8` for that run. `compileJava`/`test` pass on Java 21.
- Modrinth publishing: use the `modrinth-publish` skill. It owns the safe sequence (check the API for the branch version **before** publishing, bump the branch if the version is already out, publish once and verify via the API - never re-run the task to "check the exit code") and the traps that actually bit (`publish` circular dependency, changelog truncation past ~270 chars surfacing as an in-transit 400).

## Looking up an API / method name (dot completion)

When you need to call a method or read a field on a type you are not sure of, do not guess the name and do not read the Java source to find it. Use `jmembers` (`~/.local/bin/jmembers`), the dot-completion equivalent for agents: it lists the type's real members from the compiled symbols via `javap`, the same list an IDE completion menu shows.

```
jmembers <Type> [anchor]            # FQ or simple name; optional substring filter
jmembers --all <Type> [anchor]      # include protected/private members
```

It resolves the classpath itself (own `build/classes`, the branch's mapped Forge/MC jar from `build.gradle`, and dep jars in the Gradle cache), so it hands back the deobfuscated names you actually use (`useOn`, not the SRG `m_41661_`). If a simple name is ambiguous (`Item`), it lists the candidates; re-run with the fully-qualified name. Run from the project root or set `JMEMBERS_PROJECT`. `jmembers` gives names and signatures, not behavior: for what a method does, still read the source or a doc.

**Neo branch caveat (1.20.6-neoforge-wip):** `net.neoforged.*` types fail with `jmembers: could not resolve '…' on the classpath` (the Neo universal jar is not on its search). Fallback, verified 2026-10-09: `J=$(find ~/.gradle/caches -name 'neoforge-*-universal.jar' | head -1)` then `javap -classpath "$J" <FQ>` (the `-universal` jar; `-sources` has no bytecode — `javap` returns nothing from it).

## Adding a value to a switch-covered enum

Adding a value to an enum that has exhaustive `switch` expressions (e.g. the
chicken-arc `ChickenBeatState` and `BeatPhase`) ripples to every `switch` over it
AND to count/ordering assertions in tests. Before compiling, grep for all the
sites up front — don't discover them one compile-error at a time:

- switches: `grep -rln 'case WAITING_FOR_' src/main/java` (adjust the prefix to the
  enum's value names; an exhaustive `switch` over an enum is the usual break)
- count/ordering: `grep -rln 'values().length\|canonicalOrdering' src/test`

## Hold-mode autotest (visual verification on a live server)

See `src/main/java/ca/bradj/questown/commands/test/COMPLEX.md` ("Launch discipline")
for the kill-order, env (`JAVA_HOME` JDK 17 + `LANG=C.UTF-8`), world wipe and port
check that precede every held `runServer -Dquestown.autotest.hold=true
-Dquestown.autotest.only=<scenario>` + `runClient -Pautojoin` cycle. For the full
held-run sequence (server-first launch, 30s observation cadence, chat/observe
channels, the console command set) load the `questown-held-run` skill.

**A realtime held run is NOT capped at 20 TPS on the 1.20.6 branch** — feed
`/tick rate 200` through the FIFO console to run a tick-budgeted watch ~10× faster
(vanilla `/tick` command, since 1.20.3; `Questown-119`/1.19.2 has no lever, TPS
fixed there). It is not `turbo_mode` (a debug-log toggle) and not `warpTime`
(skip-the-ticks end-state). **Never hand-write a bare FIFO feed — use
`run/ffeed.sh` (bounded + confirmed) and `run/pace-guard.sh` (in-game day pace
is the delivery truth; an unapplied rate is silent)**. See the
`questown-held-run` skill + `commands/test/AUTOTESTS.md` "TPS" +
`references/questown-dev-launch.md` (root cause + the 7532 s case).

Checking for a held server: `pgrep -af 'gradlew runServer'` **returns nothing** for a
running held server — the held server is a `java` process (its `runServer` is in `-D`
args, not a `gradlew runServer` string), and so is the gradle wrapper. Use
`pgrep -af '[r]unServer'` instead (matches both the wrapper and the `java` server). A scenario
asserting on a non-townie thing (e.g. a dead door) should post its aim target via
the `questown.autotest.aim.door` system property or a needy townie steals the camera.

## Injecting a console command / TP into a headless runServer

RCON is broken in this build, and the dev box has no reachable GLFW window, so feed the `runServer` task's stdin from a FIFO. The working setup (the `tasks.matching { it.name == 'runServer' }` block with `standardInput = new java.io.FileInputStream(project.file('run/server-cmd.fifo'))`, the `mkfifo` + `nohup sh -c 'sleep 999999 > run/server-cmd.fifo' &` writer, the `-PperfFifo`/`questown.tp.fifo` gates, the `info`-not-`debug` console level, and the dead-server-when-a-write-blocks trap) is in `references/questown-dev-launch.md`. These are throwaway perf/TP plumbing, not features — re-apply them for a headless perf/TP run and revert before committing.

Note: `TownTickRadius` in `questown-server.toml` is compared against **squared** distance (`distSqr`) in `TownFlagTicker`, so its effective radius is `sqrt(value)` (default 10000 = ~100 blocks, not 10000). Widening it keeps the flag awake but does **not** move the player onto the room — that is not equivalent to teleporting the player to the flag.

## Agent skills

Project skills live under `.pi/skills/` and are **tracked in git** — commit a new skill with the work that created it. An untracked skill exists only in your working tree and vanishes on a clean checkout.

- `questown-strangler` — the strangler-shrink loop (gate as source of truth, cluster order, per-leaf G1 pipeline, the session-learned traps). **Shelved** — load only if the user resumes the shrink work.


### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles using default strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`), written as `status:` frontmatter on issue files. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context. `CONTEXT.md` lives at the repo root (the domain vocabulary: jobs, food, economy, rooms). ADRs go in `docs/adr/` going forward (legacy `docs/decision-*.md` files are pre-existing and not moved). See `docs/agents/domain.md`.

### Plans

Implementation plans go in `docs/plans/` (phases, a vocabulary→code map, a test ledger, and an "open decisions" list). When a plan or doc has a "verified"/status column, **verified** means confirmed in code (grep the class or the declaration); a claim seen only in a `COMPLEX.md` or an ADR is "referenced in a doc," not "verified" — re-confirm it in code before it earns the label.
