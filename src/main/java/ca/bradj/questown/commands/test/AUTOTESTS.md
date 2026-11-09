# commands/test — scenario index (which scenario proves what)

Pick a scenario from the **behavior you want to prove**, not from the one that
runs without a client. This table is the source of truth for "can this scenario
exercise behavior X." Per-scenario depth lives in each executor and in
`COMPLEX.md` (same directory).

## Table

| scenario | proves / measures | time model | what the seed staffs | run mode |
|---|---|---|---|---|
| `village/growth` | onboarding drives the town to 5 villagers | warps the clock with `time set`; cannot exercise leaver/timed job work (that only ticks realtime) — a warp skips exactly the behavior it should watch | rooms, a player sword, initial job changes; **never a cook** | autonomous (`act()` drives the placement) or held + `questown.village.agentDrive` |
| `paths/at_fork` | the jumping-off point for curated-path playtests (dev, 2026-10-26): growth's driver plays onboarding the real way and stops once a path's starter batch posts (any quest carries a `pathBatch` tag); PASS = `path-starter-posted`. Run held (`-Dquestown.autotest.hold=true`) and join to play the path from the fork | as `village/growth` | as `village/growth` (`VillageGrowthExecutor`, `Goal.FORK`) | `-Dquestown.autotest.only=paths/at_fork` |
| `paths/outfitter` | a curated path played end to end (2026-10-26): growth's driver plays onboarding, then keeps going past the fork (furnishes the path's rooms, spends BOPs on its job change and named skills, supplies unmet needs) until every batch of every loaded path has posted and completed. Checks `path-batch-posted[<path>/<batch>]` and `path-batch-complete[...]` per batch; PASS = every check, FAIL `paths-finished` on timeout (twice growth's budget) | as `village/growth` | as `village/growth` (`VillageGrowthExecutor`, `Goal.PATHS_FINISHED`) | `-Dquestown.autotest.only=paths/outfitter` (held: join and watch the path) |
| `village/day1` | the day-1 onboarding beats the playtests re-checked by hand, as named checks: camp-ready toast at the first dusk (not before), one villager on the first morning, "post a request" only with a villager, the hunter job change (BOP spend) completing Raw Food, villager 2 only after Raw Food, bedroom + kitchen furnished, the first work request posted, the cook starting on a starting job (not `stock_*`), and a mid-run overnight absence (flag chunk unloaded, clock to the next noon, 6 s wall) after which the gate still holds and the missed morning applies the pending BOP job change. Plus a log tripwire: no work spot reseeded by the stuck-block self-heal, no Questown ERROR line, no two quest announcements within one broadcast slot (`LogTripwire`). Stops at the cook beat; PASS = every check. The job board at state 1 is a resting state, exempt from the self-heal (2026-10-18; `town/COMPLEX.md` "Stuck-block self-heal"). 35/35 on 2026-10-18 | realtime at the autotest default rate, sleeping through nights; ~2.5 min scenario (45 000 ticks) | the growth village (8 buildings), driven by `VillageGrowthExecutor` in day1 mode: real item-use / wand / deposit / BOP-spend / request paths; checks in `Day1BeatChecks` | `-Dquestown.autotest.only=village/day1` |
| `village/self_sufficiency` | cook/food/leaver soft-locks after onboarding. A **watchtower: reports, not a pass/fail gate**. Run-to-run variance is large (2026-10-23: roster 1-5, the cook never unlocked in 1 of 4 runs on the same code): compare ≥3 runs per side (`tools/autotest-parallel.sh 3 village/self_sufficiency`) before attributing a change | **realtime, no warp** (leaver/cook work only ticks realtime) | one-time bootstrap: registered rooms, a cooked-meat store chest, initial job changes; the town must outproduce the seed | **held** — flag ticks + `SpawnVisitorReward`/room-scan need a player near the flag |
| `cook`, `farmer`, `hunter`, `miner`, `soup_cook` (the arborist, explorer and organizer scenarios are out of the suite until those jobs return, #403/#407) | a single job end-to-end, deterministic. Most check a floor (`*` ≥ 1); `hunter/sword [one_day_yield]` also caps one warp day at the realtime maximum (`*` ≤ `OneDayYieldCaps.HUNTER`, 15; `town/COMPLEX.md` "Warp cycle budget") | **warp-simulates N ticks of job work** (`warpTime` → `injectTicks`); some blueprints also carry a realtime phase | a per-blueprint fixture (room, input items, expected output) | `_qtdev test <job> <warp>`, or the autotest runner (`-Dquestown.autotest.only=<name>`) |
| `F1_*` … `F4_*` (chicken-arc) | helper-chicken beat state transitions | warp-simulate + settle after each warp | none scenario-wide | chicken-arc registry executor |
| `perf/town_large` | flag-tick cost vs the 50 ms budget (`TickProfile`) | realtime | none | a tick-profile, not a gate |

`-Dquestown.autotest.only=<name>` matches by **substring** against the `category/base` scenario names (verified against `AutoTestRunner.nameMatches`): `only=bop` selects both `flag/bop_boundary_63` and `flag/bop_full_64`; `only=village/growth` still works. Scenario names are registered in `TestBlueprintRegistry` (category helpers like `flagEntry("…")` → `"flag/" + name`), so list a category by grepping its helper, not a full name. **Chicken-arc scenarios carry no prefix** (`F2_chest_spawn_after_campfire`, `rotation_ambiguity_forfeit`, …), so `only=chicken-arc` logs `only='chicken-arc' matched 0 of 94 scenarios` and runs nothing; select one by its own substring (`only=F2_chest`). The whole track is `QUESTOWN_AUTOTEST_CATEGORY=chicken`, but on 2026-10-23 that single-server run failed F1-F4 and `stick_peck_and_follow_spawn` in the same second with `PLACE_FLAG: flag failed to initialize within 200 ticks` while each passed alone and in the sharded suite. Treat that instant init failure as a harness flake (the shard runner shows it too, on a shard's first tests) and confirm the chicken track with `tools/autotest-parallel.sh --shard 4`.

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

**Default raised to 1000 t/s (2026-10-19).** Scenarios are tick-budgeted, so the useful target
is "as fast as the box can": measured on `village/day1` (90 000 ticks) — 200 → ~452 s scenario
time; 400 → 318 t/s effective, 283 s; 1000 → **371 t/s effective (the ceiling), 242 s**; all
checks green at each rate. Full suite at 1000: 72/75, only the known reds (arborist flake,
roof_gatherer, skip_chicken_command), 334 s wall. `Can't keep up` lines appear at every rate
(4 at 200, 9 at 1000) and only cost wall time. The 2026-10-15 notes below (the "200 for zero
stalls" recommendation) predate this measurement. `-Dquestown.autotest.tickrate` is forwarded
to the forked server only since `c756aefe` — before it, the override was silently ignored.

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

**The force-load takes effect late.** The chunks start ticking some time
after `setChunkForced`, and on a loaded box (two suites at once) that took
~3 s: at 1000 t/s the first scenarios' 200-tick "Town flag failed to
initialize" wait ran out before the flag's chunk ticked. The suite now waits
first: `[autotest] Force-loaded chunks ticking after N ticks (M ms)`, or after
60 s of wall clock an ERROR `... still not ticking ...; starting anyway`.

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
its own footprint first.** 2026-10-29: the footprint alone isn't enough. Terrain
left around the room walled off the far side of the door, and the detector
registered that region as the door's room (`deadDoors={}; rooms=2`, about 1 run
in 3). Both builders now call `flattenAroundPostSpawnRoom` (the footprint plus a
3-block margin through `TestArenaPreparer.flatten`); 6/6 and 3/3 after. Accept-expected rooms (`floating_chest_room`,
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

Before comparing a red against another version or commit, read the failing scenario's own
block (from its `=== Test k/n: <name> ===` line to the next) for `ERROR` lines. A comparison
says whether a red is new, not why. `No work found for job ID: X. Falling back to any job in the
same root ID.` means the scenario's job isn't loaded. The organizer, arborist and explorer reds
of 2026-11-07..09 were jobs left out of the line with their scenarios still in the suite; a
scenario whose job root has no loaded jobs now fails at assignment with `No <root> jobs are
loaded`. One fallback is expected: `cook/simple_furnace_food` is a template that
`ResourceJobLoader` expands into per-recipe cook jobs, so its scenario runs on the root fallback.

When one fixture fails and a sibling fixture for the same job passes, compare their blueprints
(blocks, setup and post-spawn hooks) before bisecting the failing one. `nav/roof_gatherer` sat at
`NO_JOBSITE` for want of the welcome mat block that `welcomeMatBlueprint` places. A villager stuck
at `NO_JOBSITE` logs which check fails, sampled: `NO_JOBSITE job=… hasSupplies=… hasSpace=…
workable=…` (a missing job-site block shows as `workable=false because No rooms found with
workable blocks`).

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

## Doorway kitchen and diner guards (2026-10-29 overnight)

`cook/mutton [doorway_kitchen]`, `[rest_then_eat]`, `[raw_food_only]`: a 3x3 kitchen (walls
included; the furnace fills the one interior cell, so the cook works from the door cell) plus a
store room (`buildSupplyRoom(-6, -2)`). A `StarveAt` server-tick listener empties hunger mid-run
(daytime 1900 / 1600). `rest_then_eat` starts the cook in downtime and seeds its flag record at state
3 (the live cook's `_qtdev inspect`); `raw_food_only` has raw mutton only (no coal, no cooked food),
so the cooked-food diner expires and the raw-food diner must eat. All three passed on their first
run: they did not reproduce the dev's "stuck in going to eat" cook
(`.scratch/dev-playtest-2026-10-29-fresh/cook-stuck-going-to-eat.md`), which rules out the doorway
work spot (40323611), the downtime record on the flag, and the raw diner on a single chest.

## Mid-trip job switch (2026-10-29 overnight)

`hunter/sword [switch_after_trip]` and `[switch_now]`: a `SwitchWhenAway` server-tick listener waits
until the hunter is away (`isAwayOnTrip`), then calls `switchJob` to the other trip (sword_half_day,
or sword if already on it). After-trip passes when the job is unchanged right after the request and
changes later; now passes when it changes at once. Both also logged no `Clobbered time` ERROR.

`hunter/sword [longest_trip]` (2026-10-29 overnight): the hunter has sword / sword_half_day /
sword_full_day unlocked and starts at the board (seeking_work) at daytime 1000; its first trip must
be sword_full_day (`JobCycler`'s longest-trip pick).

`cook/mutton [no_kitchen]` (2026-10-29 overnight): a town with only a store room and a cook (cook
defaults + mutton unlocked, started at the board). A listener records the first tick a villager has
`getMissingRoom()`; it must be a kitchen (seen: questown:kitchen_small).


`cook/mutton [door_flaps]` (2026-10-29 overnight): the doorway kitchen; a server-tick listener counts
the kitchen door's closes and those with a villager within 1.5 blocks (a flap). Passes on 0 flaps
(vanilla OpenDoorGoal: 8 of 8 closes were flaps).

`crafter/stick [request_limit]` (2026-10-29 overnight): a store room with 5 bread, a bread request at
limit 4; after 100 ticks it must be paused, then the limit goes to 6 and after 100 more it must not be.
