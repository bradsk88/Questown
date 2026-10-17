---
title: 'uncomfortable_eating re-applied every tick for ~25s after a diner gives up'
status: needs-triage
origin: 2026-10-17 playtest round 5 — dev-server-8 debug.log (build 3ed88de2)
---

`Effect has been applied to b42d…: Effect[effect=questown:uncomfortable_eating, untilTick=N]`
logged with N incrementing by 1 per tick — 2506 times between 17:58 and 18:10 (243/1260/501/
1002/620/384 per minute). Each burst lasts ~500 ticks and follows
`gatherer gave up waiting for ingredients after 201 ticks (job: dining_raw_food)` → `Fullness
changed from -1940 to 2500` (one fill) → ends at `gave up waiting for different work after 501
ticks (dining_raw_food)`.

Suspect: the give-up extraction (`grabbedInsertedSupplies` → `tryExtractWithNoItem` →
`postExtractHook`) re-fires every tick while the job sits in NO_SUPPLIES, re-running the
EXTRACTING_PRODUCT rules (HUNGER_FILL_HALF + mood effects). The fill only logs on change, so
repeated fills at max would be silent — i.e. possibly free food every tick for 25s.

## Next

Re-check after the diner terminal-state fix (`DeclarativeJobs.declaresUnfinishedWork` + diner
PRIORITIZE_EXTRACTION): the diner may no longer reach the raw-food give-up path. If it still
happens, find the per-tick caller of the extract hook in the give-up path.
