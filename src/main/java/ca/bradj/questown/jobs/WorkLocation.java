package ca.bradj.questown.jobs;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.function.Predicate;

public record WorkLocation(
        Predicate<Block> isJobBlock,
        ResourceLocation baseRoom
) {
}
