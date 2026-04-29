# Triage labels

This repo uses local markdown issues (see `docs/agents/issue-tracker.md`), so "labels" are written as the `status:` field in each issue file's YAML frontmatter — not via a label API.

## Canonical roles

The five triage states are the defaults; no overrides:

| Role               | String              | Meaning                                                               |
|--------------------|---------------------|-----------------------------------------------------------------------|
| Needs triage       | `needs-triage`      | Maintainer needs to evaluate. Default for new issues.                 |
| Needs info         | `needs-info`        | Waiting on the reporter to clarify or provide a repro.                |
| Ready for agent    | `ready-for-agent`   | Fully specified; an AFK agent can pick it up with no human context.   |
| Ready for human    | `ready-for-human`   | Needs human implementation or judgment.                               |
| Won't fix          | `wontfix`           | Will not be actioned. Kept as historical record.                      |

## How skills apply them

Update the `status:` line in the issue's frontmatter. Do not rename, move, or delete the file when changing status — only edit the frontmatter.

When a status change has a non-obvious reason, append a short note to a `## Triage notes` section in the body so future readers understand the decision.

## No GitHub label API

Skills should not call `gh label` or `gh issue edit` for this repo. The `gh` CLI is not assumed to be installed.
