---
title: 'Known gather results ignore the loot-table prefix: hunters and miners "produce" wheat seeds'
status: needs-triage
origin: 2026-10-22 agent playtest (fresh world, 1 gatherer, first work request)
---

# What the player sees

Requesting wheat seeds at the job board with one gatherer in town: "There are 20 known jobs
which produce this item", with sword, golden-sword, axe, coal and boots job icons. The data
says 5 jobs make seeds (`python3 scripts/job-producers.py minecraft:wheat_seeds`: the 3
no-tool gatherers plus 2 farmer jobs). The `hunter_sword`, `miner`, `gatherer_axe` and
`gatherer_shears` loot tables contain no wheat seeds (grep of `data/questown/loot_tables/jobs/`).

# Cause (read in code, not yet run)

`KnowledgeStore.getAllKnownGatherResults(mapBiomes, ltPrefix)` never reads `ltPrefix`. It
returns the base floor (wheat seeds) plus everything known in the town's biomes from every
prefix. `ResourceJobLoader.biomeDesc` and `NewLeaverWork` use it as the results function of
each `biome_loot` job, so all 16 biome-loot jobs claim seeds, and each claims what the other
prefixes have found.

The no-arg overload does tie the floor to `GathererTools.NO_TOOL_TABLE_PREFIX`, and
`VillageSelfSufficiencyExecutor.seedGatherKnowledge` seeds hunter meat under the
`jobs/hunter_sword` prefix, so prefix-scoped knowledge looks like the intent.

# Why not fixed autonomously

The same results function is the known-gather gate on a leaver's extract (see the
`seedGatherKnowledge` Javadoc). Scoping it per prefix changes what hunters, miners and tool
gatherers bring back on a fresh town, which is economy design. Questions for the dev:

1. Should the wheat-seed floor apply only to the no-tool gatherer tables (`jobs/gatherer_notool`
   and `jobs/gatherer_notool_full`)?
2. Should known loot be scoped to the prefix that found it? If so, how does a fresh town's
   hunter discover its first meat (today the floor/cross-prefix leak may be what lets it)?
3. Or keep the engine as-is and narrow only the player-facing count
   (`ServerJobsRegistry.getJobsKnownToProduce`)?

The work seeker may also pick a hunter for a seeds request, since it reads the same
results. Not observed live yet.
