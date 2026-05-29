# Bug: `eating/dine_at_time` villager fullness goes negative

## Status: Open (discovered 2026-05-29)

## Symptom

Clean-world full autotest run produced:

```
[autotest] [FAIL] Villager fullness: -16% (min: 25%)
[autotest] jobs:eating/dine_at_time [FAIL] scenario: some expectations failed
```

After the dine-at-time scenario, the villager's fullness is **-16%**, below the **25% minimum** expectation. Other eating scenarios (`eat_no_table`, `eat_raw_food`, `eat_direct`) all pass — only `dine_at_time` fails.

## Reproduction

```sh
./gradlew runServer -Dquestown.autotest=true
```

Test entry: `eating/dine_at_time` (test 24/28 in the suite).

Blueprint location: search `TestBlueprintRegistry` for the `dine_at_time` entry (registered via `eatingEntry(...)`).

## Likely Causes

- The dining-at-table pipeline may consume hunger ticks faster than food is delivered.
- The scenario may rely on a table/food-source setup that isn't fully exercising the eat-when-seated pathway under warp.
- Possible regression in a recent commit (the `eat_*` family lives near the same code paths that the chicken-arc work touched).

## First Place to Look

1. `TestBlueprintRegistry.eatingEntry` / the `dine_at_time` blueprint construction.
2. Eating-related job phase modifiers under `src/main/java/ca/bradj/questown/jobs/special/` or `src/main/java/ca/bradj/questown/jobs/_vanilla/`.
3. Hunger drain calculation during warp (hunger is hooked into the warp tick loop somewhere — grep for `hunger`, `fullness`, `drainHunger`).

## Impact

Bar-C blocker. Players whose villagers rely on table-dining will see them slowly starve during warp/realtime cycles.

## Related

- `docs/bugs/chicken-sunset-chest-bit.md` — the other bar-C blocker from the same clean-world run.
