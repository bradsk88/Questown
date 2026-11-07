package ca.bradj.questown.mobs.helperchicken;

import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.function.Supplier;

/** The helper chicken follows the nearest player near its flag while its lessons are running. Based on vanilla's {@code TemptGoal}. */
public class HelperChickenFollowNearFlagGoal extends Goal {

    private static final double FLAG_FOLLOW_RADIUS_SQR = 16.0D * 16.0D;
    private static final double PLAYER_SEARCH_RADIUS = 10.0D;
    private static final double STOP_DISTANCE_SQR = 2.5D * 2.5D;

    private final TargetingConditions targetingConditions = TargetingConditions.forNonCombat()
            .range(PLAYER_SEARCH_RADIUS)
            .ignoreLineOfSight();

    private final HelperChickenEntity chicken;
    private final double speedModifier;
    private final Supplier<BlockPos> flagPosSupplier;

    private Player player;
    private int calmDown;

    public HelperChickenFollowNearFlagGoal(
            HelperChickenEntity chicken,
            double speedModifier,
            Supplier<BlockPos> flagPosSupplier
    ) {
        this.chicken = chicken;
        this.speedModifier = speedModifier;
        this.flagPosSupplier = flagPosSupplier;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.calmDown > 0) {
            this.calmDown--;
            return false;
        }
        BlockPos flagPos = flagPosSupplier.get();
        if (flagPos == null) {
            return false;
        }
        if (!isArcActive(flagPos)) {
            return false;
        }
        this.player = this.chicken.getLevel().getNearestPlayer(this.targetingConditions, this.chicken);
        if (this.player == null) {
            return false;
        }
        return isPlayerNearFlag(this.player, flagPos);
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos flagPos = flagPosSupplier.get();
        if (flagPos == null) {
            return false;
        }
        if (!isArcActive(flagPos)) {
            return false;
        }
        if (this.player == null || !this.player.isAlive()) {
            return false;
        }
        if (!isPlayerNearFlag(this.player, flagPos)) {
            return false;
        }
        return this.chicken.distanceToSqr(this.player) <= FLAG_FOLLOW_RADIUS_SQR;
    }

    @Override
    public void stop() {
        this.player = null;
        this.chicken.getNavigation().stop();
        this.calmDown = reducedTickDelay(100);
    }

    @Override
    public void tick() {
        if (this.player == null) {
            return;
        }
        this.chicken.getLookControl().setLookAt(
                this.player,
                (float) (this.chicken.getMaxHeadYRot() + 20),
                (float) this.chicken.getMaxHeadXRot()
        );
        if (this.chicken.distanceToSqr(this.player) < STOP_DISTANCE_SQR) {
            this.chicken.getNavigation().stop();
            return;
        }
        PathNavigation nav = this.chicken.getNavigation();
        nav.moveTo(this.player, this.speedModifier);
    }

    private boolean isArcActive(BlockPos flagPos) {
        TownFlagBlockEntity flag = readFlag(flagPos);
        if (flag == null) {
            return false;
        }
        ChickenBeatState state = flag.getChickenBeatState();
        if (state == ChickenBeatState.COMPLETE || state == ChickenBeatState.FORFEIT) {
            return false;
        }
        // After the chest drop the chicken wanders near the flag instead of following.
        return !(state == ChickenBeatState.SUNSET_AND_MAP && flag.getChickenSunsetChestSpawned());
    }

    @org.jetbrains.annotations.Nullable
    private TownFlagBlockEntity readFlag(BlockPos flagPos) {
        if (this.chicken.getLevel().getBlockEntity(flagPos) instanceof TownFlagBlockEntity flag) {
            return flag;
        }
        return null;
    }

    private boolean isPlayerNearFlag(
            Player player,
            BlockPos flagPos
    ) {
        double dx = player.getX() - (flagPos.getX() + 0.5D);
        double dy = player.getY() - (flagPos.getY() + 0.5D);
        double dz = player.getZ() - (flagPos.getZ() + 0.5D);
        return (dx * dx + dy * dy + dz * dz) <= FLAG_FOLLOW_RADIUS_SQR;
    }
}
