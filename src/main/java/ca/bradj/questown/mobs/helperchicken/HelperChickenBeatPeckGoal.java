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
 * Walks the helper chicken to the current lesson's target and pecks. Stuck for
 * {@value #STUCK_TELEPORT_TICKS} ticks, it teleports there; if the target can never work (lava, or a
 * block in the way of a placement), the lessons are marked complete.
 */
public class HelperChickenBeatPeckGoal extends Goal {

    private static final double ARRIVAL_DISTANCE_SQR = 2.0D;
    private static final int STUCK_TELEPORT_TICKS = 600;
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
            return false;
        }
        return true;
    }

    /** For lessons that need an item, the player must hold it before the chicken goes; other lessons always go. */
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
        if (this.chicken.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
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
            return seedsDeliveryTarget(flag, false);
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

    private BlockPos resolveStandTarget() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return null;
        }
        ChickenBeatState state = flag.getChickenBeatState();
        if (state == ChickenBeatState.AWAITING_WORLDLY_SEEDS_DELIVERY) {
            return seedsDeliveryTarget(flag, true);
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

    /** The sleep lesson's target: the campfire, once it is night. */
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

    /** The welcome mat lesson's target: the gate with a mat in hand, the flag with a pressure plate. */
    @org.jetbrains.annotations.Nullable
    private BlockPos resolvePressurePlateTarget(TownFlagBlockEntity flag, boolean stand) {
        Player player = ChickenArcConditions.findNearestPlayerForFlag(flag);
        Rotation rotation = flag.getChickenStructureRotation();
        BlockPos flagPos = flag.getTownFlagBasePos();
        if (ChickenArcConditions.playerHoldsRequiredItem(player, ChickenBeatState.WAITING_FOR_PRESSURE_PLATE)) {
            return stand
                    ? HelperChickenBeatOffsets.resolveStandTarget(
                            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, rotation)
                    : HelperChickenBeatOffsets.resolveTarget(
                            ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, rotation);
        }
        return stand
                ? HelperChickenBeatOffsets.resolveStandTarget(
                        ChickenBeatState.WAITING_FOR_STICK, flagPos, rotation)
                : HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.WAITING_FOR_STICK, flagPos, rotation);
    }

    /** The seeds lesson's target: the chest holding the seeds, or the gate before there are any. */
    @org.jetbrains.annotations.Nullable
    private BlockPos seedsDeliveryTarget(TownFlagBlockEntity flag, boolean stand) {
        BlockPos container = findSeedsContainerPos(flag);
        if (container != null) {
            return container;
        }
        Rotation rotation = flag.getChickenStructureRotation();
        BlockPos flagPos = flag.getTownFlagBasePos();
        return stand
                ? HelperChickenBeatOffsets.resolveStandTarget(
                        ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, rotation)
                : HelperChickenBeatOffsets.resolveTarget(
                        ChickenBeatState.WAITING_FOR_PRESSURE_PLATE, flagPos, rotation);
    }

    /** The town container holding Worldly Seeds, or null. */
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
        if (!(this.chicken.getLevel() instanceof ServerLevel sl)) {
            return false;
        }
        BlockState bs = sl.getBlockState(this.targetPos);
        FluidState fluid = bs.getFluidState();
        if (fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
            return true;
        }
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
        // A solid block where a placement lesson needs space counts as done.
        return switch (beatState) {
            case WAITING_FOR_WALL_BLOCK, WAITING_FOR_DOOR, WAITING_FOR_SIGN, WAITING_FOR_CHEST ->
                    bs.isSolidRender(this.chicken.getLevel(), this.targetPos);
            default -> false;
        };
    }

    private void teleportToTargetWithPoof() {
        if (!(this.chicken.getLevel() instanceof ServerLevel sl)) {
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
        if (!(this.chicken.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag)) {
            return;
        }
        flag.setChickenBeatState(ChickenBeatState.COMPLETE);
        flag.setChanged();
    }
}
