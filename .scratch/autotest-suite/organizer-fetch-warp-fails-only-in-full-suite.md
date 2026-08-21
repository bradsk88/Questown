---
title: organizer/fetch [warp] passes in isolation but fails in the full suite
status: needs-info
created: 2026-07-22
updated: 2026-08-21
priority: p2
---

## Triage 2026-08-21: one question for Brad, then ready

The evidence points at accumulating `run/world` residue (many-run horizon), and the proposed
fix is to have the harness start each suite from a clean world. Note hold-mode launch discipline
already wipes `run/world` manually before every run — this would just automate it for plain
suite runs.

**Question for Brad: should a plain autotest suite run wipe `run/world` at start (losing any
residue you might want to inspect after a failure), or should wiping stay a manual step?**

If yes: implement in the autotest harness/gradle task and this becomes ready-for-agent.

## Context

Found while verifying an unrelated change (job-proficiency parity gate). Confirmed on a clean
worktree at `db6c74dd`, so it is **not** caused by that work.

| Run | Result |
| --- | --- |
| `only=organizer`, HEAD baseline | 2/2 PASS |
| `only=organizer`, working tree | 2/2 PASS |
| full suite, HEAD baseline | 54/55 — `organizer/fetch [warp]` FAIL |
| full suite, working tree | 55/56 — same single failure |

## UPDATE 2026-07-28: it is nondeterministic, not merely order-dependent

Four more full-suite runs, same machine, same scenario order:

| Run | Tree | Result |
| --- | --- | --- |
| perf work | flag-tick perf fixes | 58/58 — **passed** |
| healing #1 | healing scale fix | 57/58 — FAIL |
| healing control | HEAD, change stashed | 58/58 — **passed** |
| healing #2 | healing scale fix (identical to #1) | 58/58 — **passed** |
| need bubbles | bubble slice | 57/58 — FAIL |
| need bubbles #2 | bubble slice (identical) | 57/58 — FAIL |
| need bubbles control | HEAD, bubble slice removed | 57/58 — **FAIL** |

Identical code passing and failing across runs (#1 vs #2) rules out pure order-dependence: run
order was the same every time. Something in the shared arena varies between runs — residue, entity
load, or a timing race in the fetch itself.

**It also appears to get worse the more the suite is run.** The greens above are all from earlier
in the day; the last three consecutive runs — two with a change, one with that change removed —
were all red. That is the shape of **accumulating** arena residue rather than a per-run coin flip:
`run/world` persists between runs, so each suite leaves state the next one inherits. First thing to
try is running the suite twice from a freshly wiped `run/world` and seeing whether run 1 is
reliably green and run 2 reliably red.

## UPDATE 2026-07-29: the wipe experiment ran — residue survives, the prediction was backwards

Ran exactly the experiment above: moved `run/world` aside, then two consecutive full suites on the
same tree (HEAD, `bb395e61`).

| Run | World | Result | `organizer/fetch [warp]` |
| --- | --- | --- | --- |
| 1 | freshly wiped | 57/58 | **PASS** |
| 2 | run 1's residue | 58/58 | **PASS** |

The prediction — run 1 green, run 2 red — is **refuted**: residue from a single prior suite is not
enough to make it fail. What *did* change is that this scenario had gone red on the last three
consecutive runs against a long-lived `run/world`, and wiping that world made it green twice. So
the residue story survives, but the accumulation horizon is **many** runs, not one.

**Cheap mitigation available now:** wipe `run/world` before a suite you intend to trust. That is
also the shape of a real fix — have the harness (or the gradle task) start each suite from a clean
world instead of inheriting whatever the last few runs left behind. Not implemented; it is a
harness/build change and wants Brad's call on whether a suite run should be allowed to keep state
at all.

Run 1's single failure was **not** this scenario — it was `gatherer:axe [warp]`, the separately
documented loot flake (`gatherer-warp-loot-flaky`), and run 2 passed it. Note the detail though:
`expected 0..14, got -1 (before=9, after=8)` — a **negative** delta, i.e. an item left the chest.
That is not the "not enough loot" shape the existing note describes and may be a different bug
wearing the same scenario's name.

**Cost paid so far:** this scenario has now forced three extra full-suite runs (~60 minutes) purely
to exonerate unrelated changes — a bisect that produced no information about the changes and only
confirmed the flake.

**This is now a signal-quality problem, not a curiosity.** Two of the last four full-suite runs
were red on this one scenario, so "58/58" is no longer a reliable gate, and every future change
has to argue its way past this failure before it can be committed. That cost is now larger than
the cost of fixing it.

Worth noting for whoever picks it up: the failing variant is **`[warp]`**, which does not run
entity AI at all (no brains, no pathing — warp goes through `TimeWarpWorldInteraction`). So the
cause is in warp-side fetch/container state, not in movement or behaviour code.

## Symptom

```
jobs:organizer:fetch [FAIL] CONTAINER: Some container-content expectations failed
  [FAIL] minecraft:emerald @ -4, 64, -1: expected >= 1, got 0 (before=0, after=0)
  [FAIL] minecraft:emerald @ 4, 64, -1: expected <= -1, got 0 (before=4, after=4)
```

The emerald never leaves the source chest: the fetch simply does not happen. Both the source and
destination deltas are 0, so this is "no work occurred", not a mis-delivery.

## Why it matters

It is **order-dependent, not flaky** — it reproduces in the full suite on two different trees and
passes in isolation on both. That means the suite's green/red signal for this scenario depends on
what ran before it, which is the same shared-arena residue class of bug recorded in
`treefeature-place-fails-in-autotest-arena` and the relocation cross-scenario note.

Risk: because the scenario passes when targeted, anyone verifying organizer work with
`only=organizer` gets a false green.

## Suspected cause (unverified)

Residue from an earlier scenario in the shared arena — leftover entities, claimed workspots, or a
room/container registration that makes the organizer's request resolve to a chest that is not the
one the expectation watches. Worth diffing the container/room state at scenario start between the
isolated and full-suite runs.

## Repro

```sh
./gradlew runServer -Dquestown.autotest=true                      # fails
./gradlew runServer -Dquestown.autotest=true -Dquestown.autotest.only=organizer   # passes
```
