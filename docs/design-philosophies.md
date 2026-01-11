# Questown Design Philosophies

This document captures architectural patterns and design decisions learned from debugging and development sessions. It serves as a reference for understanding why the code is structured the way it is.

## Core Architectural Patterns

### 1. Immutable State Pattern (MCTownState)

The town state is immutable. All modifications return new state instances.

```java
// Correct: Chain modifications
TOWN newState = oldState.withVillagerData(index, newVillager);

// Incorrect: Attempting to mutate
oldState.villagers.set(index, newVillager);  // Won't work
```

**Key implications:**
- State must be "threaded" through operations - each step receives the previous step's output
- When a method returns null, you must NOT use that null to overwrite accumulated state
- Test doubles that bypass immutability (e.g., directly manipulating inventory) can hide state-threading bugs

### 2. State Threading in Multi-Step Operations

When performing multi-step operations (like extracting multiple items), state must be passed from each step to the next:

```java
// Correct: Thread state through iterations
TOWN ts = getTown(inputs);
for (ITEM item : items) {
    TOWN hookResult = postExtractHook(inputs, item);
    if (hookResult != null) {
        ts = hookResult;  // Only update if non-null
    }
    ts = setHeldItem(inputs, ts, ...);  // Pass accumulated state
}

// Bug pattern: Null overwrites accumulated state
for (ITEM item : items) {
    ts = postExtractHook(inputs, item);  // If null, loses all previous changes
    ts = setHeldItem(inputs, ts, ...);   // Falls back to original state
}
```

### 3. Rate Limiting Pattern (AbstractWorldInteraction)

Work operations are rate-limited to prevent actions on every game tick:

```java
ticksSinceLastAction++;
if (ticksSinceLastAction < interval) {
    return earlyReturn;  // Block work until interval reached
}
ticksSinceLastAction = 0;  // Reset after work performed
```

**Important for time warp:**
- During warp, call `injectTicks(interval)` BEFORE each `tryWorking()` call
- This allows work to proceed without waiting for real ticks
- Without this, warp gets stuck because most calls return early

### 4. Hook Pattern for Extension Points

Abstract methods provide extension points at key lifecycle moments:

- `preExtractHook` - Before extracting products
- `postExtractHook` - After extraction, for side effects
- `postInsertHook` - After inserting ingredients
- `preStateChangeHooks` - Before state transitions

**Null handling rule:** Hooks returning null should NOT overwrite the current state. Only non-null returns should update state.

### 5. Warper Pattern for Time Simulation

Time warp uses a `Warper<LEVEL, STATE>` pattern:

```java
interface Warper<LEVEL, STATE> {
    STATE warp(STATE initialState, long ticksPassed, ...);
}
```

The warper:
1. Generates "important ticks" to process
2. For each tick, simulates what the villager would do
3. Returns the final accumulated state

**Key insight:** Warp must simulate all behaviors that would happen during real gameplay, including rate limiting bypass.

## Common Bug Patterns

### Bug: Null State Overwriting

**Symptom:** Only 1 item produced when 3 expected

**Cause:** Hook returning null overwrites accumulated state

**Fix:** Check for null before assignment:
```java
TOWN hookResult = someHook(inputs, item);
if (hookResult != null) {
    ts = hookResult;
}
```

### Bug: Rate Limiter Blocking Warp

**Symptom:** Warp appears to do nothing; state stuck

**Cause:** Rate limiter blocks work on most ticks

**Fix:** Inject ticks before warp processing:
```java
wi.injectTicks(wi.interval);
return warper.warp(state, ticks, ...);
```

### Bug: Test Doubles Hiding Bugs

**Symptom:** Tests pass but production code fails

**Cause:** Test implementations bypass real behavior (e.g., directly manipulating collections instead of using immutable state)

**Fix:** Test doubles should track state-passing behavior:
```java
// Track how setHeldItem is called
if (tuwn == null) {
    setHeldItemCallsWithNullTown++;
}
```

## Testing Guidelines

### What Unit Tests Should Verify

1. **Rate limiter behavior:**
   - Returns early when interval not reached
   - Proceeds when interval reached
   - `injectTicks()` allows immediate work

2. **State threading:**
   - Multi-item operations accumulate state correctly
   - Null hook returns don't overwrite state

3. **Job selection:**
   - Selects from preselected jobs (with supplies)
   - Respects time-of-day constraints

### Test Gaps to Watch For

Tests using mocks/doubles that don't track:
- Whether state parameters are null or accumulated
- How many times operations are called
- Whether rate limiting is bypassed correctly

## Future Testing Needs

**Exhaustive Warp vs Real-Time Comparison:**
Run a test that compares products produced by:
1. Time warp of N ticks
2. Actually running the game for N real ticks

This would catch any behavioral differences between simulated and real gameplay, ensuring warp is truly equivalent to letting time pass naturally.

**Job Selection Testing:**
`TownFlagBlockEntity.getRandomFinishableWork()` and `TownVillagers.chooseFromList()` are currently untestable in isolation because they depend on:
- `ServerJobsRegistry` static methods
- `WorksBehaviour.TownData` which requires a `ServerLevel`

Refactoring needed:
- Extract job selection logic to accept predicates/interfaces instead of calling statics
- Or create integration test infrastructure with mocked Minecraft server

## File Reference

| Pattern | Key Files |
|---------|-----------|
| Immutable State | `MCTownState.java`, `TownState.java` |
| Rate Limiting | `AbstractWorldInteraction.java:374-381` |
| State Threading | `AbstractWorldInteraction.tryGiveItems()` |
| Warp | `DeclarativeJobs.java`, `ProductionTimeWarper.java` |
| Hook Pattern | `AbstractWorldInteraction.*Hook()` methods |

---
*Last updated: 2026-01-11 based on time warp debugging session*
