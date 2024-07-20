package ca.bradj.questown.jobs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public record WorkLocation(
        Predicate<BlockState> isJobBlock,
        ResourceLocation baseRoom
) {
}
