# Decision: Proportional Deltas Over Fixed-Frequency Assumptions

**Date:** 2026-02-12
**Status:** Active
**Revisit when:** A warp hook needs tick-by-tick iteration
(non-linear process), or warp step gaps regularly exceed
~1500 ticks

---

## Context

The warp loop in `AbstractAdvanceTime` invokes
`WarpTickCallback.onTick()` at irregular intervals — the
callback fires at tick boundaries driven by villager work
steps, not at a fixed frequency. A single callback might
cover 50 ticks or 5000 ticks depending on how villager jobs
are spaced.

Hooks need to compute world-level effects (crop growth,
furnace progress, etc.) correctly regardless of how large
or irregular the gaps are.

---

## Decision

Pass `tickDelta` (elapsed ticks since last callback) to every
hook. Hooks compute effects proportionally:
`effect = probability * tickDelta`. No hook should assume a
fixed call frequency.

---

## Why

1. **Correct regardless of callback frequency.** Adding or
   removing villagers changes when callbacks fire but not
   the total effect over the same time span.

2. **Standard game-engine practice.** Delta-time computation
   is the universal pattern for variable-rate update loops.

3. **Simple for hook authors.** Multiply by delta — no need
   to understand the warp loop's internal scheduling.

---

## Risks

**Non-linear effects.** Some processes don't scale linearly
with time. Vanilla crop growth has compounding factors
(farmland moisture, neighboring crops, light level). Furnace
fuel consumption is stateful — burning time decrements
tick-by-tick and fuel is consumed in discrete units. A
`probability * tickDelta` approximation breaks down for
these. Such hooks would need to iterate tick-by-tick within
the delta, or use a more sophisticated model.

**Large delta with order-dependent state.** A hook that both
reads and writes world state may produce different results
from one large delta vs many small ones. Example: a crop at
age 5/7 with `tickDelta=4000` gets `+2` and finishes. But
if the crop were also being harvested by another rule, the
interaction depends on whether growth happened in one step
or many. We handle this for crops via the population-based
fallback, but a naive hook author might not.

**Integer overflow in hook implementations.** `tickDelta` is
a `long`, but hook authors computing intermediate values in
`int` could silently overflow. The `WarpTickEvent` javadoc
should note that arithmetic must use `long`.

---

## Why the Risks are Acceptable Today

- Only one warp hook exists (`GrowCropsWarpRule`), and it
  uses `long` arithmetic throughout
- Crop growth is deliberately simplified (the "farmers tend
  crops" lore justifies the linear approximation)
- Non-linear processes (furnace, container rules) haven't
  been migrated to warp yet
- Warp step gaps are currently bounded by villager job cycle
  lengths, which are short (hundreds of ticks, not thousands)

---

## Future Consideration: Gap Detection

The system should eventually detect when jobs are generating
warp steps that are too far apart. Large gaps between
villager steps mean large `tickDelta` values, which amplify
the approximation error for any hook that isn't purely linear.

A diagnostic log or warning when `tickDelta` exceeds a
threshold (e.g., 1500 ticks — the point where integer crop
growth kicks in) would help catch this during development.

---

## Migration Path (When Needed)

For non-linear processes, hooks can iterate within the delta:

```java
@Override
public <X> X onWarpTick(X town, WarpTickEvent event) {
    for (long t = 0; t < event.tickDelta(); t++) {
        // per-tick stateful logic
    }
    return town;
}
```

This is intentionally supported by the API — `tickDelta` is
the maximum information a hook needs. Linear hooks multiply,
stateful hooks iterate.
