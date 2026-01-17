# Questown Project Guidelines

## Development Approach

- Use TDD: test low-level behaviors before game integration testing

## Testing Workflow

- When the user wants to test in Minecraft, run: `ENABLE_DEV_COMMANDS=true ./gradlew runClient`

## Naming Conventions

- `MAX_DOWNTIME_TICKS` is actually "downtime ticks" (the number of ticks a villager stays in downtime). "Max" refers to player override capability via work requests, not an upper bound.
- Avoid generic names like "DynamicWarper" - prefer descriptive names that explain what the class does (e.g., "PostDowntimeWarper" for a warper that handles post-downtime job resolution).

## Utility Classes

- **Util** - Helper methods (legacy, may have Minecraft dependencies)
- **UtilClean** - Helper methods with no Minecraft dependencies, making it highly compatible with unit testing. Prefer this over Util.
- **Compat** - Applies the "strangler" pattern to isolate Minecraft version-specific code. This allows the mod to be easily back/forward ported between different Minecraft versions despite API changes.

## Game Design Notes

- Villager crafting recipes are intentionally more efficient than vanilla Minecraft recipes. This is a gameplay feature - villagers are meant to be better crafters than players. For example, the bowl recipe is 2 planks → 3 bowls (vs vanilla 3 planks → 4 bowls). Always check the job JSON files in `src/main/resources/data/questown/questown_jobs/` for actual recipes.

## Time Warp System (as of 2026-01-14)

### Status: WORKING (with known limitations)

Time warp simulates villager work when the town is unloaded (player leaves) or when triggered manually for testing.

**Working correctly:**
- Crafter warp: Produces items at expected rate (~12 bowls in 10,000 ticks)
- Gatherer warp: Proper food consumption (1 food per trip)
- Job switching: Villagers switch between jobs of same root (e.g., bowl ↔ stick)
- Supply handling: No supplies dropped mid-cycle when switching jobs
- Downtime: Already accounted for in `totalDuration` calculation (no extra logic needed)
- Item recovery: If NO_SUPPLIES encountered, inserted items are recovered and returned to containers
- Dynamic work evaluation: Re-evaluate available work each tick, don't pre-compute at start (supplies change as villagers produce items)

**Known limitation:**
- Warp produces ~20% fewer items than real-time (12 vs 15 bowls in 10,000 ticks)
- Likely due to starting state differences; accepted as tolerable variance

**See `docs/time-warp-plan.md` for remaining steps (8, 10).**

### Key Solution

When a gatherer is "out gathering" (`WAITING_FOR_TIMED_STATE`) when warp begins, we must:
1. Complete their gathering trip (extract loot)
2. Drop loot to containers
3. Start new cycles with proper food consumption

The fix checks for BOTH non-supply items (loot) AND supply items (food) before forcing extraction:
```java
boolean villagerHasNonSupplyItems = inventory.hasNonSupplyItems();
boolean villagerHasSupplyItems = inventory.getSupplyItemStatus().values().stream()
        .anyMatch(supplyStatus -> supplyStatus == SupplyItemStatus.HAS_ITEM);

boolean needsFirstExtraction = initialStatus.isWaitingForTimers()
        && !villagerHasNonSupplyItems
        && !villagerHasSupplyItems  // Key fix: also check for supplies
        && !wouldCollectSupplies;
```

### Documentation

- `docs/time-warp-explained.md` - Human-readable explanation of time warp system
- `docs/warp-mid-work-scenario.md` - Technical details on the gatherer fix

### Key Files

- `AbstractDeclarativeJobWarper.java` - Core warp logic (lines 195-231)
- `DeclarativeJobs.java` - Creates warper, passes initialStatus
- `DeclarativeJobWarpingTest.java` - Unit tests

### To Test

1. Run `ENABLE_DEV_COMMANDS=true ./gradlew runClient`
2. Load world with gatherer villager
3. Put food (carrots) in container, note count
4. Wait for gatherer to leave town (status = WAITING_FOR_TIMED_STATE)
5. Trigger warp (via command or by leaving and returning to town)
6. Check: food consumed should = number of gathering cycles completed
