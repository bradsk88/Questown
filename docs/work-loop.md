# Work Loop Quick Reference

Processes villager work: ingredients, work progression, tools, extraction.

## Entry Points
- **Time Warp**: `AbstractDeclarativeJobWarper.tryWorking()`
- **Real-time**: `RealtimeWorldInteraction.tryWorking()` (thin MC wrapper for animations/sounds)

## tryWorking Flow (AbstractWorldInteraction.java:362)

```
1. Check server/town readable
2. Check claim status (some jobs prevent multi-villager)
3. Check work interval (time between actions)
4. Check entity proximity
5. Get job block state → processingState (0 to maxState)
6. If state >= maxState && workLeft == 0 → tryExtractProduct()
7. Check tool requirement → collect if needed
8. Try inserting ingredients → collect if needed
9. Apply work (e.g. swing axe)

State advances when: ingredients inserted + workLeft == 0
```

## Work Progression (AbstractWorkWI.tryWork)

```
1. Get next step's work/time requirements
2. applyWork():
   - Decrease workLeft by workSpeed/10 (mood-based, unreleased)
   - If workLeft == 0:
     - preStateChangeCallback (special rules)
     - Increment processingState
     - Set workLeft for next state
     - Set timer if time requirement
3. Degrade tool if: degradeTool flag + work done + tool required
```

## State Object

- `processingState` - Current step (0 to maxState)
- `workLeft` - Work units remaining (e.g. axe swings)
- `ingredientCount` - Items inserted (for abort/refund)
- Timer value (for time-based states)

## Hooks

- `preStateChangeCallback` - Before state transitions
- `postInsertHook` - After ingredient insertion (e.g. InsertIntoSlotSpecialRule)
- `preExtractHook` / `postExtractHook` - Around product extraction

## Key Files

| File | Purpose |
|------|---------|
| AbstractWorldInteraction.java:362 | Main tryWorking |
| AbstractWorkWI.java | Work application |
| AbstractItemWI.java | Ingredient insertion |
| RealtimeWorldInteraction.java | Real-time MC |
| MCTownStateWorldInteraction.java | Time warp |
| State.java | Job block state |
| DeclarativeJobChecks.java | Job requirements |

## Notes

- Work interval: ticks between actions (20 ticks = 1 sec)
- Tool degradation: once per state completion, not every tick
