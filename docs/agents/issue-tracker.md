# Issue tracker — local markdown

Issues for this repo live as markdown files in the `.scratch/` directory at the repo root. There is no GitHub Issues integration; the `gh` CLI is not used by these skills here.

## Layout

```
.scratch/
  <feature-slug>/
    <issue-slug>.md
    <issue-slug>.md
    ...
```

- One directory per feature or epic. The directory name is a short kebab-case slug (e.g. `warp-night-handling`, `chicken-arc-cleanup`).
- One markdown file per issue inside the feature directory.
- File names are kebab-case slugs derived from the issue title.

## Issue file format

Each issue file is a markdown document with YAML frontmatter:

```markdown
---
title: Short imperative title
status: needs-triage
created: 2026-04-29
---

## Context

Why this issue exists. Link to plan, PRD, or conversation if relevant.

## Acceptance criteria

- Bulleted, testable conditions for "done".

## Notes

Free-form. Investigation log, links, gotchas.
```

Required frontmatter:

- `title` — human-readable, imperative.
- `status` — one of the canonical triage roles. See `docs/agents/triage-labels.md`.
- `created` — ISO date (`YYYY-MM-DD`).

Optional frontmatter the skills may add:

- `priority` — `p0` / `p1` / `p2`.
- `assignee` — `human` or `agent` (only meaningful once status is `ready-for-*`).
- `parent` — slug of a parent issue, when this is a sub-task.

## Skill behavior

- **`to-issues`** — given a plan or PRD, splits it into vertical-slice issues. Creates the feature directory under `.scratch/` and writes one file per slice. New issues default to `status: needs-triage`.
- **`triage`** — reads issues, evaluates them, and updates the `status:` field in frontmatter. Adds a `## Triage notes` section with the reasoning. Does not delete issues; `wontfix` is just a status.
- **`to-prd`** — writes the PRD to `.scratch/<feature>/PRD.md` (uppercase) so it sorts above the per-issue files. PRD files have no `status` frontmatter.
- **`qa`** — discovers issues by walking `.scratch/` and reading frontmatter. Filters by `status` to find work ready for review or ready for an agent.

## Git tracking

`.scratch/` is intentionally *not* gitignored — issues are part of the project history. If you want a scratchpad that doesn't get committed, use a different directory.
