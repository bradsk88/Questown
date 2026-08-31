package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * Walks the helper chicken to the current beat target (computed from
 * {@link HelperChickenBeatOffsets#resolveTarget(ChickenBeatState, BlockPos, Rotation)})
 * and pecks on arrival.
 *
 * <p>Two-exit rule for pathing failures:
 * <ol>
 *   <li>If pathing fails for {@value #STUCK_TELEPORT_TICKS} ticks while the target
 *   block is still valid: teleport to the target with a poof particle. The beat
 *   stays open — the player may still need to interact.</li>
 *   <li>If the target block is permanently invalid (lava, empty air where a block
 *   is required, or a solid replacement where empty is required): force-complete
 *   the beat (set beat state to {@link ChickenBeatState#COMPLETE}). The chicken
 *   moves on.</li>
 * </ol>
 * The two cases are NOT collapsed — an obstructed-but-valid target gives the
 * player a chance to clear the path, not a silent skip.
 *
 * <p>This goal READS {@code flag.getChickenBeatState()} but never writes it
 * (except for the force-complete-on-invalid-target escape hatch). Normal state
 * transitions are U4's job.
 */
public class HelperChickenBeatPeckGoal extends Goal {

    private static final double ARRIVAL_DISTANCE_SQR = 2.0D;
    private static final int STUCK_TELEPORT_TICKS = 600; // 30 seconds at 20 TPS
    private static final int PECK_TICKS = 20;
    private static final double SPEED_MODIFIER = 1.0D;

    private final HelperChickenEntity chicken;
    private final Supplier<BlockPos> flagPosSupplier;

    private BlockPos targetPos;
    private BlockPos lookPos;
    private int ticksSincePathProgress;
    private int peckCountdown;

    public HelperChickenBeatPeckGoal(
            HelperChickenEntity chicken,
            Supplier<BlockPos> flagPosSupplier
    ) {
        this.chicken = chicken;
        this.flagPosSupplier = flagPosSupplier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!playerReadyForPeck()) {
            return false;
        }
        BlockPos computed = resolveStandTarget();
        if (computed == null) {
            return false;
        }
        this.targetPos = computed;
        this.lookPos = resolveTarget();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!playerReadyForPeck()) {
            return false;
        }
        BlockPos current = resolveStandTarget();
        if (current == null) {
            return false;
        }
        if (!current.equals(this.targetPos)) {
            // Beat state changed; next canUse will latch the new target.
            return false;
        }
        return true;
    }

    /**
     * Item-in-hand gate. For "take item to location" beats the player must be
     * holding the matching item (any door for the door beat, any sign for the
     * sign beat, etc.) before the chicken commits to walking to the target;
     * for UI/seeds-delivery beats there is no required item and this returns
     * true so the chicken pecks the location regardless.
     */
    private boolean playerReadyForPeck() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return false;
        }
        return ChickenArcConditions.shouldPeckRun(
                ChickenArcConditions.findNearestPlayerForFlag(flag),
                flag.getChickenBeatState()
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

    @Override
    public void start() {
        this.ticksSincePathProgress = 0;
        this.peckCountdown = 0;
        if (this.targetPos != null) {
            PathNavigation nav = this.chicken.getNavigation();
            Path path = nav.createPath(this.targetPos, 1);
            if (path != null) {
                nav.moveTo(path, SPEED_MODIFIER);
            }
        }
    }

    @Override
    public void stop() {
        this.chicken.getNavigation().stop();
        this.targetPos = null;
        this.lookPos = null;
        this.ticksSincePathProgress = 0;
        this.peckCountdown = 0;
    }

    @Override
    public void tick() {
        if (this.targetPos == null) {
            return;
        }
        BlockPos look = this.lookPos != null ? this.lookPos : this.targetPos;
        this.chicken.getLookControl().setLookAt(
                look.getX() + 0.5D,
                look.getY() + 0.5D,
                look.getZ() + 0.5D
        );

        double distSqr = this.chicken.distanceToSqr(
                this.targetPos.getX() + 0.5D,
                this.targetPos.getY() + 0.5D,
                this.targetPos.getZ() + 0.5D
        );

        if (distSqr <= ARRIVAL_DISTANCE_SQR) {
            peck();
            return;
        }

        tickPathing();
    }

    private void tickPathing() {
        PathNavigation nav = this.chicken.getNavigation();
        if (nav.isDone() || nav.getPath() == null) {
            Path path = nav.createPath(this.targetPos, 1);
            if (path != null) {
                nav.moveTo(path, SPEED_MODIFIER);
            }
        }

        this.ticksSincePathProgress++;
        if (this.ticksSincePathProgress < STUCK_TELEPORT_TICKS) {
            return;
        }

        if (isTargetPermanentlyInvalid()) {
            forceCompleteBeat();
            return;
        }

        teleportToTargetWithPoof();
        this.ticksSincePathProgress = 0;
    }

    private void peck() {
        this.chicken.getNavigation().stop();
        if (this.peckCountdown <= 0) {
            this.peckCountdown = PECK_TICKS;
            this.chicken.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        } else {
            this.peckCountdown--;
        }
    }

    private BlockPos resolveTarget() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return null;
        }
        ChickenBeatState state = flag.getChickenBeatState();
        if (state == ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY) {
            return findSeedsContainerPos(flag);
        }
        if (state == ChickenBeatState.SUNSET_AND_MAP) {
            return resolveSunsetCampfireTarget(flag, false);
        }
        if (state == ChickenBeatState.WAITING_FOR_PRESSURE_PLATE) {
            return resolvePressurePlateTarget(flag, false);
        }
        Rotation rotation = flag.getChickenStructureRotation();
        return HelperChickenBeatOffsets.resolveTarget(state, flag.getTownFlagBasePos(), rotation);
    }

    /**
     * Where the chicken should walk to and stand. Diverges from
     * {@link #resolveTarget} for beats whose focal block is non-walkable
     * (campfire) — see {@link HelperChickenBeatOffsets#resolveStandTarget}.
     */
    private BlockPos resolveStandTarget() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return null;
        }
        ChickenBeatState state = flag.getChickenBeatState();
        if (state == ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY) {
            return findSeedsContainerPos(flag);
        }
        if (state == ChickenBeatState.SUNSET_AND_MAP) {
            return resolveSunsetCampfireTarget(flag, true);
        }
        if (state == ChickenBeatState.WAITING_FOR_PRESSURE_PLATE) {
            return resolvePressurePlateTarget(flag, true);
        }
        Rotation rotation = flag.getChickenStructureRotation();
        return HelperChickenBeatOffsets.resolveStandTarget(state, flag.getTownFlagBasePos(), rotation);
    }

    /**
     * Phase-3 SUNSET_AND_MAP target: the chicken walks to the campfire when
     * the chest has dropped AND it's night, demonstrating the "use wand on
     * lit campfire to sleep" action. Phases 1 (drop chest) and 2 (wait for
     * dusk) return null so the SunsetChestGoal and WanderNearFlagGoal pick
     * up at their priorities. Reuses the wand-on-campfire offsets so
     * stand/look positions match the earlier "light the campfire" beat.
     */
    @org.jetbrains.annotations.Nullable
    private BlockPos resolveSunsetCampfireTarget(TownFlagBlockEntity flag, boolean stand) {
        if (!flag.getChickenSunsetChestSpawned()) {
            return null;
        }
        ServerLevel level = flag.getServerLevel();
        if (level == null || !level.isNight()) {
            return null;
        }
        Rotation rotation = flag.getChickenStructureRotation();
        BlockPos flagPos = flag.getTownFlagBasePos();
        return stand
                ? HelperChickenBeatOffsets.resolveStandTarget(
                        ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE, flagPos, rotation)
                : HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.WAITING_FOR_WAND_ON_CAMPFIRE, flagPos, rotation);
    }

    /**
     * Pressure-plate beat target: the chicken pecks a different location
     * depending on which item the player is carrying. With a welcome mat in
     * hand it pecks the gate where the mat will be placed; with a pressure
     * plate in hand it pecks the flag base where the plate is crafted into a
     * mat. The same gate the peck runs on
     * ({@link ChickenArcConditions#shouldPeckRun}) drives which offset is
     * returned, so the chicken never pecks the gate before a mat exists.
     */
    @org.jetbrains.annotations.Nullable
    private BlockPos resolvePressurePlateTarget(TownFlagBlockEntity flag, boolean stand) {
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        Rotation rotation = flag.getChickenStructureRotation();
        BlockPos flagPos = flag.getTownFlagBasePos();
        // Welcome mat in hand → the gate, where it is placed.
        if (ChickenArcConditions.playerHoldsRequiredItem(player, ChickenBeatState.WAITING_FOR_PRESSURE_PLATE)) {
            return stand
                    ? HelperChickenBeatOffsets.resolveStandTarget(
                            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, rotation)
                    : HelperChickenBeatOffsets.resolveTarget(
                            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, rotation);
        }
        // Pressure plate in hand → the flag base, where it is crafted into a
        // mat. Both stand and look resolve to the flag base (FLAG_OFFSET),
        // shared with the WAITING_FOR_STICK beat.
        return stand
                ? HelperChickenBeatOffsets.resolveStandTarget(
                        ChickenBeatState.WAITING_FOR_STICK, flagPos, rotation)
                : HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.WAITING_FOR_STICK, flagPos, rotation);
    }

    /**
     * F4 step 4: walk to whichever registered town container is currently
     * holding Worldly Seeds. Returns null until a container has them, which
     * happens when the first gatherer deposits (U7).
     */
    private BlockPos findSeedsContainerPos(TownFlagBlockEntity flag) {
        ServerLevel level = flag.getServerLevel();
        if (level == null) {
            return null;
        }
        for (ContainerTarget<MCContainer, MCTownItem> ct : TownContainers.getAllContainers(flag, level)) {
            if (ct.hasItem(item -> item.get() == ItemsInit.WORLDLY_SEEDS.get())) {
                return ct.getBlockPos();
            }
        }
        return null;
    }

    private boolean isTargetPermanentlyInvalid() {
        if (!(this.chicken.level instanceof ServerLevel sl)) {
            return false;
        }
        BlockState bs = sl.getBlockState(this.targetPos);
        FluidState fluid = bs.getFluidState();
        if (fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
            return true;
        }
        // Placement beats (wall/door/sign/chest/plate) need the target to be empty.
        // If a player-placed solid occupies the slot and this beat expects empty space,
        // the beat is effectively complete-from-the-wrong-direction. U4 still controls
        // actual state transitions — we only force-complete for hard physical blocks
        // that the chicken cannot traverse OR lava.
        ChickenBeatState beatState = currentBeatState();
        if (beatState == null) {
            return false;
        }
        return isHardBlockedForBeat(bs, beatState);
    }

    private ChickenBeatState currentBeatState() {
        TownFlagBlockEntity flag = resolveFlag();
        return flag == null ? null : flag.getChickenBeatState();
    }

    private boolean isHardBlockedForBeat(
            BlockState bs,
            ChickenBeatState beatState
    ) {
        // Beats that expect EMPTY space at the target (so the player can place a block).
        // If that space is occupied by a solid block, the beat's physical precondition
        // is satisfied in-effect — force-complete to avoid deadlock.
        return switch (beatState) {
            case WAITING_FOR_WALL_BLOCK, WAITING_FOR_DOOR, WAITING_FOR_SIGN, WAITING_FOR_CHEST ->
                    bs.isSolidRender(this.chicken.level, this.targetPos);
            default -> false;
        };
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

    private void forceCompleteBeat() {
        BlockPos flagPos = flagPosSupplier.get();
        if (flagPos == null) {
            return;
        }
        if (!(this.chicken.level.getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
            return;
        }
        // Escape hatch for permanently-invalid targets only. Normal transitions are U4's job.
        flag.setChickenBeatState(ChickenBeatState.COMPLETE);
        flag.setChanged();
    }
}
