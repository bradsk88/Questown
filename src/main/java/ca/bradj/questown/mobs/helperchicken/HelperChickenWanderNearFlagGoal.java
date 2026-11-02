package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;

import java.util.EnumSet;
import java.util.Random;
import java.util.function.Supplier;

/** After the chest drop, before night: the chicken wanders near the flag instead of following the player. */
public class HelperChickenWanderNearFlagGoal extends Goal {

    private static final int WANDER_RADIUS = 5;
    private static final int IDLE_TICKS_MIN = 120;
    private static final int IDLE_TICKS_MAX = 240;
    private static final int MAX_PICK_ATTEMPTS = 8;
    private static final double SPEED_MODIFIER = 0.6D;

    private final HelperChickenEntity chicken;
    private final Supplier<BlockPos> flagPosSupplier;
    private final Random random = new Random();

    private enum Phase { WALKING, IDLING }

    private BlockPos targetPos;
    private Phase phase;
    private int idleTicks;

    public HelperChickenWanderNearFlagGoal(
            HelperChickenEntity chicken,
            Supplier<BlockPos> flagPosSupplier
    ) {
        this.chicken = chicken;
        this.flagPosSupplier = flagPosSupplier;
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!isWanderPhase()) {
            return false;
        }
        TownFlagBlockEntity flag = resolveFlag();
        BlockPos picked = pickWanderTarget(flag.getTownFlagBasePos());
        if (picked == null) {
            return false;
        }
        this.targetPos = picked;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return isWanderPhase() && this.phase != null;
    }

    /** After the chest drop and before night; at night the chicken goes to the campfire. */
    private boolean isWanderPhase() {
        TownFlagBlockEntity flag = resolveFlag();
        if (flag == null) {
            return false;
        }
        if (flag.getChickenBeatState() != ChickenBeatState.SUNSET_AND_MAP) {
            return false;
        }
        if (!flag.getChickenSunsetChestSpawned()) {
            return false;
        }
        net.minecraft.server.level.ServerLevel level = flag.getServerLevel();
        return level != null && !level.isNight();
    }

    @Override
    public void start() {
        this.phase = Phase.WALKING;
        this.idleTicks = 0;
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
        this.targetPos = null;
        this.phase = null;
        this.idleTicks = 0;
        this.chicken.getNavigation().stop();
    }

    @Override
    public void tick() {
        switch (this.phase) {
            case WALKING -> {
                if (this.chicken.getNavigation().isDone()) {
                    this.phase = Phase.IDLING;
                    this.idleTicks = IDLE_TICKS_MIN
                            + this.random.nextInt(IDLE_TICKS_MAX - IDLE_TICKS_MIN + 1);
                }
            }
            case IDLING -> {
                this.idleTicks--;
                if (this.idleTicks <= 0) {
                    this.targetPos = null;
                    this.phase = null;
                }
            }
        }
    }

    private BlockPos pickWanderTarget(BlockPos flagPos) {
        for (int attempt = 0; attempt < MAX_PICK_ATTEMPTS; attempt++) {
            int dx = this.random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS;
            int dz = this.random.nextInt(WANDER_RADIUS * 2 + 1) - WANDER_RADIUS;
            BlockPos candidate = flagPos.offset(dx, 0, dz);
            if (this.chicken.level().getBlockState(candidate).isAir()
                    && this.chicken.level().getBlockState(candidate.above()).isAir()) {
                return candidate;
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private TownFlagBlockEntity resolveFlag() {
        BlockPos flagPos = flagPosSupplier.get();
        if (flagPos == null) {
            return null;
        }
        if (this.chicken.level().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
            return flag;
        }
        return null;
    }
}
