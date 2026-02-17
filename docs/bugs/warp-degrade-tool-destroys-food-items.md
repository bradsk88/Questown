# Bug: Warp degradeTool destroys non-damageable items (fixed)

## Symptom

During time warp, cook jobs consumed 2x the expected raw food (e.g. 10 beef consumed for 5 cooked beef).

## Investigation

Diagnostic logging was added to four locations in the warp pipeline:

1. **PostDowntimeWarper** — which job was selected each tick, whether `runToCompletion` made progress
2. **DeclarativeJobs.warper()** — the resolved `ProductionStatus`, current `processingState`, and villager inventory at each warp iteration
3. **ProductionTimeWarper.simulateCollectSupplies()** — whether a tool or ingredient was grabbed, what item, or if skipped
4. **SlotPrecondition checks** — whether the precondition passed or blocked

## What the logs revealed

Each cook/beef cycle showed this sequence:

```
COLLECTING_SUPPLIES state=0  villagerItems=[]        — grabs beef as TOOL
state:0                      villagerItems=[beef]     — works at station
COLLECTING_SUPPLIES state=1  villagerItems=[]         — beef GONE, grabs another as INGREDIENT
state:1                      villagerItems=[beef]     — inserts into furnace
EXTRACTING_PRODUCT           villagerItems=[]         — cycle complete
```

The critical observation: between `state:0` (villager holds beef) and `COLLECTING_SUPPLIES state=1` (villager inventory empty), the tool beef vanished. This pointed directly to `degradeTool`.

## Root cause

`TimeWarpWorldInteraction.degradeTool()` used manual durability logic:

```java
itemStack.hurt(1, random, null);
if (itemStack.getDamageValue() >= itemStack.getMaxDamage()) {
    itemStack = ItemStack.EMPTY;  // destroyed
}
```

For food items like beef, `maxDamage = 0` and `getDamageValue()` returns 0 after `hurt()`, so `0 >= 0` is true — the item is immediately destroyed.

The realtime path uses `itemStack.hurtAndBreak(1, entity, callback)` which is a no-op for non-damageable items.

## Fix

Added an early return for non-damageable items:

```java
if (!itemStack.isDamageableItem()) {
    return tuwn;  // no degradation needed
}
```

This matches the realtime `hurtAndBreak` behavior.

## Verification

After the fix, cook warp produces a 1:1 ratio of raw beef consumed to cooked beef produced, matching the expected behavior from realtime play.
