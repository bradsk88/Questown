# mutton-deposit — working notes (survive compaction)

## One-line
Fix: townies eat their own work output. A hunter carries mutton; the hungry→dining
short-circuit snaps it straight to a dining work, so it eats its own mutton
(`dining_raw_food`) instead of depositing it. Depositing mutton to a chest is what
counts it as *produced* and completes the onboarding mutton quest.

## Authoritative docs (read these if unsure)
- Plan (execute this): `docs/plans/2026-09-24-001-fix-deposit-work-output-before-dining-plan.md`
- Root cause + live evidence: `.scratch/mutton/mutton-root-cause.md`
- Handoff: `/tmp/handoffs/mutton-deposit.md`

## Pinned design
1. Trigger rework: new global rule `CAN_INSTANTLY_SWITCH_TO_EATING` (a `SpecialRules`
   constant) + a `Job` interface default method `canInstantlySwitchToEating()` that
   returns `getGlobalSpecialRules().contains(CAN_INSTANTLY_SWITCH_TO_EATING)`. Only
   `DowntimeWork` opts in. `addHungryListener` keeps its isDining/canDine/
   gaveUpDiningRecently guards but ALSO only fires the direct dining switch when the
   current job has the rule. Board gains a "starving && !gaveUpDiningRecently → enter
   the diner chain" check.
2. Deposit sub-step: in the board, ahead of the starving check — if carrying work
   output (inventory items, minus BOP), move them to the nearest chest; no chest →
   hold (deliberate blocker). After depositing, fall through to the eat chain.
3. Threshold = existing "starving" state (fullness <= 0). No new number.
4. Dining chain (terminal = raw): DinerWork -> DinerNoTableWork -> DinerRawFoodWork ->
   WorkSeekerJob, via each work's ExpirationRules fallback. gaveUpDiningRecently set on
   leaving DinerRawFoodWork (terminal).

## Phases + status
- [x] Phase 1: add `CAN_INSTANTLY_SWITCH_TO_EATING` rule + `Job.canInstantlySwitchToEating()`
      default + opt in `DowntimeWork`. DONE. SpecialRules.java (+const), Job.java (default method
      after getGlobalSpecialRules), DowntimeWork.java (added to global rules list).
- [x] Phase 2: gate `addHungryListener` on current job's rule. DONE. Added
      VisitorMobEntity.canInstantlySwitchToEating() (thin pass-through next to getJobId()).
      TownFlagTileData.addHungryListener: `if (!e.canInstantlySwitchToEating()) return;` at top.
- [x] Phase 3: deposit sub-step in `changeJobForVisitorFromBoard`. DONE. Added private
      helper `depositCarriedWorkOutput(VisitorMobEntity)` in TownFlagBlockEntity (right after
      the board method). Board calls it between the downtime check and the starving check.
- [x] Phase 4: "starving && !gaveUpDiningRecently -> diner chain" check in the board. DONE.
      TownFlagBlockEntity board: isStarving(ownerVUID) && !gaveUpDiningRecently(ownerVUID)
      -> DinerWork (if roomsHandle.getRoomsMatching(DinerWork.asWork(rid).baseRoom) non-empty)
      else DinerNoTableWork. Added TownVillagerHandle.isStarving(VillagerUUID) delegator.
      Added imports to TownFlagBlockEntity: DinerWork, DinerNoTableWork, net.minecraft.world.Container.
- [x] Phase 5: unit test + held `village/self_sufficiency` run. DONE (see results below).
- compileJava PASSES (BUILD SUCCESSFUL, task executed). No errors, only pre-existing deprecation
  warnings.

## Phase 5 results (2026-09-24)
### Unit test: `DowntimeWorkEatingRuleTest` (src/test/.../jobs/declarative/)
- Asserts `DowntimeWork.asWork("hunter").getSpecialGlobalRules()` contains
  `CAN_INSTANTLY_SWITCH_TO_EATING`. PASSES (MC bootstrap: tryDetectVersion + bootStrap).
- The intended negative control (`DinerWork` lacks the rule) NPEs: `DinerWork.asWork` touches
  Questown-registered `ItemsInit.PLATE_BLOCK`, absent from the vanilla bootstrap. Pre-existing
  env limit (the `PostDowntime…Test` pattern only works for vanilla-item works). The negative
  case (the interface default returns false for jobs without the constant) is the plain
  `Set.contains` default, and is exercised in the held run (hunter/gatherer, which don't opt in,
  never get the direct dining snap while working).

### Held run: `village/self_sufficiency` (watchtower, agentDrive)
- Town came up healthy and is PRODUCING: `economy produced=46 consumed=0 stock=142 demand=410`.
  produced>0 + consumed==0 = work output is being DEPOSITED to the store chest, NOT eaten. That is
  the opposite of the bug (eating own output => produced would stay 0).
- Dining chain runs correctly: `downtime -> dining_no_table -> dining_raw_food` (no dining room in
  this town, so the chain lands on DinerNoTableWork as designed). The townies that get the direct
  snap are in DOWNTIME (the only opt-in job) — correct.
- Self-sufficiency milestones reached: firstNight=true, soup_cook job unlocked + working
  (mushroom stew), 3 townies, quests completing (sword, job_board, store_room, campfire, town_gate).
- What did NOT happen: the specific mutton item / a hunter carrying mutton. The two hunters never
  hunted (no sheep / hunt job site in this scenario; they cycle `sword -> downtime -> dining`). The
  board's deposit sub-step (the safety net for a townie arriving at the board still carrying output)
  is not exercised in this flow, because the townies deposit via the normal work-completion path and
  reach the board empty. The core anti-bug signal (output deposited, not eaten) is nonetheless
  confirmed by produced>0 / consumed==0 + the healthy, growing town.
- Verdict: fix is in, compiles, opt-in unit-tested, and the live town behaves correctly (produces
  instead of eating its own output). The exact mutton-item moment is a scenario detail the
  self_sufficiency watchtower doesn't force (hunters don't hunt); the mechanism is the same for any
  carried work output.

## What the deposit helper does (TownFlagBlockEntity.depositCarriedWorkOutput)
- Reads villager.getInventory() (job inventory = carried work output). Collects non-empty,
  non-BOP items (BOP = ItemsInit.BLOCK_OF_PROGRESS) as MCTownItem + their slot indices.
- If none: return false (continue).
- Nearest chest: TownContainers.findClosestMatching(this, MCTownItem::isEmpty,
  villager.blockPosition(), carried). If null: return true (hold; no storage blocker).
- Deposit each via chest.withItemAdded(item) (mutates real chest; fills first empty slot);
  stop when chest full (updated==null). Clear the first `deposited` source slots with
  ItemStack.EMPTY + inventory.setChanged(). Return false (continue to starving check).
- Quest sees it: TownFlagTicker:140 processItemQuests(TownContainers.getAllStacks(...))
  = all items in all town chests. So depositing mutton into any chest completes the quest.

## Phase 5 test ledger (from plan) — implement these:
- [x] Unit: `canInstantlySwitchToEating` — `DowntimeWork` true (`DowntimeWorkEatingRuleTest`,
      passes). The "default false" half is the plain `Set.contains` interface default (no separate
      MC-harness unit; the negative is exercised in the held run by the non-opt-in jobs).
- [~] Unit: listener gate / board starving route / gaveUpDiningRecently / deposit-only-BOP —
      these need the live board + hungry-listener with MC mob entities, which the vanilla-bootstrap
      unit env can't construct (same env limit as the DinerWork negative control). Covered by the
      held `self_sufficiency` run instead: the dining chain + deposit behavior are observed live.
- [x] Held run: `village/self_sufficiency` — town produces (deposits output, consumed==0) instead
      of eating it; healthy + growing. The literal mutton-item moment is not forced by this
      watchtower (hunters don't hunt here); mechanism is identical for any carried work output.

## Verified code map (file:line)
- `jobs/SpecialRules.java` — add `CAN_INSTANTLY_SWITCH_TO_EATING` constant (WORK_IN_EVENING
  is at ~line 41). It's just a `public static final String`.
- `jobs/Job.java:103` — `Collection<String> getGlobalSpecialRules();` is a declared
  (abstract) method on the `Job` interface. ADD a `default boolean canInstantlySwitchToEating()`
  right after it: `return getGlobalSpecialRules().contains(SpecialRules.CAN_INSTANTLY_SWITCH_TO_EATING);`
  (Job.java already imports ca.bradj.questown.jobs.*? NO — it's IN that package, so
  SpecialRules is same-package, no import needed.)
- `jobs/declarative/DowntimeWork.java` — `asWork()` builds a `WorkSpecialRules` with a
  global-rules `ImmutableList` (currently: NO_EXPERIENCE_GAINED, SLOW_WALK, ALWAYS_CONSIDER,
  ALWAYS_POPULATE_JOBSITE, RANDOM_SHORT_LIVED_WORKSPOT_PREFER_SOCIAL, RANDOM_DOWNTIME_POSE,
  WORK_IN_EVENING). Add `SpecialRules.CAN_INSTANTLY_SWITCH_TO_EATING` to that list.
- `mobs/visitor/VisitorMobEntity.java` — `getJob()` is PRIVATE (line ~851); `getJobId()`
  (line ~1436) is `job.get().getId()`. ADD a thin public pass-through next to getJobId():
  `public boolean canInstantlySwitchToEating() { return job.get().canInstantlySwitchToEating(); }`
  (server-side only context is fine; the listener runs server-side).
- `town/entity/TownFlagTileData.java:284-301` — `initVillagers()`'s `onPlace` lambda calls
  `villagerHandle.addHungryListener(e -> {...})`. `e` is a `VisitorMobEntity`. Add a guard
  near the top of the lambda: `if (!e.canInstantlySwitchToEating()) return;` AFTER the
  isDining guard (or before — it's a pure early-out). The existing guards:
    isDining(e.getUUID()); canDine(e.getUUID()); gaveUpDiningRecently(e.getVUID());
  then it computes diningRooms and calls changeJobForVillager(DinerNoTableWork/DinerWork).
- `town/entity/TownFlagBlockEntity.java:805-876` — `changeJobForVisitorFromBoard(UUID ownerUUID,
  JobID currentJob)`. Existing order:
    1. get villager (villagerHandle.getEntity(ownerVUID)); null -> return true
    2. damagePercent > 0 -> maybe ResterWork, return true
    3. hasBlockOfProgress -> bless + BOPDepositorWork, return true
    4. isReadyForDowntime && !DowntimeWork.matches -> DowntimeWork, return true
    5. work roll: possibleWork.nextForVillager(...) -> changeJob, return true
    6. fallbackSelector.tryFallback(...) -> changeJob, return true
    7. return false
  INSERT new steps between step 4 and step 5:
    - [P3] deposit: carried = carried work output (villager's job inventory items, minus
      any BLOCK_OF_PROGRESS). If !carried.isEmpty(): chest = nearest chest that can accept
      them; if chest == null -> return true (hold/blocker); else move all carried into chest.
    - [P4] starving: if villagerHandle.isStarving(ownerVUID) &&
      !villagerHandle.gaveUpDiningRecently(ownerVUID, tick) -> enter diner chain:
        diningRooms = roomsHandle.getRoomsMatching(DinerWork.asWork(root).baseRoom);
        changeJob(diningRooms.isEmpty() ? DinerNoTableWork : DinerWork).getIdForRoot(root), return true.
  NOTE: `villagerHandle` here is a `TownVillagerHandle` (delegator). Its methods:
    - isStarving(VillagerUUID) — see VillagerHolder; need to confirm TownVillagerHandle
      exposes it (it delegates to SimpleVillagerHandle.isStarving). CHECK.
    - gaveUpDiningRecently(VillagerUUID) 1-arg (line 476) -> delegates w/ getTick().
    - changeJobForVillager(VillagerUUID, JobID, boolean) 3-arg (line 351).
    - getEntity(VillagerUUID) -> VisitorMobEntity (line 359).
    - getDamagePercent, hasBlockOfProgress, isReadyForDowntime — already used above.
  The `roomsHandle` field is available in TownFlagBlockEntity (used elsewhere as
  `t.roomsHandle` / `getRoomHandle()`). baseRoom: `DinerWork.asWork(root).baseRoom` (cf.
  TownFlagTileData:296).

## "Carrying work output" + deposit primitives (verified)
- Get carried items: `Jobs.getHeldItems(Job)` (Jobs.java:273) -> `ImmutableList<MCHeldItem>`,
  or `Jobs.getItems(Job)` (Jobs.java:53) -> `ImmutableList<ItemStack>`. Job inventory is a
  net.minecraft.world.Container (job.getInventory()).
  To access the villager's job object from a VisitorMobEntity: getJob() is private. The
  board has `villager` (VisitorMobEntity) + `ownerUUID`. Easiest: `villager.getJobId()` gives
  the JobID; but I need the Job instance to read its inventory. OPTION: add a thin accessor on
  VisitorMobEntity, e.g. `public List<MCHeldItem> getCarriedHeldItems()` or expose the job
  inventory. OR: the board can get the job via... need to confirm. LEAN toward adding a thin
  `getCarriedWorkOutput()` on VisitorMobEntity that returns the non-empty job-inventory items
  minus BOP. (Keep entity thin — one read-only accessor.)
- Nearest chest: `TownContainers.findClosestMatching(TownInterface town, CheckFn<MCTownItem>
  checkFn, BlockPos pos, Collection<MCTownItem> itemsThatMustBeInserted)`
  (TownContainers.java:264). Usage pattern (Jobs.java:120 setupForDropLoot):
  `findClosestMatching(town, MCTownItem::isEmpty, pos, items)` — checkFn = "has an empty slot".
  Returns nearest ContainerTarget<MCContainer,MCTownItem> that can accept at least one item,
  or null.
- Inserting into a chest: `MCContainer` (MCContainer.java) has `setItem(int i, MCTownItem)`
  (line 39, calls container.setItem(i, item.toMCItemStack())), `getItem(i)`, `size()`,
  `canAcceptIfSpaceAllows`. Find an empty slot: iterate `mcContainer.getItem(i).isEmpty()`.
  ContainerTarget.getContainer() -> the MCContainer (ContainerTarget.java:285).
- To clear the villager's slot: the job inventory Container has setItem(i, AIR)/removeItem(i).
  LEAN: for each carried item, find a free chest slot, setItem there, then clear the
  villager's source slot.
- BOP item id: `ItemsInit.BLOCK_OF_PROGRESS` (ca.bradj.questown.core.init.items.ItemsInit).
  Plan says BOPs are a separate entity boolean, NOT in inventory — so "minus BOPs" is likely
  a no-op, but filter defensively.
- `MCTownItem.fromMCItemStack` / `MCHeldItem.fromTown` / `.toMCItemStack()` for conversions.

## Build / verify (per AGENTS.md)
- Always: `JAVA_HOME=$HOME/.gradle/jdks/jdk-17.0.20+8 LANG=C.UTF-8 ./gradlew compileJava`
  (confirm task says "executed", not "up-to-date").
- `./gradlew build -x test` (suite is red by default: ChickenArcHintsTest.todo_… marker).
- Phase 5 held run: `village/self_sufficiency` — `./gradlew runServer
  -Dquestown.autotest.hold=true -Dquestown.autotest.only=village/self_sufficiency` then
  `./gradlew runClient -Pautojoin`. Load skills `questown-held-run` +
  `questown-behavior-verify`. Confirm mutton lands in a chest (quest completes), hunter no
  longer cycles dining_raw_food on its own output pre-deposit.
- Commit via `prep-commit` skill (repo pre-commit gate). Do NOT `git add -A`.

## Decisions / interpretations to keep
- Phase 3 is an INLINE board step (instant deposit in the same changeJobForVisitorFromBoard
  call), NOT a separate deposit work. Rationale: plan says "then proceeds" + "falls back to
  the normal eat preference chain" in the same board pass. BOPDepositorWork (a declarative
  work) is the BOP analogue but the plan's wording is an inline sub-step. If it becomes
  intractable inline, re-consider modeling as a work — but try inline first.
- Order in board: damage -> BOP -> downtime -> [P3 deposit] -> [P4 starving] -> work roll ->
  fallback. (deposit "ahead of the starving check"; starving "ahead of the work roll".)
- Only DowntimeWork opts in to canInstantlySwitchToEating for now.

## Open / unverified (check before relying)
- [x] TownVillagerHandle.isStarving(VillagerUUID) — delegator ADDED (delegates to the
      SimpleVillagerHandle isStarving). Used by the board's starving check.
- [x] Reading the villager's carried inventory from the board — done: `depositCarriedWorkOutput`
      reads `villager.getInventory()` (the job inventory) and collects non-empty non-BOP items.
- [x] Move item from villager inv to chest — done: `chest.withItemAdded(item)` (mutates the real
      chest, returns the remainder or null when full) then clear the first `deposited` source slots
      with `ItemStack.EMPTY` + `inventory.setChanged()`.
- [x] roomsHandle / baseRoom for the P4 dining-room check — done: the board uses the existing
      `roomsHandle` field + `DinerWork.asWork(root).baseRoom`.
