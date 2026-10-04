# Why the cook/food chain sometimes never staffs (self_sufficiency soft-lock)

2026-10-04 (battery v3 appended). Verified from 19 fresh `village/self_sufficiency`
samples (~50%–80% soft-lock, env/seed-dependent) + code reads. This is a **continuation**
of the DEV-DIAG `raw_food hunt` investigation (2026-10-02 → 10-04;
`DevWorkScore`/`DevLootFire` exist).

## Verified chain (soft seeds vs healthy)

The cook is **never offered** in soft seeds, not "offers and fails":

```
cook quest offered only in tutorial phase 2.5        TownQuests.tryPhase2_5_jobChangeAndFood (L677)
   └─ gated on  hunterRawFoodLootComplete()         L1113
       └─ = a LOOT for  villager_simple_furnace_food_raw  is .isComplete()
           └─ closes only when a townie EXTRACTS raw meat   (onLootExtracted → processLootQuests)
```

Evidence (soft `run_1719_f1` vs healthy `tickrate-valid` / `run_1736_f1`):

| | cook quest opened | `extract … → true` (LOOT claim) | meat-grant trace (`tryGiveItem`) | end-state |
|---|---|---|---|---|
| **soft** | **0** (119× "no open JOB_CHANGE quests to drive") | **0** | **0** | `594` × `hunter/seeking_work` |
| **healthy** | 1 (staffed) | 2 (Raw Mutton→raw, Apple→cooked) | present | `87` × `cook/extract` |

So in the soft seeds **no raw-meat extract ever happens** — the hunter sits in
seeking_work and never completes a mutton hunt — so the (already-fixed) LOOT-close has
no input, the phase-2.5 gate never opens, and the cook is never offered.

## The improvement fixed the wrong link

This session's "LOOT-quest closure on extraction" work fixed **`extract → LOOT closes`**
(the `onLootExtracted` seam + `LootExtractGuard.questCheckFires(nonEmpty, rule)`; verified
by the `loot_on_extract` autotest GREEN, for realtime *and* warp — see `town/entity/COMPLEX.md`
"LOOT quest close"). That half works. But the self_sufficiency soft-lock is the **upstream**
half those samples show: **the hunter never extracts in the first place.** The claim is not
the blocker; the hunter stall is.

## Gate level: verified down to the start-gate

- `nextForVillager` (`TownPossibleWork.java:571`) calls `nextValid(possible,
  canAlwaysStart, canFitInDay, requestedResults, td)`. `JobCycler.isValid` rejects a
  candidate when `!canAlwaysStart.test(c) && !canFitInDay.test(c)` OR no
  `requestedResults` is satisfied by `ServerJobsRegistry.canSatisfy(td, c, ingredient)`.
- The scoreDump (global probe, no can-start gates) shows the sword job as
  `score >1.0 … All ingredients and tools available … Satisfies request: mutton` in the
  soft runs → **`canSatisfy` is NOT the blocker** (the request *is* satisfied); the
  rejection sits in `canAlwaysStart || canFitInDay`.
- `ALWAYS_CONSIDER`'s own comment (`SpecialRules.java:95-98`) says what
  `canFitInDay`'s side of the gate stands for: *“By default, Questown will only allow
  villagers to start a job if there is an abundance of supplies available. This rule
  causes the job to ignore that, so villagers can always start the associated job.”*
  → the gate the sword candidate fails is the supply/day-budget start-check, and the
  rule exists precisely to bypass it. No job JSON uses the rule today; `DowntimeWork.
  java:93` is the only Java-side precedent.

## Battery v3 — the guarantee helped, but the stall survives (post `08249841`)

**Result: 4/8 GREEN (50%)**, vs 7/9 soft (78%) pre-fix. 8 `village/self_sufficiency`
seeds @ 200 t/s, early-exit on `cookUnlocked=true`, 5-day watch otherwise.
(`run/samples/BATCH-fast-SUMMARY.md` + per-sample `run_*_f*-log`/`-report.md`; batch v3
of `run/suff-batch-fast.sh`.)

Two findings refine the earlier read:

1. **The guarantee reaches only the extraction path.** In GREEN `1933_f4` the LOOT closed
   on a **natural `Raw Chicken` roll** (chicken is 15% of the loot table — a raw food too),
   with the prepended mutton landing *after* the close (`extracted '1 minecraft:mutton' but
   no open LOOT quests exist`). The prepend = insurance on the "a hunt completes" branch,
   not a rescue of the "no hunt ever completes" branch — which is where most of the
   residual soft lives.
2. **The residual SOFTs (4) split into two distinct variants, both upstream of any
   extraction:**
   - **Variant B (3/4: `1922_f3`, `1937_f6`, `1950_f8`) — the
     work-picker never adopts the hunt.** Verified in all three: swords CRAFTED
     (`ITEM/COMPLETED wooden+stone sword`), `JOB_CHANGE/hunter_qt_sword` OFFERED
     (`[job-change] requested … work request posted; quest closes on pickup`), then
     **0** `hunter/sword` work transitions, **298** `hunter/seeking_work` watch-samples
     to the end. Same-dump scoreDump: all three sword jobs `score >1.0 … Satisfies
     request: mutton` → rejection is exactly the `canAlwaysStart || canFitInDay` gate,
     i.e. the supply/day-budget check `ALWAYS_CONSIDER` bypasses.
   - **Variant C (1/4: `1912_f2`) — room-builder gave up upstream.**
     `[room-builder] could not resolve recipe blocks for questown:fisher_qt_fish` and
     `… farmer_qt_harvest_wheat`, then `no open ROOM quests to build for`; the sword ITEM
     quests never even opened; roster frozen at {gatherer=2, hunter=1} all 6 days. That is
     a **separate** RoomRecipes-resolution bug, not the hunt gate.

## Suggested fix shapes (none applied since the guarantee landed; pick before we touch code)

1. **Job-JSON (town-side, data-only — now the leading candidate)** — add
   `always_consider` to the global special rules of `hunter/sword`, `sword_half_day`,
   `sword_full_day`. Targets variant B (3/4 of the residual soft) directly. Semantics per
   the rule's own comment: bypasses the supply/day-budget start-gate — exactly what the
   scoreDump-vs-selection gap shows. (First JSON use of the rule; the only precedent is
   Java-side, `DowntimeWork.java:93`.)
2. **Investigate the `canFitInDay` predicate first** — one read confirms whether it is
   the supply-abundance check (in which case 1 is safe and targeted) or the
   day-remaining-tick budget (in which case 1 still unblocks it, but the town may start
   multi-tick hunts late in the day — check they can still finish). Cheap to verify, then
   re-run the same battery.
3. **Variant C (the room-builder `could not resolve recipe blocks`)** — separate bug;
   needs its own reproduction (which quest → which blocks it expected, and why the
   resolution failed) before any fix. Not the main residual (1/4) but a permanent stall
   when it fires.
4. **Harness-side timing** (the earlier option 1) — deprioritized: the battery's SOFTs
   sat in `seeking_work` for the whole 5-day watch at 200 t/s; this is not a
   “missed a one-day window” flake, the hunt never starts at all.

## Confound worth checking with the observer
Have the hunter "stuck early, no cook ever unlocks" been seen on a live server? That pins
whether the autotest arena's day/energy budget is representative of live play.
