package ca.bradj.questown.world;

import ca.bradj.questown.QT;
import ca.bradj.questown._vanilla.TreeFeatureResolver;
import ca.bradj.questown._vanilla.VoidLevel;
import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;

public class MinecraftWorldAccess implements QTWorldAccess {

    private final ServerLevel level;
    private final boolean silent;

    public MinecraftWorldAccess(ServerLevel level) {
        this(level, false);
    }

    private MinecraftWorldAccess(ServerLevel level, boolean silent) {
        this.level = level;
        this.silent = silent;
    }

    public static MinecraftWorldAccess silent(ServerLevel level) {
        return new MinecraftWorldAccess(level, true);
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

    @Override
    public OptionalInt getBlockIntProperty(BlockPos pos, String propertyName) {
        BlockState bs = level.getBlockState(pos);
        IntegerProperty ip = findIntProperty(bs, propertyName);
        return ip != null ? OptionalInt.of(bs.getValue(ip)) : OptionalInt.empty();
    }

    @Override
    public OptionalInt getMaxBlockIntProperty(BlockPos pos, String propertyName) {
        BlockState bs = level.getBlockState(pos);
        IntegerProperty ip = findIntProperty(bs, propertyName);
        return ip != null ? OptionalInt.of(Collections.max(ip.getPossibleValues())) : OptionalInt.empty();
    }

    @Override
    public void setBlockIntProperty(BlockPos pos, String propertyName, int value) {
        BlockState bs = level.getBlockState(pos);
        IntegerProperty ip = findIntProperty(bs, propertyName);
        if (ip != null) {
            bs = bs.setValue(ip, value);
            level.setBlock(pos, bs, 10);
        }
    }

    @Override
    public boolean isAir(BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    @Override
    public List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool) {
        BlockState bs = level.getBlockState(pos);
        return Block.getDrops(bs, level, pos, null);
    }

    @Override
    public void removeBlock(BlockPos pos) {
        level.removeBlock(pos, true);
    }

    @Override
    public List<ItemStack> chopTree(BlockPos trunkPos) {
        BlockState trunkState = level.getBlockState(trunkPos);
        // Only chop log/wood columns (RotatedPillarBlock = logs/wood/stems). A tag-free proxy for
        // #minecraft:logs so ChopDownTree's scan over all room positions never chops chests/grass/saplings.
        if (!(trunkState.getBlock() instanceof net.minecraft.world.level.block.RotatedPillarBlock)) {
            return List.of();
        }
        Block trunkBlock = trunkState.getBlock();
        List<ItemStack> drops = new ArrayList<>();
        chopTreeRecursive(trunkPos, trunkBlock, drops);
        return drops;
    }

    private void chopTreeRecursive(BlockPos center, Block matchBlock, List<ItemStack> drops) {
        for (BlockPos adjacent : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 1, 1))) {
            if (!level.getBlockState(adjacent).is(matchBlock)) {
                continue;
            }
            level.removeBlock(adjacent, true);
            drops.add(matchBlock.asItem().getDefaultInstance());
            chopTreeRecursive(adjacent.immutable(), matchBlock, drops);
        }
    }

    @Override
    public boolean canTreeGrowAt(BlockPos pos, ItemStack sapling) {
        TreeFeatureResolver.Resolved r = TreeFeatureResolver.resolve(sapling, level.registryAccess());
        if (r == null) {
            return false;
        }
        // Dry run: VoidLevel discards the placement writes; we only want the plantability verdict.
        return r.feature().place(
                r.config(), new VoidLevel(level),
                level.getChunkSource().getGenerator(), level.random, pos
        );
    }

    @Override
    public boolean growTreeAt(BlockPos pos, ItemStack sapling) {
        TreeFeatureResolver.Resolved r = TreeFeatureResolver.resolve(sapling, level.registryAccess());
        if (r == null) {
            return false;
        }
        // Clear the sapling first (as vanilla SaplingBlock.advanceTree does) so the trunk base is
        // free, then write the live level directly (ServerLevel is a WorldGenLevel). Restore on fail.
        BlockState previous = level.getBlockState(pos);
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        boolean placed = r.feature().place(
                r.config(), level, level.getChunkSource().getGenerator(), level.random, pos
        );
        if (!placed) {
            level.setBlock(pos, previous, 3);
        }
        return placed;
    }

    @Override
    public Collection<PlantedSapling> getPlantedSaplings() {
        // Realtime has no warp carrier — vanilla random ticks grow planted saplings.
        return List.of();
    }

    @Override
    public void clearPlantedSapling(BlockPos pos) {
        // No-op in realtime.
    }

    @Override
    public boolean useItemOnBlock(ItemStack item, BlockPos pos) {
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(pos), Direction.UP, pos, false
        );
        InteractionResult result = item.getItem().useOn(new UseOnContext(
                level, null, InteractionHand.MAIN_HAND, item, bhr
        ));
        return result.consumesAction();
    }

    private static ItemStack toolForAction(QTToolAction toolAction) {
        return switch (toolAction) {
            case HOE_TILL -> Items.WOODEN_HOE.getDefaultInstance();
            case AXE_STRIP, AXE_SCRAPE -> Items.WOODEN_AXE.getDefaultInstance();
            case SHOVEL_FLATTEN -> Items.WOODEN_SHOVEL.getDefaultInstance();
        };
    }

    private static ToolAction forgeToolAction(QTToolAction toolAction) {
        return switch (toolAction) {
            case HOE_TILL -> ToolActions.HOE_TILL;
            case AXE_STRIP -> ToolActions.AXE_STRIP;
            case AXE_SCRAPE -> ToolActions.AXE_SCRAPE;
            case SHOVEL_FLATTEN -> ToolActions.SHOVEL_FLATTEN;
        };
    }

    private UseOnContext toolContext(BlockPos pos, QTToolAction toolAction) {
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(pos), Direction.UP, pos, false
        );
        return new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                toolForAction(toolAction), bhr
        );
    }

    @Override
    public boolean canToolTransformBlock(BlockPos pos, QTToolAction toolAction) {
        BlockState bs = level.getBlockState(pos);
        BlockState modified = bs.getToolModifiedState(
                toolContext(pos, toolAction), forgeToolAction(toolAction), false
        );
        return modified != null;
    }

    @Override
    public void applyToolTransformation(BlockPos pos, QTToolAction toolAction) {
        BlockState bs = level.getBlockState(pos);
        BlockState modified = bs.getToolModifiedState(
                toolContext(pos, toolAction), forgeToolAction(toolAction), false
        );
        if (modified == null) {
            return;
        }
        IntegerProperty moisture = findIntProperty(modified, "moisture");
        if (moisture != null) {
            modified = modified.setValue(moisture, 2);
        }
        level.setBlockAndUpdate(pos, modified);
    }

    @Override
    public <T> List<T> getShuffledCopy(Collection<T> items) {
        return Compat.shuffle(items.iterator(), level);
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound) {
        if (silent) return;
        Compat.playNeutralSound(level, pos, sound);
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound, SoundSource source) {
        if (silent) return;
        Compat.playSound(level, pos, sound, source);
    }

    @Nullable
    private Container resolveContainer(BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        if (bs.getBlock() instanceof ChestBlock cb) {
            return ChestBlock.getContainer(cb, bs, level, pos, true);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) {
            return c;
        }
        return null;
    }

    @Nullable
    private IItemHandler resolveItemHandler(BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) {
            return null;
        }
        LazyOptional<IItemHandler> cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER);
        return cap.resolve().orElse(null);
    }

    @Override
    public boolean isContainer(BlockPos pos) {
        return resolveContainer(pos) != null || resolveItemHandler(pos) != null;
    }

    @Override
    public int getContainerSlotCount(BlockPos pos) {
        IItemHandler handler = resolveItemHandler(pos);
        if (handler != null) {
            return handler.getSlots();
        }
        Container c = resolveContainer(pos);
        if (c != null) {
            return c.getContainerSize();
        }
        return 0;
    }

    @Override
    public ItemStack getContainerSlot(BlockPos pos, int slot) {
        IItemHandler handler = resolveItemHandler(pos);
        if (handler != null) {
            return handler.getStackInSlot(slot);
        }
        Container c = resolveContainer(pos);
        if (c != null) {
            return c.getItem(slot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean insertIntoSlot(BlockPos pos, int slot, ItemStack item) {
        Container c = resolveContainer(pos);
        if (c == null) {
            return false;
        }
        if (!c.getItem(slot).isEmpty()) {
            return false;
        }
        c.setItem(slot, item);
        return true;
    }

    @Override
    public ItemStack extractFromSlot(BlockPos pos, int slot, int count) {
        Container c = resolveContainer(pos);
        if (c == null) {
            return ItemStack.EMPTY;
        }
        return c.removeItem(slot, count);
    }

    @Override
    public boolean insertIntoContainer(BlockPos pos, ItemStack item) {
        IItemHandler handler = resolveItemHandler(pos);
        if (handler == null) {
            return false;
        }
        return Compat.insertInNextOpenSlot(handler, item, 1);
    }

    // A full stack (64) at 200 ticks/item = 12,800 ticks.
    // Cap higher to allow some headroom but prevent
    // runaway loops from huge tick deltas.
    private static final int MAX_PROCESSING_TICKS = 13_000;

    @Override
    public void advanceProcessing(BlockPos pos, int ticks) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            return;
        }
        QT.FLAG_LOGGER.debug(
                "advanceProcessing at {} for {} ticks. Slot0={}, Slot1={}, Slot2={}",
                pos.toShortString(), ticks,
                furnace.getItem(0), furnace.getItem(1), furnace.getItem(2)
        );
        int capped = Math.min(ticks, MAX_PROCESSING_TICKS);
        int ticksRun = 0;
        for (int i = 0; i < capped; i++) {
            BlockState bs = level.getBlockState(pos);
            AbstractFurnaceBlockEntity.serverTick(level, pos, bs, furnace);
            ticksRun++;
            if (noSmeltingPossible(furnace, pos)) {
                break;
            }
        }
        QT.FLAG_LOGGER.debug(
                "advanceProcessing done after {} ticks. Slot0={}, Slot1={}, Slot2={}",
                ticksRun,
                furnace.getItem(0), furnace.getItem(1), furnace.getItem(2)
        );
    }

    private boolean noSmeltingPossible(AbstractFurnaceBlockEntity furnace, BlockPos pos) {
        if (furnace.getItem(0).isEmpty()) {
            return true;
        }
        BlockState bs = level.getBlockState(pos);
        boolean lit = bs.hasProperty(AbstractFurnaceBlock.LIT) && bs.getValue(AbstractFurnaceBlock.LIT);
        return !lit && furnace.getItem(1).isEmpty();
    }

    @Override
    public @Nullable ServerLevel asServerLevel() {
        return level;
    }
}