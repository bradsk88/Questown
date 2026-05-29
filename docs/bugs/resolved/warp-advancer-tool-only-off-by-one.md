# Bug: Advancer Off-By-One Loot for Tool-Only Jobs

## Status: Resolved 2026-05-28 — autotest-verified 2026-05-29

Verify before beta cut: `/_qtdev test gatherer_unmapped_axe_short`, `/_qtdev test gatherer_unmapped_rod_short`, `/_qtdev test hunter_unmapped_sword_short`, `/_qtdev test miner_short`. Advancer should fill all 6 inventory slots and the block state should end at 0 (idle), not 3.

Last clean-world full autotest run (2026-05-29): gatherer, hunter, miner scenarios passed.

## Summary

The advancer (warp path) produces one fewer loot item than the ticker (realtime path)
for jobs that require only a tool and no consumable ingredient. The advancer fills 5 of
6 inventory slots while the ticker fills all 6.

## Affected Jobs

- `gatherer_unmapped_axe_short`
- `gatherer_unmapped_rod_short`
- `hunter_unmapped_sword_short`
- `miner_short`

All share the same pattern: `work_states` has a `tools` step but no `ingredients` step.

## Observed Behavior

| System   | Inventory after completion          | Job block state |
|----------|-------------------------------------|-----------------|
| Ticker   | [tool, loot, loot, loot, loot, loot] (6/6) | 0 (idle)  |
| Advancer | [tool, loot, loot, loot, loot]       (5/6) | 3 (stuck) |

The ticker completes enough cycles to fill all 6 slots then idles. The advancer
completes one fewer cycle and remains in the timed-wait state (block state 3).

## Root Cause

The advancer calculates ticks-per-cycle via `getWarpTicksPerCycle()` in
`EquivalenceTestFramework` (line ~267):

    ingredientSteps (0) + toolSteps (1) + workRequired (1) + 3 = 5 ticks/cycle

This covers: acquire tool, do work, wait, extract loot. It does **not** account for the
tick(s) needed to recognize the inventory is full and execute the drop-loot transition.

### Realtime (ticker) sequence

1. Tick N: extract loot -> inventory is now 6/6 (full)
2. Tick N+1: `JobStatuses.usualRoutineRoot()` sees `inventoryIsFull` -> transitions to
   `droppingLoot`
3. Tick N+2: loot is dropped to town storage -> inventory has free slots -> next cycle
   starts

### Warp (advancer) sequence

1. `ImportantTicks` groups ~5 game ticks into one simulated step
2. Within that step, extraction fills the inventory to capacity
3. The drop-loot decision happens at the boundary of the **next** important-tick group
4. But the next group also tries to start a new cycle, and the inventory is still full
   from the previous extraction (the drop hasn't happened yet)
5. Result: one fewer cycle completes before the tick budget runs out

The core issue is that the advancer's sparse tick schedule doesn't leave room for the
intermediate "inventory full -> drop -> resume" transition that the ticker gets for free
because it runs every single tick.

## Key Files

- Inventory fullness check: `DefaultInventoryStateProvider.inventoryIsFull()` (line ~34)
- Loot placement (empty slots only): `AbstractWorldInteraction.tryGiveItems()` (line ~249)
- Status decision tree: `JobStatuses.usualRoutineRoot()` (line ~147)
- Warp tick calculation: `EquivalenceTestFramework.getWarpTicksPerCycle()` (line ~267)
- ImportantTicks spacing: `ImportantTicks` (line ~162)

## Impact

Low. These are the shortest-duration variants of each job type. The medium and full
variants (which require food as an ingredient) are unaffected because the consumable
ingredient naturally bounds cycle count, so the drop-loot transition timing doesn't
matter.

In-game, the practical effect is that a tool-only villager produces slightly less loot
during a short warp than during equivalent realtime play. The difference is 1 loot item
out of 5-6.

## Possible Fixes

1. **Add a drop-loot tick to the warp cycle budget**: increase `getWarpTicksPerCycle()`
   by 1-2 ticks when no ingredient is consumed (unbounded cycling).
2. **Make the advancer check for full inventory between cycles**: after extraction,
   immediately trigger the drop-loot path before starting the next cycle's tool
   acquisition.
3. **Accept the discrepancy**: since it's only 1 item and only affects short tool-only
   jobs, it may not be worth fixing for V1.

## Discovered

2026-02-17, during parameterized equivalence test expansion for gatherer/hunter/miner
jobs. Tests are excluded with comments in `TickerAdvancerEquivalenceTest`.
