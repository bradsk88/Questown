package ca.bradj.questown.town;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Pose;

public record PoseInPlace(
        Pose pose,
        BlockPos place
) {
}
