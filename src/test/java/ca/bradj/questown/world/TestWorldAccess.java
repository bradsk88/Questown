package ca.bradj.questown.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TestWorldAccess implements QTWorldAccess {

    // Block int properties: pos -> (propertyName -> value)
    private final Map<BlockPos, Map<String, Integer>> blockProperties = new HashMap<>();
    // Max property values: pos -> (propertyName -> maxValue)
    private final Map<BlockPos, Map<String, Integer>> maxProperties = new HashMap<>();
    // Configured drops per position
    private final Map<BlockPos, List<ItemStack>> drops = new HashMap<>();
    // Removed blocks (for assertions)
    private final Set<BlockPos> removedBlocks = new HashSet<>();
    // useItemOnBlock results (configurable)
    private final Map<BlockPos, Boolean> useItemResults = new HashMap<>();
    // canToolTransform results (configurable)
    private final Map<BlockPos, Boolean> toolTransformResults = new HashMap<>();
    // Transformations applied (for assertions)
    private final Set<BlockPos> transformationsApplied = new HashSet<>();
    // Sounds played (for assertions)
    private final List<SoundEvent> soundsPlayed = new ArrayList<>();
    // Positions where useItemOnBlock was called (for assertions)
    private final Set<BlockPos> itemUsedOnBlock = new HashSet<>();
    // Container contents: pos -> list of slots (each slot is an ItemStack)
    private final Map<BlockPos, List<ItemStack>> containers = new HashMap<>();
    // Smelting simulation
    private final Map<net.minecraft.world.item.Item, net.minecraft.world.item.Item> smeltRecipes = new HashMap<>();
    private final Map<BlockPos, Integer> smeltProgress = new HashMap<>();
    private static final int SMELT_TICKS = 200;

    // --- Builder-style setup methods ---

    public TestWorldAccess withBlockProperty(BlockPos pos, String name, int value, int max) {
        blockProperties.computeIfAbsent(pos, k -> new HashMap<>()).put(name, value);
        maxProperties.computeIfAbsent(pos, k -> new HashMap<>()).put(name, max);
        return this;
    }

    public TestWorldAccess withDrops(BlockPos pos, List<ItemStack> items) {
        drops.put(pos, items);
        return this;
    }

    public TestWorldAccess withUseItemResult(BlockPos pos, boolean result) {
        useItemResults.put(pos, result);
        return this;
    }

    public TestWorldAccess withToolTransformResult(BlockPos pos, boolean result) {
        toolTransformResults.put(pos, result);
        return this;
    }

    public TestWorldAccess withSmeltRecipe(net.minecraft.world.item.Item input, net.minecraft.world.item.Item output) {
        smeltRecipes.put(input, output);
        return this;
    }

    public TestWorldAccess withContainer(BlockPos pos, int slotCount) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < slotCount; i++) {
            slots.add(ItemStack.EMPTY);
        }
        containers.put(pos, slots);
        return this;
    }

    public TestWorldAccess withContainerSlot(BlockPos pos, int slot, ItemStack item) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null) {
            throw new IllegalStateException("No container at " + pos + ". Call withContainer first.");
        }
        slots.set(slot, item);
        return this;
    }

    // --- Assertion helpers ---

    public boolean wasBlockRemoved(BlockPos pos) {
        return removedBlocks.contains(pos);
    }

    public boolean wasTransformApplied(BlockPos pos) {
        return transformationsApplied.contains(pos);
    }

    public int getPropertyValue(BlockPos pos, String name) {
        Map<String, Integer> props = blockProperties.get(pos);
        if (props == null) return -1;
        return props.getOrDefault(name, -1);
    }

    public List<SoundEvent> getSoundsPlayed() {
        return Collections.unmodifiableList(soundsPlayed);
    }

    public boolean wasItemUsedOnBlock(BlockPos pos) {
        return itemUsedOnBlock.contains(pos);
    }

    public ItemStack getSlotContents(BlockPos pos, int slot) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        return slots.get(slot);
    }

    public int getContainerSize(BlockPos pos) {
        List<ItemStack> slots = containers.get(pos);
        return slots != null ? slots.size() : 0;
    }

    // --- QTWorldAccess implementation ---

    @Override
    public OptionalInt getBlockIntProperty(BlockPos pos, String propertyName) {
        Map<String, Integer> props = blockProperties.get(pos);
        if (props == null || !props.containsKey(propertyName)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(props.get(propertyName));
    }

    @Override
    public OptionalInt getMaxBlockIntProperty(BlockPos pos, String propertyName) {
        Map<String, Integer> props = maxProperties.get(pos);
        if (props == null || !props.containsKey(propertyName)) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(props.get(propertyName));
    }

    @Override
    public void setBlockIntProperty(BlockPos pos, String propertyName, int value) {
        blockProperties.computeIfAbsent(pos, k -> new HashMap<>()).put(propertyName, value);
    }

    @Override
    public List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool) {
        return drops.getOrDefault(pos, Collections.emptyList());
    }

    @Override
    public void removeBlock(BlockPos pos) {
        removedBlocks.add(pos);
    }

    @Override
    public boolean useItemOnBlock(ItemStack item, BlockPos pos) {
        itemUsedOnBlock.add(pos);
        return useItemResults.getOrDefault(pos, false);
    }

    @Override
    public boolean canToolTransformBlock(BlockPos pos, QTToolAction toolAction) {
        return toolTransformResults.getOrDefault(pos, false);
    }

    @Override
    public void applyToolTransformation(BlockPos pos, QTToolAction toolAction) {
        transformationsApplied.add(pos);
    }

    @Override
    public <T> List<T> getShuffledCopy(Collection<T> items) {
        return new ArrayList<>(items);
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound) {
        soundsPlayed.add(sound);
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound, SoundSource source) {
        soundsPlayed.add(sound);
    }

    @Override
    public boolean isContainer(BlockPos pos) {
        return containers.containsKey(pos);
    }

    @Override
    public int getContainerSlotCount(BlockPos pos) {
        List<ItemStack> slots = containers.get(pos);
        return slots != null ? slots.size() : 0;
    }

    @Override
    public ItemStack getContainerSlot(BlockPos pos, int slot) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        return slots.get(slot);
    }

    @Override
    public boolean insertIntoSlot(BlockPos pos, int slot, ItemStack item) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null) {
            return false;
        }
        slots.set(slot, item);
        return true;
    }

    @Override
    public ItemStack extractFromSlot(BlockPos pos, int slot, int count) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = slots.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = existing.split(count);
        if (existing.isEmpty()) {
            slots.set(slot, ItemStack.EMPTY);
        }
        return extracted;
    }

    @Override
    public boolean insertIntoContainer(BlockPos pos, ItemStack item) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null) {
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            ItemStack existing = slots.get(i);
            if (existing.isEmpty()) {
                slots.set(i, item.copy());
                return true;
            }
            if (existing.sameItem(item) && existing.getCount() < existing.getMaxStackSize()) {
                existing.grow(item.getCount());
                return true;
            }
        }
        return false;
    }

    @Override
    public void advanceProcessing(BlockPos pos, int ticks) {
        List<ItemStack> slots = containers.get(pos);
        if (slots == null || slots.size() < 3) {
            return;
        }
        ItemStack input = slots.get(0);
        ItemStack fuel = slots.get(1);
        if (input.isEmpty() || fuel.isEmpty()) {
            return;
        }
        net.minecraft.world.item.Item output = smeltRecipes.get(input.getItem());
        if (output == null) {
            return;
        }
        int progress = smeltProgress.getOrDefault(pos, 0) + ticks;
        while (progress >= SMELT_TICKS) {
            progress -= SMELT_TICKS;
            ItemStack resultSlot = slots.get(2);
            if (resultSlot.isEmpty()) {
                slots.set(2, new ItemStack(output, 1));
            } else if (resultSlot.getItem() == output && resultSlot.getCount() < resultSlot.getMaxStackSize()) {
                resultSlot.grow(1);
            } else {
                break;
            }
            input.shrink(1);
            if (input.isEmpty()) {
                slots.set(0, ItemStack.EMPTY);
                break;
            }
        }
        smeltProgress.put(pos, progress);
    }

    @Override
    public boolean isAir(BlockPos pos) {
        // NOTE: isAir is not configured in TestWorldAccess. Returns false by default.
        return false;
    }

    @Override
    public List<ItemStack> chopTree(BlockPos trunkPos) {
        // NOTE: chopTree is not yet implemented in TestWorldAccess.
        // Add implementation here when arborist rules are tested.
        throw new UnsupportedOperationException("chopTree not implemented in TestWorldAccess");
    }

    @Override
    @Deprecated(forRemoval = true)
    public @Nullable ServerLevel asServerLevel() {
        return null;
    }
}
