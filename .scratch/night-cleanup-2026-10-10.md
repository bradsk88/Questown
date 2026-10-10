# Night cleanup session (2026-10-10, user asleep, auto-compact on)

Mandate: behaviour-preserving cleanups / dedup, as many as the night allows.
Verification bar per item: targeted tests + full `:test` (known red = the 6
`todo_…` harness-gated markers only) + `build -x test` green.

## Done
1. [x] aec8a7f6 — the in-flight baker follow-up (warp work-block assignment by
   block identity; v2 stock job files; production-loader test; docs). Gates:
   baker/bread held autotest PASS (After {bread=4,wheat=26,coal=10}),
   cook/simple_furnace_food PASS (cooked_beef 5), targeted + full :test,
   build -x test, strangler-check 0.
2. [x] f7b7035c — weight-spam dedupe (RoomRecipes.getRecipeWeight; new
   logic/COMPLEX.md) + AbstractVillagerMenu System.out -> logger. Live
   verified: 8 WARN vs 54 ERROR at start, autotest PASS.
3. [x] 59295a7d — held-run skill: log4j.config relative-path trap
   (fork cwd is run/ → use file://$PWD/ absolute form).
4. [x] e001e995 — dead-code sweep of jobs/town/logic: removed 5 unreferenced
   private methods (ResourceJobLoader.smeltingResultWorkInt,
   ServerJobsRegistry.isSpecial, AbstractWorkStatusStore.levelBlockIsMat,
   TownQuests.sameItem, TownQuests.producesFood). importCheck + :test green.

## In progress
- [x] Dead-private scan extended to remaining packages → removed 9, kept
  NeoLoader.notYet (migration signpost). Committed 5211cbd3. import-clean
  dropped 5 orphaned imports; importCheck + :test green.
- [ ] Final gate: full autotest suite (or a wide subset) on the committed
  tree, then close-out note + summary.

## Notes / findings
- Baker refs audit: clean (docs + intentional TownQuests.BAKER_BREAD_JOB only).
- TODO/FIXME: 222 total, 4 FIXME — design markers, NOT cleanup targets
  (workaround TODOs are entry points, not conclusions). Skipped.
- The 6 known-red :test failures: TestScenarioNamesTodoTest,
  ServerJobsRegistryCanFitTest, ChickenArcHintsTest, TownVillagerMoodsTest x2,
  WorkStatusStoreMatSeedTest — all deliberate harness-gated markers.

## Rules for the night
- One token per commit: T=$(./scripts/gen-commit-token) && WORKFLOW_MD_CHECKED="$T" git commit -m …
- Explicit path staging only; the foreign untracked set (HANDOFF-*.md,
  docs/plans/2026-10-09-002, docs/todo/*.md, .scratch/sleepless-debuff/runs/*.log)
  is NOT mine to commit.
- No gradle while a held server runs (pgrep '[r]unServer|[f]orgeserveruserdev' first).
- Teardown pkill patterns must be bracket-safe (a bare pattern matches my own shell).

## chopTreeInMemory SOE fix (2026-10-10 evening, same session)

- **Fix implemented**: `WarpWorldAccess.chopTreeInMemory` + `MinecraftWorldAccess.chopTreeRecursive`
  converted to iterative flood fill (ArrayDeque work stack). All `[chopdbg]` instrumentation removed.
- **Regression test**: `WarpWorldAccessTest.chopTree_cutsLargeConnectedRegion_withoutStackOverflow`
  (5000-log line via new 5-arg `fallbackStateProvider` test seam). Red-verified on recursive
  variant (SOE), green on iterative.
- **Full-suite gate running** at 18:41 → `/tmp/chop-suite-verify.log`. At 18:56: test 29/57,
  61 PASS / 7 FAIL / 0 SOE — server alive, **crash gone**.
- **New finding (pre-existing, NOT caused by the fix)**: `arborist/cut_trees` (+full_cycle) and
  `organizer/fetch` now fail with verdicts instead of crashing: villager chops all logs
  (LOOTTRACE x4/x5, logs in its inventory) but never deposits them in the chest within the
  warp+settle window → `expected >= 4/5, got 0 (before=0, after=0)` — the checker counts
  `state.containers` only. Proven pre-existing: the 18:00 standalone repro on the OLD recursive
  code shows the identical FAIL (`/tmp/chop-repro-server.log`). Persist as a new issue.
