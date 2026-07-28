---
title: organizer/fetch [warp] passes in isolation but fails in the full suite
status: needs-triage
created: 2026-07-22
priority: p2
---

## Context

Found while verifying an unrelated change (job-proficiency parity gate). Confirmed on a clean
worktree at `db6c74dd`, so it is **not** caused by that work.

| Run | Result |
| --- | --- |
| `only=organizer`, HEAD baseline | 2/2 PASS |
| `only=organizer`, working tree | 2/2 PASS |
| full suite, HEAD baseline | 54/55 — `organizer/fetch [warp]` FAIL |
| full suite, working tree | 55/56 — same single failure |

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
