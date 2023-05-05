package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class JoinTownGoal extends RandomStrollGoal {
    private final BlockPosProvider targetBlock;

    interface BlockPosProvider {
        BlockPos getPosition();
    }

    public JoinTownGoal(
            PathfinderMob p_26140_,
            BlockPosProvider bp
    ) {
        super(p_26140_, 0.5f, 1);
        this.targetBlock = bp;
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        BlockPos tb = this.targetBlock.getPosition();
        return new Vec3(tb.getX(), tb.getY(), tb.getZ());
    }
}
