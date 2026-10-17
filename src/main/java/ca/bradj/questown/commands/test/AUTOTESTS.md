# commands/test — scenario index (which scenario proves what)

Pick a scenario from the **behavior you want to prove**, not from the one that
runs without a client. This table is the source of truth for "can this scenario
exercise behavior X." Per-scenario depth lives in each executor and in
`COMPLEX.md` (same directory).

## Table

| scenario | proves / measures | time model | what the seed staffs | run mode |
|---|---|---|---|---|
| `village/growth` | onboarding drives the town to 5 villagers | warps the clock with `time set`; cannot exercise leaver/timed job work (that only ticks realtime) — a warp skips exactly the behavior it should watch | rooms, a player sword, initial job changes; **never a cook** | autonomous (`act()` drives the placement) or held + `questown.village.agentDrive` |
| `village/day1` | the day-1 onboarding beats the playtests re-checked by hand, as named checks: camp-ready toast at the first dusk (not before), one villager on the first morning, "post a request" only with a villager, the hunter job change (BOP spend) completing Raw Food, villager 2 only after Raw Food, bedroom + kitchen furnished, the first work request posted, the cook starting on a starting job (not `stock_*`), and a mid-run chunk reload after which the gate still holds. Plus a log tripwire: no work spot reseeded by the stuck-block self-heal and no Questown ERROR line during the run (`LogTripwire`). Stops at the cook beat; PASS = every check. **Known red (2026-10-17): the job board (seeking_work) stuck at state 1 and "Clobbered time" ERRORs at the flag-adjacent block** | realtime at the autotest 200 t/s; ~8 min | the growth village (8 buildings), driven by `VillageGrowthExecutor` in day1 mode: real item-use / wand / deposit / BOP-spend / request paths; checks in `Day1BeatChecks` | `-Dquestown.autotest.only=village/day1` |
| `village/self_sufficiency` | cook/food/leaver soft-locks after onboarding. A **watchtower: reports, not a pass/fail gate** | **realtime, no warp** (leaver/cook work only ticks realtime) | one-time bootstrap: registered rooms, a cooked-meat store chest, initial job changes; the town must outproduce the seed | **held** — flag ticks + `SpawnVisitorReward`/room-scan need a player near the flag |
| `cook`, `farmer`, `hunter`, `miner`, `organizer`, `fetch`, `soup_cook` | a single job end-to-end, deterministic | **warp-simulates N ticks of job work** (`warpTime` → `injectTicks`); some blueprints also carry a realtime phase | a per-blueprint fixture (room, input items, expected output) | `_qtdev test <job> <warp>`, or the autotest runner (`-Dquestown.autotest.only=<name>`) |
| `F1_*` … `F4_*` (chicken-arc) | helper-chicken beat state transitions | warp-simulate + settle after each warp | none scenario-wide | chicken-arc registry executor |
| `perf/town_large` | flag-tick cost vs the 50 ms budget (`TickProfile`) | realtime | none | a tick-profile, not a gate |

`-Dquestown.autotest.only=<name>` matches by **substring** against the `category/base` scenario names (verified against `AutoTestRunner.nameMatches`): `only=bop` selects both `flag/bop_boundary_63` and `flag/bop_full_64`; `only=village/growth` still works. Scenario names are registered in `TestBlueprintRegistry` (category helpers like `flagEntry("…")` → `"flag/" + name`), so list a category by grepping its helper, not a full name.

The scenario-tuning knobs (`questown.village.selfsuff.days`, `questown.village.selfsuff.coal` — the store-chest coal, and `questown.village.selfsuff.mat_coal` — the mat supply chest's coal, a **second seed path** `coal` does not control; zero **both** for a truly coal-starved world) are read in the **forked** server JVM, and `build.gradle`'s `server` block forwards only its explicit whitelist of `-D` properties - a knob not on that whitelist is silently ignored (the selfsuff trio was added 2026-10-03/09 after a run that thought it was coal=0 seeded 16, and a run whose coal-starved world wasn't, because the mat chest seeded its own). Check the seed/run log line, not the command line, for what actually landed.

## Three things all called "warp"

- **`-Dquestown.autotest.warp` / `warpAmount`** (default `24000`, injected by
  `build.gradle`) — how many ticks a scenario simulates per step. A knob on work
  covered per step, not a global speed and not a TPS setting.
- **`warpTime(ticks)` / `MCTownState.warp`** (`TimeWarpCommand`; the job +
  chicken scenarios) — the mod's "time warp": advances N ticks of **job work**
  (`injectTicks`) by **skipping the real ticks and computing the end state**
  (evaluates each job's important-tick transitions in one pass), NOT by running
  those ticks faster. A "Warp took N ms" log line is the computation time.
- **`time set`** — the Minecraft day/night clock jump. Advances the clock, runs
  **no** job work. `village/growth` uses this (per `COMPLEX.md`), which is why
  it cannot exercise realtime cooking.

## TPS

**Autotest runs set the rate themselves (2026-10-15).** `AutoTestRunner.onServerStarted`
calls `Compat.setServerTickRate(server, 200f)` right after `BOOT_OK` (log line
`[autotest] tick rate set to 200.0 t/s`): every autotest scenario - warp or
realtime - is tick-budgeted, never wall-clock-budgeted, so 200 t/s (≈10x the
vanilla 20) delivers the same tick sequence in ~5% of the wall time (a
4800-tick realtime hunter scenario: ~4 min at 20 t/s, ~27 s at 200). Held
runs get it too - this is what the 2026-10-09 guidance did by hand via
`ffeed.sh '/tick rate 200'`. Override: `-Dquestown.autotest.tickrate=<tps>`
(1..10000, same bounds as the command; `=20` restores vanilla pace).

**Branch-dependent.** The **1.20.6 branch (this one)** has a vanilla tick-rate
lever: the `/tick` command group — `query`, **`rate <float>`** (set the TPS
rate; `ServerTickRateManager.setTickRate`), `step`, `sprint`, `stop`, `time`,
`freeze`, `unfreeze` — stable since 1.20.3 (snapshot `23w43a`). So a held
realtime run here can tick *faster* than realtime: feed
`/tick rate 30` from the FIFO (verified in the branch's 1.20.6
`TickCommand`, 2026-10-12). The **1.19.2 legacy line (Questown-119)** predates
it: there TPS is fixed at 20 and no lever exists.

**The upper limit is measured, not set.** Vanilla only clamps the *minimum*
(20) — no maximum; the ceiling is where the *effective* rate stops tracking the
target under load. Sweep (2026-10-15, held `self_sufficiency`, Dev near flag,
this box): **20 / 30 / 60 / 120 / 200 / 240 t/s held exactly at target, zero
`Can't keep up`** (effective = target across 55–60s spans, counted on the 200-tick
`tick diag` cycle); **270 t/s sustained a ~3% shortfall (261.8 eff, stalls on both
passes)**; 255 and 300 *failed* on first pass (232–244 eff, stalls) but **held on
clean retest** (254.5 and 300.0 eff, no new stalls) once the town's load settled.
The limit is therefore **load-dependent, not rate-dependent** — the server
stutters when peak-town-load cost × rate exceeds the per-tick budget, and 270 t/s
is where a town *with* load hits it on this box, while a settled town holds
beyond it. **Reading the limit:** sweep the rate up *during* the busiest load
window you actually care about (not a settled one) and take the highest rate with
zero `Can't keep up` across that window; expect the number to shift ±10–30 t/s
with town and box load. Working recommendation: **200 t/s for speed with zero
stalls under observed load**; treat 250+ as stutter-prone, 300+ as headroom only.
**Caveat (2026-10-15):** the 150-300 band was measured with a cook staffing gap
live (the cook re-selecting `extract → extract` every `cooldown_ticks`, 73 of
these job JSONs at `src/main/resources/data/questown/questown_jobs/` — `cooldown_ticks`
itself is the loop period). The fix is a design feature (new cooks get ≥2 jobs
unlocked via an `unlocked_with_root_job` tag — see the 2026-10-15 handoff), so
**re-run this sweep band after the staffing fix lands** before treating the
240/270 numbers as the ceiling for a correct town. Either way,
`warpTime` does not run ticks faster — it skips them and simulates the end
state, so it **bypasses** the realtime behavior a realtime proof depends on and
cannot substitute for it, *even on 1.20.6*. To prove a realtime behavior you
run real ticks (the realtime watch); `warpTime` is the skip-and-simulate
end-state tool behind the single-job tests (`cook`/`farmer`).

## Headless tick range (why the held world force-loads a 3×3)

A headless held world has no player ticket, so non-player mobs tick **only
inside entity-ticking chunks**. The autotest therefore force-loads a 3×3
block of chunks around the flag at boot (`[autotest] Force-loaded 3x3 chunks
around …`), not just the flag's own chunk: the town's doors/mats/chests sit
in the chunks west and south of the flag, and a leaver crossing into an
unticked chunk freezes mid-walk — `TOOK` never fires and the run stalls in
`COLLECTING_SUPPLIES`. That was the `village/self_sufficiency` soft-lock
(fail worlds 811/812/815/817/818/820/822; passing ones happened to have their
vanilla world spawn — the only other tick source — at the town's chunk, which
is the 50/50: check `SpawnX`/`SpawnZ` bytes in `level.dat`). If a leaver ever
freezes again, `forceload add <x> <z>` over the door's chunk (block pos /16,
truncated) unfreezes it in seconds — that is both the diagnostic and the
workaround.

**The invariant self-checks.** `VillageWatchListener.checkTickCoverage()`
polls after boot and, the moment the town has rooms, force-loads every room
chunk outside the boot 3×3 (idempotent) — a future layout that outgrows the
3×3 self-repairs instead of soft-locking. Read its verdict in the log:
`[village] tick-coverage OK` (all rooms inside the boot region) or
`[village] tick-coverage EXTENDED: force-loaded …` (it had to reach outside,
naming the chunks). If the run still stalls despite `OK` / `EXTENDED`, it is
not a tick-range issue — stop suspecting chunks.

## Post-spawn rooms live outside the arena flatten (seed hazard)

The jobs-track arena flatten clears `TestArenaPreparer.buildVolume(...)` — the
blueprint's `blocks()` bounding box ∪ the 15×15 base square (halfWidth=7), up to
the tallest block + 10 headroom. A room built **in `withPostSpawnAction` is not
in `blueprint.blocks()`**, so its footprint is never flattened — the interior
foot level is un-flattened random-seed terrain. The walkability filter
(`MultiLevelRoomDetector.hasWalkableInterior`) then reads whatever the terrain
gave: a solid foot level yields `floorCells=0` and the day-1 storeroom escape
hatch **accepts** the room — which flips any scenario that expects *rejection*
(NOT_ENCLOSED) by seed. That was `flag/floating_furniture_over_half` (new
[FAIL] in the 2026-10-15 morning full-suite run; `.scratch/floaty-room-2026-10-15/`).
Both rejection-asserting flag builders (`floating_furniture_over_half`,
`too_short_room_dead`) now clear their footprint (feet + head levels) to air
before building. **Rule: a postSpawn-built room in a REJECTION test must clear
its own footprint first.** Accept-expected rooms (`floating_chest_room`,
`filled_storeroom_alive`) pass under either terrain — the same escape hatch
accepts them — so they are not seed-fragile for pass/fail.

## Reading the tally

The log carries two granularities of `[PASS]`/`[FAIL]` lines: **scenario-level**
(`jobs:arborist:cut_trees [FAIL] scenario: some expectations failed` — one per
scenario, the number to count) and **check-level details** (`… [FAIL] WARP: Some
expectations failed`, `[FAIL] minecraft:oak_log: expected >= 4, got 0`,
chicken-arc `flag bit …` / `final beat state` lines). Count scenarios, not
lines: `grep -cE '\[FAIL\] scenario:'` (2026-10-10 run: 29 `[FAIL]` lines =
14 scenarios).

The same discipline applies to the `[village] KNOWN` dumps: in logs
predating the xN collapse the `KNOWN quests open=` line is a raw wall
(one entry per handle instance; 200+ entries ≈ a 100KB line), and reading
membership in that wall by eye is how the 214-phantom JOB_UNLOCK spam got
misread as "day-1 quests re-opened" (2026-10-11, closed as a
misdiagnosis in `.scratch/mat-gated-arrival/`). Compress per id before
claiming anything about list contents:
`python3 scripts/known-dumps.py run/<log> [HH:MM] [HH:MM]` prints
open/done counts per id only on state change. Townies are **one line
each** (`KNOWN townie <uuid8> job=…`), so roster claims aggregate across
lines, not within one. The log's timestamps are bracketed
(`[01:55:00]`), which breaks naive awk/grep time filters on the field
offset — the script handles it.

## Related

- Per-scenario depth + the realtime/warp reasoning: `COMPLEX.md` (same dir).
- Evidence standard (server log, not pixels) and reading a "stuck at roster 0"
  run: the `questown-behavior-verify` skill.
- Held-run launch order and teardown: the `questown-held-run` skill.
