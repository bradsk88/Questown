# Special Rules Decoupling Plan

This document outlines jobs using special rules and proposes `QTWorldAccess` methods
to make them warpable and testable.

> **Status: CLOSED (2026-06-08).** Every active job is single-tier and Tier-1 jobs
> are warp-green (verified by autotest). Phases 1–5 done; Categories 1–6 resolved.
> The only un-migrated held-item rules belong to `organizer/fetch`, which lives in
> `questown_job_archive/` and is structurally unloadable (see Category 6). No
> further decoupling work is required for any shipped job.

---

## Background

Special rules currently couple directly to Minecraft APIs. To support:
1. **Time Warp simulation** - Rules must execute against in-memory state
2. **Integration testing** - Rules must run without a live Minecraft server

We propose a **two-tier system**:
- **Tier 1 (QT-native)**: Rules using `QTWorldAccess` - fully warpable/testable
- **Tier 2 (MC-native)**: Rules using Minecraft directly - realtime only

Jobs cannot mix tiers. Mixed-tier jobs log errors at startup and during warp.

---

## Jobs by Special Rule Category

### Already Warp-Compatible (No MC Coupling)

These rules modify internal state only - no world interaction needed:

| Rule | Used By | Notes |
|------|---------|-------|
| `remove_from_world` | gatherer/*, hunter/*, miner/* | Villager "leaves" the world |
| `nullify_excess_results` | (various) | Caps carried items |
| `shared_work_status` | farmer/* | Internal job state sharing |
| `claim_spot` | (various) | Internal claim tracking |
| `always_consider` | cook/*, organizer/* | Job selection logic |
| `hunger_fill` / `hunger_fill_half` | gatherer/*, hunter/*, miner/* | Villager stat modification |
| `require_air_above` | farmer/till, farmer/plant_*, arborist/plant_* | Block query (snapshotable) |
| `no_experience_gained` | (various) | Internal stat logic |
| `work_in_evening` | (various) | Schedule logic |
| `slow_walk` | (various) | Navigation modifier |

**Conclusion**: These need no changes.

---

### Category 1: Crop/Plant Interaction

**Jobs affected:**
- `farmer/harvest_wheat` - uses `questown:harvest_crop`
- `farmer/plant_wheat` - uses `questown:use_last_inserted_item_on_block`
- `farmer/bone_meal` - uses `questown:use_last_inserted_item_on_block`
- `farmer/weed` - uses `questown:destroy_bush`

**Current MC coupling:**
- `CropBlock.getDrops()`, `CropBlock.AGE` property
- `BushBlock.getDrops()`, block removal
- `Item.useOn()` for planting/bone meal

**Proposed QTWorldAccess methods:**

```java
interface QTWorldAccess {
    // Crop state
    OptionalInt getGrowthStage(BlockPos pos);
    OptionalInt getMaxGrowthStage(BlockPos pos);
    void setGrowthStage(BlockPos pos, int stage);

    // Growth simulation (for warp)
    double getGrowthProbabilityPerTick(BlockPos pos);

    // Harvesting
    List<ItemStack> getBlockDrops(BlockPos pos, ItemStack tool);
    void removeBlock(BlockPos pos);

    // Planting
    UseItemResult useItemOnBlock(ItemStack item, BlockPos pos);
}
```

**UseItemResult** would capture:
- Whether the action succeeded
- What block state changed (if any)
- Whether the item was consumed

---

### Category 2: Soil/Block Transformation

**Jobs affected:**
- `farmer/till` - uses `questown:till_workspot`
- `farmer/compost` - uses `questown:compost_at_workspot`

**Current MC coupling:**
- `ToolActions.HOE_TILL`, `BlockState.getToolModifiedState()`
- `ComposterBlock.LEVEL`, `ComposterBlock.insertItem()`

**Proposed QTWorldAccess methods:**

```java
interface QTWorldAccess {
    // Tool transformation
    boolean canToolTransformBlock(BlockPos pos, String action);
    void applyToolTransformation(BlockPos pos, String action);

    // Composter (generalized as "processing block")
    OptionalInt getProcessingLevel(BlockPos pos);  // 0-8 for composter
    int getMaxProcessingLevel(BlockPos pos);
    void setProcessingLevel(BlockPos pos, int level);
    Optional<ItemStack> extractProcessedItem(BlockPos pos);  // bone meal when full
}
```

**Note**: The `String action` will use minecraft's ToolAction.get in the non-abstract
implementation. We expect modders to use the correct string and will run a check
during job definiton loading to ensure that ToolAction.get runs successfully, throwing
an exception if it does not.

---

### Category 3: Container Interaction

**Jobs affected:**
- `baker/stock_coal`, `baker/stock_wheat` - uses `add_item_to_container`
- `cook/stock_fuel`, `cook/stock_ingredients` - uses `add_item_to_container`
- `cook/fuel`, `cook/fish`, `cook/simple_furnace_food` - uses `insert_into_slot_*`
- `cook/extract` - uses `questown:take_from_slot_2`
- `organizer/fetch` - uses `add_item_to_container`

**Current MC coupling:**
- `Container.setItem()`, `Container.removeItem()`
- `ForgeCapabilities.ITEM_HANDLER`
- `BlockEntity` lookup

**Proposed QTWorldAccess methods:**

```java
interface QTWorldAccess {
    // Container operations
    boolean isContainer(BlockPos pos);
    int getContainerSlotCount(BlockPos pos);
    ItemStack getContainerSlot(BlockPos pos, int slot);
    boolean insertIntoSlot(BlockPos pos, int slot, ItemStack item);
    ItemStack extractFromSlot(BlockPos pos, int slot, int count);

    // Generic insertion (finds first available slot)
    boolean insertIntoContainer(BlockPos pos, ItemStack item);
}
```

---

### Category 4: Tree Operations

**Jobs affected:**
- `arborist/plant_sapling` - uses `questown_vanilla:check_tree_plantable`, `questown:use_last_inserted_item_on_block`
- `arborist/cut_trees` - uses `questown_vanilla:chop_down_tree`

**Current MC coupling:**
- `SaplingBlock`, `TreeFeature.place()` for plantability check
- `Block.asItem()`, recursive block removal for chopping
- `BuiltinRegistries.CONFIGURED_FEATURE` for tree configs

**Shipped QTWorldAccess methods** (see ADR-0005 and
`docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`):

```java
interface QTWorldAccess {
    // Plantability gate (dry run)
    boolean canTreeGrowAt(BlockPos pos, ItemStack sapling);

    // Generate the tree (writes blocks)
    boolean growTreeAt(BlockPos pos, ItemStack sapling);

    // Tree chopping — already in-memory
    List<ItemStack> chopTree(BlockPos trunkPos);

    // In-warp planted-sapling carrier, grown by GrowTreesWarpRule
    Collection<PlantedSapling> getPlantedSaplings();
    void clearPlantedSapling(BlockPos pos);
}
```

**Resolved design (no heuristic, no reconciliation).** Both `canTreeGrowAt` and
`growTreeAt` run the *real* `TreeFeature.place`; they differ only in the
`WorldGenLevel` adapter — `VoidLevel(level)` in realtime, `SnapshotWorldGenLevel`
in warp (reads snapshot-first, writes into `WarpWorldAccess`'s snapshot/dirty-set;
a dry-run mode discards writes for the plantability probe). So realtime and warp
geometry are identical — no "3x3 air heuristic" divergence.

Tree growth runs **in-memory during the warp** via the deterministic, elapsed-time
`GrowTreesWarpRule` (`questown_vanilla:grow_trees_warp`, a global warp-tick rule):
a sapling planted (or farm-seeded) during the warp grows once
`currentTick - plantTick >= TREE_GROWTH_TICKS`, and `cut_trees` can chop the
resulting logs from the same snapshot. `applyTo` commits everything atomically.
The earlier "place sapling, reconcile after `applyTo`" idea was **dropped** — it
couldn't deliver same-warp harvest (ADR-0005, "Considered and rejected").

**Reference example**: `SnapshotWorldGenLevel` is the decoupling doc's intended
example of complex world interaction (real worldgen) that behaves identically in
realtime and warp behind the `QTWorldAccess` seam.

---

### Category 5: Fishing

**Jobs affected:**
- `fisher/fish` - uses `questown_vanilla:deploy_and_retract_fishing_hook`

**Status: Already warp-compatible. No QTWorldAccess changes needed.**

`deploy_and_retract_fishing_hook` spawns a `FishingHook` entity as a
**visual-only** effect. It does not determine what fish are caught — the
job result comes from the normal result generator. The rule already skips
entity deployment when `asServerLevel()` returns null (warp/test contexts),
so fishing works correctly during warp without modification.

`DeployFishingHookRule` is intentionally **Tier 2** (does not implement
`QTNativeRule`): the cosmetic `FishingHook` entity genuinely requires a
`ServerLevel` to spawn, and `QTWorldAccess` deliberately has no
entity-spawn abstraction — inventing one for a visual-only hook would be
gold-plating. The hook is spawned in realtime via `afterInsertItem()` and
restored post-warp via `afterWarpRecovery(ServerLevel, …)` (the documented
visual-restoration seam, correctly typed as `ServerLevel`).

**Verified warp-green** end-to-end via autotest (2026-06-03): a targeted
`-Dquestown.autotest.only=fisher` run catches fish from the loot table
during warp (e.g. `[salmon, cod, cod, cod, pufferfish, cod]`),
`RESULT: 1/1 passed`. The autotest assertion stays a `wildcardExpectation`
(net gain ≥1 item) on purpose — the `gameplay/fishing` loot table is random
(fish/junk/treasure), so a specific-fish assertion would flake. Nothing
further to decouple here.

See `DeployFishingHookRule.afterInsertItem()` for the post-warp visual
restoration consideration (low priority).

---

### Category 6: Held Item Derivation (Organizer)

**Jobs affected:**
- `organizer/fetch` - uses `workspot_from_held_item`, `ingredients_from_held_item`, `tools_from_held_item`

**Current MC coupling:**
- These appear to inspect held items and derive job parameters
- Less world-coupled, more item-inspection logic

**Reviewed and confirmed (2026-06-07): these rules need no `QTWorldAccess`.**

All three operate purely on `StockRequestItem` NBT read off the villager's held
items — zero Minecraft world coupling, no `asServerLevel()`. All three implement
`QTNativeRule`.

- `ingredients_from_held_item` / `tools_from_held_item` (both =
  `IngredientsFromHeldItemSpecialRule`) override only `beforeInit` (fires in warp)
  → fully warp-safe.
- `workspot_from_held_item` (`WorkSpotFromHeldItemSpecialRule`): `beforeInit` +
  `afterDropLoot` are warp-fine, but its dynamic workspot routing lives in
  `beforeTick`, which does **not** fire in warp. This is **not** a decoupling
  (`QTWorldAccess`) gap — it's a hook-placement caveat. It is also **moot today**:
  the only consumer is `questown_job_archive/organizer_fetcher.json`, which
  `ResourceJobLoader` (bound to the `questown_jobs` subdirectory) can never load.
  If organizer/fetch is ever un-archived for warp, move that routing from
  `beforeTick` to a warp-firing hook (`beforeInit`/`onWarpTick`).

**Category 6 is closed — no work required for any active job.**

---

## Proposed QTWorldAccess Interface (Consolidated)

```java
public interface QTWorldAccess {

    // === Block State Queries ===
    boolean isBlockType(BlockPos pos, BlockCategory category);
    OptionalInt getBlockProperty(BlockPos pos, String propertyName);
    void setBlockProperty(BlockPos pos, String propertyName, int value);

    // === Crop Operations ===
    OptionalInt getGrowthStage(BlockPos pos);
    OptionalInt getMaxGrowthStage(BlockPos pos);
    void setGrowthStage(BlockPos pos, int stage);
    double getGrowthProbabilityPerTick(BlockPos pos);

    // === Block Manipulation ===
    List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool);
    void removeBlock(BlockPos pos);
    UseItemResult useItemOnBlock(ItemStack item, BlockPos pos);

    // === Tool Transformations ===
    boolean canToolTransformBlock(BlockPos pos, String toolAction);
    void applyToolTransformation(BlockPos pos, String toolAction);

    // === Processing Blocks (Composter, etc.) ===
    OptionalInt getProcessingLevel(BlockPos pos);
    int getMaxProcessingLevel(BlockPos pos);
    void setProcessingLevel(BlockPos pos, int level);
    Optional<ItemStack> extractProcessedItem(BlockPos pos);

    // === Container Operations ===
    boolean isContainer(BlockPos pos);
    int getContainerSlotCount(BlockPos pos);
    ItemStack getContainerSlot(BlockPos pos, int slot);
    boolean insertIntoSlot(BlockPos pos, int slot, ItemStack item);
    ItemStack extractFromSlot(BlockPos pos, int slot, int count);
    boolean insertIntoContainer(BlockPos pos, ItemStack item);

    // === Tree Operations ===
    boolean canTreeGrowAt(BlockPos pos, ItemStack sapling);
    TreeChopResult chopTreeAt(BlockPos pos, ItemStack tool);

    // === Fishing ===
    FishingSession startFishing(BlockPos stationPos, UUID villagerID);
    Optional<List<ItemStack>> checkFishingResult(FishingSession session, long ticksElapsed);
    void endFishing(FishingSession session);
}
```

---

## Implementation Strategy

### Phase 1: Create Interface and MC Implementation — **Done**

1. ✅ Define `QTWorldAccess` interface with methods for highest-priority jobs (farmer)
2. ✅ Create `MinecraftWorldAccess implements QTWorldAccess` that delegates to real world
3. ✅ Inject `QTWorldAccess` into special rule constructors/events

### Phase 2: Migrate Farmer Rules — **Done**

1. ✅ `HarvestCropSpecialRule` → use `getGrowthStage`, `getBlockDrops`, `setGrowthStage`
2. ✅ `UseLastInsertedItemOnBlockSpecialRule` → use `useItemOnBlock`
3. ✅ `DestroyBushSpecialRule` → use `getBlockDrops`, `removeBlock`
4. ✅ `TillWorkspotSpecialRule` → use `applyToolTransformation`
5. ✅ `CompostAtWorkspotSpecialRule` → use processing level methods

### Phase 3: Create Warp Implementation — **Done**

1. ✅ Created `WarpWorldAccess implements QTWorldAccess`
2. ✅ Backed by in-memory block state and container slot snapshots
3. ✅ Mutations tracked in dirty sets; `applyTo(ServerLevel)` writes back atomically
4. ✅ Used by all hook calls (PostInsertHook, PreExtractHook, PostExtractHook) and
   the warp-interleaved callback (WarpTickHook)

### Phase 4: Migrate Remaining Rules — **Done**

1. ✅ Container rules — `InsertIntoSlotSpecialRule`, `TakeFromSlotSpecialRule`,
   `AddItemToContainerSpecialRule` all use QTWorldAccess container methods;
   `WarpWorldAccess` handles them in-memory
2. ✅ Tree / planting rules:
   - ✅ `useItemOnBlock` — now fully in-memory in `WarpWorldAccess` for the live
     crop operations: seed `BlockItem`s plant at `pos.above()` (age 0) and bone
     meal advances the crop `age` by a deterministic +3, both routed through the
     snapshot/dirty-set so `applyTo` commits them only on a successful warp. The
     old real-world `Item.useOn` punch-through is gone (fixes the leak-on-abort and
     plant-then-no-grow parity bugs). Live consumers: `farmer_wheat_plant.json`,
     `farmer_global_bone.json`.
   - ✅ `chopTree` — already in-memory (snapshot + dirty-set recursion).
   - ✅ Arborist sapling placement + plantability are **un-archived and warp-green**
     (both JSONs now live in `questown_jobs/`; in-warp grow→chop verified end-to-end
     by autotest). See Category 4, ADR-0005, and
     `docs/plans/2026-06-02-001-feat-arborist-warp-unarchive-plan.md`.
3. ✅ Fishing rules — `deploy_and_retract_fishing_hook` is visual-only; already
   warp-compatible via null-guard on `asServerLevel()`
4. ✅ Held-item derivation (organizer) — reviewed; needs no `QTWorldAccess`.
   See Category 6 (the only consumer is archived and structurally unloadable).

### Phase 5: Third-Party Support — **Done**

1. ✅ `QTNativeRule` marker interface in `ca.bradj.questown.integration.jobs` —
   full javadoc explaining the tier system for modders
2. ✅ 20 Tier 1 rules implement `QTNativeRule`; 3 Tier 2 rules do not
   (`RandomShortLivedWorkSpot`, `CheckTreePlantable`, `DeployFishingHookRule`)
3. ✅ `SpecialRulesRegistry.finalizeForServer()` logs all Tier 2 rules at
   startup so the count and names are visible in the server log
4. ✅ `JobPhaseModifier` javadoc explains the tier system and links to
   `QTNativeRule`

---

## Jobs Summary Table

| Job | Special Rules | Tier Target | Blocking Methods Needed |
|-----|--------------|-------------|------------------------|
| farmer/harvest_wheat | harvest_crop | Tier 1 | getGrowthStage, getBlockDrops, setGrowthStage |
| farmer/plant_wheat | use_last_inserted_item_on_block | Tier 1 | useItemOnBlock |
| farmer/bone_meal | use_last_inserted_item_on_block | Tier 1 | useItemOnBlock |
| farmer/till | till_workspot | Tier 1 | applyToolTransformation |
| farmer/weed | destroy_bush | Tier 1 | getBlockDrops, removeBlock |
| farmer/compost | compost_at_workspot | Tier 1 | processing level methods |
| baker/* | add_item_to_container | Tier 1 | insertIntoContainer |
| cook/* | insert_into_slot_*, take_from_slot_* | Tier 1 | container slot methods |
| arborist/plant_sapling | check_tree_plantable, use_item | Tier 1 | canTreeGrowAt, useItemOnBlock |
| arborist/cut_trees | chop_down_tree | Tier 1 | chopTreeAt |
| fisher/fish | deploy_and_retract_fishing_hook | Tier 1 | (already compatible — hook is visual-only, skipped during warp) |
| gatherer/* | remove_from_world, hunger_fill | Tier 1 | (already compatible) |
| hunter/* | remove_from_world, hunger_fill | Tier 1 | (already compatible) |
| miner/* | remove_from_world, hunger_fill | Tier 1 | (already compatible) |
| organizer/fetch | various | Tier 1 | (review - likely compatible) |

---

## Warp-Interleaved Hooks

### Overview

Some world-level effects (like crop growth) need to happen
*between* villager warp steps during time warp, not as
per-villager job actions. Rather than hardcoding these into the
warp loop, jobs declare them as global rules that get interleaved
automatically.

### How It Works

1. **Declaration**: Jobs add a rule to their `global` rules in
   JSON — same as any other global rule like `claim_workspot`:

```json
"special": [
  {"type": "global", "rules": [
    "claim_workspot", "questown:crop_growth_warp"
  ]}
]
```

2. **Collection**: Before warp starts, `TownFlagState` collects
   all global rules from active villagers' jobs and deduplicates
   them. If 5 farmers all declare `crop_growth_warp`, it runs
   once.

3. **Execution**: The `AbstractAdvanceTime` warp loop calls a
   `WarpTickCallback` at each distinct tick boundary (before
   villager steps at that tick). The callback delegates to
   `WarpTickHook.run()`, which resolves rules via
   `SpecialRulesRegistry` and calls `onWarpTick()` on each
   `JobPhaseModifier`.

4. **Hook method**: `JobPhaseModifier.onWarpTick(X town,
   WarpTickEvent event)` — same pattern as `beforeExtract`,
   `beforeTick`, etc. Default returns town unchanged. Overrides
   apply world-level effects.

### Key Types

| Type | Location | Purpose |
|------|----------|---------|
| `WarpTickEvent` | `integration/jobs/` | Event record: world, currentTick, tickDelta, workBlockPositions |
| `WarpTickHook` | `jobs/declarative/` | Static hook utility (like PreTickHook) |
| `WarpTickCallback` | `town/AbstractAdvanceTime` | Generic callback interface for the warp loop |
| `GrowCropsWarpRule` | `jobs/special/` | First implementation: crop growth |

### Writing a Warp-Interleaved Hook (For Modders)

1. Create a class extending `JobPhaseModifier`
2. Override `onWarpTick(X town, WarpTickEvent event)`
3. Use `event.tickDelta()` to compute effects proportionally
   — don't assume any particular call frequency
4. Use `event.world()` (`QTWorldAccess`) for block operations
5. Use `event.workBlockPositions()` for known job block positions
6. Register via `SpecialRulesRegistry.registerSpecialRule()`
7. Add the rule name to relevant jobs' `global` rules in JSON

### Example: `GrowCropsWarpRule`

Simulates crop growth during warp using vanilla random tick
probability (3/4096 per tick per block). Iterates tracked work
block positions, checks for `"age"` property, and advances it
proportionally to `tickDelta`. Declared by all farmer jobs.

---

## Open Questions

1. **ItemStack type**: Should `QTWorldAccess` use Minecraft's `ItemStack` or a QT abstraction?
   - Lets start with ItemStack and make time travel our first target. 

2. **BlockPos type**: Same question for positions
   - Use <POS> generic whenever possible

3. **Warp delta application**: When warp ends, how do we batch-apply changes?
   - Immediate writes during warp. If a warp fails halfway through, 
     but the world was manipulated in the process, consider that a "success".

4. **Error handling**: What if a warp operation fails mid-simulation?
   - See above. Note that warp already gracefully handles exceptions. 
