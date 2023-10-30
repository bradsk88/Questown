package ca.bradj.questown.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public interface ItemAccepting {
    BlockState insertItem(
            ServerLevel sl,
            BlockPos bp,
            ItemStack item,
            int workToNextStep
    );
}
