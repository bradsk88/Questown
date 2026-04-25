---
title: "Two-layer fix for cross-scenario entity pollution in shared-world test harnesses"
date: 2026-04-24
category: docs/solutions/design-patterns/
module: AutoTest harness / chicken-arc track
problem_type: design_pattern
component: testing_framework
severity: high
applies_when:
  - A headless test runner executes multiple scenarios sequentially in a single shared ServerLevel
  - Mobile entities spawned in one scenario can wander outside the teardown AABB before the next scenario resets
  - Read-side queries (count, pick-first) scan a spatial AABB and do not filter by scenario ownership
tags: [autotest, entity-pollution, sequential-scenarios, shared-world, cleanup, owner-filter]
---

# Two-layer fix for cross-scenario entity pollution in shared-world test harnesses

## Context

The headless `AutoTestRunner` drives chicken-arc scenarios sequentially inside a single `ServerLevel`. Between scenarios the `RESET_ARENA` phase calls `TestArenaPreparer.destroyNearbyFlags` and `TestArenaPreparer.flatten`, both bounded to the arena's `halfWidth` AABB. `HelperChickenEntity` instances spawned in one scenario have active AI goals (`HelperChickenFollowNearFlagGoal`, `RandomLookAroundGoal`) that let them wander 20+ blocks from origin. By the time the next scenario's reset fires, one or more "strangler" chickens from the prior scenario may be outside the bounded AABB and therefore survive the teardown.

Those stranglers then corrupt the next scenario in two ways:

1. `ChickenArcResultChecker.checkChickenEntityState` called `level.getEntitiesOfClass(HelperChickenEntity.class, area)` and counted every live entity in the arena bounds. A wandered-in strangler inflated the count, producing a spurious `chicken spawned: live count >= 1` PASS when the scenario expected no chicken.
2. `ChickenArcTestExecutor.handleRightClickChicken` picked the first entity returned by the same query. A strangler could be that first match, so the interaction targeted the wrong entity and the scenario's scripted action silently misfired.

Both failures were intermittent and order-dependent, appearing only when a prior scenario spawned a chicken that wandered far enough before the next scenario checked.

## Guidance

Neither fix alone is sufficient. Apply both layers.

### Layer 1 — Wide kill-sweep at scenario teardown

The teardown AABB must be large enough that no mobile entity spawned during the scenario can escape it within the scenario's lifetime. For `HelperChickenEntity` (which has an active follow-goal and random-look goal) a 128×48×128 sweep (±128 x/z, −16 to +32 y) around origin is used. Switch from `entity.kill()` to `entity.discard()`: `kill()` fires death events and can drop loot, creating new entities that must themselves be cleaned up; `discard()` removes the entity deterministically with no side-effects.

```java
// TestArenaPreparer.destroyNearbyFlags
if (options.killHelperChickens()) {
    AABB wide = new AABB(
            origin.offset(-128, -16, -128),
            origin.offset(128, 32, 128)
    );
    for (HelperChickenEntity e : level.getEntitiesOfClass(HelperChickenEntity.class, wide)) {
        e.discard();   // deterministic; no death events, no loot drops
        chickens++;
    }
}
```

Before this fix the sweep was bounded to `halfWidth` (typically 7–12 blocks), matching the `flatten()` AABB. That is far too small for any entity with active locomotion.

### Layer 2 — Owner-filter on every read-side query

Every spatial query that counts or selects `HelperChickenEntity` instances must additionally filter by `flagPos.equals(e.getOwnerFlagPos())`. `HelperChickenEntity.getOwnerFlagPos()` returns the `BlockPos` of the flag the entity is bound to, set at spawn time by `HelperChickenSpawnController`. A strangler from a prior scenario retains its original `ownerFlagPos` and is therefore excluded from the current scenario's queries regardless of its physical position.

```java
// ChickenArcResultChecker.checkChickenEntityState — owner-filtered count
int live = 0;
for (HelperChickenEntity e : level.getEntitiesOfClass(HelperChickenEntity.class, area)) {
    if (!e.isAlive()) {
        continue;
    }
    if (flagPos.equals(e.getOwnerFlagPos())) {   // excludes stranglers
        live++;
    }
}
```

```java
// ChickenArcTestExecutor.handleRightClickChicken — owner-filtered pick-first
HelperChickenEntity chicken = null;
for (HelperChickenEntity e : level.getEntitiesOfClass(HelperChickenEntity.class, area)) {
    if (e.isAlive() && flagPos.equals(e.getOwnerFlagPos())) {
        chicken = e;
        break;
    }
}
```

Before this fix both methods used `level.getEntitiesOfClass(...).get(0)` or a plain loop with no ownership check.

### Why each layer alone fails

| Layer applied | Failure mode that remains |
|---|---|
| Wide sweep only | Stranglers outside even the wide bounds (edge cases, very long scenarios) still survive; more importantly, read-side code still has no ownership semantics — any future AABB widening regression reintroduces the bug silently |
| Owner-filter only | Stranglers accumulate in the world across all scenarios, consuming entity slots, potentially interfering with block-update ticks, and making log output noisy. The world is never actually clean between scenarios. |
| Both | Scenarios start with a clean world AND read-side code is robust against any entity that escapes teardown |

## Why This Matters

Sequential-in-one-level test harnesses trade isolation for speed and simplicity (no level reload between scenarios). That trade-off is valid but it shifts the responsibility for isolation onto the harness itself. Without both layers, test results become order-dependent: a scenario that passes in isolation may fail or spuriously pass when run after a scenario that leaves mobile entities behind. The `41/41 ALL PASS` result is only reliable when both layers are present.

The `discard()` vs `kill()` distinction matters independently of the scenario-pollution problem: using `kill()` in teardown code can trigger game logic (drops, advancements, statistics) that pollutes the next scenario's item counts or flag bits even when the spatial escape problem is not present.

## When to Apply

- Any test harness that runs multiple scenarios sequentially in a shared `ServerLevel` (or equivalent shared world state).
- Any scenario that spawns entities with active AI goals (locomotion, random wander, follow) that can move the entity out of a bounded cleanup AABB within the scenario duration.
- Any read-side query (`count`, `pick-first`, `find-nearest`) over a spatial AABB where the result must be scoped to the current scenario, not the entire world.

## Examples

### Full teardown call-site (chicken-arc track)

```java
// ChickenArcTestExecutor.resetArena — both layers active
TestArenaPreparer.PreparerOptions opts = new TestArenaPreparer.PreparerOptions(
        halfWidth, true, true, true   // killHelperChickens=true
);
TestArenaPreparer.destroyNearbyFlags(level, origin, opts, fakePlayer);
TestArenaPreparer.flatten(level, origin, opts);
```

The `PreparerOptions` record makes the opt-in explicit and keeps the jobs track unaffected (the jobs track's defaults pass `killHelperChickens=false`).

### Owner field on the entity

```java
// HelperChickenEntity
@Nullable
private BlockPos ownerFlagPos;

@Nullable
public BlockPos getOwnerFlagPos() {
    return this.ownerFlagPos;
}

public void setOwnerFlagPos(@Nullable BlockPos pos) {
    this.ownerFlagPos = pos;
}
```

The field is set at spawn time and never mutated during the entity's lifetime, so it is a stable identity token across all scenarios.

## Related

- `src/main/java/ca/bradj/questown/commands/test/TestArenaPreparer.java` — Layer 1 implementation
- `src/main/java/ca/bradj/questown/commands/test/ChickenArcResultChecker.java` — Layer 2 on count query
- `src/main/java/ca/bradj/questown/commands/test/ChickenArcTestExecutor.java` — Layer 2 on pick-first query
- `src/main/java/ca/bradj/questown/mobs/helperchicken/HelperChickenEntity.java` — `ownerFlagPos` field
- Parent plan: `docs/plans/2026-04-23-001-feat-chicken-arc-agent-automation-plan.md`
