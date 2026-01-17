# In-Game Testing Scenarios

Summary of all in-game testing scenarios covered during time warp development.

## Crafter Jobs

- **Stick production** - Sapling -> stick, verified output count
- **Bowl production** - Planks -> bowls, verified ~12 bowls in 10,000 ticks
- **Job switching** - With both planks + saplings available, villager produces BOTH bowls AND sticks
- **Supply detection** - Crafter correctly identifies available ingredients in containers

## Gatherer Jobs

- **Basic gathering** - Food consumed, villager leaves, returns with biome loot
- **Food consumption** - Verified 1 food per trip (e.g., 52 -> 49 carrots = 3 trips)
- **Mid-warp start** - Villager in WAITING_FOR_TIMED_STATE when warp begins completes trip correctly
- **Timer simulation** - 4000-tick wait state simulated properly

## Cook Jobs

- **NO_JOBSITE status** - Cook shows "no kitchen" when kitchen room missing
- **Kitchen detection** - Tested various kitchen room recipes (kitchen_small, kitchen_cafe, etc.)
- **Furnace interaction** - Cook attempts to insert beef into furnace slot
- **Known issue** - Items consumed but furnace slot stays empty during warp

## Baker Jobs

- **Multi-input collection** - Collects wheat, then coal
- **Baking timer** - Wait state for baking simulated correctly
- **Bread output** - Verified bread production

## Item Recovery

- **NO_SUPPLIES handling** - When villager runs out of supplies mid-job, inserted items are recovered
- **Container return** - Recovered items deposited back to containers

## Debug Commands Verified

- `/qt debug warp <pos> <ticks>` - Basic warp execution
- `/qt debug warp <pos> <ticks> verbose` - Detailed tick-by-tick logging
- `/qt debug warp-status <pos>` - Shows villager inventories, container contents, work block states
- `/qt debug log <pos>` - Full town state dump

## Edge Cases

- **No supplies available** - NO_SUPPLIES status, no crash
- **No job site room** - NO_JOBSITE status, no crash
- **Very long warp** - 24,000 ticks (full Minecraft day) completes without crash
- **Empty containers** - Villager handles gracefully

## Bugs Found In-Game

- **Day/night rejection** - Jobs rejected during warp because of time-of-day check (fixed: virtual morning time)
- **Game freeze** - Expensive `recomputeNow()` called every warp tick (fixed: hasRecomputed flag)
- **Wrong job selected** - `planks` selected instead of `stick`/`bowl` when planks had no supplies (fixed: use preselected list)
- **Double food consumption** - Gatherer ate 2 food per trip instead of 1 (fixed: supply item check)
- **0 warp ticks** - Some villagers got 0 important ticks computed (fixed: dynamic resolution fallback)
- **PreferredBuffer throttle** - Blocking warp too early (fixed: ticksElapsed bypass)

## Known Limitations

- **~20% under-production** - Warp produces ~12 bowls vs ~15 in real-time for 10,000 ticks
- **Cook furnace sync** - Items consumed but not placed in actual furnace during warp
- **EagerCookResolver** - Alternative approach created but not fully integrated

## Not Yet Tested In-Game

- **World reload after warp** - State persistence after save/load
- **Arborist jobs** - Tree chopping and world modification
- **Smelter jobs** - Tool-based work with pickaxe
- **Multi-villager warp** - Multiple villagers warping simultaneously
