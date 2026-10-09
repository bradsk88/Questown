---
name: questown-refactor-chunks
description: Runs a Questown refactor (moving classes/methods between packages,
  consolidating "clean" helpers into a canonical package) as verifiable chunks —
  take the baseline red set first, then move one family to one destination per
  chunk, re-point call sites per method (never per class prefix), gate each chunk
  on the identical known-baseline red set (not the exit code), and let the
  compiler name the misses. TRIGGER when asked to move/consolidate/relocate code
  between packages, de-legacy a helper class, or "finish the X migration" where X
  is a package consolidation. SKIP for adding a feature, a bug fix (load
  questown-unit-tdd-fix), an MC version migration (load
  questown-mc-version-migration), or deleting dead deprecated code (see
  docs/solutions/conventions/deprecate-then-delete-triplet.md for the
  deletion-side standard — that one IS a repo principle and stays in the repo).
  Codifies the chunk standard the 2026-10-09 declarative consolidation learned
  (the C4 slip: a per-class-prefix replace that mis-pointed 10 call sites, 5
  caught by the compiler and 5 by hand before the build).
---

# Refactor chunk standard ("moves are moves")

**Rule:** a move has four properties, and a chunk that breaks any of them is not yet a move.

1. **One destination per chunk.** One chunk moves one family into one home (one new or
   existing class). The chunk's diff is relocation + import changes + call-site re-points
   and *nothing else* — no signature edits, no algorithm edits, no drive-by renames. That
   is what makes the gate below meaningful.
2. **Re-point call sites per method, never per class prefix.** `JobsClean.` → `SupplyItems.`
   as a text replace is *exactly* the wrong tool: a legacy class usually holds *several
   families* destined for *different* new homes, so the prefix is the wrong key. Re-point
   each call site on the method it names (grep `JobsClean\.shouldTakeItem`,
   `JobsClean\.rooms(`, …) — the method name, not the class name, is the migration unit.
3. **Verify against the known-baseline red set, not "green".** Establish the baseline
   *before* the first chunk (e.g. 1056 tests, 43 skipped, 4 `todo_…` `forRemoval` reds).
   Each chunk's gate = **the same set, no more**. A chunk that introduces a 5th red has
   failed, even if the build "passes" in the sense of not crashing; a chunk that *fixes*
   one of the baseline reds has quietly changed scope and must be called out before it is
   celebrated.
4. **Let the compiler/test name the misses; don't pre-derive the import surface by hand.**
   The NeoForge migration already runs "let the compiler name each change; fix per category
   and re-run." Moves in *our own* codebase follow the same loop — run `compileJava`, read
   each `cannot find symbol`, fix that symbol (import added / reference re-pointed),
   re-run. Hand-constructing the import set before the compiler has spoken produces both
   missed references and *spurious* ones the next chunk then has to undo.

## Evidence (why this is a rule, not a preference)

2026-10-09 declarative consolidation, C4: the chunk's first re-point was a per-class-prefix
replace of `JobsClean.` → `SupplyItems.` across six files. It worked for C4's own family and
*mis-pointed the C5-family methods* (`getEntityCurrentJobSite`, `findJobSite`) in
`JobsCleanTest`, `DeclarativeJob`, `Jobs`, `DeclarativeJobTicker` — 10 call sites total; the
compiler named 5 (`DeclarativeJob:805`, `Jobs:182/206/218`, `DeclarativeJobTicker:523`) at
the next build and the other 5 (in `JobsCleanTest`) were caught and reverted by hand before
the build ran. They were re-pointed correctly in C5. The same loop, applied per-method up
front, would have produced the same final state in fewer build cycles and without the
intermediate red.

## Applying the rule

- Before the first chunk of a multi-family move, **take the baseline** (run the full gate
  once; record the exact red set) so chunk 1 isn't also the session that discovers it.
- **Chunk boundary = one destination.** `SupplyItems`, `DeclarativeJobs`, `the new
  WorkWorldInteractions` are three chunks, not one "move the jobs helpers" blob.
- **Quote the method in the grep, not the class.** `grep -rn 'JobsClean\.'` is a *discovery*
  tool (find every family's callers); `grep -rn 'JobsClean\.<method>('` is the *action*
  tool (re-point one family). Mixing them up is how the C4 accident happened.
- **The known-baseline red set is the acceptance test**, not the build's exit code — a
  green-with-5-reds build and a red-with-4-reds build are both "the gate failed" until the
  fifth red is explained.
- Cross-reference (repo-side, agent-agnostic): the *deletion* standard lives at
  `docs/solutions/conventions/deprecate-then-delete-triplet.md` — it stays in the repo
  because it is a property of the code (marker + pointer + successor-test), not a sequence
  the agent must run.
