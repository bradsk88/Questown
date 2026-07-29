---
title: Session handoff — design grilling + flag-tick perf investigation
status: ready-for-human
created: 2026-07-27
closed: 2026-07-29
priority: p1
---

> **CLOSED 2026-07-29.** The perf work here is done and committed (`1f066b04`, `4a07c0ac`, plus the
> config fixes `2c2cf80b`/`83efc333`). The live thread moved on to the legibility pass — see
> [[../legibility-2026-07/HANDOFF]]. Kept for the measurements, the reasoning, and the two perf
> leads that are still open (`possibleWork`, `updateStoredData`).

# Handoff

Two threads ran in one session: a **design grilling** (what interferes with player enjoyment)
and a **perf investigation** it produced. The design half is fully captured in committed-shaped
docs; the perf half is half-done and is the next task.

**UPDATE 2026-07-27 (later session): the next task is done.**
[[updateworkstatuses-is-64pct-of-tick-time]] is **fixed** (that phase: 5314us → 138us avg; whole
flag tick p99 14668us → 2704us, max 29562us → 5955us), including the timer-decay correctness bug.
[[possiblework-spikes-to-7ms-behind-a-0us-p95]] is **improved, not closed** (max 8197 → 4632us);
its remaining cost is the per-job town-container scan, not the scheduling. Both issue files carry
the numbers and the reasoning. The uncommitted-state list in section 3 below is now larger — see
`git status`.

The largest phase is now `roomsHandle` (219us avg, 57% of tick time) and the largest single spike
is `updateStoredData` (6.6ms max). Neither has been investigated.

## 1. Design decisions (captured — read these, don't re-derive)

- `CONTEXT.md` (modified) — new/updated terms: **Pressure**, **Injury**, **Onset-shaped vs
  forecast-shaped**, **Blight**, **Pressure raises demand**, **Post office / Letter**,
  **Milestone**, **Mood**, **Proficiency owns speed; mood owns downtime**.
- `docs/adr/0012-threat-as-pressure-onset-shaped-no-combat.md` (new).

Headlines, in case you only read one thing:

- Questown's reference is **Dragon Quest Builders**, minus its combat waves. Adversity is
  **threat-as-pressure**: the response is the same verb as the core loop (supply the town),
  not a combat mode.
- Loss layers: **injury** + **demand-side buffer loss**. NOT roster loss, NOT skill decay,
  NOT item spoilage (Questown does not own the vanilla items in vanilla chests).
- Anticipation lives in an event's **onset** (blight spreads, cold snaps ramp), not a calendar.
  No seasons — that's a seasons mod's job. The preparation fantasy comes from the **post
  office** instead (letters = Stardew-style deadline orders; they pay items/villagers/knowledge,
  never BOPs).
- **Proficiency owns work speed; mood owns downtime.** Mood becomes a multi-input town-quality
  score with a breakdown tab on the villager UI.
- Arc = **named milestones**, not a DQB town level.
- **Sequencing agreed:** the "nothing in this mod is silent" legibility pass ships BEFORE any
  new systems — `WANDER_GIVEUP_TICKS` 2000→~200, deed-consumption dupe bug, craft-button
  disable/feedback + crafting-tab layout, dead-door failure message, then **need bubbles**
  (the P0). Then injury activation → blight → post office.

## 2. Perf investigation (done)

Full findings + numbers in the agent memory note `perf-flag-tick-profile-2026-07`. Summary:

**Questown is not a TPS problem; it is a stutter problem.** At the config players actually run
(`FlagTickInterval = 10`), a 20-townie / 16-room town measures **avg 793us, p95 9.4ms,
p99 13.5ms** per game tick — one town eating ~19% of the whole server budget one tick in twenty.
p50 is 18us, so averages hide it completely.

### Tooling built (new, reusable)

- `ca.bradj.questown.town.TickProfile` — phase-bucketed profiler, **disabled by default**
  (`TickProfile.INSTANCE.enable()`), microsecond resolution, avg/p50/p95/p99/max.
- `AbstractTownFlagTicker` — profiling now wraps **all** tick exit paths (try/finally) and each
  phase is timed via `TickProfile.INSTANCE.phase(name, body)`.
- `perf/town_small` + `perf/town_large` autotest scenarios (category `perf`), run with
  `-Dquestown.autotest.only=perf`. They are **measurements, not gates** — empty expectation,
  always pass, print the timing table.

### Fixes applied and verified (58/58 autotest PASS)

1. **Container-scan throttle.** New `ContainerScanInterval` config (default 10);
   `TownFlagState.tick` early-returns via `isDueForContainerScan()` instead of scanning every
   game tick. `updateStoredData` went **44% → 12%** of tick time (187us → 96us avg). This was
   the room-scaling growth risk; it is defused.
2. **Dev world aligned to the shipped default.** `run/world/serverconfig/questown-server.toml`
   `FlagTickInterval` 100 → 10, so all local playtesting had been running the heavy path 10x less
   often than the code default. This fix did not improve performance — it **revealed** the stutter.

   **CORRECTION (2026-07-28):** that file had NOT been hand-edited. `Config.java` registered
   `ECONOMIC_RECORDS_DEPTH` under the key `"FlagTickInterval"`, colliding with the real one; the
   later `define` won, so **100 was the value written into every world's config** — all 25 worlds
   under `run/saves/` show `FlagTickInterval = 100` and no `EconomicRecordsDepth` key at all.
   Fixed: `ECONOMIC_RECORDS_DEPTH` gets its own key, and the flag-tick key is renamed
   **`FlagTickIntervalV2`** so the wrong value cannot persist. Forge never rewrites a valid entry,
   so fixing the collision alone would have pinned every existing world at 100 forever; a renamed
   key is absent from every toml, gets written at the current default, and the stale entry is
   dropped as unknown. Verified on a world holding `FlagTickInterval = 100`: after one boot it
   reads `FlagTickIntervalV2 = 10`. **So the "25 other worlds still at 100" caveat below is now
   moot** — every world migrates itself on next load.

   (Project convention, per Brad: when the value already written into people's worlds is wrong and
   should be re-defaulted, rename the key with a version suffix rather than write a migration.)

### Deliberately NOT done (decisions for the human)

- ~~**25 other worlds under `run/saves/` are still at `FlagTickInterval = 100`.**~~ Resolved by
  the `FlagTickIntervalV2` rename above — every world re-defaults itself to 10 on next load.
- **The shipped default was not raised to 100.** It would help perf but makes job work-statuses
  10x less responsive for every player — a gameplay call, not a cleanup. Still open, but the
  case for 10 is stronger now that the flag tick's p99 is 2.7ms instead of 14.7ms.

## 3. Uncommitted state

```
 M CONTEXT.md
 M src/main/java/ca/bradj/questown/commands/test/TestBlueprintRegistry.java
 M src/main/java/ca/bradj/questown/core/Config.java
 M src/main/java/ca/bradj/questown/town/AbstractTownFlagTicker.java
 M src/main/java/ca/bradj/questown/town/entity/TownFlagState.java
?? docs/adr/0012-threat-as-pressure-onset-shaped-no-combat.md
?? src/main/java/ca/bradj/questown/town/TickProfile.java
?? .scratch/perf-2026-07/
```

Plus `run/world/serverconfig/questown-server.toml` (gitignored, local only).

## 4. Harness gotchas (cost real time this session — don't repeat)

- `getMatches(x -> true)` counts **recipe-matched** rooms, not registered doors. A bare
  cobblestone shell matches nothing, so the first perf run reported `rooms=1` and silently
  measured nothing. Extra rooms need a chest.
- Profiling must wrap **all** tick exit paths. The original `profileTick` sat below the
  `isFlagTick` early return, so it measured only the every-Nth heavy tick — making an idle town
  look like it cost 5.3ms/tick when the true average was 172us. An early conclusion was wrong
  because of this.
- The arena carries **cross-run residue**: a previous run's flag keeps ticking (phase sample
  count was exactly half the total tick count), and its townies reload with
  `IllegalStateException: Town has not been initialized on TownVillagerSleepModule`.
- `runServer` exiting with **code 1 after `RESULT: N/N passed`** is the self-halting server,
  not a failure.
- Never run two `runServer`s at once (session.lock).
