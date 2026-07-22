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

/**
 * Drives one town-flag shutdown ritual: recall every townie to the flag, absorb them as they
 * arrive (or once a deadlock deadline passes), and — once the whole roster is absorbed and the
 * minimum-duration floor has elapsed — flip the flag to {@link FlagPhase#DORMANT} (ADR-0009).
 *
 * <p>Transient per-flag state: ticked only on the realtime path (warp never recalls), from
 * {@link TownFlagTicker}'s {@code SHUTTING_DOWN} branch. The blockstate phase is the durable
 * record of the ritual; this controller's progress resets if the chunk unloads mid-ritual, which
 * is benign because shutdown is realtime + player-present (the chunk stays loaded).
 */
class TownShutdownController {

    // A townie counts as "home" within this squared distance of the flag base. The flag is a solid
    // block, so a townie pathing to it lands adjacent (~1 block away); 9 (3 blocks) is forgiving.
    private static final double ABSORB_DISTANCE_SQR = 9.0;

    // Deadlock backstop, well beyond the 200-tick floor: a townie that cannot path home is absorbed
    // where it stands so the ritual always terminates.
    private static final long FORCE_ABSORB_TICKS = 600;

    private @Nullable ShutdownProgress progress;
    // Absorbed townies' stable identities, remembered so a cancel can re-spawn exactly them.
    private final List<VillagerUUID> absorbedForRespawn = new ArrayList<>();

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
        absorbedForRespawn.clear();
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

    /**
     * Wake a dormant town in place: re-spawn the absorbed roster and return to
     * {@link FlagPhase#ACTIVE}. The loss-protection path — interacting with the dormant flag instead
     * of placing the deed (ADR-0009).
     */
    void wake(
            ServerLevel level,
            BlockPos flagPos,
            TownFlagBlockEntity town
    ) {
        respawnAbsorbedAndActivate(level, flagPos, town);
        QT.FLAG_LOGGER.info("Town woken in place; flag {} re-activated", flagPos);
    }

    /**
     * Restore an in-progress (not yet completed) ritual to {@link FlagPhase#ACTIVE} and re-spawn the
     * townies absorbed so far. The town's data never left the flag, so this is loss-proof.
     */
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
        for (VillagerUUID vuid : absorbedForRespawn) {
            town.addImmediateReward(new SpawnVisitorReward(town, vuid));
        }
        absorbedForRespawn.clear();
        setPhase(level, flagPos, FlagPhase.ACTIVE);
    }

    private void recallAndAbsorb(
            BlockPos flagPos,
            TownFlagBlockEntity town,
            boolean force
    ) {
        VillagerHolder handle = town.getVillagerHandle();
        // Snapshot before mutating: absorbing removes from the live entity list mid-iteration.
        List<LivingEntity> live = new ArrayList<>(handle.entities());
        for (LivingEntity e : live) {
            if (!(e instanceof VisitorMobEntity vme)) {
                continue;
            }
            if (force || vme.blockPosition().distSqr(flagPos) <= ABSORB_DISTANCE_SQR) {
                absorb(handle, vme);
                continue;
            }
            vme.setWalkTarget(flagPos);
        }
    }

    private void absorb(
            VillagerHolder handle,
            VisitorMobEntity vme
    ) {
        progress.markAbsorbed(vme.getUUID());
        absorbedForRespawn.add(vme.getVUID());
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
