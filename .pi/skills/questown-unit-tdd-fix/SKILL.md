---
name: questown-unit-tdd-fix
description: Fixes a Questown bug by TDD at the unit level — extract the
  Forge-free seam, red test, green, close out. TRIGGER when asked to TDD-fix a
  unit-level Questown bug (an NPE, a wire-format flaw, a bad guard, a dead
  path), e.g. "TDD fixes for X". SKIP when a held autotest or behaviour drive
  is the real verification (load `questown-behavior-verify` /
  `questown-held-run` instead), for MC version migrations
  (`questown-mc-version-migration`), or for refactors with no bug to red-test.
  Codifies the loop the work-board fixes (2026-10-02) re-derived from AGENTS
  fragments: seam choice, the registry-bound-untestable doctrine, the
  build/verify facts, and the same-commit close-out through prep-commit.
---

# Questown unit-TDD fix loop

## 0. Pre-flight (once)

- `pgrep -af '[r]unServer|[r]unClient'` — a live dev session contests the build; ask to kill it first.
- Read the `.scratch` issue in full; grep `CONTEXT.md`/`docs/adr/` for the subject before asserting a domain negative.
- `jmembers <Type> [anchor]` for ANY API shape you have not compile-checked (FriendlyByteBuf members bit the 2026-10-02 session: no `readBooleanList`, no `Boolean::writeBoolean` encoder — it is `writeCollection(col, (buf, v) -> ...)` or a varint+loop).

## 1. Pick the testable seam (the fix is its wiring)

Three proven shapes, in decreasing preference:

1. **Pure guard on a Forge-free type** — e.g. `static boolean IStatus.isBusy(@Nullable IStatus<?>)`; the production call site changes one line. Fake = a small interface impl in the test (`IStatusFactory` is pure).
2. **Wire payload codec** — a `…Payload` class with `write(buf)`/`read(buf)`, round-tripped in a `FriendlyByteBuf(Unpooled.buffer())` test (pattern: `QuestTypesTest`). The entity/server-side data is folded into the payload by the existing writer (e.g. `VillagerMenus.write`); the writer's *callers* do not change.
3. **`SimpleVillagerHandle`-style fakes** — `TestDelegator`/`TestSleepModule` from `SimpleVillagerHandleTest`; handle logic without MC.

## 2. Red, then green

- Write the test file first. `LANG=C.UTF-8 timeout 600 ./gradlew :compileTestJava` must FAIL (the method/class does not exist — compile-red is the accepted red for seams).
- Implement the seam at minimum; wire the production call site(s); rebuild `:compileJava :compileTestJava`.

## 3. Verify (in this order)

- `:test --tests "package.TheNewTest"` — then **read `build/test-results/test/TEST-<class>.xml`**: `tests="N"` matching the methods, `failures="0"`. `BUILD SUCCESSFUL` + `executed` does not name the suite (repo AGENTS build-verification).
- Affected-area slice: `--tests "ca.bradj.questown.gui.*" --tests "ca.bradj.questown.town.*" --tests "ca.bradj.questown.jobs.*"`.
- `sh tools/strangler-check` (silent = pass) whenever you add/touch `gui/` or move code near the seam.
- Do NOT gate on `./gradlew build` — it is red by default (`ChickenArcHintsTest.todo_…`), independent of your change.

## 4. The untestable half — doctrine, not simulation

Registry-bound construction (`MenuTypesInit`/`Holder`, any MC class whose
`<clinit>` touches Forge registries, entity classes) **cannot be loaded in a
unit test** (`ExceptionInInitializerError`). Per repo AGENTS: do not simulate
Questown logic. Instead:

- Pin the codec/guard seam (what you CAN test) and **write a one-line note in
  the issue file naming the untestable half** ("the menu-construction half is
  registry-bound and not unit-testable"). Prefer a note over a `todo_`-style
  perpetually-failing test (that convention is for known-incomplete *features*).
- Keep test paths lazy-safe: for collection payloads, guard `if (size == 0)`
  so the element type (e.g. `UIQuest`, whose statics pull a `SpecialQuests`
  → `Quest` chain) is never class-loaded by an empty-list test.

## 5. Close-out, same commit (the gate enforces prep-commit)

1. **Issue**: set `closed: <ISO date>` in the `.scratch/…` frontmatter + a short `## Resolved` body note (what shipped, test name, anything requested-and-checked). Read the exact bytes (`sed -n`) before `edit` — a failed `edit` call is atomic (no block applies) and a misread continuation-indent is the classic miss.
2. **COMPLEX.md**: if the fix adds a non-obvious mechanism or contract, add/extend the section in the nearest co-located doc (`gui/COMPLEX.md`, `town/entity/COMPLEX.md`, …) — mechanism + why + which test pins it. Small pure guard: Javadoc + test may suffice (earn a "nothing to add" verdict deliberately).
3. **Commit**: stage explicit paths only. Single bash call: `T=$(./scripts/gen-commit-token) && WORKFLOW_MD_CHECKED="$T" git commit -m "…"` (token expires in 30 min; env prefix, never `echo`-wrapped). Confirm `git log --oneline -1` + `git status --short` before claiming it landed.

## 6. What this session set (2026-10-02 work-board fixes)

- `IStatus.isBusy(@Nullable)` guard + `IStatusTest` — null status = not busy for job assignment.
- `VillagerMenusPayload` (uuid + slot locks on the wire; entity id best-effort) + `VillagerMenusPayloadTest` — client menus no longer crash on a despawned local townie; `VillagerStatsMenu.attach()` optional live updates.
- Flow documented in `gui/COMPLEX.md` "Villager menu wire"; both issues signed off closed in their fix commits.
