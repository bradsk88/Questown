# Decision: Declare Effects Locally, Collect Globally

**Date:** 2026-02-12
**Status:** Active
**Revisit when:** A global rule needs to outlive its declaring
job, or rule ordering becomes significant

---

## Context

During time warp, some world-level effects (crop growth,
furnace smelting) need to run between villager steps. These
effects are logically associated with specific jobs (farmers
grow crops, cooks run furnaces), but must execute at the town
level — once per tick, not per-villager.

We needed to decide where these global effects are declared
and how they're collected for execution.

---

## Decision

Global warp effects are **declared in each job's JSON** as
entries in the `global` special rules list. At warp time,
`TownFlagState` iterates active villagers, collects their
jobs' `getSpecialGlobalRules()` into an `ImmutableSet`, and
passes the deduplicated set to `WarpTickHook`.

No central registration of global effects at the town level.

---

## Why

1. **Adding a new effect is local.** Implement the rule,
   register it, add it to relevant job JSONs. No town-level
   wiring changes.

2. **Effects disappear with their jobs.** If a job type has
   no active villagers, its global effects don't run. No
   stale registrations.

3. **Natural deduplication.** `ImmutableSet` ensures
   `crop_growth_warp` runs once even if 5 farmers declare it.

---

## Risks

**Orphaned effects.** If all farmers die, crop growth stops —
planted crops stall. This is arguably correct lore ("no one's
tending the crops") and is consistent with the philosophy
documented in `population-based-crop-growth.md`.

**Effects tied to assignment, not action.** A cook who inserts
food into a furnace then switches to a different job phase
loses their global rule declaration. The furnace smelting
effect won't run during warp even though the furnace is
actively cooking. See `docs/bugs/cook-global-rule-dropped.md`.

**Rule ordering.** `ImmutableSet` doesn't guarantee execution
order. If two global rules interact (e.g., one grows crops,
another harvests mature crops), results depend on iteration
order. Acceptable today — we only have one global rule
(`crop_growth_warp`).

**Collection cost.** Every warp callback iterates all villagers
and resolves their jobs. Cheap with the current `ImmutableSet`
of strings, but `Works.get()` runs per villager. Acceptable
at current town sizes.

---

## Current status

- Only one global rule exists (`crop_growth_warp`)
- Crop stalling when farmers die is defensible as intended
  behavior
- Rule ordering is irrelevant with a single rule
- The cook issue is a real bug but affects a job category
  that hasn't been migrated to QTWorldAccess yet — see the
  bug doc for the proposed fix

---

## Migration Path (When Needed)

If effects need to outlive their declaring job, collect rules
from **all jobs sharing a root** with each villager's current
job, not just the current job. This captures the full job
family (e.g., all `cook/*` variants) so that a global rule
declared by any phase of the cook job runs as long as the
villager is a cook.
