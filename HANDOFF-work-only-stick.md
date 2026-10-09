# Handoff: work-only start-gate fix — shipped + re-verified (cook/stick stall)

Completion of the work stream `avocet` started. The TDD fix for "a job whose
state 0 declares only `work` can never start" is **committed** as `c9e55434`
on `1.20.6-neoforge-wip` (14 files; amended once from `061ecb76` to correct a
false `farmer/till` claim). The live re-watch that settled it is **done**
(decision 10): it found one new production NPE (`roomsAtState`, guarded in
`84d131fb`), after which the held `self_sufficiency` run showed the cook
entering the work-only `cook/extract` state live, working, and cycling on.
This supersedes `HANDOFF-work-only-stick.md` in the repo root (the archive
copy the sending session wrote; the diagnosis in
`~/.pi/agent/handoffs/avocet.md` is now historical — all its "Not yet done"
items 1–4 are done). What's left: one separate work stream (`stranglerCheck`).

## Process (pointers)

- `src/main/java/ca/bradj/questown/jobs/COMPLEX.md` — new section "Work-only
  state 0 — the start-gate trio": the mechanism, why, and which tests pin it.
  Read this first; it is the settlement doc for the fix.
- `.pi/skills/questown-held-run/SKILL.md` — for the follow-on re-watch
  (launch sequence, 30s cadence, FIFO console, TPS lever).
- `AGENTS.md` (project root) — build-gate facts: `:test` red-by-default
  (`todo_…` markers), TEST-xml discipline, strangler gate, held-run rules.

## State / Files

Commits, oldest → newest, all on `1.20.6-neoforge-wip`: `c9e55434` (the fix)
→ `ea05bebd` (hollow-equivalence tier 1–3 + tag resolution) → `35955d4d`
(retro/hygiene: `AGENTS.md` + `.pi/skills/questown-unit-tdd-fix/SKILL.md`) →
`84d131fb` (the `roomsAtState` NPE guard found on re-watch). Verify with
`git log --oneline -4`. Everything in this section is @ `84d131fb` (HEAD);
the per-file notes below were written @ `c9e55434` and are unchanged except
where noted.

- `src/main/java/ca/bradj/questown/jobs/ResourceJobLoader.java` (~:857-861)
  — `workStates`: a `work`-only state is now `valid = true` at any index
  (was `valid = valid || i != 0`). The index-0 special error message is gone.
- `src/main/java/ca/bradj/questown/jobs/declarative/RoomsStatusLogic.java`
  (:115-117) — work bucket filter is now
  `(bp, s) -> canClaim.test(bp) && (workDeclaredAt(i) || s.hasWorkLeft())`
  behind the unchanged `!requiresTools` gate; `workDeclaredAt` is a new
  compute parameter (key presence in the work map, even at floored 0).
- `src/main/java/ca/bradj/questown/jobs/declarative/DeclarativeJobs.java`
  (:678) — `getStatesWithUnfinishedWork` admits
  `apply.workLeft() > 0 || workDeclaredAt(apply.processingState())`.
- `src/main/java/ca/bradj/questown/jobs/TickTownProvider.java` (:34,:55,:115)
  — new `workDeclaredAt` constructor param, fed to the admission above
  (`state -> deps.getWorkForStep(state) != null` at the call site in
  `declarative/DeclarativeJobTicker.java`); the deprecated `Jobs` delegate
  (`jobs/Jobs.java` :218,:224) and the TimeWarp call site
  (`declarative/TimeWarpWorldInteraction.java`) got the same param.
- `src/main/java/ca/bradj/questown/jobs/TickTownProvider.java` — ADDITION @
  `84d131fb`: `roomsAtState` now null-guards
  `roomsNeedingVillagerInput.get().get(state)` (state absent from the room
  map → empty room set, was an NPE). Red test
  `TickTownProviderTest.roomsAtState_stateAbsentFromRoomMap_yieldsEmptyCollection`
  reproduces the live crash verbatim.
- `src/main/resources/data/questown/questown_jobs/cook_extract.json` —
  de-stick: `work_states: [{"work": 0.1}]` only; the `tools`
  (`minecraft:stick`) + `//TODO` note are gone.
- New tests: `cook_extract_workOnly_state0_emptyInventory_shouldExtractProduct`
  (`src/test/java/ca/bradj/questown/jobs/declarative/CookEquivalenceTest.java`),
  `compute_workOnlyState0_freshBlock_yieldsWorkOnlyBucket`
  (`…/declarative/RoomsStatusLogicTest.java`). Updated:
  `cook_extract_shouldLoadCorrectly` (`…/declarative/JobIntegrationTest.java`
  — now asserts `toolsRequiredAtStates().isEmpty()`) + the two test callers of
  the changed signatures (`TestTickerDependencies.java`, `TickTownProviderTest.java`).

Committed in `35955d4d` (the hygiene item that was once "deliberately not
in the commit"): `AGENTS.md` (workaround-marker triage bullet + data-scan
clause + the 4-member `todo_…` red set) and
`.pi/skills/questown-unit-tdd-fix/SKILL.md` (pre-flight lines + two §2
red-test lines + commit-claim lint). `~/.pi/agent/prompts/handoff.md`
(bird-pick verbatim-note + dataset-scan claims lint) is user-level, outside
this repo — not committable here.

## Live state (re-check before acting)

- Held `runServer` was killed with the user's OK at ~2026-10-09 18:50 (the
  `village/self_sufficiency` watch had finished 08:18, server had idled since).
  Re-check: `pgrep -af '[r]unServer'` (expect none) +
  `pgrep -af GradleDaemon` (daemon 48665 was kept alive and should still be
  up; a dead daemon is fine too — gradle starts a fresh one).
- The commit is local only; branch `1.20.6-neoforge-wip`. Re-check:
  `git log --oneline -1` → `c9e55434`.
- Run-config read-back: `run/config/questown-server.toml:107` says
  `CrashOnInvalidJobs = true` (Config default is also true,
  `core/Config.java:265-267`). So before the loader fix, a work-only state 0
  would have **crashed the server at job load** — the de-stick and the
  loader fix are one atomic unit; never re-land one without the other.

## Decisions landed

1. **DECIDED** — kill the idle held server (wrapper tree), keep the gradle
   daemon. User confirmed ("confirm") at the start of this session.
2. **CONFIRMED** — the three-gate rejection chain from `avocet` held on disk
   (read at working tree 2026-10-09, pre-edit): loader `valid = valid || i != 0`
   (ResourceJobLoader.java:860), work bucket `State::hasWorkLeft`-only
   (RoomsStatusLogic.java:104), admission `apply.workLeft() > 0`-only
   (DeclarativeJobs.java:661,673). All three are now fixed as in "State /
   Files" above (@ c9e55434).
3. **DECIDED** — the work bucket must ALSO be `canClaim`-gated, not just
   work-declared. The first green run regressed the 2026-10-07 lock-in
   `matExcludedByCanClaim_yieldsEmptyNeededSet_neededEmptyReject`
   (a canClaim-excluded gate mat must yield an empty needed set); adding the
   position-level `canClaim` check to the work bucket resolved it. This went
   slightly beyond `avocet`'s fix plan (a) — the lock-in is the authority.
4. **CONFIRMED** — after a successful extraction the job block reads back as
   state 0 because `getResetFunc` resets it: `AbstractWorldInteraction.java:294-295`
   (@ c9e55434) does `setJobBlockState(inputs, ts, workSpot, State.fresh())`
   + `clearClaim(workSpot)` post-extract. So the new test asserts
   `productExtracted` + the beef in the inventory, NOT block state — a
   post-cycle state 0 is correct, not a stall.
5. **CONFIRMED** — `cook/extract` is the ONLY work-only state-0 job in
   `src/main/resources/data/questown/questown_jobs/` (python3 scan over all
   job JSONs @ c9e55434: every other state-0 that has `work` also declares a
   `tools` entry; work-only states at index ≥1 are pre-existing and were
   loader-legal). **Self-correction:** this session initially claimed
   `farmer/till` was also work-only state-0 and would have failed to load —
   false, it declares `tools: #questown:hoes`. The amend of the commit
   (`061ecb76` → `c9e55434`) and the COMPLEX.md fix corrected this.
6. **CONFIRMED** — post-fix verification (2026-10-09, @ c9e55434): full `:test`
   1082 tests, only the 4 deliberate `todo_…` harness-gated asserts fail
   (`TestScenarioNamesTodoTest`, `ChickenArcHintsTest`,
   `ServerJobsRegistryCanFitTest`, `WorkStatusStoreMatSeedTest` — pre-existing
   red-by-default, none jobs-related), 45 pre-existing `@Disabled` skips.
   Named suites read from their TEST-xml: `JobStatusesTest` 18/18,
   `JobIntegrationTest` 66, `TickerAdvancerEquivalenceTest` 62/62 (baker/fisher
   timer-hazard tripwires green), `RoomsStatusLogicTest` 12/12,
   `CookEquivalenceTest` 5/5. `compileJava`/`jar`/`importCheck` executed green.
7. **CONFIRMED** — `stranglerCheck` is red **at HEAD independently of this
   work**: it flags `logic/RoomRecipes.java`,
   `logic/RoomRecipesUpgradeCandidatesTest.java`, `town/entity/TownQuests.java`
   — none modified by this diff, so the diff cannot be the cause. It is a
   separate work stream (route the leaks through the seam).
8. **Settled by decision 10** — the de-stick clears the cook's stall;
   re-watch confirmed the cook cycles cook states live (the mechanism behind
   the 17-day `NO_FORWARD_PROGRESS` shape is gone). The day-17 horizon was
   not re-run to completion; no lock of any kind appeared in ~13 min of
   200-t/s wall time.
9. **CONFIRMED (crash, then guard)** — the re-watch's first run crashed the
   server thread ~40s in: `NullPointerException` at
   `TickTownProvider.roomsAtState` (`Map.get(state).stream()` unguarded),
   called from `JobStatuses.tryChoosingItemlessWork` walking the *newly
   widened* state list (work-only state 0 now admitted) against the *cached*
   room map, which held no state-0 room yet on a fresh-world morning.
   Guarded in `84d131fb` (absent state → empty room set); red test
   `TickTownProviderTest.roomsAtState_stateAbsentFromRoomMap_yieldsEmptyCollection`
   (the builder's empty room map hits the guard). `TimeWarpWorldInteraction`
   was already guarded. Crash artifacts: `.scratch/cook-stick-rewatch/`
   (`crash-2026-10-09_19.45.25-server.txt`, both run logs). COMPLEX.md
   carries "Consequence, found on re-watch" under the trio section.
10. **CONFIRMED (re-watch green, 2026-10-09 ~19:55–20:06)** — second held
    `self_sufficiency` run @ `84d131fb` (200 t/s via ffeed): cook cycled
    `cook/fuel` → `cook/dining_raw_food` → `cook/mutton` and entered the
    work-only `cook/extract` state live (watch lines 19:57:10–19:57:22, no
    CANT_REACH, no lock) before cycling on; world product extraction
    running (`LOOTTRACE extracted … mutton/wheat_seeds/oak_sapling/…`);
    zero `[lock]` lines; zero NPEs after the guard. The pre-fix crash
    moment (~40s, day-1 morning) was passed and survived. Verdict: the
    work-only start gate opens and the cook works. Logs:
    `.scratch/cook-stick-rewatch/run-selfsuff-stickfix-run2.log` (run 2,
    green) and `run-selfsuff-stickfix.log` (run 1, the crash).

## Not yet done (ranked)

1. **Separate work stream:** the pre-existing `stranglerCheck` red
   (decision 7).

Done since the previous revision of this handoff: the `self_sufficiency`
re-watch (decision 10), the retro-edits hygiene commit (`35955d4d`), and the
`roomsAtState` guard (`84d131fb`).

## Do NOT

- Don't re-add a `tools` entry to `cook_extract.json` — the whole point of
  the fix is that work-only state 0 is loadable and startable; a stick would
  silently revert to the old stall shape (tools route the room through the
  tools bucket, which requires the villager to HOLD a stick it has nowhere
  to get).
- Don't touch the "tools" on `cook_fish` / `cook_fuel` /
  `cook_simple_furnace_food` / `cook/stock_*` — those are real supply
  entries (item tags), not the workaround.
- Don't read a green `stranglerCheck` as the success gate for this work: it
  is red at HEAD (decision 7). Gate on targeted `:test` + TEST-xml, and
  `compileJava`/`jar` executed.
- Don't re-propose the rejected alternatives from `avocet` decision 9:
  seeding `workLeft` in `JobLogic.setup` (already done when
  `workRequiredAtFirstState != 0`) and fixing only the `JobStatuses` circuit
  (the circuit was never the defect).
- Don't conflate this stick with the chicken-arc `WAITING_FOR_STICK` log
  noise (wand-chicken onboarding, different owner).
- Don't drop the `roomsAtState` null guard or re-widen
  `getStatesWithUnfinishedItemlessWork` without re-checking the room-map
  read: the fresh-computed state set and the cached room map diverge in
  time, so any new admission can ask `roomsAtState` about a state the map
  does not hold (that is exactly what the 2026-10-09 crash was).

## Inputs (newest first)

- `.scratch/cook-stick-rewatch/` — crash report + both held-run logs (run 1
  crashed at `roomsAtState`; run 2 green, cook cycled `cook/extract`).
- `git show 84d131fb` — the guard + red test; `git show c9e55434` — the fix.
- `src/main/java/ca/bradj/questown/jobs/COMPLEX.md` — "Work-only state 0 —
  the start-gate trio" (+ "Consequence, found on re-watch") and "Equivalence
  tests: equivalence is not completion".
- `~/.pi/agent/handoffs/avocet.md` — the pre-fix diagnosis (decisions 1–9)
  and the original red-test spec; now historical but the source of the
  rejected-alternatives and the NO_FORWARD_PROGRESS context.
- `AGENTS.md` — build-gate + held-run discipline. Note: agent commits in this
  repo run the `.githooks/pre-commit` prep-commit gate (COMPLEX.md docs
  before the commit; unlock via `scripts/gen-commit-token` inline on the
  `git commit` command — the skill at
  `/workspace/IdeaProjects/CodeStyle/commit-prep/skills/prep-commit/SKILL.md`).

## Next

1. If you want the full 17-day horizon re-run: launch the held
   `self_sufficiency` watch again (`.pi/skills/questown-held-run` skill,
   daemon warm, world wiped by the autotest) and watch for `[lock]` lines —
   the mechanism is fixed, the long-horizon confirmation is the only
   unexercised check.
2. If a stall appears anywhere, load `.pi/skills/questown-status-why` and
   dump the decision that assigns the stuck status (the `[WHY]`/`Lacking`
   lines) — don't re-read code to speculate.
3. `stranglerCheck` red (decision 7) is a separate work stream — route the
   three flagged files through the seam when picked up.

## Recommended model

Sonnet-class. The work stream is complete through live re-verification; what
remains is either a long-horizon confirmation run (run-and-observe, clear
pass/fail signature) or the unrelated strangler work stream. Escalate only
if a new stall reproduces and its `[WHY]` dump points outside the start-gate
trio + roomsAtState guard.
