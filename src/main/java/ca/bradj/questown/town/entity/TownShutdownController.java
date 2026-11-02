package ca.bradj.questown.town.entity;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.FlagPhase;
import ca.bradj.questown.blocks.TownFlagBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import ca.bradj.questown.town.rewards.SpawnVisitorReward;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Runs a town shutdown: villagers walk to the flag and are taken in, and once all are in and the minimum time has passed the flag goes dormant. Real time only. */
class TownShutdownController {

    // Within 3 blocks of the flag counts as home.
    private static final double ABSORB_DISTANCE_SQR = 9.0;

    // After this, a villager that can't get home is taken in where it stands.
    private static final long FORCE_ABSORB_TICKS = 600;

    private @Nullable ShutdownProgress progress;

    boolean isRunning() {
        return progress != null;
    }

    void begin(
            ServerLevel level,
            BlockPos flagPos,
            BlockState state,
            TownFlagBlockEntity town
    ) {
        if (progress != null) {
            return;
        }
        Set<UUID> roster = town.getVillagerHandle().entities().stream()
                                .map(Entity::getUUID)
                                .collect(Collectors.toSet());
        progress = new ShutdownProgress(
                roster, level.getGameTime(), Config.TOWN_SHUTDOWN_TICKS.get(), FORCE_ABSORB_TICKS
        );
        setPhase(level, flagPos, FlagPhase.SHUTTING_DOWN);
        QT.FLAG_LOGGER.info("Town shutdown started at {} with {} townie(s)", flagPos, roster.size());
    }

    void tick(
            ServerLevel level,
            BlockPos flagPos,
            BlockState state,
            TownFlagBlockEntity town
    ) {
        if (progress == null) {
            return;
        }
        long now = level.getGameTime();
        recallAndAbsorb(flagPos, town, progress.forceAbsorbDue(now));
        if (progress.isComplete(now)) {
            setPhase(level, flagPos, FlagPhase.DORMANT);
            town.makeDeedAvailable();
            progress = null;
            QT.FLAG_LOGGER.info("Town shutdown complete; flag {} is now dormant", flagPos);
        }
    }

    /** Wakes a dormant town where it is, bringing the villagers back. */
    void wake(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town
    ) {
        respawnAbsorbedAndActivate(level, flagPos, town);
        QT.FLAG_LOGGER.info("Town woken in place; flag {} re-activated", flagPos);
    }

    /** Stops a shutdown in progress, bringing back the villagers taken in so far. */
    void cancel(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town
    ) {
        if (progress == null) {
            return;
        }
        respawnAbsorbedAndActivate(level, flagPos, town);
        progress = null;
        QT.FLAG_LOGGER.info("Town shutdown cancelled; flag {} re-activated", flagPos);
    }

    private void respawnAbsorbedAndActivate(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town
    ) {
        // The villagers taken in are the ones with saved state on the flag.
        for (UUID absorbed : town.absorbedVillagers()) {
            town.addImmediateReward(new SpawnVisitorReward(town, TownRelocation.persistedVillagerUuid(absorbed)));
        }
        setPhase(level, flagPos, FlagPhase.ACTIVE);
    }

    private void recallAndAbsorb(
            BlockPos flagPos,
            TownFlagBlockEntity town,
            boolean force
    ) {
        VillagerHolder handle = town.getVillagerHandle();
        // Copy first: taking a villager in removes it from the live list.
        List<LivingEntity> live = new ArrayList<>(handle.entities());
        for (LivingEntity e : live) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            if (force || vme.blockPosition().distSqr(flagPos) <= ABSORB_DISTANCE_SQR) {
                absorb(town, handle, vme);
                continue;
            }
            vme.setWalkTarget(flagPos);
        }
    }

    private void absorb(
            TownFlagBlockEntity town,
            VillagerHolder handle,
            VisitorMobEntity vme
    ) {
        town.rememberAbsorbedJournal(vme);
        progress.markAbsorbed(vme.getUUID());
        handle.remove(vme);
        vme.remove(Entity.RemovalReason.DISCARDED);
    }

    private void setPhase(
            ServerLevel level,
            BlockPos flagPos,
            FlagPhase phase
    ) {
        BlockState current = level.getBlockState(flagPos);
        if (!current.hasProperty(TownFlagBlock.PHASE)) {
            return;
        }
        level.setBlockAndUpdate(flagPos, current.setValue(TownFlagBlock.PHASE, phase));
    }
}
