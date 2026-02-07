# Special Rules Decoupling Plan

This document outlines jobs using special rules and proposes `QTWorldAccess` methods
to make them warpable and testable.

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

**Proposed QTWorldAccess methods:**

```java
interface QTWorldAccess {
    // Tree planting validation
    boolean canTreeGrowAt(BlockPos pos, ItemStack sapling);

    // Tree chopping
    TreeChopResult chopTreeAt(BlockPos pos, ItemStack tool);
}

record TreeChopResult(
    List<ItemStack> drops,
    int blocksRemoved,
    int toolDamage
) {}
```

**Note**: `canTreeGrowAt` is complex - it simulates world gen. The MC implementation
would actually run tree generation checks. This allows for even complex tree shapes
to "really get planted" by villagers. The warp implementation could use a simplified 
heuristic (e.g., "3x3 air above = valid") for mid-warp calculations and, if a new 
tree "was planted" during the warp, we should decide how many trees were planted and 
schedule post-warp code that will actually check the spots to confirm if they are 
plantable and plant the N trees that would have remained planted after the warp.

**Meta-Note**: The note above reveals that this would be a great example for modders
to use as a reference for when complex world-interaction is required. We should take
care to make it a good reference that is easy to follow even though it achieves 
complex results.

---

### Category 5: Fishing

**Jobs affected:**
- `fisher/fish` - uses `questown_vanilla:deploy_and_retract_fishing_hook`

**Current MC coupling:**
- Custom `FishingHook` entity spawning
- `ServerLevel.addFreshEntity()`, entity removal
- Position/vector math for hook placement

**Proposed QTWorldAccess methods:**

```java
interface QTWorldAccess {
    // Fishing abstraction
    FishingSession startFishing(BlockPos stationPos, UUID villagerID);
    Optional<List<ItemStack>> checkFishingResult(FishingSession session, long ticksElapsed);
    void endFishing(FishingSession session);
}
```

**Note**: Fishing is heavily entity-based. The abstraction hides entity management
entirely, treating it as a stateful "session" that produces results over time.

---

### Category 6: Held Item Derivation (Organizer)

**Jobs affected:**
- `organizer/fetch` - uses `workspot_from_held_item`, `ingredients_from_held_item`, `tools_from_held_item`

**Current MC coupling:**
- These appear to inspect held items and derive job parameters
- Less world-coupled, more item-inspection logic

**Proposed approach:**

These rules likely don't need `QTWorldAccess` - they operate on inventory/item data
which is already abstracted. Review implementation to confirm.

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

### Phase 1: Create Interface and MC Implementation

1. Define `QTWorldAccess` interface with methods for highest-priority jobs (farmer)
2. Create `MinecraftWorldAccess implements QTWorldAccess` that delegates to real world
3. Inject `QTWorldAccess` into special rule constructors/events

### Phase 2: Migrate Farmer Rules

1. `HarvestCropSpecialRule` → use `getGrowthStage`, `getBlockDrops`, `setGrowthStage`
2. `UseLastInsertedItemOnBlockSpecialRule` → use `useItemOnBlock`
3. `DestroyBushSpecialRule` → use `getBlockDrops`, `removeBlock`
4. `TillWorkspotSpecialRule` → use `applyToolTransformation`
5. `CompostAtWorkspotSpecialRule` → use processing level methods

### Phase 3: Create Warp Implementation

1. Create `WarpWorldAccess implements QTWorldAccess`
2. Backed by in-memory state snapshots
3. Reads from snapshot, writes to delta buffer
4. After warp, apply deltas to real world via `MinecraftWorldAccess`

### Phase 4: Migrate Remaining Rules

1. Container rules → container methods
2. Tree rules → tree methods
3. Fishing rules → fishing session methods

### Phase 5: Third-Party Support

1. Document tier system for modders
2. Add startup validation for mixed-tier jobs
3. Add warp-time logging for skipped Tier 2 jobs

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
| fisher/fish | deploy_and_retract_fishing_hook | Tier 1 | fishing session methods |
| gatherer/* | remove_from_world, hunger_fill | Tier 1 | (already compatible) |
| hunter/* | remove_from_world, hunger_fill | Tier 1 | (already compatible) |
| miner/* | remove_from_world, hunger_fill | Tier 1 | (already compatible) |
| organizer/fetch | various | Tier 1 | (review - likely compatible) |

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
