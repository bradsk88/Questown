package ca.bradj.questown.commands.test;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RoomBuilder {

    public static void build(ServerLevel level, BlockPos flagPos, TestBlueprint blueprint) {
        for (TestBlueprint.BlockPlacement bp : blueprint.blocks()) {
            BlockPos worldPos = flagPos.offset(bp.offset());
            level.setBlockAndUpdate(worldPos, bp.blockState());
        }

        placeChest(level, flagPos, blueprint);
    }

    public static void placeChest(ServerLevel level, BlockPos flagPos, TestBlueprint blueprint) {
        BlockPos chestPos = flagPos.offset(blueprint.chestOffset());
        level.setBlockAndUpdate(chestPos, Blocks.CHEST.defaultBlockState());

        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof Container container)) {
            return;
        }

        int slot = 0;
        for (ItemStack item : blueprint.supplyItems()) {
            if (slot >= container.getContainerSize()) {
                break;
            }
            container.setItem(slot++, item.copy());
        }
    }
}
