package ca.bradj.questown.world;

import ca.bradj.questown.mc.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;

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
    public List<ItemStack> getBlockDrops(BlockPos pos, @Nullable ItemStack tool) {
        BlockState bs = level.getBlockState(pos);
        return Block.getDrops(bs, level, pos, null);
    }

    @Override
    public void removeBlock(BlockPos pos) {
        level.removeBlock(pos, true);
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

    @Override
    public @Nullable ServerLevel asServerLevel() {
        return level;
    }
}