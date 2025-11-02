package ca.bradj.questown.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface OnOrphaned {
    void run(
            ServerLevel sl,
            BlockPos childPos,
            BlockPos flagPos
    );
}
