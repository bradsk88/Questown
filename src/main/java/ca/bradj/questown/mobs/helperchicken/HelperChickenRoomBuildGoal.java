package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.QT;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.rooms.TownPosition;
import ca.bradj.roomrecipes.serialization.MCRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Room-build goal for the {@link ChickenBeatState#WAITING_FOR_ROOM_BUILD} beat.
 *
 * <p>A door registered in open ground is a door but not a room (the wall walk
 * has no walls to close around), so the chest beat that follows would stand
 * down forever. This goal is the fix: after the door is registered, the
 * chicken walks to it and lays a small enclosed shell around it, one block at
 * a time on the peck cadence, so the player watches a room appear where they
 * put the door. When the shell is up and the room scan sees an enclosed room
 * at the door, the beat's condition is met and the controller advances.
 *
 * <p>The layout is pure ({@link ChickenRoomShellPlan}); this goal owns the
 * world side effects: pathing to the next block, the peck, the place, and the
 * POOF. It re-plans from the live world every block, so a block the player
 * placed (or broke) mid-build is simply skipped (or re-laid).
 */
public class HelperChickenRoomBuildGoal extends Goal {

    private static final double ARRIVAL_DISTANCE_SQR = 2.0D;
    /**
     * Same nav-settle loosening as the chest goal: the path follower stops a
     * block-and-a-half short, which is past the tight arrival radius but close
     * enough to place the block. See HelperChickenSunsetChestGoal.
     */
    private static final double NAV_SETTLE_ARRIVAL_SQR = 9.0D;
    private static final int STUCK_TELEPORT_TICKS = 600;
    private static final int PECK_TICKS = 20;
    private static final double SPEED_MODIFIER = 1.0D;

    private final HelperChickenEntity chicken;
    private final Supplier<BlockPos> flagPosSupplier;

    /** Blocks this goal has already laid, so re-planning does not loop on them. */
    private final Set<BlockPos> laid = new HashSet<>();

    private BlockPos targetPos;
    private int ticksSincePathProgress;
    private int peckCountdown;

    public HelperChickenRoomBuildGoal(
            HelperChickenEntity chicken,
            Supplier<BlockPos> flagPosSupplier
    ) {
        this.chicken = chicken;
        this.flagPosSupplier = flagPosSupplier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null || flag.getChickenBeatState() != ChickenBeatState.WAITING_FOR_ROOM_BUILD) {
            return false;
        }
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return false;
        }
        if (doorHasRoom(flag)) {
            // Nothing to build; the controller advances the beat this tick.
            return false;
        }
        BlockPos next = nextBlock(flag, sl);
        if (next == null) {
            // The plan is empty but no room registered: nothing more the chicken
            // can lay (every cell is occupied). Stand down rather than loop.
            logStoodDown(sl, flag, "plan exhausted but no room detected");
            return false;
        }
        this.targetPos = next;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null || flag.getChickenBeatState() != ChickenBeatState.WAITING_FOR_ROOM_BUILD) {
            return false;
        }
        return this.targetPos != null;
    }

    @Override
    public void start() {
        this.ticksSincePathProgress = 0;
        this.peckCountdown = 0;
        moveTo(this.targetPos);
    }

    @Override
    public void stop() {
        this.chicken.getNavigation().stop();
        this.targetPos = null;
        this.ticksSincePathProgress = 0;
        this.peckCountdown = 0;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        this.chicken.getLookControl().setLookAt(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );
        double distSqr = this.chicken.distanceToSqr(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );
        if (HelperChickenSunsetChestGoal.hasArrived(distSqr, this.chicken.getNavigation().isDone())) {
            peckAndPlace();
            return;
        }
        tickPathing();
    }

    private void moveTo(BlockPos target) {
        if (target == null) {
            return;
        }
        PathNavigation nav = this.chicken.getNavigation();
        Path path = nav.createPath(target, 1);
        if (path != null) {
            nav.moveTo(path, SPEED_MODIFIER);
        }
    }

    private void tickPathing() {
        PathNavigation nav = this.chicken.getNavigation();
        if (nav.isDone() || nav.getPath() == null) {
            moveTo(this.targetPos);
        }
        this.ticksSincePathProgress++;
        if (this.ticksSincePathProgress >= STUCK_TELEPORT_TICKS) {
            teleportToTargetWithPoof();
            this.ticksSincePathProgress = 0;
        }
    }

    private void peckAndPlace() {
        this.chicken.getNavigation().stop();
        if (this.peckCountdown == 0) {
            this.peckCountdown = PECK_TICKS;
            this.chicken.swing(InteractionHand.MAIN_HAND);
            return;
        }
        this.peckCountdown--;
        if (this.peckCountdown == 0) {
            placeTarget();
        }
    }

    private void placeTarget() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null || !(this.chicken.level instanceof ServerLevel sl)) {
            return;
        }
        BlockPos p = this.targetPos;
        if (sl.getBlockState(p).isAir()) {
            sl.setBlockAndUpdate(p, Blocks.COBBLESTONE.defaultBlockState());
            sl.sendParticles(
                    ParticleTypes.POOF,
                    p.getX() + 0.5D, p.getY() + 0.5D, p.getZ() + 0.5D,
                    10, 0.3D, 0.3D, 0.3D, 0.02D
            );
            QT.JOB_LOGGER.info("[chicken-arc] room build: laid {}", p.toShortString());
        }
        this.laid.add(p);
        this.targetPos = null; // force re-plan on next canUse
    }

    /**
     * The next shell block to lay, or null when the plan is exhausted. Re-plans
     * from the live world each call: occupied cells (the door, terrain, a block
     * the player placed) and blocks this goal already laid are skipped, so the
     * plan converges instead of re-laying.
     */
    @org.jetbrains.annotations.Nullable
    private BlockPos nextBlock(TownFlagBlockEntity flag, ServerLevel sl) {
        TownPosition door = firstRegisteredDoor(flag);
        if (door == null) {
            logStoodDown(sl, flag, "no registered door");
            return null;
        }
        int baseY = flag.getTownFlagBasePos().getY() + door.scanLevel;
        BlockPos doorPos = new BlockPos(door.x, baseY, door.z);
        ChickenRoomShellPlan.DoorFrame frame = ChickenRoomShellPlan.frameFor(
                doorPos, baseY, p -> !sl.getBlockState(p).isAir()
        );
        ChickenRoomShellPlan.Plan plan = ChickenRoomShellPlan.plan(
                frame, p -> !sl.getBlockState(p).isAir(), this.laid
        );
        if (plan.steps().isEmpty()) {
            return null;
        }
        return plan.steps().get(0).pos();
    }

    /** True when any registered door already has an enclosed room. */
    private boolean doorHasRoom(TownFlagBlockEntity flag) {
        Collection<MCRoom> rooms = flag.getRoomHandle().getRegisteredRooms().getAllRooms();
        return !rooms.isEmpty();
    }

    @org.jetbrains.annotations.Nullable
    private TownPosition firstRegisteredDoor(TownFlagBlockEntity flag) {
        return flag.getRoomHandle().getAllRegisteredDoors().stream().findFirst().orElse(null);
    }

    private void teleportToTargetWithPoof() {
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return;
        }
        double tx = this.targetPos.getX() + 0.5D;
        double ty = this.targetPos.getY();
        double tz = this.targetPos.getZ() + 0.5D;
        sl.sendParticles(ParticleTypes.POOF, tx, ty + 0.5D, tz, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        this.chicken.teleportTo(tx, ty, tz);
        sl.sendParticles(ParticleTypes.POOF, tx, ty + 0.5D, tz, 8, 0.2D, 0.2D, 0.2D, 0.02D);
        this.chicken.getNavigation().stop();
    }

    private static long lastStoodDownLogTick = -1L;

    private void logStoodDown(ServerLevel sl, TownFlagBlockEntity flag, String reason) {
        long tick = sl.getGameTime();
        if (tick - lastStoodDownLogTick < 100L) {
            return;
        }
        lastStoodDownLogTick = tick;
        QT.JOB_LOGGER.info(
                "[chicken-arc] room build goal stood down: {} (doors={} rooms={})",
                reason,
                flag.getRoomHandle().getAllRegisteredDoors().size(),
                flag.getRoomHandle().getRegisteredRooms().getAllRooms().size()
        );
    }

    @org.jetbrains.annotations.Nullable
    private TownFlagBlockEntity resolveFlag() {
        BlockPos flagPos = flagPosSupplier.get();
        if (flagPos == null) {
            return null;
        }
        if (this.chicken.level.getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
            return flag;
        }
        return null;
    }
}
