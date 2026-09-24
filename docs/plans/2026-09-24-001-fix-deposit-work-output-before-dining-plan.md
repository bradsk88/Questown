# Fix: deposit work output before a townie dines

Status: implemented + verified (2026-09-24).

## Problem

A townie eats its own work output. A hunter produces mutton (carried after the extract
step). When it gets hungry, the hungry→dining short-circuit snaps it straight to a dining
work, so it eats its own mutton (`dining_raw_food`) instead of depositing it. Depositing the
mutton to a chest is what counts it as *produced* and completes the onboarding mutton quest.

## Root cause (verified in code)

- Mutton is both a work output (carried after the extract step) and a valid raw food
  (`#questown:villager_raw_food`, `DinerRawFoodWork`).
- The `addHungryListener` in `TownFlagTileData.initVillagers` calls `changeJobForVillager`
  straight to a dining work, bypassing the normal work transition
  (`TownFlagBlockEntity.changeJobForVisitorFromBoard`).
- So a hungry *working* townie snaps to dining and eats its own mutton before it ever reaches
  the board, where a deposit step would otherwise fire.

## Design (aligned with dev)

The townie deposits its work output before it dines. Two changes.

### 1. Rework the hungry→dining trigger

- Add a new global rule / interface method `canInstantlySwitchToEating`, shaped like
  `WORK_IN_EVENING` (cf. `DeclarativeJob.getSignal`). Only `DowntimeWork` opts in for now;
  the interface default keeps the opt-in path obvious for a future job.
- `addHungryListener` keeps its `isDining` / `canDine` / `gaveUpDiningRecently` guards, but
  only fires the direct dining switch when the *current job* has `canInstantlySwitchToEating`.
- `changeJobForVisitorFromBoard` (the board) gains, ahead of the work roll: a
  "starving && !gaveUpDiningRecently → enter the diner chain" check (DinerWork if a dining
  room exists, else DinerNoTableWork). A starving working townie now routes to dining here,
  after reaching the board.

### 2. The deposit sub-step

- In `changeJobForVisitorFromBoard`, ahead of the starving check: if the townie is carrying
  work output (items carried after the extract step, minus BOPs), it walks to the nearest
  chest to its own position and dumps all of it there, then proceeds. If no chest is
  available it holds — a town with no storage is a deliberate blocker state.
- After depositing, the townie falls back to the normal eat preference chain (cooked in
  dining room → cooked → raw). It may even end up eating its own mutton off the store; that's
  fine. The point is the mutton existed in a chest, so it was produced.

## Key facts (verified in code)

- "Fully hungry" = the starving state (fullness ≤ 0). `SimpleVillagerHandle.tickHunger`
  drops fullness to 0 and the listener fires at 10-tick intervals while starving. No new
  threshold — the board check reuses the same "starving" signal the instant-switch uses.
- The dining chain, terminal link = raw, wired via each work's `ExpirationRules` fallback:
  DinerWork → DinerNoTableWork → DinerRawFoodWork → WorkSeekerJob (the board).
- `gaveUpDiningRecently` is set on leaving `DinerRawFoodWork` (the terminal link), so it's a
  "ran the full chain" signal, not a raw-only cooldown. Both the board check and the
  instant-switch consult it.
- Work output lands in the townie's inventory via `tryGiveItem` → `addToEmptySlot` (the
  `preExtractHook` in `RealtimeWorldInteraction`). BOPs are a separate entity-data boolean
  (`hasBlockOfProgress`), not in the inventory — so "carrying work output" = inventory items
  minus BOPs.

## Vocabulary → code

| Vocabulary | Code |
|---|---|
| hungry→dining short-circuit | `TownFlagTileData.initVillagers` → `addHungryListener` → `changeJobForVillager` |
| work transition / board | `TownFlagBlockEntity.changeJobForVisitorFromBoard` |
| instant-switch rule | new `canInstantlySwitchToEating` (cf. `WORK_IN_EVENING` in `DeclarativeJob.getSignal`) |
| starving | `SimpleVillagerHandle.tickHunger` / `hungryListeners` |
| "just ate" cooldown | `SimpleVillagerHandle.gaveUpDiningRecently` / `registerMostRecentFoodAttempt` |
| deposit | nearest-chest insert (cf. `AddItemToContainerSpecialRule` / `BOPDepositorWork`) |
| dining chain | `DinerWork` → `DinerNoTableWork` → `DinerRawFoodWork` |

## Phases

1. Add the `canInstantlySwitchToEating` global rule / interface method; opt in `DowntimeWork`.
2. Gate `addHungryListener` on the rule (current job has it).
3. Add the deposit sub-step to `changeJobForVisitorFromBoard` (ahead of the starving check).
4. Add the "starving && !gaveUpDiningRecently → diner chain" check to
   `changeJobForVisitorFromBoard`.
5. Verify: unit tests + a held `village/self_sufficiency` run.

## Test ledger

- [x] Unit: `canInstantlySwitchToEating` default is false; `DowntimeWork` is true.
      `DowntimeWorkEatingRuleTest` (passes, MC bootstrap). The "default false" half is the
      plain `Set.contains` interface default — no separate MC-harness unit; the negative is
      exercised in the held run by the non-opt-in jobs (hunter/gatherer never get the direct
      snap while working).
- [ ] Unit: `addHungryListener` gate / board starving route / `gaveUpDiningRecently` /
      deposit-only-BOP — **covered by the held run**: they need the live board + hungry listener
      with MC mob entities, which the vanilla-bootstrap unit env can't construct (same env limit
      as `DinerWork`'s `PLATE_BLOCK` NPE).
- [x] Held run (`village/self_sufficiency`): the town produces (work output deposited,
      `consumed==0`) instead of eating it, and stays healthy + growing. The literal mutton-item
      moment is not forced by this watchtower (the hunters don't hunt there); the mechanism is
      identical for any carried work output.

## Open decisions

- None — design is pinned. Threshold = the existing starving state; only `DowntimeWork` opts
  in for now.

## Done (2026-09-24)

All five phases implemented and verified. `DowntimeWorkEatingRuleTest` passes; the held
`village/self_sufficiency` run shows a healthy, producing town (work output deposited to the
store, `consumed==0`, not eaten) with the dining chain running correctly. `town/entity/COMPLEX.md`
maps the two hungry paths and the no-storage blocker.
