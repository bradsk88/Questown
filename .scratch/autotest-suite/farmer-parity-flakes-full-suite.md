---
title: farmer/harvest_wheat [proficiency_parity] flakes: 0 harvest actions in the 4800-tick realtime window
status: needs-triage
created: 2026-10-15
updated: 2026-10-15
priority: p2
---

## Symptom

One full-suite run (2026-10-14 22:37, `run/autotest-full-final.log`, test 30/59):
`parity realtime: actions=0 gain=0.0` → FAIL "parity realtime did work". The warp
half passed (actions=43-ish pattern continues). `regrew 0 crops` before the warp
window — consistent with (downstream of) zero realtime harvests, not an
independent cause.

## Ruled out / re-derived this session (2026-10-15)

- **Not deterministic predecessor pollution.** A 34-test binary-search subset
  (identical predecessor set: tests 1–29 + parity + held_tool_warp + 3 axe
  variants, via the new comma-separated `-Dquestown.autotest.only=`) passed
  parity **3/3** (`run/autotest-bisect-{A,B,C}.log`), and a full-suite run
  (`run/autotest-full-2.log`) passed it too (realtime actions=9, warp 43;
  regrew 9 crops). So 4/4 passing vs the one 10-14 failure: rare flake, not
  suite-order-dependent. (Note the realtime action count varies 9–22 across
  passing runs — the window's harvest count is already seed/timing-variable;
  the failure is the tail at 0.)
- **The empty gate dump is a routine init-timing artifact, NOT the signal.**
  `gate[rooms={}]` lines: 128 in passing run A, 116 in passing run B, 252 in
  the failing run — all runs emit them. The 10-14 handoff read "gates empty for
  the whole window" from a single init-time dump (the [WHY] dump is rate-limited
  per label, leaver-only at the time); that reading was wrong.
- **Supplies were fine.** 10-14 log: `[PROBE:supplies] TOOK wooden_hoe
  (totalTakes=159)` at test start — the villager took the hoe once.

## Failure signature (10-14 log, test 30 span lines 5453–5670)

- 22:37:53 — spawn, job → `farmer:harvest_wheat`, one hoe TOOK.
- 22:37:56 → onward — **`seeking_work` recompute every ~3 ticks for the entire
  window** (22:37:56, :59, 22:38:02, :05, …). No second hoe TOOK, no
  `bop_depositing`, no work.
- Compare a passing run (bisect A, same span): TOOK-hoe **every second**
  (totalTakes 103→112 over 9 s) interleaved with recompute, then
  `bop_depositing` at 00:36:50 — the working loop.
- So the flake is: after the first supplies take, the farmer:harvest_wheat
  *workable* precondition (farm room spot / ripe-crop gate in the farmer
  journal) came back false on **every** recompute for 4800 ticks.

## Why it was invisible before

`JobStatuses.maybeLogWhy`'s `[WHY]` gate-dump filter only included
`gatherer:`/`hunter:` labels — the stuck journal was `farmer:`, so the failing
run logged no gate detail for the circuit that actually decided it.

## Instrumentation (REVERTED 2026-10-15; patch saved)

Reverted from the working tree after the 2026-10-15 baseline runs (parity
passed 3 consecutive full-suite runs: 10-14 22:37 FAIL was the only
failure; 10-15 04:32 and 04:48 both PASS). The full diff is preserved at
`.scratch/autotest-suite/ws-instrumentation-2026-10-15.patch` — re-apply
with `git apply` if parity flakes again:

- `JobStatuses.maybeLogWhy`: filter widened to `farmer:` too.
- `TownFlagBlockEntity.getRealWorkStatusHandle`: `[WS]` log at per-owner
  store creation (owner + rooms-in-snapshot).
- `AbstractWorkStatusStore.registerNewRooms`: `[WS]` log per room
  registration.
- `ServerJobsRegistry`: `[WS]` log on `jobInterestByBlock.put` /
  `forgetWhichBlocksJobsCareAbout` (cache fill/clear).
- `AutoTestRunner.nameMatches`: comma-separated substrings for
  binary-search runs.

All `[WS]` lines verified on cold paths (creation/registration/cache-miss),
none on the per-tick hot path.

## Next move when it flakes again

Re-run the full suite (or hammer the 34-subset ~3 min/run) until parity fails;
the log will then carry: store creation snapshot, room registrations,
jobInterestByBlock entries, and `[WHY]` dumps **for the farmer journal** every
4 s during the stuck window — which pinpoints which gate (`rooms`, `spots`,
`job_blocks`) stayed empty and why.

## Related

- `.scratch/autotest-suite/organizer-fetch-warp-fails-only-in-full-suite.md` —
  the sibling "full-suite-only" failure; observed flaky both directions this
  session (failed in bisect A, passed in B/C/full-2), i.e. seed-dependent,
  not order-dependent.
- `.scratch/wallwalk-perf-stall/` — independent perf blocker found the same
  session (seed-dependent wall-walk blowup in `perf/town_large`).

## 2026-10-18 samples: `[held_tool_warp]` flakes too (pre-existing)

Isolated `-Dquestown.autotest.only=held_tool_warp` runs: c321107c 7/8, 9a7d57da 3/5, 80711a6f
(HEAD) 7/9 — fails on code before that evening's changes, so not a regression from them (the
9a7d57da timer change is a no-op for farmer jobs: no farmer work state declares `time`).
Failure shape differs slightly from the 10-14 parity one: the farmer **never takes the hoe**
(no `[PROBE:supplies] TOOK`, journal `[]` at assertion; "held-tool warp precondition" FAIL), no
`seeking_work` cycling. Logs: scratchpad `rerun-held_tool_warp.log` (fail),
`base-held_tool_warp.log` (pass). The instrumentation patch above is the next move.
