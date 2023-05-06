package ca.bradj.questown.mobs.visitor;

import ca.bradj.questown.Questown;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class TownWalk extends Goal {
    private static final int GIVE_UP_TICKS = 100;

    final VisitorMobEntity trader;
    final double stopDistance;
    final double speedModifier;
    private int stuckTicks;

    TownWalk(
            VisitorMobEntity p_35899_,
            double p_35900_,
            double p_35901_
    ) {
        this.trader = p_35899_;
        this.stopDistance = p_35900_;
        this.speedModifier = p_35901_;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public void stop() {
        this.trader.setWanderTarget(null);
        trader.getNavigation().stop();
        this.stuckTicks = 0;
    }

    @Override
    public void start() {
        super.start();
        this.stuckTicks = 0;
        trader.newWanderTarget();
        BlockPos wt = trader.getWanderTarget();
        Questown.LOGGER.debug("{} navigating to {}", this.trader.getUUID(), wt);
    }

    public boolean canUse() {
        if (this.stuckTicks > this.adjustedTickDelay(GIVE_UP_TICKS)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (this.stuckTicks > this.adjustedTickDelay(GIVE_UP_TICKS)) {
            return false;
        }
        return true;
    }

    public void tick() {
        BlockPos startPos = this.trader.getOnPos();
        BlockPos blockpos = this.trader.getWanderTarget();
        if (blockpos != null && trader.getNavigation().isDone()) {
            PathNavigation nav = trader.getNavigation();
            if (this.isTooFarAway(blockpos, 10.0D)) {
                Vec3 vec3 = (new Vec3(
                        (double) blockpos.getX() - this.trader.getX(),
                        (double) blockpos.getY() - this.trader.getY(),
                        (double) blockpos.getZ() - this.trader.getZ()
                )).normalize();
                Vec3 vec31 = vec3.scale(10.0D).add(this.trader.getX(), this.trader.getY(), this.trader.getZ());
                nav.moveTo(vec31.x, vec31.y, vec31.z, this.speedModifier);
            } else {
                nav.moveTo(
                        (double) blockpos.getX(),
                        (double) blockpos.getY(),
                        (double) blockpos.getZ(),
                        this.speedModifier
                );
            }
        }
        if (startPos.equals(this.trader.getOnPos())) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
    }

    private boolean isTooFarAway(
            BlockPos p_35904_,
            double p_35905_
    ) {
        return !p_35904_.closerToCenterThan(this.trader.position(), p_35905_);
    }
}
