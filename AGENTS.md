Try to avoid asking the user to test something themselves. You have access to a tool called "autotest". When unsure whether to run a heavy check (an autotest, a dev client, a build), default to running it yourself rather than offering it as the user's choice or deferring it.

When the user reports a behaviour that does not work ("X sits idle after Y", "this does nothing"), that is a *verification* question, not a code investigation — the first move is an autotest, not reading code to speculate. "Understanding the code" is not "does it work"; a code reading that cannot settle a broken-behaviour report is a signal to set up the test, not to keep re-reading the same files.

## Running the dev client (`./gradlew runClient`)

**When the background client task reports exit code 1 / 143, or "Gradle build daemon disappeared unexpectedly", that is just the user closing the Minecraft window.** It is NOT a crash, a launch failure, or a regression. Do not investigate it, do not re-read the log looking for the cause, and do not relaunch — the user closed it on purpose. Only treat it as a real failure if the log shows an actual exception/crash BEFORE the window opened.

Launch the client with `run_in_background: true` and then **immediately hand control back to the user**. Never follow a launch with a foreground wait-loop (`for ... sleep ...` polling the log for "Sound engine started") — that holds the turn open and blocks the user from typing. If you need the boot/gameplay log later, just read the task's output file on a later turn.

When writing unit tests, DO NOT "simulate" core questown logic. If a situation comes up which causes testing to be impossible due to insufficient interfaces on the real code: 1) make a note of that, 2) Add a failing assertion to the test so it is possible to understand what tests are failing due to the code not being testable, 3) continue working on what is possible to test.

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

When verifying a build output, identify the newest artifact, not an alphabetical match. The version is the git branch, so several versioned jars coexist in `build/libs`; `find build/libs/*.jar | head -1` returns the first by name (often a stale version), so you can verify a jar you did not just build. Use `ls -t build/libs/*.jar | head -1` (newest by mtime) instead.

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

## Agent skills

### Issue tracker

Issues live as local markdown files under `.scratch/<feature>/` in this repo. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles using default strings (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`), written as `status:` frontmatter on issue files. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context. `CONTEXT.md` not yet present at repo root; ADRs go in `docs/adr/` going forward (legacy `docs/decision-*.md` files are pre-existing and not moved). See `docs/agents/domain.md`.
