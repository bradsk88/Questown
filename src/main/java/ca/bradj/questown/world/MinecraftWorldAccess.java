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
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

public class MinecraftWorldAccess implements QTWorldAccess {

    private final ServerLevel level;

    public MinecraftWorldAccess(ServerLevel level) {
        this.level = level;
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

    private static ItemStack toolForAction(String toolAction) {
        String toolType = toolAction.split("_")[0];
        return switch (toolType) {
            case "hoe" -> Items.WOODEN_HOE.getDefaultInstance();
            case "axe" -> Items.WOODEN_AXE.getDefaultInstance();
            case "shovel" -> Items.WOODEN_SHOVEL.getDefaultInstance();
            case "pickaxe" -> Items.WOODEN_PICKAXE.getDefaultInstance();
            default -> ItemStack.EMPTY;
        };
    }

    private UseOnContext toolContext(BlockPos pos, String toolAction) {
        BlockHitResult bhr = new BlockHitResult(
                Vec3.atCenterOf(pos), Direction.UP, pos, false
        );
        return new UseOnContext(
                level, null, InteractionHand.MAIN_HAND,
                toolForAction(toolAction), bhr
        );
    }

    @Override
    public boolean canToolTransformBlock(BlockPos pos, String toolAction) {
        BlockState bs = level.getBlockState(pos);
        BlockState modified = bs.getToolModifiedState(
                toolContext(pos, toolAction), ToolAction.get(toolAction), false
        );
        return modified != null;
    }

    @Override
    public void applyToolTransformation(BlockPos pos, String toolAction) {
        BlockState bs = level.getBlockState(pos);
        BlockState modified = bs.getToolModifiedState(
                toolContext(pos, toolAction), ToolAction.get(toolAction), false
        );
        if (modified != null) {
            IntegerProperty moisture = findIntProperty(modified, "moisture");
            if (moisture != null) {
                modified = modified.setValue(moisture, 2);
            }
            level.setBlockAndUpdate(pos, modified);
        }
    }

    @Override
    public boolean compostItem(BlockPos pos, ItemStack item) {
        BlockState bs = level.getBlockState(pos);
        BlockState result = ComposterBlock.insertItem(bs, level, item, pos);
        if (item.getCount() > 0) {
            return false;
        }
        level.setBlockAndUpdate(pos, result);
        return true;
    }

    @Override
    public Optional<ItemStack> extractCompostProduct(BlockPos pos) {
        BlockState bs = level.getBlockState(pos);
        if (bs.getBlock() instanceof ComposterBlock && bs.getValue(ComposterBlock.LEVEL) >= 8) {
            bs = bs.setValue(ComposterBlock.LEVEL, 0);
            level.setBlockAndUpdate(pos, bs);
            return Optional.of(Items.BONE_MEAL.getDefaultInstance());
        }
        return Optional.empty();
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound) {
        Compat.playNeutralSound(level, pos, sound);
    }

    @Override
    public void playSound(BlockPos pos, SoundEvent sound, SoundSource source) {
        Compat.playSound(level, pos, sound, source);
    }

    @Override
    public @Nullable ServerLevel asServerLevel() {
        return level;
    }
}