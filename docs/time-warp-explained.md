# Time Warp System Explained

This document explains how time warp works, starting from simple scenarios and building to complex ones.

## What is Time Warp?

Time warp simulates villager work during periods when the town is unloaded. This happens in two main scenarios:

1. **Player leaves town** - When the player travels far enough away, the town chunks unload. When they return, time warp simulates the work that would have happened while they were gone.

2. **Manual testing** - Developers can trigger time warp via command to test that villager work cycles function correctly over long periods.

The warp might cover anywhere from a few ticks to thousands of ticks, depending on how long the town was unloaded.

## Basic Concepts

### Villager Work Cycle

Every villager job follows a cycle of statuses:

```
COLLECTING_SUPPLIES → 
    work states → 
        EXTRACTING_PRODUCT → 
            DROPPING_LOOT → 
                DOWNTIME (if due/overdue) → 
                    JOB/TASK SELECTION (if possible) →
                        repeat
```

For example, a **Gatherer** cycle:
1. `COLLECTING_SUPPLIES` - Take food from chest
2. `state:0` - Take food to the flag, "packing a lunch" (fuel for the work)
3. `state:1` - Technical requirement - by doing 1 "work", the villager activates the timer for the next state
4. `WAITING_FOR_TIMED_STATE` - Wait 4000 ticks (villager is "out gathering")
5. `EXTRACTING_PRODUCT` - Generate "gathered" loot
6. `DROPPING_LOOT` - Put loot in chest
7. Back to step 1 (or downtime if due/overdue)

### The Journal

Each villager has a "journal" that tracks their current status. This is saved to disk and persists across game sessions.

**Important:** During warp, the journal is NOT updated. The warp system reads the journal once at the start and must figure out the correct behavior from there.

---

## Scenario 1: Villager starting from relaxed state

**Setup:** Villager is doing the "downtime" job. E.g. "gatherer/downtime"
(Technically, this could also be applied if the villager is in `IDLE` state of their main job, but that's not common 
because villagers eagerly work when possible.)

**What happens during warp:**
1. Warp reads journal: `IDLE`
2. Computes what status the villager should have
3. If supplies available → `COLLECTING_SUPPLIES`
   - (other possible statuses: `NO_SUPPLIES`, `NO_JOBSITE`, or `RELAXING` if it is evening or night)
4. Villager collects and inserts supplies from containers (e.g. chests) and into the job-appropriate "job block", works 
   at job block (or waits for a timer associated to that job block), extracts results from that job block, drops loot in 
   containers (e.g. chests)
5. Repeat until warp time is exhausted

**This is the simplest case** - the villager starts fresh and works normally.

---

## Scenario 2: Villager Mid-Work (E.g. Crafter)

**Setup:** A crafter villager is in `state:1` (actively crafting) when warp begins

**What happens during warp:**
1. Warp reads journal: `state:1`
2. Computes status normally → still `state:1` or next state
3. Villager continues from where they left off (work remains to be done on the job block)
4. Completes current task, extracts result, drops loot, starts new cycle

**Key point:** The status computation naturally handles this because the villager's inventory and the "job block" state 
tell the system where they are in the cycle.

---

## Scenario 3: Villager Waiting for Timer (Gatherer Out Gathering)

**Setup:** A gatherer is "out gathering" (status = `WAITING_FOR_TIMED_STATE`) when warp begins. They already left town, 
consumed food, and are waiting for their timer to expire.

**The Problem:**
- Journal says `WAITING_FOR_TIMED_STATE`
- Villager has no items (food was consumed, loot not yet collected)
- Normal status computation might not know they need to extract loot first

**What should happen:**
1. Wait for the timer to expire (for warp, this means create a fake tick that represents that future tick)
2. Drain the timer down to 0
3. Complete current trip (extract loot)
4. Drop loot to chest
5. Start new cycle (See: Scenario 1)

**The Solution:**

When we detect:
- Journal status is `WAITING_FOR_TIMED_STATE`
- Villager has NO items (no loot AND no supplies)
- Computed status is NOT `COLLECTING_SUPPLIES`

...we force `EXTRACTING_PRODUCT` to complete their trip. (Works for now, should be improved to better match the "should happen" section, above)

```java
boolean needsFirstExtraction = initialStatus.isWaitingForTimers()
        && !villagerHasNonSupplyItems
        && !villagerHasSupplyItems
        && !wouldCollectSupplies;
```

---

## Scenario 4: After Collecting Supplies (The Tricky Part)

**Setup:** During warp, villager just did `COLLECTING_SUPPLIES` and now has food in inventory.

**The Problem:**

After `COLLECTING_SUPPLIES`:
- Villager has food (a supply item)
- `hasNonSupplyItems()` returns `false` (food is not "loot")
- Journal still says `WAITING_FOR_TIMED_STATE` (never updated during warp)

Without proper handling, the system would see:
- Journal: `WAITING_FOR_TIMED_STATE` ✓
- No non-supply items ✓
- Computed status is `state:0` (not `COLLECTING_SUPPLIES`) ✓

...and incorrectly force extraction again, skipping food consumption!

**The Fix:**

Also check if villager has supply items (food):

```java
boolean villagerHasSupplyItems = inventory.getSupplyItemStatus().values().stream()
        .anyMatch(supplyStatus -> supplyStatus == SupplyItemStatus.HAS_ITEM);
```

Now the check is:
- Journal: `WAITING_FOR_TIMED_STATE` ✓
- No non-supply items ✓
- **No supply items** ✗ (villager has food!)

Forced extraction is NOT triggered. Normal flow proceeds to `state:0` which consumes the food.

---

## Correct Warp Flow (Gatherer)

Here's what a correct warp looks like in the logs:

```
tick=301443 COLLECTING_SUPPLIES  ← Take food from chest
tick=301444 state:0              ← "Pack lunch" (destroys food item)
tick=301445 state:1              ← Do work (activates timer)
tick=301446 EXTRACTING_PRODUCT   ← Timer skipped in warp, get loot
tick=301447 DROPPING_LOOT        ← Put loot in chest
tick=301448 COLLECTING_SUPPLIES  ← Take food for next trip
tick=305444 state:0              ← Consume food (tick jump = timer simulation)
tick=305445 state:1              ← Do work
tick=305446 EXTRACTING_PRODUCT   ← Get loot
tick=305447 DROPPING_LOOT        ← Put loot in chest
...
```

Note: The large tick jump (301448 → 305444) represents the ~4000 tick timer being simulated. The warp system
generates "important ticks" at these intervals rather than processing every single tick.

Each cycle consumes 1 food and produces loot. If warp covers 10,000 ticks and each trip takes ~4,000 ticks, expect 2
trips (or 3 if the villager already left before the warp and only has a few ticks left on the timer)

---

## Common Bugs and How to Identify Them

### Bug: No Food Consumed
**Symptom:** Loot appears but food count unchanged
**Cause:** `state:0` (food consumption) is being skipped
**Check logs for:** `COLLECTING_SUPPLIES` jumping directly to `EXTRACTING_PRODUCT`

### Bug: Double Food Consumed
**Symptom:** Food decreases by 2 per trip instead of 1
**Cause:** `COLLECTING_SUPPLIES` happening twice per cycle
**Check logs for:** Two consecutive `COLLECTING_SUPPLIES` entries

### Bug: No Loot Produced
**Symptom:** Food consumed but no loot in chest
**Cause:** `DROPPING_LOOT` not being called, or extraction failing
**Check logs for:** Missing `DROPPING_LOOT` entries

---

## Summary

The time warp system must handle villagers in any state when warp begins.

The key insight is that **the journal never updates during warp**, so we must use inventory state (both loot AND 
supplies) to determine where the villager actually is in their cycle.
