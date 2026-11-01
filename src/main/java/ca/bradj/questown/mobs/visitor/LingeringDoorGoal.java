package ca.bradj.questown.mobs.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Vanilla's {@link OpenDoorGoal} closes the door 20 ticks after opening it, however close the mob
 * still is, so a villager working from a 1x1 room's door cell, or walking through, would bump it
 * open again at once. This keeps the door open while the villager is near and closes it once the
 * villager has left ({@link #tickLingeringDoor}, called from the entity tick).
 */
public class LingeringDoorGoal extends OpenDoorGoal {

    /** Blocks (squared, horizontal) within which a villager keeps its door open. */
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

    /** Closes a door this villager held open, once it has moved away. */
    public void tickLingeringDoor() {
        if (lingering == null || stillNear(lingering)) {
            return;
        }
        close(mob.getLevel(), lingering);
        lingering = null;
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
