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

Vanilla server TPS is fixed at 20; there is **no config knob to raise it past
20**, so there is **no "faster than realtime" / increased-TPS lever**.
`warpTime` does not run ticks faster — it skips them and simulates the end
state, so it **bypasses** the realtime behavior a realtime proof depends on and
cannot substitute for it. To prove a realtime behavior you run real ticks (the
realtime watch); `warpTime` is the skip-and-simulate end-state tool behind the
single-job tests (`cook`/`farmer`).

## Related

- Per-scenario depth + the realtime/warp reasoning: `COMPLEX.md` (same dir).
- Evidence standard (server log, not pixels) and reading a "stuck at roster 0"
  run: the `questown-behavior-verify` skill.
- Held-run launch order and teardown: the `questown-held-run` skill.
