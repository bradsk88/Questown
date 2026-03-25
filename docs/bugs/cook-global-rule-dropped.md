# Bug: Cook's Global Warp Rule Dropped After Job Phase Change

**Severity:** Medium (affects warp correctness for container jobs)
**Status:** Fixed — `collectGlobalRulesForAllVillagerRoots()` collects from
all sub-jobs sharing a root (e.g., all `cook/*`), and `SmeltFurnaceWarpRule`
implements `onWarpTick()` via `advanceProcessing`. `WarpWorldAccess` now
routes hook calls through in-memory state, so `asServerLevel()` returns null.
**Related decision:** `docs/decision-declare-and-collect-global-rules.md`

---

## Problem

`TownFlagState` collects global warp rules by reading
`getSpecialGlobalRules()` from each villager's **current job
ID**. A cook villager cycles through multiple job phases:
`cook/stock_fuel` → `cook/stock_ingredients` → `cook/extract`.

When a cook inserts food into a furnace and moves to the next
phase, their job ID changes. If the global rule (e.g., a
future "furnace keeps smelting" warp effect) is declared only
on `cook/stock_ingredients`, it won't appear in the collected
set once the villager advances to `cook/extract`.

The effect is tied to **job assignment at collection time**,
not to the fact that a cook started a process that should
continue.

---

## Impact

Today: None. Container rules (cook, baker) haven't been
migrated to QTWorldAccess yet, and no global warp rules exist
for container jobs.

Future: When we add a "furnace smelting" global warp rule for
cooks, it will only run during the specific job phase that
declares it, not throughout the full cook cycle.

---

## Proposed Fix

When collecting global rules, consider **all jobs sharing a
root** with the villager's current job — not just the current
job ID.

For a villager on `cook/extract`, collect global rules from
all `cook/*` variants: `cook/stock_fuel`,
`cook/stock_ingredients`, `cook/fuel`, `cook/fish`,
`cook/simple_furnace_food`, `cook/extract`.

This ensures that a global rule declared by any phase of the
cook job family runs as long as the villager is a cook.

### Implementation sketch

```
// Instead of:
ruleIDs.addAll(ws.get().getSpecialGlobalRules());

// Do:
String root = jobRoot(v.journal.jobId()); // "cook"
for (Work w : Works.getAllWithRoot(root)) {
    ruleIDs.addAll(w.getSpecialGlobalRules());
}
```

`Works.getAllWithRoot(String)` would need to be added — it
returns all registered `Work` entries whose job ID starts with
the given root prefix.

---

## When to Fix

When container rules are migrated to QTWorldAccess (Phase 4
of the decoupling plan) and a global warp rule is needed for
furnace/container processing.
