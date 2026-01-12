# Questown Project Guidelines

## Development Approach

- Use TDD: test low-level behaviors before game integration testing

## Naming Conventions

- `MAX_DOWNTIME_TICKS` is actually "downtime ticks" (the number of ticks a villager stays in downtime). "Max" refers to player override capability via work requests, not an upper bound.
- Avoid generic names like "DynamicWarper" - prefer descriptive names that explain what the class does (e.g., "PostDowntimeWarper" for a warper that handles post-downtime job resolution).

## Time Warp System (as of 2026-01-11)

### Status: WORKING

Time warp simulates villager work when the town is unloaded (player leaves) or when triggered manually for testing.

Both crafter and gatherer warp scenarios are working correctly:
- Crafter: 17 bowls vs 18 real-time
- Gatherer: Proper food consumption (1 food per trip)

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
