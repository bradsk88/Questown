package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Keeps a door open while the villager is near and closes it once it has left; vanilla closes it after a second. */
public class LingeringDoorGoal extends OpenDoorGoal {

    static final double KEEP_OPEN_DIST_SQR = 2.5 * 2.5;

    private @Nullable BlockPos lingering;

    public LingeringDoorGoal(Mob mob) {
        super(mob, true);
    }

    @Override
    public void stop() {
        if (hasDoor && stillNear(doorPos)) {
            lingering = doorPos;
            return;
        }
        super.stop();
    }

    public void tickLingeringDoor() {
        if (lingering == null || stillNear(lingering)) {
            return;
        }
        close(mob.level(), lingering);
        lingering = null;
    }

    /** Closes any door this villager is holding open: a sleeping villager no longer ticks to close it. */
    public void closeHeldDoor() {
        if (lingering != null) {
            close(mob.level(), lingering);
            lingering = null;
        }
        if (hasDoor) {
            close(mob.level(), doorPos);
        }
    }

    private boolean stillNear(BlockPos door) {
        return keepsOpen(mob.distanceToSqr(door.getX() + 0.5, mob.getY(), door.getZ() + 0.5));
    }

    static boolean keepsOpen(double distSqr) {
        return distSqr <= KEEP_OPEN_DIST_SQR;
    }

    private void close(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(mob, level, state, pos, false);
        }
    }
}
