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

    @Override
    public boolean canUse() {
        double distance = mob.distanceToSqr(
                targetBlock.getPosition().getX(),
                targetBlock.getPosition().getY(),
                targetBlock.getPosition().getZ()
        );
        if (distance < 100) { // 100 is 10^2, the squared distance for 10 blocks away TODO: Add to config
            return false;
        }
        return super.canUse();
    }

    @Nullable
    @Override
    protected Vec3 getPosition() {
        BlockPos tb = this.targetBlock.getPosition();
        return new Vec3(tb.getX(), tb.getY(), tb.getZ());
    }
}
