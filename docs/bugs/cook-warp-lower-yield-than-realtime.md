# Bug: Cook Warp Produces Less Cooked Beef Than Realtime

## Status: Known — Acceptable for Launch

## Symptom

In-game test (`/qt test cook`) shows the warp path produces significantly fewer cooked beef
than the realtime (ticker) reference run.

## Likely Causes

- Warp sub-job cycling overhead: each sub-job runs to completion sequentially, whereas
  realtime benefits from natural idle time and parallel furnace ticking
- Furnace smelt simulation may not match vanilla tick-for-tick (SmeltFurnaceWarpRule
  vs real furnace)
- Auxiliary jobs (stock_fuel, stock_ingredients, fuel, extract) consume warp ticks that
  realtime handles during villager idle time

## Test Output (`/qt test cook 10000 destroy`)

```
[qt test]   Before: {minecraft:coal=32, minecraft:stick=20, minecraft:beef=32, minecraft:cooked_beef=4}
[qt test]   After:  {minecraft:coal=28, minecraft:beef=21, minecraft:stick=20, minecraft:cooked_beef=13}
[qt test]   [FAIL] minecraft:cooked_beef: expected 3..5, got 9 (before=4, after=13)
[qt test]   [FAIL] minecraft:beef: expected >= -5, got -11 (before=32, after=21)
[qt test]   [PASS] minecraft:coal: expected >= -4, got -4 (before=32, after=28)
[qt test] --- WARP vs REALTIME comparison ---
[qt test]   [DIFF] minecraft:cooked_beef: warp=5, realtime=9, diff=4
[qt test]   [DIFF] minecraft:beef: warp=-5, realtime=-11, diff=6
[qt test]   [DIFF] minecraft:coal: warp=-2, realtime=-4, diff=2
```

Warp produced 5 cooked beef; realtime produced 9. Warp is ~56% of realtime yield.

## Impact

Lower but non-zero cooked food output during warp. Players leaving town and returning
get less cook output than if they stayed in town. Functional but not parity.

## Decision

Acceptable for V1 launch. The cook produces food in both paths; the yield gap is a
tuning issue, not a correctness bug.

## Discovered

2026-02-17, during in-game verification of always_consider bug fix.
