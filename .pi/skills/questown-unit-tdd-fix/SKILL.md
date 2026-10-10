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
  fragments — seam choice, the registry-bound-untestable doctrine, the
  build/verify facts, and the same-commit close-out through prep-commit.
---

# Questown unit-TDD fix loop

## 0. Pre-flight (once)

- `pgrep -af '[r]unServer|[r]unClient'` — a live dev session contests the build; ask to kill it first. **Triage it before asking**: an idle post-finish server (tail `run/logs/latest.log` for the run's completion marker, e.g. `WATCH complete` / `autotest PASS|FAIL`) has nothing left to observe — present the kill as the recommendation with the finish timestamp; a still-observing run gets the ask/wait treatment.
- **A diagnosis is unconfirmed until something ran.** Before declaring the mechanism found, establish a baseline (run the one existing test in the target class) and write the failing variant — the red test is the first deliverable of an investigation, not the last.
- Read the `.scratch` issue in full; grep `CONTEXT.md`/`docs/adr/` for the subject before asserting a domain negative.
- `jmembers <Type> [anchor]` for ANY API shape you have not compile-checked (FriendlyByteBuf members bit the 2026-10-02 session: no `readBooleanList`, no `Boolean::writeBoolean` encoder — it is `writeCollection(col, (buf, v) -> ...)` or a varint+loop).

## 1. Pick the testable seam (the fix is its wiring)

Three proven shapes, in decreasing preference:

1. **Pure guard on a Forge-free type** — e.g. `static boolean IStatus.isBusy(@Nullable IStatus<?>)`; the production call site changes one line. Fake = a small interface impl in the test (`IStatusFactory` is pure).
2. **Wire payload codec** — a `…Payload` class with `write(buf)`/`read(buf)`, round-tripped in a `FriendlyByteBuf(Unpooled.buffer())` test (pattern: `QuestTypesTest`). The entity/server-side data is folded into the payload by the existing writer (e.g. `VillagerMenus.write`); the writer's *callers* do not change.
3. **`SimpleVillagerHandle`-style fakes** — `TestDelegator`/`TestSleepModule` from `SimpleVillagerHandleTest`; handle logic without MC.

## 2. Red, then green

- **Assert with JUnit `org.junit.jupiter.api.Assertions`** — AssertJ is *not* on
  the test classpath (`testImplementation` is `junit-jupiter` + `mockito`
  + `junit4` only), so an `org.assertj` import is a compile-red before your real
  red. (Repo convention: ~100 tests on JUnit Assertions, 0 on AssertJ.)
- **Assert the terminal effect, traced.** Before choosing the assertion target,
  trace the flow's terminal step to the end: `AbstractWorldInteraction.getResetFunc`
  resets a job block to `State.fresh()` after a successful extraction, so
  "block at maxState" is the WRONG terminal observation for a completed
  extract cycle — assert `productExtracted` + the item in the inventory
  instead (2026-10-09 cook_extract red test: first assertion set was
  refuted by one extra run + a read of the reset func).
- **Pin the data shape when a later step mutates it.** If the fix plan has a
  later step that changes the data file your test reads (e.g. de-sticking a
  job JSON), don't write the test against the live file: load-and-mutate the
  JSON in the test (strip the key in-memory) + a precondition assert on the
  shape. Otherwise the test stays red after the code fix, failing on the data
  state instead of the gate you're settling (2026-10-09 cook/stick: the
  de-stick was step 4; the red test had to be tool-stripped in-memory to fail
  on the gates, not the stick).
- Write the test file first. `LANG=C.UTF-8 timeout 600 ./gradlew :compileTestJava` must FAIL (the method/class does not exist — compile-red is the accepted red for seams).
- Implement the seam at minimum; wire the production call site(s); rebuild `:compileJava :compileTestJava`.
- **Red-verify a regression pin against the pre-fix code with a /tmp variant, not a `git show` overwrite.** Copy the live file to /tmp, patch ONLY the method under test back to its old form, swap the variant in, run the pin (expect red), restore. Never `git show HEAD:path > path` on a live file: the working tree may carry uncommitted seams the HEAD version lacks, and the overwrite then eats both the fix and those seams (2026-10-10 chop SOE: the 5-arg test constructor and the iterative chop were lost in one clobber — a /tmp backup made the loss recoverable). `git status` the target first; back it up regardless.

## 3. Verify (in this order)

### Oracle discipline (autotest PASS ≠ the assertion ran)

- **Trust a scenario PASS only after its assertion's log line appears for the run under test.** Grep the run's section for the assertion message; a `PASS` whose assertion output exists only in the *other* phase's section is a hollow pass — the harness skipped it. (2026-10-04: `loot_on_extract_warp` passed on a trivial item check while its `quest complete=` line existed only in the realtime run; the gap was `TestExecutor.checkResults` not running `withCustomAssertion` — now fixed. Audit at the time: only this scenario was exposed.)
- `-Dquestown.autotest.only` matches with **contains** — `only=loot_on_extract` runs both the realtime and warp entries in one server boot (`Test 1/2`, `Test 2/2` in one log, `/tmp/<run>.log`).
- **A field on `MCTownState` must be carried through `MCAdvanceTime.finalizeState`** — the terminal rebuild (called by `AbstractAdvanceTime` before the warp result is returned) drops instance fields: unit-tested copy semantics do *not* cover it (2026-10-04: 8 records written, settle read 0). See `town/entity/COMPLEX.md` “LOOT quest close”.

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
3. **Commit**: stage explicit paths only. **Lint the message's claims first**: a commit message and the COMPLEX.md note carry data/flow claims ("the only work-only state-0 job", "farmer/till also failed to load") — verify each against the raw entry (open the actual JSON/line) the same standard as handoff receipt lint applies to citations. A false structural claim shipped in a commit message needed an amend on 2026-10-09 (farmer/till misread from a positional scan). Then, single bash call: `T=$(./scripts/gen-commit-token) && WORKFLOW_MD_CHECKED="$T" git commit -m "…"` (token expires in 30 min; env prefix, never `echo`-wrapped). Confirm `git log --oneline -1` + `git status --short` before claiming it landed.

## 6. What this session set (2026-10-02 work-board fixes)

- `IStatus.isBusy(@Nullable)` guard + `IStatusTest` — null status = not busy for job assignment.
- `VillagerMenusPayload` (uuid + slot locks on the wire; entity id best-effort) + `VillagerMenusPayloadTest` — client menus no longer crash on a despawned local townie; `VillagerStatsMenu.attach()` optional live updates.
- Flow documented in `gui/COMPLEX.md` "Villager menu wire"; both issues signed off closed in their fix commits.
