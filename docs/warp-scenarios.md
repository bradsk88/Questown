# Time Warp Scenarios

This document describes key producer-consumer job patterns that the time warp
system must handle correctly.

## Scenario Types

### 1. Simple Producer (Gatherer)
**Example**: `gatherer/gather`

```
work_states:
  [0] ingredients: #questown:villager_food (×1)
  [1] work: 1
  [2] time: 2000
result: biome_loot
```

**Flow**: Collect food → Do work → Wait (leave town) → Return with loot

**Warp behavior**:
- State 0: Collect food from container
- State 1: Simulate work tick
- State 2: Wait for time to pass (WAITING_FOR_TIMED_STATE)
- Extract: Generate biome loot, add to inventory
- Drop: Put loot in container

---

### 2. Simple Transformer (Crafter)
**Example**: `crafter/stick`

```
work_states:
  [0] ingredients: #minecraft:saplings (×1)
  [1] work: 5
result: minecraft:stick (×1)
```

**Flow**: Collect sapling → Work at crafting table → Produce stick

**Warp behavior**:
- State 0: Collect sapling from container
- State 1: Simulate work (5 ticks)
- Extract: Generate stick
- Drop: Put stick in container

---

### 3. Multi-Input Transformer (Baker)
**Example**: `baker/bread`

```
work_states:
  [0] ingredients: minecraft:wheat (×2)
  [1] ingredients: #minecraft:coals (×1)
  [2] time: 1000
result: minecraft:bread (×1)
```

**Flow**: Collect wheat → Collect coal → Wait (baking) → Produce bread

**Warp behavior**:
- State 0: Collect 2 wheat from container
- State 1: Collect 1 coal from container
- State 2: Wait for baking time
- Extract: Generate bread
- Drop: Put bread in container

---

### 4. Tool-Based Work (Smelter)
**Example**: `smelter/process_ore`

```
work_states:
  [0] ingredients: minecraft:iron_ore (×1)
  [1] tools: #questown:pickaxes, work: 20
result: minecraft:raw_iron (×2)
```

**Flow**: Collect ore → Use pickaxe to process → Produce raw iron

**Warp behavior**:
- State 0: Collect iron ore from container
- State 1: Need pickaxe in inventory, simulate 20 work ticks
- Extract: Generate 2 raw iron
- Drop: Put raw iron in container

**Note**: Tool is NOT consumed, remains in inventory.

---

### 5. Slot Inserter (Cook)
**Example**: `cook/simple_furnace_food`

```
work_states:
  [0] tools: minecraft:beef, work: 0.1
  [1] ingredients: minecraft:beef
special:
  - state 1: insert_into_slot_0
result: minecraft:air (extraction handled separately)
```

**Flow**: Check for beef tool → Insert beef into furnace slot 0

**Warp behavior**:
- State 0: Verify beef in inventory (tool check)
- State 1: Insert beef into furnace (special rule)
- Furnace handles cooking, separate `cook/extract` job retrieves result

**Note**: This is a "producer" for the furnace, which is an external processor.

---

### 6. World Modifier (Arborist)
**Example**: `arborist/cut_trees`

```
work_states:
  [0] tools: #questown:axes, work: 100
special:
  - EXTRACTING_PRODUCT: questown_vanilla:chop_down_tree
result: minecraft:air (drops come from tree)
```

**Flow**: Use axe to work → Chop tree (world modification)

**Warp behavior**:
- State 0: Need axe, simulate 100 work ticks
- Extract: Execute `chop_down_tree` rule (modifies world)
- Result items come from tree drop, not job result

**Warp limitation**: World modifications may be skipped or simplified during
warp since we're simulating, not executing real-time.

---

## Key Warp Challenges

### 1. Ingredient Collection
Each `ingredients` state requires:
- Finding items in town containers
- Removing from container
- Adding to villager inventory

### 2. Tool Requirements
`tools` states require:
- Checking villager has tool in inventory
- Tool is NOT consumed (stays in inventory)
- Tool may degrade (durability)

### 3. Time vs Work
- `work`: Decremented by work speed each tick
- `time`: Decremented by 1 each game tick (real time passage)

### 4. Special Rules
Some jobs have special rules that may not translate well to warp:
- `remove_from_world`: Villager leaves the loaded area
- `insert_into_slot_X`: Interact with block inventory
- `claim_workspot`: Prevent other villagers from using same spot
- `chop_down_tree`: World modification

### 5. Extraction
Extraction must:
- Generate result items based on `result` config
- Handle `biome_loot` (randomized based on biome)
- Handle item results (fixed item/quantity)
- Add items to villager inventory

### 6. Drop Loot
After extraction, villager must:
- Find container with space
- Transfer items from inventory to container

---

## Test Priority

1. **Simple Producer** - Gatherer (already tested, working)
2. **Simple Transformer** - Crafter (tested, working - no time state)
3. **Multi-Input** - Baker (tested, working - multiple ingredient states)
4. **Tool-Based** - Smelter (tested, working - tool NOT consumed)
5. **Slot Inserter** - Cook (tested, working - insert_into_slot special rule)
6. **World Modifier** - Arborist (may need special handling)
