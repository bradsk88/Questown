package ca.bradj.questown.world;

import ca.bradj.questown.QT;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * An in-memory implementation of QTWorldAccess for use during time warp.
 * <p>
 * Snapshots relevant world state (block properties, container contents) at construction
 * time. All mutations go to in-memory maps. After warp succeeds, call
 * {@link #applyTo(ServerLevel)} to write changes back to the real world atomically.
 * <p>
 * {@link #asServerLevel()} returns {@code null} — warp rules must not rely on it.
 */
public class WarpWorldAccess implements QTWorldAccess {

    /** Simplified smelting result used internally and in tests. */
    record SmeltResult(ItemStack result, int cookingTime) {}

    // Keeps a private level for complex operations (drops, tool transforms)
    // but does NOT expose it via asServerLevel().
    private final ServerLevel level;

    private final Map<BlockPos, BlockState> blockStates = new HashMap<>();
    private final Map<BlockPos, List<ItemStack>> containerSlots = new HashMap<>();
    // cookProgress[0] = cookTime, cookProgress[1] = litTime
    private final Map<BlockPos, int[]> cookProgress = new HashMap<>();

    final Set<BlockPos> dirtyBlocks = new HashSet<>();
    final Set<BlockPos> dirtyContainers = new HashSet<>();

    private final Function<ItemStack, Optional<SmeltResult>> recipeResolver;
    private final Function<ItemStack, Integer> fuelTimeProvider;

    private static final int MAX_PROCESSING_TICKS = 13_000;

    public WarpWorldAccess(ServerLevel level, Collection<BlockPos> positions) {
        this.level = level;
        this.recipeResolver = ingredient -> level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new net.minecraft.world.SimpleContainer(ingredient), level)
                .map(r -> new SmeltResult(r.getResultItem().copy(), r.getCookingTime()));
        this.fuelTimeProvider = fuel -> ForgeHooks.getBurnTime(fuel, RecipeType.SMELTING);
        for (BlockPos pos : positions) {
            blockStates.put(pos, level.getBlockState(pos));
            snapshotContainer(pos);
        }
    }

    /**
     * Test constructor — initializes from pre-built maps with injected recipe and fuel resolvers.
     * Only for use in unit tests; does not snapshot from a real ServerLevel.
     */
    WarpWorldAccess(
            Map<BlockPos, BlockState> blockStates,
            Map<BlockPos, List<ItemStack>> containerSlots,
            Function<ItemStack, Optional<SmeltResult>> recipeResolver,
            Function<ItemStack, Integer> fuelTimeProvider
    ) {
        this.level = null;
        this.recipeResolver = recipeResolver;
        this.fuelTimeProvider = fuelTimeProvider;
        this.blockStates.putAll(blockStates);
        for (Map.Entry<BlockPos, List<ItemStack>> e : containerSlots.entrySet()) {
            List<ItemStack> copy = new ArrayList<>();
            for (ItemStack s : e.getValue()) copy.add(s.copy());
            this.containerSlots.put(e.getKey(), copy);
            this.cookProgress.put(e.getKey(), new int[]{0, 0});
        }
    }

    private void snapshotContainer(BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container c)) {
            return;
        }
        int size = c.getContainerSize();
        List<ItemStack> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(c.getItem(i).copy());
        }
        containerSlots.put(pos, slots);

        if (be instanceof AbstractFurnaceBlockEntity) {
            // Cook progress starts at 0 — any mid-cook state is restarted during warp simulation.
            cookProgress.put(pos, new int[]{0, 0});
        }
    }

    /**
     * Writes all dirty in-memory state back to the real world.
     * Call once after warp completes successfully.
     */
    public void applyTo(ServerLevel target) {
        for (BlockPos pos : dirtyBlocks) {
            BlockState bs = blockStates.get(pos);
            if (bs != null) {
                target.setBlock(pos, bs, 10);
            }
        }
        for (BlockPos pos : dirtyContainers) {
            BlockEntity be = target.getBlockEntity(pos);
            if (!(be instanceof Container c)) {
                continue;
            }
            List<ItemStack> slots = containerSlots.get(pos);
            if (slots == null) {
                continue;
            }
            for (int i = 0; i < Math.min(slots.size(), c.getContainerSize()); i++) {
                c.setItem(i, slots.get(i).copy());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Block property access
    // -------------------------------------------------------------------------

    @Override
    public OptionalInt getBlockIntProperty(BlockPos pos, String propertyName) {
        BlockState bs = resolveBlockState(pos);
        if (bs == null) return OptionalInt.empty();
        IntegerProperty ip = findIntProperty(bs, propertyName);
        return ip != null ? OptionalInt.of(bs.getValue(ip)) : OptionalInt.empty();
    }

    @Override
    public OptionalInt getMaxBlockIntProperty(BlockPos pos, String propertyName) {
        BlockState bs = resolveBlockState(pos);
        if (bs == null) return OptionalInt.empty();
        IntegerProperty ip = findIntProperty(bs, propertyName);
        return ip != null ? OptionalInt.of(Collections.max(ip.getPossibleValues())) : OptionalInt.empty();
    }

    @Override
    public void setBlockIntProperty(BlockPos pos, String propertyName, int value) {
        BlockState bs = resolveBlockState(pos);
        if (bs == null) return;
        IntegerProperty ip = findIntProperty(bs, propertyName);
        if (ip == null) {
            return;
        }
        blockStates.put(pos, bs.setValue(ip, value));
        dirtyBlocks.add(pos);
    }

    @Nullable
    private BlockState resolveBlockState(BlockPos pos) {
        if (blockStates.containsKey(pos)) return blockStates.get(pos);
        if (level != null) return level.getBlockState(pos);
        return null;
    }

    @Nullable
    private static IntegerProperty findIntProperty(BlockState bs, String propertyName) {
        for (Property<?> prop : bs.getProperties()) {
            if (prop.getName().equals(propertyName) && prop instanceof IntegerProperty ip) {
                return ip;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Block queries and manipulation
    // -------------------------------------------------------------------------

    @Override
    public boolean isAir(BlockPos pos) {
        BlockState bs = resolveBlockState(pos);
        return bs == null || bs.isAir();
    }

    @Override
    public List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool) {
        BlockState bs = resolveBlockState(pos);
        if (bs == null || level == null) return List.of();
        return Block.getDrops(bs, level, pos, null);
    }

    @Override
    public void removeBlock(BlockPos pos) {
        blockStates.put(pos, Blocks.AIR.defaultBlockState());
        dirtyBlocks.add(pos);
    }

    @Override
    public List<ItemStack> chopTree(BlockPos trunkPos) {
        BlockState trunkState = resolveBlockState(trunkPos);
        if (trunkState == null || trunkState.isAir()) {
            return List.of();
        }
        Block trunkBlock = trunkState.getBlock();
        List<ItemStack> drops = new ArrayList<>();
        chopTreeInMemory(trunkPos.immutable(), trunkBlock, drops, new HashSet<>());
        return drops;
    }

    private void chopTreeInMemory(
            BlockPos center, Block matchBlock,
            List<ItemStack> drops, Set<BlockPos> visited
    ) {
        for (BlockPos adjacent : BlockPos.betweenClosed(
                center.offset(-1, 0, -1), center.offset(1, 1, 1)
        )) {
            BlockPos immutable = adjacent.immutable();
            if (visited.contains(immutable)) {
                continue;
            }
            BlockState bs = resolveBlockState(immutable);
            if (bs == null || !bs.is(matchBlock)) {
                continue;
            }
            visited.add(immutable);
            blockStates.put(immutable, Blocks.AIR.defaultBlockState());
            dirtyBlocks.add(immutable);
            drops.add(matchBlock.asItem().getDefaultInstance());
            chopTreeInMemory(immutable, matchBlock, drops, visited);
        }
    }

    @Override
    public boolean useItemOnBlock(ItemStack item, BlockPos pos) {
        // Complex item interactions use the real world (out of scope for in-memory simulation)
        BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        net.minecraft.world.InteractionResult result = item.getItem().useOn(
                new UseOnContext(level, null, InteractionHand.MAIN_HAND, item, bhr)
        );
        return result.consumesAction();
    }

    // -------------------------------------------------------------------------
    // Tool transformation
    // -------------------------------------------------------------------------

    @Override
    public boolean canToolTransformBlock(BlockPos pos, QTToolAction toolAction) {
        BlockState bs = blockStates.getOrDefault(pos, level.getBlockState(pos));
        return bs.getToolModifiedState(toolContext(pos, toolAction), forgeToolAction(toolAction), false) != null;
    }

    @Override
    public void applyToolTransformation(BlockPos pos, QTToolAction toolAction) {
        BlockState bs = blockStates.getOrDefault(pos, level.getBlockState(pos));
        BlockState modified = bs.getToolModifiedState(toolContext(pos, toolAction), forgeToolAction(toolAction), false);
        if (modified == null) {
            return;
        }
        IntegerProperty moisture = findIntProperty(modified, "moisture");
        if (moisture != null) {
            modified = modified.setValue(moisture, 2);
        }
        blockStates.put(pos, modified);
        dirtyBlocks.add(pos);
    }

    private UseOnContext toolContext(BlockPos pos, QTToolAction toolAction) {
        BlockHitResult bhr = new BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false);
        return new UseOnContext(level, null, InteractionHand.MAIN_HAND, toolForAction(toolAction), bhr);
    }

    private static ToolAction forgeToolAction(QTToolAction toolAction) {
        return switch (toolAction) {
            case HOE_TILL -> ToolActions.HOE_TILL;
            case AXE_STRIP -> ToolActions.AXE_STRIP;
            case AXE_SCRAPE -> ToolActions.AXE_SCRAPE;
            case SHOVEL_FLATTEN -> ToolActions.SHOVEL_FLATTEN;
        };
    }

    private static ItemStack toolForAction(QTToolAction toolAction) {
        return switch (toolAction) {
            case HOE_TILL -> Items.WOODEN_HOE.getDefaultInstance();
            case AXE_STRIP, AXE_SCRAPE -> Items.WOODEN_AXE.getDefaultInstance();
            case SHOVEL_FLATTEN -> Items.WOODEN_SHOVEL.getDefaultInstance();
        };
    }

    // -------------------------------------------------------------------------
    // Randomness
    // -------------------------------------------------------------------------

    @Override
    public <T> List<T> getShuffledCopy(Collection<T> items) {
        return Compat.shuffle(items.iterator(), level);
    }

    // -------------------------------------------------------------------------
    // Container operations
    // -------------------------------------------------------------------------

    @Override
    public boolean isContainer(BlockPos pos) {
        return containerSlots.containsKey(pos);
    }

    @Override
    public int getContainerSlotCount(BlockPos pos) {
        List<ItemStack> slots = containerSlots.get(pos);
        return slots != null ? slots.size() : 0;
    }

    @Override
    public ItemStack getContainerSlot(BlockPos pos, int slot) {
        List<ItemStack> slots = containerSlots.get(pos);
        if (slots == null || slot >= slots.size()) {
            return ItemStack.EMPTY;
        }
        return slots.get(slot);
    }

    @Override
    public boolean insertIntoSlot(BlockPos pos, int slot, ItemStack item) {
        List<ItemStack> slots = containerSlots.get(pos);
        if (slots == null || slot >= slots.size()) {
            return false;
        }
        if (!slots.get(slot).isEmpty()) {
            return false;
        }
        slots.set(slot, item.copy());
        dirtyContainers.add(pos);
        return true;
    }

    @Override
    public ItemStack extractFromSlot(BlockPos pos, int slot, int count) {
        List<ItemStack> slots = containerSlots.get(pos);
        if (slots == null || slot >= slots.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = slots.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = existing.split(count);
        slots.set(slot, existing.isEmpty() ? ItemStack.EMPTY : existing);
        dirtyContainers.add(pos);
        return extracted;
    }

    @Override
    public boolean insertIntoContainer(BlockPos pos, ItemStack item) {
        List<ItemStack> slots = containerSlots.get(pos);
        if (slots == null) {
            return false;
        }
        for (int i = 0; i < slots.size(); i++) {
            ItemStack existing = slots.get(i);
            if (existing.isEmpty()) {
                slots.set(i, item.copy());
                dirtyContainers.add(pos);
                return true;
            }
            if (ItemStack.isSameItemSameTags(existing, item)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int space = existing.getMaxStackSize() - existing.getCount();
                int toAdd = Math.min(space, item.getCount());
                existing.grow(toAdd);
                dirtyContainers.add(pos);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Sound — no-op during warp
    // -------------------------------------------------------------------------

    @Override
    public void playSound(BlockPos pos, SoundEvent sound) {
        // No-op during warp
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound, SoundSource source) {
        // No-op during warp
    }

    // -------------------------------------------------------------------------
    // Processing block advancement (in-memory furnace simulation)
    // -------------------------------------------------------------------------

    @Override
    public void advanceProcessing(BlockPos pos, int ticks) {
        List<ItemStack> slots = containerSlots.get(pos);
        if (slots == null || slots.size() < 3) {
            return;
        }

        int[] progress = cookProgress.computeIfAbsent(pos, p -> new int[]{0, 0});
        int cookTime = progress[0];
        int litTime = progress[1];

        int capped = Math.min(ticks, MAX_PROCESSING_TICKS);

        QT.FLAG_LOGGER.debug(
                "[WarpWorldAccess] advanceProcessing at {} for {} ticks. Slot0={}, Slot1={}, Slot2={}",
                pos.toShortString(), capped,
                slots.get(0).getItem(), slots.get(1).getItem(), slots.get(2).getItem()
        );

        for (int i = 0; i < capped; i++) {
            ItemStack ingredient = slots.get(0);
            if (ingredient.isEmpty()) {
                break;
            }

            // Look up recipe for current ingredient
            Optional<SmeltResult> recipeOpt = recipeResolver.apply(ingredient);
            if (recipeOpt.isEmpty()) {
                break;
            }
            SmeltResult recipe = recipeOpt.get();

            // Consume fuel if needed
            if (litTime <= 0) {
                ItemStack fuel = slots.get(1);
                int burnTime = fuelTimeProvider.apply(fuel);
                if (burnTime <= 0) {
                    break;
                }
                fuel.shrink(1);
                if (fuel.isEmpty()) {
                    slots.set(1, ItemStack.EMPTY);
                }
                litTime = burnTime;
            }

            litTime--;
            cookTime++;

            if (cookTime >= recipe.cookingTime()) {
                // Produce output
                ItemStack output = recipe.result().copy();
                ItemStack existingOutput = slots.get(2);
                if (existingOutput.isEmpty()) {
                    slots.set(2, output);
                } else if (ItemStack.isSameItemSameTags(existingOutput, output)
                        && existingOutput.getCount() < existingOutput.getMaxStackSize()) {
                    existingOutput.grow(output.getCount());
                } else {
                    // Output slot full — can't produce
                    cookTime = recipe.cookingTime(); // don't overflow
                    break;
                }
                ingredient.shrink(1);
                if (ingredient.isEmpty()) {
                    slots.set(0, ItemStack.EMPTY);
                }
                cookTime = 0;
                dirtyContainers.add(pos);
            }
        }

        progress[0] = cookTime;
        progress[1] = litTime;

        QT.FLAG_LOGGER.debug(
                "[WarpWorldAccess] advanceProcessing done. Slot0={}, Slot1={}, Slot2={}",
                slots.get(0).getItem(), slots.get(1).getItem(), slots.get(2).getItem()
        );
    }

    // -------------------------------------------------------------------------
    // Escape hatch — intentionally null
    // -------------------------------------------------------------------------

    @Override
    @Nullable
    public ServerLevel asServerLevel() {
        return null;
    }
}
