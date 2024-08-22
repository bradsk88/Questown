package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;

public record VillagerSleptEvent(
        Long duration,
        BlockPos bedPos
) {
}
