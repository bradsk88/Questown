# Cook Warp Bugs — Investigation Summary

Consolidated status of all known cook warp bugs as of 2026-02-17.

---

## Overview

The cook role is the most complex job family in Questown. Unlike crafters or gatherers
(single self-contained job), the cook operates as 4-6 cooperating sub-jobs that share a
real Minecraft furnace:

- `cook/simple_furnace_food` — insert raw food into furnace slot 0
- `cook/fish` — insert raw fish into furnace slot 0
- `cook/fuel` — insert coal into furnace slot 1
- `cook/extract` — take cooked result from furnace slot 2
- `cook/stock_fuel` — move fuel from town storage to kitchen chest
- `cook/stock_ingredients` — move raw food from town storage to kitchen chest

In realtime, Minecraft's furnace ticks between these sub-jobs, and natural waiting
(villager idles when there's nothing to do) gives each sub-job fair scheduling. In warp,
everything happens within a single game tick, and there is no real furnace to do the
smelting — every mechanic must be simulated.

---

## Bug Status Matrix

| # | Bug | Status | Impact |
|---|-----|--------|--------|
| 1 | `always_consider` immediate return bypasses canSatisfy | **Fixed** | High — beef never selected |
| 2 | Empty `requestedResults` breaks canSatisfy loop | **Fixed** | High — no job matched when board empty |
| 3 | `canFit` in preselection uses real server time | **Fixed** | Was excluding beef from preselection |
| 4 | `postInsertHook` discards state advancement | **Fixed** | Was preventing furnace state from advancing |
| 5 | Cook sub-jobs clobber shared processingState | **Fixed** | Was causing wrong items in wrong slots |
| 6 | `TakeFromSlotSpecialRule` null context | **Fixed** | Was NPE during extraction |
| 7 | Warp extraction under wrong sub-job | **Partially fixed** | Reduced by runToCompletion loop |
| 8 | Global warp rule dropped on phase change | **Fixed** | Collection and SmeltFurnaceWarpRule in place |
| 9 | Auxiliary jobs monopolize via re-evaluation | **Fixed** | Beef gets ~20% chance per shuffle |

---

## Bug 1: `always_consider` Immediate Return (Fixed)

**File:** `TownVillagerData.java` line ~48
**Doc:** `docs/bugs/always-consider-warp-monopoly.md`

`chooseFromList()` iterates shuffled sub-jobs. For any job where `canAlwaysStart` is
true, it returns **immediately** without checking `canSatisfy` (request matching):

```java
if (canAlwaysStart.test(p)) {
    return p;  // bypasses canSatisfy entirely
}
```

Four cook sub-jobs declare `always_consider`: `cook/extract`, `cook/fuel`,
`cook/stock_fuel`, `cook/stock_ingredients`. The two production jobs
(`cook/simple_furnace_food`, `cook/fish`) do not. With 4 always-consider jobs shuffled
ahead of beef, beef almost never gets selected.

**Intended semantics of `always_consider`** (from `SpecialRules.java`): bypass supply
abundance scoring in preselection — NOT bypass request matching in job selection. The fix
is to have `always_consider` skip `canFitInDay` but still go through `canSatisfy`.

**Human note from the existing bug doc:** The `always_consider` rule may not even be
necessary anymore. It was added because jobs with empty `work_states` got excluded by
`getHighestPossibleState()`. A subsequent bug fix required adding a tool (stick) to
`cook/extract`, so it might score normally without the rule. Removing `always_consider`
from the 4 auxiliary cook jobs may be the simplest fix.

---

## Bug 2: Empty `requestedResults` Breaks canSatisfy Loop (Fixed)

**File:** `TownVillagerData.java` line ~55
**Doc:** `docs/bugs/always-consider-warp-monopoly.md` (Cause 3)

When the job board has no player requests, `requestedResults` is empty. The canSatisfy
loop iterates over an empty list and never executes:

```java
List<Ingredient> i = requestedResults.stream()
    .map(WorkRequest::asIngredient).toList();
for (Ingredient requestedResult : i) {    // never runs when i is empty
    if (ServerJobsRegistry.canSatisfy(td, p, requestedResult)) {
        return p;
    }
}
```

No job is ever returned. The method falls through to `return null`, forcing the 100-tick
fallback buffer before `getPreferredWork()` kicks in.

**Note:** This bug is masked by Bug 1 — the `always_consider` immediate return fires
first. But if Bug 1 is fixed, Bug 2 becomes the primary blocker.

**Fix:** When `requestedResults` is empty, return the first eligible job. There's nothing
to match against, so all jobs passing `canFitInDay` or `canAlwaysStart` should be
selectable.

---

## Bug 3: `canFit` in Preselection Uses Real Server Time (Fixed)

**File:** `TownPossibleWork.java`
**Doc:** `docs/bugs/always-consider-warp-monopoly.md` (Cause 1)

Previously, `getWorkPercentPossible()` called `canFit` with the real server time. During
warp, `PostDowntimeWarper` uses `VIRTUAL_MORNING_TICK = 1000`. Mismatch caused beef to
get score 0 and be excluded from the preselected list.

**Fixed:** The `canFit` call was removed from preselection. The method now does pure
state computation (ingredients/tools availability). Time filtering is handled downstream.

---

## Bug 4: `postInsertHook` Discards State Advancement (Fixed)

**File:** `AbstractWorldInteraction.java`
**Doc:** `docs/bugs/always-consider-warp-monopoly.md` (finding #1)

`postInsertHook` was passing the **original** unmodified state instead of the updated
context after ingredient insertion. The `insert_into_slot_0` hook operated on stale
state, losing the `processingState` advancement.

**Fixed:** Changed to pass `ctx` (post-insertion state) instead of the original.

---

## Bug 5: Cook Sub-Jobs Clobber Shared processingState (Fixed)

**File:** `PostDowntimeWarper.java`
**Doc:** `docs/bugs/cook-warp-state-clobbering.md`

All cook sub-jobs share the same furnace `BlockPos` for `MCTownState.workStates`. Each
sub-job's warper ran only one step per warp tick. On the next tick, a different sub-job's
warper would see the previous job's leftover `processingState` and misinterpret it.

**Fixed:** `runToCompletion()` loops each sub-job's warper up to 64 iterations (breaking
on `isCycleComplete()` or no-progress). This ensures each sub-job finishes its full cycle
(collect → insert → extract → reset) before the next sub-job runs. The fix has been
generalized to all jobs, not just cook.

---

## Bug 6: `TakeFromSlotSpecialRule` Null Context (Fixed)

**File:** `TakeFromSlotSpecialRule.java`
**Doc:** `docs/bugs/cook-warp-state-clobbering.md` (secondary finding)

`beforeExtract()` called `super.beforeExtract()` which returns `null` by default. The
null was passed downstream causing NPE in `ProductionTimeWarper.getHeldItems`.

**Fixed:** Falls back to input context when parent returns null.

---

## Bug 7: Warp Extraction Under Wrong Sub-Job (Partially Fixed)

**Doc:** `docs/bugs/always-consider-warp-monopoly.md` (finding #4)

When `cook/simple_furnace_food` advances processingState to maxState, the warp handler
processes one status per tick. On the next tick, `PostDowntimeWarper` may cycle to a
different sub-job whose warper sees `processingState >= maxState` and does extraction
with the **wrong result generator**.

**Partially addressed by Bug 5 fix:** The `runToCompletion()` loop means each sub-job
completes its full cycle (including extraction) before the next sub-job runs. This
prevents cross-sub-job state misinterpretation in most cases.

**Still uncertain:**
- Whether the result generator is always correct (cook template JSON uses
  `"result": {"item": "minecraft:air"}` as a placeholder — realtime relies on the actual
  furnace, warp needs to look up the smelting recipe)
- The 2:1 consumption-to-production ratio observed during testing suggests half of
  insertions don't cook

---

## Bug 8: Global Warp Rule Dropped on Phase Change (Dormant)

**File:** `TownFlagState.java` line ~218
**Doc:** `docs/bugs/cook-global-rule-dropped.md`

Global warp rules are collected once at warp start by reading `getSpecialGlobalRules()`
from each villager's current job ID. If a rule is declared on only one cook sub-job, it
would disappear when the villager transitions to a different phase.

**Fixed:** `collectGlobalRulesForAllVillagerRoots()` collects from ALL jobs sharing a root
with active villagers. `SmeltFurnaceWarpRule` implements `onWarpTick()` and is registered
in `QuestownSpecialRules`. It calls `event.world().advanceProcessing(pos, ticks)`, which
is implemented in `MinecraftWorldAccess`.

**Remaining risk:** Rules are collected once at warp start and never refreshed. If a
villager changes job root during warp (unlikely but possible), the new root's rules won't
be picked up.

---

## Bug 9: Auxiliary Jobs Monopolize Via Re-evaluation (Fixed)

**Doc:** `docs/bugs/always-consider-warp-monopoly.md` (Cause 4)

Even after fixing Bugs 1-3, auxiliary `always_consider` jobs complete fast (pick up item,
deposit) and trigger frequent re-evaluations. Each re-evaluation shuffles all eligible
jobs. With 4 auxiliary jobs vs 1 beef, beef has ~20% chance per evaluation.

**Potential fix (from existing doc):** `PostDowntimeWarper` should prioritize the
assigned job (`fallbackJobID`). After an auxiliary job completes a cycle, switch back to
the assigned production job rather than random selection. This ensures auxiliary jobs
support the main job rather than replacing it.

---

## Recommended Fix Order

1. **Try removing `always_consider` from the 4 auxiliary cook jobs** — simplest fix,
   eliminates Bugs 1 and 9 simultaneously. Test whether preselection scoring still
   includes them without the rule (they now have tools/ingredients, so they should score
   normally).

2. **If removal isn't viable, fix `chooseFromList()`** — change the `canAlwaysStart`
   check to skip `canFitInDay` but still check `canSatisfy`.

3. **Fix empty `requestedResults`** (Bug 2) — return the first eligible job when the
   board is empty.

4. **Verify warp result generator** (Bug 7) — confirm that `cook/extract` uses the
   correct smelting recipe lookup, not the template's `minecraft:air`.

5. ~~**Implement furnace smelting warp rule** (Bug 8)~~ — Done: `SmeltFurnaceWarpRule` registered and `advanceProcessing` implemented.

---

## Discovered

Bugs 1-4: 2026-02-15, during warp cook investigation.
Bugs 5-6: 2026-02-15, during runToCompletion fix.
Bug 8: Identified during warp-interleaved hooks design.
Bug 9: 2026-02-15, observed after fixing Bugs 1-3.
This summary: 2026-02-17.
