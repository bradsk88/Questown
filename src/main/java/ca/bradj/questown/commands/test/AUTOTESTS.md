# commands/test — scenario index (which scenario proves what)

Pick a scenario from the **behavior you want to prove**, not from the one that
runs without a client. This table is the source of truth for "can this scenario
exercise behavior X." Per-scenario depth lives in each executor and in
`COMPLEX.md` (same directory).

## Table

| scenario | proves / measures | time model | what the seed staffs | run mode |
|---|---|---|---|---|
| `village/growth` | onboarding drives the town to 5 villagers | warps the clock with `time set`; cannot exercise leaver/timed job work (that only ticks realtime) — a warp skips exactly the behavior it should watch | rooms, a player sword, initial job changes; **never a cook** | autonomous (`act()` drives the placement) or held + `questown.village.agentDrive` |
| `village/self_sufficiency` | cook/food/leaver soft-locks after onboarding. A **watchtower: reports, not a pass/fail gate** | **realtime, no warp** (leaver/cook work only ticks realtime) | one-time bootstrap: registered rooms, a cooked-meat store chest, initial job changes; the town must outproduce the seed | **held** — flag ticks + `SpawnVisitorReward`/room-scan need a player near the flag |
| `cook`, `farmer`, `hunter`, `miner`, `organizer`, `fetch`, `soup_cook` | a single job end-to-end, deterministic | **warp-simulates N ticks of job work** (`warpTime` → `injectTicks`); some blueprints also carry a realtime phase | a per-blueprint fixture (room, input items, expected output) | `_qtdev test <job> <warp>`, or the autotest runner (`-Dquestown.autotest.only=<name>`) |
| `F1_*` … `F4_*` (chicken-arc) | helper-chicken beat state transitions | warp-simulate + settle after each warp | none scenario-wide | chicken-arc registry executor |
| `perf/town_large` | flag-tick cost vs the 50 ms budget (`TickProfile`) | realtime | none | a tick-profile, not a gate |

`-Dquestown.autotest.only=<name>` matches by **substring** against the `category/base` scenario names (verified against `AutoTestRunner.nameMatches`): `only=bop` selects both `flag/bop_boundary_63` and `flag/bop_full_64`; `only=village/growth` still works. Scenario names are registered in `TestBlueprintRegistry` (category helpers like `flagEntry("…")` → `"flag/" + name`), so list a category by grepping its helper, not a full name.

The scenario-tuning knobs (`questown.village.selfsuff.days`, `-Dquestown.village.selfsuff.coal`) are read in the **forked** server JVM, and `build.gradle`'s `server` block forwards only its explicit whitelist of `-D` properties - a knob not on that whitelist is silently ignored (the selfsuff pair was added 2026-10-03 after a run that thought it was coal=0 seeded 16). Check the seed/run log line, not the command line, for what actually landed.

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

## Related

- Per-scenario depth + the realtime/warp reasoning: `COMPLEX.md` (same dir).
- Evidence standard (server log, not pixels) and reading a "stuck at roster 0"
  run: the `questown-behavior-verify` skill.
- Held-run launch order and teardown: the `questown-held-run` skill.
