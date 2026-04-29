# Domain docs

## Layout

**Single-context.** This is a single Forge mod repo; there is no `CONTEXT-MAP.md` and no per-subproject contexts.

## CONTEXT.md

Not yet present at the repo root. Skills that read `CONTEXT.md` (e.g. `improve-codebase-architecture`, `diagnose`, `tdd`) should treat its absence as "no domain glossary available yet" rather than an error — they should fall back to reading code and existing docs.

If/when a `CONTEXT.md` is written, it lives at the repo root: `/CONTEXT.md`.

## ADRs

New architectural decision records go in `docs/adr/` as `NNNN-short-slug.md` (e.g. `0001-qttoolaction-closed-enum.md`). Skills should look there first for past decisions.

### Legacy decision docs

Pre-existing decision documents live at the root of `docs/`:

- `docs/decision-declare-and-collect-global-rules.md`
- `docs/decision-proportional-deltas.md`
- `docs/decision-qttoolaction-closed-enum.md`

These files are **not** moved. Skills should treat them as supplementary ADR-equivalent context when reasoning about historical decisions, but new ADRs should be authored under `docs/adr/`.

## Other domain context

The following directories contain useful supporting context that domain-aware skills may consult:

- `docs/INDEX.md` — index of project docs
- `docs/conventions/` — coding/architecture conventions
- `docs/solutions/` — pattern docs and learnings (see `ce-learnings-researcher`)
- `docs/plans/` — feature plans
- `docs/bugs/` — known-bug write-ups
