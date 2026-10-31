# Plan: Fill the `TODO[Warp]` lane stubs in `TimeWarpWorldInteraction` (realtime ↔ warp parity)

Date: 2026-10-10
Status: **OPEN — filed, not started.** Filed as the deferred follow-on of
`2026-10-09-001-refactor-declarative-jobs-consolidation-plan.md`, whose scope note read:
"the 5 `TODO[WARP]` functional gaps in `TimeWarpWorldInteraction` (functional work, separate
plan)". The mechanical lane dedupe that was the other half of that note landed 2026-10-10
(`3f2f6909`), which is why this is filed now: the base class already owns the shared seams the
stubs would implement against.

## Why

The warp lane (`TimeWarpWorldInteraction`) still silently no-ops behaviours that the realtime
lane performs: unmet-need/unmet-room tracking, inserted-supply give-back bookkeeping,
state-change job switching, the supply-status predicate, and inserted-item history. Every
realtime-vs-warp parity bug that lands in these areas today has a warp lane that quietly drops
the behaviour behind a `// TODO[Warp]` comment instead of failing loudly. The count has grown
from the plan's "5 gaps" to 7 in-pair markers (plus 6 related out-of-pair `TODO[Warp]`s).

## Baseline (2026-10-10, `1.20.6-neoforge-wip`, after `3f2f6909` + `882dee55`)

- Full `:test`: **1112 tests, 46 skipped, 6 failed = exactly the `todo_…`
  harness-gated markers** (confirmed via suite XML, not gradle stdout).
- `stranglerCheck` is **red at HEAD for pre-existing reasons** — 3 files
  (`logic/RoomRecipes`, its test, `town/entity/TownQuests`) leaked 1.20+ APIs in
  `51f8bc9a`/`af202d27` (a `git revert` was in progress in-tree). Run `sh tools/strangler-check`
  before interpreting a `build` failure as caused by this plan's chunks; the gate's output
  paths are relative to `src/main/java/ca/bradj/questown/`.
- Agent commits are gated by the prep-commit hook (`AGENTS.md` "Commit gating"): staged diff →
  co-located `COMPLEX.md` first, token on the commit command line.

## Verified call-site chain (2026-10-10, grep-verified in code)

The four supply/need stubs are *called through shared code*, not just declared:

```
JobLogic (LZCD circuit driver)
  └─ worldBeforeTick.tryGrabbingInsertedSupplies()        JobLogic.java:157
  └─ worldBeforeTick.timesInserted()                      JobLogic.java:132, 202
  └─ worldBeforeTick.registerUnmetNeeds(...)              JobLogic.java:130, 199
  └─ worldBeforeTick.registerUnmetRooms()                 JobLogic.java:205
DeclarativeJobTickerDependencies.java:542-563
  └─ delegates each to job.getWorld()                     (DeclarativeJobTicker.java:171 owns the JobLogic)
TimeWarpWorldInteraction is constructed in WorksBehaviour + DeclarativeJobs
```

What this does **not** yet establish: whether the *warp-lane wiring* actually routes through
this `DeclarativeJobTicker`/`DeclarativeJobTickerDependencies` chain (vs. the warper's own
loop), and which `JobLogic` branches fire under warp inputs. That is C1. A TODO marker is an
entry point, not a conclusion — several of these stubs may be vestigial (the warp lane already
tracks inserted items via `postInsertHook`'s `withInsertedItem` + `recoverInsertedItems` /
`simulateRecoverInsertedItems`, which may already cover the give-back these two stubs name).

## Scope

**In:** the 7 in-pair markers in `TimeWarpWorldInteraction` (re-enumerate at C0; the 2026-10-10
set, pre-dedupe lines): `tryGrabbingInsertedSupplies`, `timesInserted`, `registerUnmetNeed`,
`registerUnmetRoom`, `preStateChangeHooks` ("Set Job"), `asInventory().getSupplyItemStatus`
(`(s) -> true` predicate), `preExtractHook` (inserted-item history).

**Not in (follow-on, reassess after C1):** the 6 out-of-pair `TODO[Warp]`s —
`MCTownState:176`, `TownStateSerializer:90` (load work states), `TownFlagState:120` / `:136`,
`DeclarativeJobs:433` (timers + "walk time"), `MutableEntityInvStateProvider:8`. `C1` may pull
one in only if a chunk here proves to require it.

## Chunks

| # | Chunk | Deliverable | Gate |
|---|-------|-------------|------|
| C0 | Baseline + re-enumerate | Fresh `grep -rn 'TODO\[Warp\]'` list with current lines; baseline build state | `:test` XML |
| C1 | **Lane-routing audit** | For each stub: who calls it on the warp lane (if anyone); disposition = (a) implement / (b) vestigial → delete / (c) documented-inert (keep, javadoc why it's a no-op on warp). **Disposition table appended to this plan** before any implementation chunk starts | grep + this plan updated |
| C2..Cn | One chunk per "implement" stub | Chosen shape below; moves are moves (no opportunistic refactors) | `./gradlew build` + named-suite XML per chunk |
| C∞ | Final gate | Full build; confirm only `todo_…` markers red; `stranglerCheck` state unchanged vs baseline | full log + XML |

**Design guidance (to be confirmed per stub during C1, not before):**

- The established warp-mirror pattern is **record on the `MCTownState` snapshot, settle at
  `TownFlagState.warp`** — the LOOT-quest close is the precedent (realtime closes the quest in
  `onLootExtracted`; warp's `recordLootExtract` records, the settle point applies it through
  the live handles, and both lanes yield the same quest outcome). `registerUnmetNeed` /
  `registerUnmetRoom` fit this shape naturally (settle through the economics handle at warp
  end). `Inputs` *does* carry `level` + `vUUID`, so a live economics call is also possible —
  but the `recordLootExtract` precedent chose the snapshot deliberately to keep the warper
  pure; follow the precedent unless C1 shows why not.
- `tryGrabbingInsertedSupplies` / `timesInserted`: check whether the warp NO_SUPPLIES path
  already uses `recoverInsertedItems` / `withInsertedItem` before "implementing" these — if so
  they are C1 disposition (b)/(c), not implementation work.
- `preStateChangeHooks` "Set Job": the job-state representation on `MCTownState`
  (per-villager job data) needs confirming against the job-change flow in `TownFlagState` /
  `MCTownState` before the callback is filled in.

## Vocabulary → code map

- "the two lanes" = the `AbstractWorldInteraction` subclasses:
  `RealtimeWorldInteraction` (live entity + `ProductionJournal`, `TOWN = Boolean` unit) and
  `TimeWarpWorldInteraction` (immutable `MCTownState` snapshot). Shared logic lives on the
  abstract base since `3f2f6909`.
- "settle point" = `TownFlagState.warp`'s end-of-warp application of snapshot records onto the
  live handles (the `recordLootExtract` path).
- "unmet need" = the economics registration (realtime: `EconomicsHandle.registerUnmetNeed` at
  `Util.getTick(level)` for the entity UUID) that drives town production priorities.
- "inserted supplies" = items the townie placed in the work block, pending completion —
  realtime: `VisitorMobEntity.WorkToUndo`; warp: `MCTownState.withInsertedItem` tracking.

## Test ledger

| Chunk | Gate | Result |
|-------|------|--------|
| C0 | `:test` XML | |
| C1 | grep + plan update | |
| C2..Cn | per chunk | |
| C∞ | full build + XML | |

## Open decisions

- C1 may demote stubs to vestigial/inert — acceptable to **delete** a dead override vs. keep it
  with a documented-inert javadoc (default proposal: delete; the base class already provides
  sane null/identity behaviour where a lane genuinely has nothing to do).
- Warp needs-tracking shape: snapshot-record (`recordLootExtract` precedent) vs live call
  (`Inputs.level` + `vUUID` available). Default: snapshot-record.
- Whether the 6 out-of-pair `TODO[Warp]`s join this plan or stay a follow-on (default:
  follow-on; C1 may promote `TownStateSerializer:90` if a chunk needs persisted work states).
- Whether any implemented stub earns a dedicated autotest scenario (warp parity is normally
  proven by the held `warpTime` autotest patterns) vs. unit tests on the new `MCTownState`
  record/settle seam.
