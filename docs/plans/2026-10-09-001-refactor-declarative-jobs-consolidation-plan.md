# Plan: Finish the declarative-jobs consolidation (de-`*Clean` jobs package)

Date: 2026-10-09
Status: **DONE — C0–C6 all verified green on 2026-10-09 (03:47 local).** `JobsClean`,
`ContainersClean`, and `WorkWorldInteractions` no longer exist; their logic now lives in
`jobs/declarative/` (`SupplyItems`, `DeclarativeJobs`, and the moved records).

## Why

`jobs/declarative/package-info.java` principle 4: *"Logic scattered in JobsClean,
ContainersClean, *WI classes, etc. should be migrated here over time."* The declarative
engine is the canonical runtime (context: `CONTEXT.md` gatherer trace — job instances run
`DeclarativeJob → DeclarativeProductionJob → ProductionJob`), yet three legacy helpers with
~20 call sites still live in the legacy `jobs/` package. The consolidation has been moving
in one direction (e.g. `JobsClean.roomsWithState` is `@Deprecated(forRemoval)` with zero
callers; `JobsCleanTest#roomsWithState_*` already tests the successor).

Scope = **pure moves/deletions**. No behavior change; each chunk verified by the full gate.
Not included: the 5 `TODO[WARP]` functional gaps in `TimeWarpWorldInteraction` (functional
work, separate plan) and job proficiency (inert on purpose — `jobs/COMPLEX.md`).

## Baseline (C0, 2026-10-09, `1.20.6-neoforge-wip`)

- `importCheck` was red at HEAD (1 unused import, `loader/neo/TownFlagCapabilityBinder.java`
  — removed via `import-clean apply`, the documented fix).
- `./gradlew build`: **1056 tests, 4 failed — exactly the 4 deliberate `todo_…`
  registry-bound markers** (`ChickenArcHintsTest`, `ServerJobsRegistryCanFitTest`,
  `TestScenarioNamesTodoTest`, `WorkStatusStoreMatSeedTest`), 43 skipped. Known-baseline
  reds, not regressions (AGENTS build verification).

## Chunks (each verified before the next)

| # | Move | From | To | Call sites |
|---|------|------|----|------------|
| C1 | Delete `roomsWithState` + `filterByPredicates` + `describeFilteredRoom` (`@Deprecated forRemoval`, zero callers) and dead `doOrGoTo` | `jobs/JobsClean.java` | — | 0 |
| C2 | `WorkWorldInteractions` record (+`ALWAYS_EMPTY_RESULT_GENERATOR`) | `jobs/WorkWorldInteractions.java` | `jobs/declarative/` | WorksBehaviour, ResourceJobLoader, ResterWork, BOPDepositorWork, DinerWork, DinerNoTableWork, DowntimeWork, meta/DinerRawFoodWork, gatherer/ExplorerWork, gatherer/NewLeaverWork |
| C3 | `ContainersClean` (incl. nested `Block`/`JobSite` interfaces) | `jobs/ContainersClean.java` | `jobs/declarative/` | Containers, TownHasSupplies, TickTownProvider |
| C4 | supply-take family: `shouldTakeItem`, `hasNonSupplyItems`×2, `tryTakeContainerItems`, `SuppliesTarget` + privates | `jobs/JobsClean.java` | `jobs/declarative/SupplyItems.java` (new) | DeclarativeJobTicker(×2), AbstractSupplyGetter, ProductionJob, Jobs, (test: JobsCleanTest#hasNonSupplyItems, JobsCleanShouldTakeItemTest) |
| C5 | rooms/unfinished-work family: `rooms`, `isUnfinishedTimeWorkPresent`, `getStatesWithUnfinishedWork`, `getEntityCurrentJobSite`, `findJobSite` | `jobs/JobsClean.java` | `jobs/declarative/DeclarativeJobs.java` (already home of `roomsWithState`, `warper`, 7 public statics) | TickTownProvider(×3), Jobs(×4), DeclarativeJobTicker, DeclarativeJob |
| C6 | delete `jobs/JobsClean.java`; `import-clean apply`; full gate | — | — | — |

## Decisions (recorded)

- **Chunk order = zero-callers first, mechanical moves second, design-destination last.**
  C1–C3 are unambiguous; C4/C5 destinations chosen from existing declarative homes
  (`SupplyItems` is new because no existing class fits the item-take predicate family;
  C5 lands in `DeclarativeJobs` next to its already-migrated sibling `roomsWithState`).
- **Moves are moves**: no signature or algorithm rewriting in C2–C5 (keeps the ledger
  auditable — each chunk's diff must show relocation, not logic edits).
- **Tests move with their subject** (C4: the `shouldTakeItem`/`hasNonSupplyItems` tests
  follow the new class; suite names change, expectations do not).

## Vocabulary → code map

- "declarative job" = `jobs/declarative/*` canonical runtime (package-info design principles)
- "supply-take" = picking a container item the townie needs *for work* (`shouldTakeItem`)
  vs. extracting a finished result — see `CONTEXT.md` (Leaver / supply chest vocabulary)
- "job block work-state" = `State` (`town/workstatus`), the per-block seed of the NO_JOBSITE
  gate — the C5 methods read it (`rooms`, `getStatesWithUnfinishedWork`). That seed semantics
  live in `State` javadoc + `jobs/COMPLEX.md`, unchanged by this plan.

## Test ledger

| Chunk | Gate | Result |
|-------|------|--------|
| C0 baseline | `./gradlew build` | 1056 tests, 4 known-baseline `todo_…` reds only (2026-10-09) |
| C1–C3 (combined gate) | `./gradlew build` | green, 2026-10-09 (`/tmp/dj-c13b.log`) — compile clean; 1056 tests, 43 skipped, only the 4 known-baseline `todo_…` reds. Two follow-on fixes named by the compiler: `ResultGenerator` import in the moved `WorkWorldInteractions`; `Dependencies2.java` import of the moved `ContainersClean`. |
| C4 | `./gradlew build` | green, 2026-10-09 (`/tmp/dj-c4c.log`) — same 4 baseline reds only. `import-clean` removed 3 now-unused `JobsClean` imports (JobsClean, SupplyItems) as part of the gate. Note: C5-family methods (`findJobSite`, `getEntityCurrentJobSite`, `rooms`, `isUnfinishedTimeWorkPresent`, `getStatesWithUnfinishedWork`) were initially over-replaced to `SupplyItems` in 3 main files + `JobsCleanTest`; reverted to `JobsClean.` and re-pointed in C5. |
| C5 | `./gradlew build` + `jobs/declarative/` suite XML | |
| C5 | `./gradlew build` | green `/tmp/dj-c5c.log` — 1056 tests, 43 skipped, 4 baseline `todo_…` reds only. `DeclarativeJobs.rooms` (MC-specialized) delegates to the moved generic `DeclarativeJobs.<BlockPos,MCRoom,ResourceLocation>rooms` (overloaded); `// TODO: Compare with JobsClean version and eliminate one` now targets two in-class siblings. Javadoc-only `JobsClean.rooms` refs in `State` + `StateFreshIfNullTest` re-pointed. |
| C6 (final gate) | `./gradlew build` | `/tmp/dj-final.log` — **executed** (not up-to-date): `:compileJava`, `:importCheck`, `:compileTestJava`, `:stranglerCheck` all passed; `:test` run to completion: **1056 tests, 4 failed = exactly the baseline markers, 43 skipped.** Named suites re-confirmed via XML: `JobsCleanShouldTakeItemTest` 16/16 green, `JobsCleanTest` 16 green. |

Staging hygiene: `git add -A` briefly swept two pre-existing untracked docs (`docs/boat-keeper.md`,
`docs/warehouse.md`) into the index — unstaged with `git reset`; they remain untracked as found.

## Open decisions

- (C4 name resolved: `SupplyItems` — kept.)
- `production/AbstractSupplyGetter` still in `jobs/production/` — deferred as recorded above;
  it now imports `declarative.SupplyItems`, so the legacy boundary is one import, not one package.
- **New candidate surfaced by C5:** `DeclarativeJobs` now has two `rooms` overloads (its
  original MC-specialized one and the moved generic one) — the existing `// TODO: Compare
  with JobsClean version and eliminate one` marker should be revisited now that both are
  in-class, but that is a dedupe decision, out of this plan's scope (moves were moves).
