---
title: Agent Chicken Verification Loop
type: convention
status: active
date: 2026-04-23
---

# Agent-driven chicken-arc verification loop

This doc tells an agent how to run the headless helper-chicken verification suite, read its log output, and triage common failures without asking a human to boot a dev client.

## Launch command

```sh
./gradlew runServer -Dquestown.autotest=true
```

That's it. The server boots, the fake player spawns at origin, `TestAllExecutor` runs every job blueprint, then `ChickenArcAllExecutor` runs every chicken scenario, and the runner halts with exit code `0` on all-pass or `1` on any fail.

Narrow to a specific track:

```sh
# Chicken only (~1-2 min)
./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=chicken

# Jobs only (full suite minus chicken; ~5 min)
./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=jobs
```

The env-var / system-property routing reuses the existing `TestBlueprintRegistry.getTestsByCategory(String)` plumbing. Values other than `chicken` filter the jobs track only; `chicken` filters the chicken track only; unset runs both.

## Log location + shape

Everything lands in `run/logs/latest.log`. Every line the runner cares about starts with `[autotest]`. The unified shape is:

```
[autotest] <track>:<name> [PASS|FAIL] <expectation>: <details>
```

Where `<track>` is either `jobs` or `chicken-arc`. The `AutotestLogFormatter` class is the single source of truth for this format; any change to the shape must go through there.

## Grep patterns

| What you need | Command |
| --- | --- |
| Final aggregate result | `grep '\[autotest\] RESULT:' run/logs/latest.log` |
| Per-scenario results | `grep -E '\[autotest\] (jobs|chicken-arc):[^ ]+ \[(PASS|FAIL)\]' run/logs/latest.log` |
| Only failures | `grep '\[autotest\].*\[FAIL\]' run/logs/latest.log` |
| Suite entry probes | `grep -E '\[autotest\] (BOOT_OK|jobs:SUITE_START|chicken-arc:SUITE_START)' run/logs/latest.log` |

A passing run ends with a line like:

```
[autotest] RESULT: 24/24 passed
```

A failing run ends with a line like:

```
[autotest] RESULT: 22/24 passed (FAILURES)
```

## Exit-code semantics

- Exit `0` — every scenario in both tracks passed.
- Exit `1` — at least one scenario reported `[FAIL]`, OR the runner timed out, OR the JVM crashed.
- Non-`0` with no `RESULT:` line — the suite was truncated; see triage below.

## Missing-output triage

If `grep '\[autotest\]' run/logs/latest.log` returns fewer lines than you expected, walk the probe list in order:

1. **`[autotest] BOOT_OK` is absent.** The server failed to start before `AutoTestRunner` could register its listener. Check the end of `run/logs/latest.log` for:
   - `java.net.BindException: Address already in use` → a prior JVM is still holding the port. `pgrep -a java` and kill the stale daemon, re-run.
   - `Failed to acquire lock on session.lock` → a stale `run/world/session.lock`. Verify no other Minecraft process is running, then delete the lock and re-run.
   - Gradle daemon crash → `./gradlew --stop`, re-run.
   - **Do not** move past this step asking a human. Each of the above is a recoverable environment issue the agent can fix in-loop.

2. **`[autotest] BOOT_OK` present, but `[autotest] chicken-arc:SUITE_START` absent.** The jobs track crashed before the chicken track started. Grep the log for the last `[autotest] jobs:<name>` line followed by a stack trace — that scenario broke the process. Fix the underlying bug (it's a jobs-track regression, not a chicken issue) and re-run.

3. **A `SUITE_START` line exists but no matching track `RESULT:` summary follows.** That track was truncated mid-run. Look for `OutOfMemoryError`, `ConcurrentModificationException`, or an uncaught exception near the last per-scenario line. Same pattern: fix the exception's root cause, re-run.

None of these branches hands back to a human. Each resolves to either "retry after clearing stale process / lock" or "fix code bug and re-run".

## Debugging a single failing scenario

Re-run with the category filter to narrow:

```sh
./gradlew runServer -Dquestown.autotest=true -DQUESTOWN_AUTOTEST_CATEGORY=chicken
```

(Single-scenario filtering beyond the category is not wired yet; the chicken suite is small enough — 13 scenarios — that running all of it and greping the fail line is sufficient.)

Keep the world directory around for post-mortem: the runner never deletes `run/world/`, so you can inspect block state via `/locate structure questown:empty_town` in a dev client against the same world afterward.

### Enable DEBUG logging

The server's default console level is `debug` per `build.gradle`, but most chicken-arc code logs at `.info()` (see `MEMORY.md`: "`QT.JOB_LOGGER.debug()` does NOT appear in `run/logs/latest.log`"). If you need `.debug()` lines, adjust `property 'forge.logging.console.level', 'debug'` in `build.gradle`'s `server {}` run config — it's already `debug` by default.

## The 13 chicken-arc scenarios

Registered in `ChickenArcBlueprintRegistry` at
`src/main/java/ca/bradj/questown/commands/test/ChickenArcBlueprintRegistry.java`.
Names are stable — the runbook greps them verbatim; CI integrations key off them.

| # | Name | Covers | Notes |
|---|------|--------|-------|
| 1 | `stick_peck_and_follow_spawn` | AE1 | Initial spawn + peck goal + `SynchedEntityData` icon |
| 2 | `F1_stick_to_campfire` | AE4 | Wand lights campfire → `SUNSET_AND_MAP` |
| 3 | `F3_build_room_to_welcome_mat` | — | Wall / door / sign / chest / welcome mat |
| 4 | `F4_seeds_to_statue` | AE5 | Seeds → statue handoff, chicken discards |
| 5 | `F1_rotation_clockwise_90` | — | Rotated-offset happy path |
| 6 | `rotation_ambiguity_forfeit` | — | Two campfire anchors → forfeit |
| 7 | `rotation_zero_match_forfeit` | — | Zero anchors after `MAX_RETRY_TICKS` |
| 8 | `two_flags_independent_arcs` | AE6 | Per-flag independence (setup-gap: needs executor second-flag support) |
| 9 | `skip_chicken_command` | AE7 | `/qt flag place_above skip-chicken` |
| 10 | `forfeit_remove_command` | — | `/questown chicken remove` |
| 11 | `first_gather_worldly_seeds_realtime` | AE5 | Gatherer loot guarantee, no warp (setup-gap: needs villager-spawn action) |
| 12 | `first_gather_worldly_seeds_warp` | — | Warp parity with scenario 11 (setup-gap as above) |
| 13 | `wand_on_unlit_campfire_outside_flag` | AE4 (no-op) | Radius-gate rejects click |

Scenarios 8, 11, 12 currently emit a `[FAIL] setup` line because the `ChickenArcTestExecutor` lacks the actions they need (second flag, villager spawn). They're registered under their canonical names so future work can flip them to passing without touching the runbook.

## When to re-run

- **After any change to `ChickenArcController` / `ChickenArcTransitions` / `ChickenArcConditions` / `ChickenStatueTransformHandler` / `ChickenArcLootGuarantee`** — the scenarios directly exercise these.
- **After any change to `HelperChickenSpawnController` or `HelperChickenRotationDetector`** — the spawn and detect scenarios will surface regressions.
- **After any change to `TownWand`** — scenarios 2, 10, 13 touch the wand surface.
- **After any change to `empty_town.nbt` or `ChickenScaffoldingLayout`** — the scaffolding placement is a precondition for rotation detection and room registration.

## Related docs

- `docs/conventions/editing-empty-town-nbt.md` — editing the structure file itself + agent-authored path.
- `docs/plans/2026-04-23-001-feat-chicken-arc-agent-automation-plan.md` — this runbook's authoring plan.
- `docs/plans/2026-04-22-001-feat-helper-chicken-onboarding-plan.md` — the parent chicken-arc implementation plan whose verification hole this runbook closes.
