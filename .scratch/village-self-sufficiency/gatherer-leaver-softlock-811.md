---
title: Fix the gatherer leaver soft-lock (village/self_sufficiency 811/812 stall)
status: ready-for-agent
created: 2026-10-13
closed: 2026-10-08
---

## Resolution (2026-10-08, closed)

**The root cause was the entitytick range — not the room-set race.** In a
headless world (no player tickets) non-player mobs only tick inside
entity-ticking chunks. The autotest force-loaded exactly one chunk — the flag's
own chunk `(0,0)` — so the town's doors/mats/chests west of it (chunk −1) were
not entity-ticking: any leaver crossing into that region stopped ticking
mid-walk, the take never fired, and the village soft-locked. The 50/50 was the
vanilla world spawn: it is the only other tick-source, and it only coincides
with the town when the spawn lands near origin — **every passing world spawned
at (0,·,0) (819/821/824) and every failing one elsewhere (811 (16,0), 812
(96,16), 817 (0,32), 818 (48,0), 820 (−160,−32), 822 (−32,48)); 829 (112,480)
and 815 (−224,−96) were the hostile extreme.** Live proof: fail world 823's
stuck leaver sat at (−0.07, 64, 0.02) — 0.07 blocks past the x=0 boundary;
`forceload add −1 0` unfroze it within 60s and it `TOOK`.

**Fix:** `AutoTestRunner` now force-loads a 3×3 around the flag, covering the
whole town footprint. With the fix, hostile-spawn worlds (828 (16,0) and 829
(112,480) — spawn classes that previously failed, cf. 811/815) **pass** (827–829
all reached `cook`/extraction). Acceptance met: first `TOOK` in many fresh
worlds, 2+ passing, hostile spawns now pass, this session's 2026-10-08 probes
stripped (the `PROBE-J` + `TOOK item=` oracle kept, as this doc asks).

The earlier "room-set race" theory below was wrong (it was the tick-freeze
all along) but useful — it delivered the `TOOK` discriminator and `PROBE-J`.
The Gate A/B keep-vs-revert call is still open and its changes remain
uncommitted by design; this commit stages only `AutoTestRunner.java`,
`commands/test/AUTOTESTS.md`, and this file. Note: the `created`/prose dates in
this file (2026-10-13) are from a drifted session clock; the box date at
closure is 2026-10-08.

---

## Context

`village/self_sufficiency` stalls ~50/50: the gatherer leaver never grabs raw food (no `TOOK`),
so the raw-food LOOT quest → cook chain → self-sufficiency pipeline dead-ends. Investigated
2026-10-13 on psweep worlds 807–812. **NOT fixed** — this issue carries the scoped diagnosis so a
session does not re-derive the `cleanRooms ← cache ← ticker` chain. (Distinct from the 2026-07-25
overnight batch in this dir, `run-1.md … run-6.md` / `BLOCKERS.md` / `SUMMARY.md`.)

**The pass/fail discriminator (verified 2026-10-13):** the first `[PROBE:supplies] … TOOK item=`.
- PASS: 807 `TOOK=12` (first `cooked_mutton` @02:38:29), 808 `TOOK=18`, 809 `TOOK=11`.
- FAIL: 811 `TOOK=0`, 812 `TOOK=0` (whole run).
- `grep -E 'TOOK item=' /tmp/psweep-8*/stdout.log` reproduces it.
- Churn in *every* world incl. passing 807 (red herrings, **not** the cause): `isJobBlock FALSE`,
  `jobSites 0 (flagMats=0)`, `cleanRooms EMPTY`, `PROBE-J rni-empty … NO-SITES`.

## Root cause (scoped, not fixed)

A **per-leaver, per-moment room-set race** on the `gather` job (the raw-food grab):
`getJobSites()` (DeclarativeJobTickerDependencies:71) = `getRoomsMatching(baseRoom)` returns
**no sites** for the stalling gatherer → `inner=[0=0,1=0,2=0]` → `cleanFns` empty →
`shouldTakeItem REJECT needed-empty` → stuck in `COLLECTING_SUPPLIES`.

`PROBE-J` (DeclarativeJobTicker) pins it: the `gather` job's `inner` is all-zero with **no site
dump**, while the *same leaver's* `seeking_work` job sees the `[12,12]` site (door, 42 blocks) + a
fresh claimable mat (`c=true st=p0/ic0/w0`) **and still gets `inner[0]=0`**. → Not one gate.

**Top suspect: per-owner work-status-store instance / seeding.** `PROBE:ownerStore` shows two store
instances for the same owner: `store@…1d9d1356 gateMatState=null` (gather/town_gate) vs
`store@…48a40ff7 …fresh` (job_board). A whose-seeds-first / instance-identity race is the strongest
candidate for why the gather job's room set is empty for some leavers and non-empty for others.

## What was tried and corrected

| Change | Verdict |
|---|---|
| `UtilClean.specialRulesAllPass` (Gate A: empty special-rules → pass) | A latent V2/cook defect, **not** the flake (807 TOOKs while `isJobBlock` is also false). Tested green. Keep-or-revert. |
| `State.freshIfNull` + two `RoomsStatusLogic` null-skips (Gate B "fix") | **A silent no-op on the critical path** — the ticker already maps null→fresh upstream (`DeclarativeJobTicker.getJobBlockState`, `if (s == null) return State.fresh();` @:278). 812 still stalled (TOOK=0). Recommended **revert**. |

Lesson from the no-op, codified 2026-10-13: a fix that compiles / is referenced / passes a test is
**not verified** until the failing input reaches it; see `.pi/skills/questown-status-why/SKILL.md`
("a fix that never fires is not a fix") and `~/.pi/agent/references/verified-rough-inferred.md`
("a fix that compiles is not a fix that runs").

## Acceptance criteria

- A fresh psweep world (813+) logs the **first `TOOK item=`** (the discriminator), not just
  "raw_LOOT completed".
- 2+ passing worlds AND the previously-failing signature (TOOK=0) now produces a `TOOK`.
- A single passing world is **not** enough (A/B discipline: a 50/50 pass is luck).
- TEMP DEBUG probes stripped before commit (keep `PROBE-J` + the `TOOK item=` line for the oracle).
- `./gradlew build -x test` green (`ChickenArcHintsTest.todo_…` red is NOT a regression).
  Run with `LANG=C.UTF-8`; JDK 17 for gradle on this machine.

## Next actions (in order)

1. Add ONE targeted probe in the gather job's room-loop (`RoomsStatusLogic.getRoomsWhereWorkCanBeDone` /
   `roomsWithState`, or the ticker's inner-building site) logging per mat block: `getJobSites().size()`,
   `isJobBlock(mat)`, `canClaim(mat)`, `getJobBlockState(mat).processingState()`, `getIngredients(0)` size,
   `stateQty`, `mat ∈ getContainedBlocks(room)`. Isolates **which** predicate drops the mat.
2. Confirm store-instance identity: is the gather leaver's `WorkStatusStore` the **same instance** that
   seeded the mat? (the `@1d9d1356` vs `@48a40ff7` split.) If not, the fix is on the store-lookup /
   TOWN_GATE-seed path, not the room loop.
3. Validate with a fresh world (813+), gate on the first `TOOK item=`.

## Triage notes

Diagnosed by the 811/812 investigation (2026-10-13); root cause scoped, **not** fixed. Promoted from
`needs-triage` to `ready-for-agent`: diagnosis + ordered next-action + probes + discriminator are
fully specified, so an AFK agent with `questown-status-why` + `questown-held-run` can begin step 1.

## Still open (decisions the next agent should not silently make)

- Keep vs revert the two orthogonal fixes (above) — recommend reverting `State.freshIfNull` (no-op);
  keep/revert `specialRulesAllPass` (legit latent defect).
- Whether to pin a regression test at the **real** layer (room-set / store-seed) rather than the
  (no-op) null-skip tests already in-tree (`StateFreshIfNullTest`, `WorkStatusStoreMatSeedTest`).

## Paths (evidence + code + lessons)

- Handoff (session-resume): `/tmp/handoffs/avocet.md` — resume with `/handoff-resume avocet`.
  Prior handoff: `/tmp/handoffs/bittern.md`.
- Pass logs: `/tmp/psweep-807|808|809/stdout.log`; fail logs: `/tmp/psweep-811|812/stdout.log`.
- Code chain (line numbers verified 2026-10-13): `DeclarativeJob:391` (cleanRooms) →
  `DeclarativeJobTickerDependencies:302` (cache) → `DeclarativeJobTicker:272` (getJobBlockState,
  null→fresh @:278) + `:71` (getJobSites) → `RoomsStatusLogic` → `ResourceJobLoader`.
- Probes: `PROBE-J` (DeclarativeJobTicker), `PROBE:ownerStore`, `PROBE:cleanRooms`, `PROBE:jobSites`,
  `PROBE-C`, `PROBE:supplies`.
