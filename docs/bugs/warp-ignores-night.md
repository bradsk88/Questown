**RESOLVED** — Fixed via `Signals.calculateProductiveTicks()`.

Villagers do not work at night. The warp now subtracts evening/night ticks (11500-24000 per day cycle)
from the warp duration before simulating. Only ticks in the productive window (0-11500, i.e. MORNING+NOON)
count toward villager work.

The fix lives in `TownFlagState.advanceTime()` — a single call to `Signals.calculateProductiveTicks(dayTime, ticksPassed)`
after capping to `TIME_WARP_MAX_TICKS`. If the entire warp falls during nighttime, no simulation runs.