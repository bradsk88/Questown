---
title: Warp may score a job as unequipped when the worker is holding the only tool
status: needs-triage
created: 2026-07-22
priority: p2
---

## Context

Observed while building the job-proficiency parity gate (ADR-0010). Not caused by that work —
it reproduces on the farmer arena as shipped.

## Symptom

In a warp window, with the villager **holding** the town's only wooden hoe (visible in the
serialized journal), the job scorer rated the harvest job as unequipped and picked a different
job instead:

```
JobPossibility{jobID=farmer/harvest_wheat, score=0.004 because Highest possible job state: 0
  because Lacking tools for job state 0/1 (out of 1, ...)}
JobPossibility{jobID=farmer/compost, score=1.008 because ... All ingredients and tools available}
...
Prepared for farmer: [compost]
```

The villager then spent the warp composting rather than harvesting a field full of age-7 wheat.
Adding a second hoe to the town chest made the harvest job win and the warp behave as expected.

## Why it matters

If warp scores tool availability against **town containers only**, then whenever the worker is
carrying the tool (the normal steady state after they pick one up), offline production silently
switches them to a different job than the one they'd do live. That is a warp/realtime divergence
of the same family the proficiency gate exists to prevent, but wider: it affects any tool-gated
job, with no proficiency involved.

Unconfirmed: I did not read the scorer to verify the container-only hypothesis — the evidence is
behavioural (log above + the second-hoe fix). Worth confirming before acting.

## Where it currently hides

`TestBlueprintRegistry.proficiencyParityBlueprint()` works around it by adding a spare hoe to the
arena, with a comment pointing here. If this is fixed, that spare hoe can go.

## Repro

Run any tool-gated job through warp with exactly one tool instance, held by the worker, and read
the `job_possibilities_compute` debug lines.
