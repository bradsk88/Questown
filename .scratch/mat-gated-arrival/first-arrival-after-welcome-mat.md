---
title: Gate the first villager's arrival on the welcome mat; move the gate quest to day one, pre-first-sleep
status: implemented
created: 2026-10-10
implemented: 2026-10-11
---

> **IMPLEMENTED 2026-10-11, with one dev-ruled deviation (Order B).** This
> doc's "Proposed design" had the mat BEFORE the campfire (chest → mat →
> flag-UI → campfire → sleep). The dev explicitly ruled campfire first:
> **campfire → mat → flag-UI → sleep → sign** — so the implemented enum
> order is `CHEST → WAND_ON_CAMPFIRE → PRESSURE_PLATE → FLAG_UI →
> SUNSET_AND_MAP → SIGN`, and the acceptance criterion "PRESSURE_PLATE and
> FLAG_UI precede WAND_ON_CAMPFIRE" does NOT hold. Everything else landed as
> designed: campfire-batch toast reward (`CampfireReadyReward`), gate batch
> with `registerWelcomeMat` as the canonical TOWN_GATE completion, mat
> gating the first arrival, basics batch (`AddBasicsBatchReward` →
> `QuestsHolder.addBasicsBatch`) posted on mat completion, wooden sword in
> the hunter-gatherer batch, `visitorSpot` mat fallback in `TownPois.tick`,
> F-scenario fixtures re-ordered (F1/F1-90/F2/F3/F4/wand-on-unlit),
> `VillageGrowthExecutor` comment updates, the doc sweep, and the day-one
> flow source map in `docs/onboarding-chicken/REQUIREMENTS.md`. Chicken-arc
> autotest: 13/14 — all six previously known-failing F-scenarios pass except
> `skip_chicken_command` (a pre-existing baseline failure unrelated to beat
> order: the `/qt flag … skip-chicken` path never set the
> `chicken-ever-spawned` bookkeeping bit).
> **F2 fixture trap found while verifying:** the autotest fake player
> cannot be moved with `/tp` or `teleportTo` — same-level
> `ServerPlayer.teleportTo` routes through `connection.teleport(...)`, and
> NeoForge's `FakePlayer.tick()` is empty, so the pending teleport never
> applies. The `MovePlayer` scripted action uses `setPos` instead.


## Context

Design discussion (2026-10-10), spawned from noticing the first gatherer in
`docs/player-first-ten-days.md` "leaves" before any town gate exists. Intent:
an item literally called **welcome mat** should set the expectation that
someone will arrive — so the gate is built *first*, and the first arrival is a
morning reward earned *because* the gate exists.

### Decisions (2026-10-10, dev rulings)

1. **Arrival gate = mat registered only** (the flag's registered-mat list,
   `getWelcomeMats()` non-empty — the same condition the mat beat already uses).
   NOT the full gate structure (mat flanked by walls + torch,
   `TownCycle.findTownGate`): that is the right end-state but requires
   multi-block structure UX we don't have yet.
2. **Campfire quest reward** = a place to sleep + an advancement. (Verified:
   wand-on-campfire sleep is already open on any *registered* lit campfire —
   within `CAMPFIRE_SEARCH_RADIUS` of the flag, unlit→light is a separate wand
   branch — so this reward communicates "the campfire is your camp" via toast +
   advancement; it is not a new mechanical unlock.)
3. **Welcome-mat completion gives two rewards**:
   - (A) first villager, **rewarded at the next morning tick**;
   - (B) a new town quest batch — the "basics": **storage room + job board** —
     to kill time until evening.
4. **Flag-menu intro happens right after the mat quest**: the player is
   introduced to the quests UI on the flag; the flag menu opens on the
   villagers view, which at that point says **"a villager will arrive in the
   morning"** — the information they need, plus an invitation to work the open
   basics quests.

### Verified current mechanics (all 2026-10-10)

- **Beat order** (`ChickenBeatState`): wand → wall → door → wand-on-door →
  room-build → chest → **campfire → first sleep** → sign → **welcome mat** →
  free-food → villager-UI → flag-UI → seeds → done. Mat is currently *after*
  the first sleep; flag-UI is *after* villager-UI.
- **First arrival is campfire-gated**: the flag-placement batch
  (`TownQuests.setUpQuestsForNewlyPlacedFlag`) attaches
  `defaultQuestCompletionRewards` (first `SpawnVisitorReward`) to
  `special_quest.campfire`; it completes via `TownPois.campfireFound` and
  fires at the next morning tick. Sleeping without a mat still gets a villager
  today.
- **The TOWN_GATE quest sits in the PHASE_KICKOFF batch** (job-board +
  store-room + gate + sword), whose reward is a *second* spawn. Completion
  paths: (a) POI structure — mat flanked by walls with a torch above
  (`townGateFound`); (b) room-recipe — mat inside a registered room, on
  re-detection. Neither is "mat registered as a fixture".
- **The chicken's mat beat is the fixture condition**: `welcomeMatPlaced()` =
  `!flag.getWelcomeMats().isEmpty()`.
- **Arrival position already prefers the mat** (`TownPois.tick`): mat/gate >
  campfire > flag fallback. Edge: mat registered but the structure doesn't
  resolve → `visitorSpot` stays null → spawn at the flag.
- **The "arriving in the morning" state already exists and persists**:
  `MCDelayedReward` → `TownFlagBlockEntity.addMorningReward` →
  `MCMorningRewards` (NBT-serialized; the `MCAsapRewards` queue is the
  transient second half, popped at `morningTick`). The flag BE already exposes
  `hasVillagerArrivingInMorning()`.
- **The flag menu already opens on the villagers view**: `MultiStatusMenu`
  returns `FlagMenus.villagersMenu`; `FlagTabs` shows the *other* tabs
  (`allExcept(VILLAGERS)`).
- Enum **reordering is safe** for persisted arcs (`ChickenBeatState` javadoc:
  persisted value is the name; the controller advances to the
  lowest-numbered incomplete state).

## Proposed design

**Day one, new order:**
wand → wall → door → wand-on-door → room-build → chest → **mat** →
**flag-UI** → campfire → first sleep → sign (job board) → free-food →
villager-UI (first villager has arrived) → seeds → done.

1. **Reorder the arc**: `WAITING_FOR_PRESSURE_PLATE` moves ahead of
   `WAITING_FOR_WAND_ON_CAMPFIRE` (right after the chest beat — the shell
   exists, the gate spot is in it); `WAITING_FOR_FLAG_UI` moves to right after
   the mat (the quests-UI introduction moment).
2. **Day-one quest batches at flag placement**:
   - campfire batch: `{campfire}` → reward: advancement + "your camp is ready"
     toast (teaches wand-on-campfire sleep).
   - gate batch: `{town_gate}` → reward: `MCDelayedReward(SpawnVisitorReward)`
     + add the basics batch.
   - The TOWN_GATE quest's completion gains a **fixture path**: marking
     complete when `registerWelcomeMat` fires (mat registered with the flag),
     per decision 1. The structure path (`townGateFound`) and the room-recipe
     path can stay or go — the fixture path is now the canonical one for the
     quest.
3. **Basics batch** (added by the gate reward, town-owned): `{store_room,
   job_board}` (+ see Open decisions re: the wooden-sword quest). The
   store_room quest may backfill-complete immediately (the chicken already
   dropped the chest in the shell room) — the batch then rides on the
   job-board quest, which the sign beat finishes pre-sleep. The old
   PHASE_KICKOFF mechanism (board + store + gate + sword, `tryPhase1_…`,
   `VILLAGER_KICKOFF_QUESTS`) is replaced by this; the sword quest's home is an
   open decision below.
4. **Villagers view line**: when `hasVillagerArrivingInMorning()`, the flag
   menu's default (villagers) view shows "a villager will arrive in the
   morning." Data source and persistence already exist; this is render-only.
5. **Arrival position edge**: when a mat is registered but `findTownGate`
   doesn't resolve, set `visitorSpot` to the mat position instead of leaving it
   null (flag fallback).

### Mat-registered vs leaver workability (keep distinct)

Decision 1 gates *arrival* on the fixture. Leavers' **job sites** still need
the mat **inside a registered room** (the `special_quest.town_gate` room —
"loose mat produced `getJobSites()=0`", `VillageSelfSufficiencyExecutor`). The
chicken beat teaches placement in the shell room, so both are normally
satisfied together; a player who parks the mat in the open gets the arrival but
a gatherer who can't work (`NO_JOBSITE`, need bubble explains). This is the
unlock ≠ workability principle at work, not a bug to close.

## Acceptance criteria

- A player who lights the campfire and sleeps **without** placing the welcome
  mat wakes to an **empty** town; the chicken is at the gate beat; the campfire
  quest's reward (advancement + toast) fired on lighting.
- Placing the welcome mat (registered with the flag) yields: the first
  villager arriving at the **next morning** at the gate/mat position, and the
  basics batch (storage, job board) open on the quests UI.
- After mat completion, opening the flag menu lands on the villagers view,
  which shows the "a villager will arrive in the morning" line while the spawn
  is pending.
- Mat-registered-then-restart: the pending spawn survives (morning-reward queue
  persistence) and the line still shows.
- `WAITING_FOR_PRESSURE_PLATE` and `WAITING_FOR_FLAG_UI` precede
  `WAITING_FOR_WAND_ON_CAMPFIRE` in `ChickenBeatState`;
  `ChickenArcTransitionsTest` and the F-scenario fixtures in
  `ChickenArcBlueprintRegistry` pass on the new order; a mid-arc pre-reorder
  save re-sequences without corrupting.
- **Doc sweep in the same commit as the code**: the four onboarding docs under
  Impact (`onboarding-chicken/REQUIREMENTS.md`, `onboarding-v4.md`,
  `onboarding-player-experience.md`, `player-first-ten-days.md") plus the
  `questown-held-run` skill are updated to the mat-before-sleep order, and the
  sweep produces a single **day-one flow source map** section in
  `docs/onboarding-chicken/REQUIREMENTS.md` naming every surface that defines
  the flow (beat enum, `TownQuests` day-one batches, POI listeners,
  executor comments, the docs, the skill) — so the next narrative or
  diagnosis has one oracle instead of re-deriving from seven. Leaving the
  docs stale is exactly how the gate-precondition confusion that motivated
  this issue got created.

## Open decisions

- **Dawn-without-mat legibility**: empty town + chicken at the gate beat is the
  minimum. Optional morning toast ("No one can find the town yet — it has no
  gate.").
- **Where the chicken's gate spot is** in the new earlier position: validate
  the shell plan (`ChickenRoomShellPlan` / `HelperChickenBeatOffsets`) — does
  the shell the chicken builds frame the mat spot so the placement reads as
  "the gate"?
- **Advancement**: new one for the campfire, or reuse an existing
  tutorial/trigger advancement?

## Ruled defaults (revise if the dev disagrees)

- **Wooden-sword quest**: the dev's basics list was explicitly storage + job
  board, so the sword leaves day one. Its flavor ("each hunter carries one")
  is day-two-meaningful, so it moves into the hunter-phase batch (raw food +
  stone sword + job change + bedroom). Day-one kill-time is then: job board
  (the store quest backfill-completes on the chicken's chest).

## Impact

- `TownQuests` (day-one batch composition, TOWN_GATE completion, the
  PHASE_KICKOFF phase replaced by the basics batch, `VILLAGER_KICKOFF_QUESTS`,
  `tryPhase1_campfireToKickoff` trigger).
- `TownFlagBlockEntity` / `TownPois` (`registerWelcomeMat` → quest
  completion; `visitorSpot` edge case).
- `ChickenBeatState` order (+ `ChickenArcTransitions`,
  `ChickenArcConditions`, `AgentBeatGuidance` wording,
  `ChickenArcBlueprintRegistry` F-scenario beat bits).
- GUI: the flag menu's villagers view — render
  `hasVillagerArrivingInMorning()` (seam: `FlagMenus.villagersMenu` /
  `MultiStatusMenu`).
- `VillageGrowthExecutor.act()` (its welcome mat is placed in the open — under
  arrival gating the mat must register with the flag for a spawn to happen;
  check the scenario's `StoreParentOnNBT` path does that).
- Docs to update after implementation (they encode mat-after-sleep order):
  `docs/onboarding-chicken/REQUIREMENTS.md`, `docs/onboarding-v4.md`,
  `docs/onboarding-player-experience.md`, `docs/player-first-ten-days.md`
  ("Morning" paragraph).

## Notes

- `village/self_sufficiency` seeds the gate room already (mat in a registered
  shell) — unaffected.
- `MCAsapRewards` has a stub `Serializer` ("TODO: Implement and use") — the
  *morning* queue is persisted, the *asap* queue is transient by design
  (pop-to-claim at `morningTick`); no new persistence work is implied by this
  issue, but don't "fix" that TODO as part of it.
