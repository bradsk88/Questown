package ca.bradj.questown.jobs;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiPredicate;

public record WorkLocation(
        IsJobBlock isJobBlock,
        ResourceLocation baseRoom
) {

    public interface BlockInfo {
        BlockState state(BlockPos bp);

        @Nullable BlockEntity entity(BlockPos bp);
    }

    public static BiPredicate<BlockInfo, BlockPos> isBlock(Class<? extends Block> blockClass) {
        return (sl, bp) -> blockClass.isInstance(sl.state(bp).getBlock());
    }
}