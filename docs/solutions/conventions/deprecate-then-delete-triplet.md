---
title: "Deprecate-then-delete: the trio proves a deprecated method is safe to remove"
date: 2026-10-09
category: docs/solutions/conventions/
module: jobs / any
problem_type: convention
component: deprecation, deletion, refactor
severity: high
applies_when:
  - "You are about to delete a @Deprecated method or class"
  - "You are about to mark a method @Deprecated(forRemoval) in favour of a successor"
  - "You find a deprecated method and are deciding whether it is dead"
tags: [deprecation, forRemoval, successor, deletion, test-coverage, migration]
---

# Deprecate-then-delete — the triplet is the proof

**Rule:** a deprecated method is safe to delete only when **all three** of these hold:

1. **The marker is terminal:** `@Deprecated(forRemoval = true)` (not a bare `@Deprecated`).
2. **The pointer exists:** the javadoc names the successor class/method (e.g.
   `@deprecated Use {@code DeclarativeJobs.roomsWithState()} instead.`).
3. **The successor is tested:** a live test already exercises the successor's behaviour
   (it must *name the successor*, not just a test with a similar name).

Any one missing → the method is "deprecated-but-not-proven", not "delete-me". Do not
delete it; either finish the trio (migrate callers, re-point the test onto the successor) or
leave it.

**Why (evidence, 2026-10-09 declarative consolidation, C1):** `JobsClean.roomsWithState`
satisfied all three — `@Deprecated(forRemoval = true)`, a javadoc pointer to
`DeclarativeJobs.roomsWithState`, and `JobsCleanTest#roomsWithState_*` already calling the
*successor*. The triplet is what made "zero callers ⇒ pure deletion" a verified claim
instead of a grep guess. The converse trap is real: a *test named* after the method (`rooms
…`) pointing at the successor, while the method it appears to pin has zero callers — the
name match proves nothing; the *call target* is the evidence. This is the deletion-side
rule; `AGENTS.md` already carries the use-side rule (never call `forRemoval` methods in
new code).

## Applying the rule

- When **deleting**: verify (1)+(2)+(3) in that order; cite what satisfied 3 (suite + test
  name) in the plan/commit, not just "it was deprecated".
- When **deprecating a new method**: write all three at once, in the *same* commit as the
  successor implementation. A lone `@Deprecated(forRemoval)` with no successor is a promise
  with no body — do not make one.
- When **finding** a deprecated method with the marker but no successor or no re-pointed
  test: treat it as a *live migration with its first chunk missing*, scope it the way this
  repo scopes migrations (plan + chunks + gate), don't treat it as a stray to yank.
