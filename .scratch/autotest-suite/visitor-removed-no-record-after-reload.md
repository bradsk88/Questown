---
title: 'Reload race: "Visitor mob''s parent has no record of entity. Removing visitor."'
status: needs-triage
origin: 2026-10-19 day1 autotest (worktree, uncommitted fixes on dd7a43f5), 1 of ~13 day1 runs
---

`/tmp/claude-1000/-workspace-IdeaProjects-Questown/927d3466-6b45-494d-b2de-2e0997372aa0/scratchpad/day1-tick-3.log` 23:56:39: right after the overnight-absence
reload ("flag and 1 villager(s) back after 46 ticks"), ERROR (×2) "Visitor mob's parent has no
record of entity. Removing visitor. uuid=be812007-… handleSize=1" for Villager 1 (the BOP'd
future hunter). Logged from `SimpleVillagerHandle` (~L529). day1 tripped `no-questown-errors`.
Not seen in the other ~12 runs today. Not investigated.
