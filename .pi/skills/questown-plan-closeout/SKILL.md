---
name: questown-plan-closeout
description: >
  Closes out a multi-session implementation plan in docs/plans/ so its status is
  honest and its stale rows stop reading as open work. TRIGGER when asked to
  "close out" / "finish up" / "ship" a plan, to mark a migration or multi-phase
  doc done, or when a plan's status/ledger rows disagree with what is actually
  verified. SKIP for a single-file fix (no plan), a live behaviour re-verification
  (load `questown-behavior-verify`), an MC version migration (load
  `questown-mc-version-migration`), or re-deriving the 1.19.2 legacy build.
  The one load-bearing insight it exists for: a status row is only as current as
  its last verification timestamp, and closing out re-runs the gate so "DONE" is
  an evidence-backed claim, not a copy of an old "pending" row.
---

# Plan close-out

Turn a plan that is functionally done into one whose **stated** state is
correct and re-verified. Two failure modes to avoid: marking it DONE without
re-running the gate (the "stale row reads as open work" stumble this skill
exists to kill), and marking it DONE with a branch rename / release you were
never told to ship.

## Step 0 — Pre-flight

- `pgrep -af '[r]unServer|[r]unClient'` — a live dev session contests the build;
  ask to kill it or wait before running any gradle task.
- Read the plan's frontmatter/status, its **test ledger**, its **open
  decisions**, and its **Phase** bullets. List every row that is `pending`,
  `in progress`, `WIP`, `not green`, `stub`, or `deferred` — that is the exact
  set of stale-status rows this close-out owes a verdict on.
- Do not trust the ledger's own "verified" claims yet. Each is a *claim*;
  close-out re-earns them.
- **A resumed WIP tree may hold tests that never went green.** If the
  close-out is picking up a plan mid-flight from a previous session, the
  working tree can contain tests that were *written but never run* (2026-10-10:
  `BreadOvenBlockEntityTest` was red 6/6 — `Registry is already frozen` at
  the BE constructor — when the resume session first ran `:test`). Step 1's
  gate re-run is what catches it; a plan doc's "P4 landed" is not evidence.

## Step 1 — Re-run the gates fresh (evidence, not up-to-date)

- `./gradlew build -x test` and confirm `compileJava`/`jar` report **`executed`**,
  not `up-to-date`. Pass `--rerun-tasks` when you just changed sources. A cached
  re-run proves nothing about the code you just touched.
- Then the full `:test`. Expected reds are **only** the deliberate `todo_…`
  registry-bound markers (see AGENTS "Build verification"; at the time of
  writing: `ChickenArcHintsTest`, `ServerJobsRegistryCanFitTest`,
  `WorkStatusStoreMatSeedTest`, `TestScenarioNamesTodoTest`). Any other red is a
  regression — stop and fix, do not close.
- For a filtered `:test --tests <Class>`, confirm the suite actually ran:
  `build/test-results/test/TEST-<Class>.xml` exists with the expected
  `tests="N"` and `failures="0"`.
- If a close-out fix touches a *behaviour* a unit test can't settle (a cap, a
  deposit, a live loop), drive the real autotest instead (`questown-held-run` /
  `questown-behavior-verify`) — the live run is the evidence, not the doc.

## Step 2 — Promote ledger rows only with a new evidence + date stamp

For each pending/`in progress` row, either:
- attach the fresh evidence from Step 1 (gate run + the *date*) and flip it to
  `verified`, or
- leave it and say exactly which gate still blocks it.

Never copy a "verified" word across from a neighbouring row. A status row with
no `verified (<date>: <evidence>)` stamp is, by rule, still `pending`.

## Step 3 — Settle "open decisions" against the code oracle

An "open decision" is a *question*. Answer it from the **code**, not from the
plan's own speculative "decide in Phase N" line: grep the class it describes,
read the call site, and record the chosen shape (e.g. "events = per-family
binder classes", "network = one `NeoEnvelope` over `StreamCodec`"). Re-verify
any one that the ledger already marked done — a doc that is stale twice in one
session is a placement problem, not a diligence problem.

## Step 4 — Classify every `deferred` / `dropped` / `not shipped` item

For each, state **why** and point at its home:
- **Intentionally deferred** (still in scope, later): name when it revives.
- **Dropped by dev decision** (out of scope, kept for the idea): point at the
  `.scratch/…` issue that carries it.
- **Unshipped by accident** (a gap): this is a finding — fix it now or open an
  issue; do not let it ride along as "deferred."

## Step 5 — Record scope/branch decisions, not implied ones

A plan being DONE does **not** mean the branch is release-ready, that the
branch should be renamed, or that a publish is owed. If the dev said "keep it
on `-wip`" / "not yet", say so in the frontmatter and in Step 4, so the next
session cannot misread "DONE" as "ship it." A scope decision the dev made
verbally is a fact that lives in the doc until it is re-told.

## Step 6 — Commit, then verify the commit is the one you think it is

- Stage explicit paths only. One `prep-commit` token per commit:
  `T=$(./scripts/gen-commit-token) && WORKFLOW_MD_CHECKED="$T" git commit -m "…"`
  (token expires in 30 min; env prefix, never `echo`-wrapped). Separate a
  pure-gate fix (e.g. unused imports) from the functional close-out commit.
- Confirm `git log --oneline -1` names the commit you meant and `git status
  --short` shows the tree is as you intend (pre-existing unrelated edits may
  legitimately remain unstaged — say so, don't sweep them in).

## What this session set (2026-10-09 migration close-out)

- Closed the 1.20.6/NeoForge plan: re-ran `build -x test` + full `:test`
  (green apart from the known `todo_…` reds), promoted every `pending`
  ledger row to `verified (date + evidence)`, settled all "open decisions"
  against the code oracle, classified Phase 3 as intentionally deferred and
  the journal as dropped-with-home. Found and fixed the one real gap it
  exposed — the flag BOP item-handler capability was never registered on
  Neo 1.20.6 (leaver deposit path + flag container-watch silently dead), plus
  the 1.20.6 `ItemStack.EMPTY`-singleton trap it masked. Live-verified via
  `flag/bop_boundary_63` + `flag/bop_full_64` (2/2 PASS). Recorded the
  dev-confirmed keep-on-`-wip` decision.
