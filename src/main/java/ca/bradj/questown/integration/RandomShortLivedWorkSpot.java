package ca.bradj.questown.integration;

import ca.bradj.questown.QT;
import ca.bradj.questown.integration.jobs.*;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.declarative.WithReason;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

public class RandomShortLivedWorkSpot extends JobPhaseModifier {

    // TODO: Consider namespacing these to the rule's class name to isolate rules from each other.
    private static final String DATA_KEY_WORKSPOT_OVERRIDE = "questown:work_spot_override";
    private static final String DATA_KEY_WORKSPOT_OVERRIDE_UNTIL = "questown:work_spot_override_until";

    private final boolean preferSocial;
    private final int pauseForTicks;

    public RandomShortLivedWorkSpot(
            boolean preferSocial,
            int pauseForTicks
    ) {
        super();
        this.preferSocial = preferSocial;
        this.pauseForTicks = pauseForTicks;
    }

    public static boolean hasTargetChanged(
            UnsafeVillagerData currentPosSource,
            BlockPos vsPos
    ) {
        if (currentPosSource.get("questown:work_spot_override") != null) {
            @Nullable BlockPos ovr = getOverride(currentPosSource, false);
            return ovr != null && !ovr.equals(vsPos);
        }
        return false;
    }

    // When a wander-target scan comes back null (town has no valid room), we
    // record a retry-after tick on the villager instead of rescanning every
    // tick. Without this, a villager in a room-less town spun a full room
    // interior scan each tick and drowned the log in repeated errors.
    private static final String DATA_KEY_WORKSPOT_RETRY_AFTER = "questown:work_spot_retry_after";
    private static final long RETRY_AFTER_FAILURE_TICKS = 100;

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        UnsafeVillagerData villagerData = bxEvent.villagerData();
        ServerLevel serverLevel = bxEvent.world().get().asServerLevel();
        @Nullable BlockPos override = getOverride(villagerData, false);
        if (override == null) {
            if (isWaitingToRetry(villagerData, serverLevel)) {
                return;
            }
            boolean chose = choosePosAndStoreOnVillager(
                    bxEvent.otherVillagerPositions(),
                    bxEvent.randomWalkableTownPosition(),
                    villagerData::write,
                    serverLevel
            );
            if (!chose) {
                villagerData.write(
                        DATA_KEY_WORKSPOT_RETRY_AFTER,
                        Long.toString(Util.getTick(serverLevel) + RETRY_AFTER_FAILURE_TICKS)
                );
            }
            return;
        }
        villagerData.clear(DATA_KEY_WORKSPOT_RETRY_AFTER);
        String until = villagerData.get(DATA_KEY_WORKSPOT_OVERRIDE_UNTIL);
        if (until == null) {
            if (Jobs.isCloseTo(bxEvent.position(), override)) {
                setNewTimeout(serverLevel, villagerData);
            }
            return;
        }
        if (Util.getTick(serverLevel) >= Long.parseLong(until)) {
            villagerData.clear(DATA_KEY_WORKSPOT_OVERRIDE);
            villagerData.clear(DATA_KEY_WORKSPOT_OVERRIDE_UNTIL);
        }
    }

    private void setNewTimeout(
            ServerLevel serverLevel,
            UnsafeVillagerData villagerData
    ) {
        long newUntil = Util.getTick(serverLevel) + pauseForTicks;
        villagerData.write(DATA_KEY_WORKSPOT_OVERRIDE_UNTIL, Long.toString(newUntil));
    }

    private boolean isWaitingToRetry(
            UnsafeVillagerData villagerData,
            ServerLevel serverLevel
    ) {
        String retryAfter = villagerData.get(DATA_KEY_WORKSPOT_RETRY_AFTER);
        if (retryAfter == null || retryAfter.isEmpty()) {
            return false;
        }
        return Util.getTick(serverLevel) < Long.parseLong(retryAfter);
    }

    /** Returns true if a position was stored, false if the search came back null. */
    private boolean choosePosAndStoreOnVillager(
            java.util.function.Supplier<ImmutableList<BlockPos>> otherVillagerPositions,
            java.util.function.Supplier<BlockPos> randomWalkableTownPosition,
            java.util.function.BiConsumer<String, String> writeUnsafeDataToVillager,
            ServerLevel serverLevel
    ) {
        if (preferSocial && Compat.getRandomBool(serverLevel)) {
            ImmutableList<BlockPos> ovp = otherVillagerPositions.get();
            if (!ovp.isEmpty()) {
                BlockPos pos = Compat.shuffle(ovp, serverLevel).get(0);
                storePosOnVillager(writeUnsafeDataToVillager, pos);
                return true;
            }
        }
        BlockPos p = randomWalkableTownPosition.get();
        if (p == null) {
            // The town-side wander search already logged the specific reason
            // (no rooms / all rooms filtered out), rate-limited. Retrying is
            // deferred via DATA_KEY_WORKSPOT_RETRY_AFTER by the caller.
            return false;
        }
        storePosOnVillager(writeUnsafeDataToVillager, p);
        return true;
    }

    private void storePosOnVillager(
            java.util.function.BiConsumer<String, String> writeUnsafeDataToVillager,
            BlockPos p
    ) {
        String val = Long.toString(p.asLong());
        writeUnsafeDataToVillager.accept(DATA_KEY_WORKSPOT_OVERRIDE, val);
        QT.JOB_LOGGER.debug("RandomWorkSpot: Chose new work spot at " + p + " stored as " + val);
    }

    @Override
    public void beforeFindJobSite(BeforeFindJobSiteEvent event) {
        super.beforeFindJobSite(event);
        // Missing override is the common case (villager has not been assigned a
        // random spot yet, or the last search found no valid room). It is not a
        // bug, so don't logBug here — that fired every tick in a room-less town.
        BlockPos override = getOverride(event.unsafeVillagerData(), false);
        if (override == null) return;
        event.applyWorkspotOverride()
             .accept(WithReason.always(override, "Overridden by " + this.getClass().getSimpleName()));
    }

    private static @Nullable BlockPos getOverride(
            UnsafeVillagerData event,
            boolean logMissing
    ) {
        String posStr = event.get(DATA_KEY_WORKSPOT_OVERRIDE);
        if (posStr == null || posStr.isEmpty()) {
            if (logMissing) {
                QT.logBug("RandomWorkSpot: No work spot override data found.");
            }
            return null;
        }
        BlockPos override = BlockPos.of(Long.parseLong(posStr));
        return override;
    }

    @Override
    public void beforeMaxTicksJobChange(BeforeMaxTicksJobChangeEvent ctx) {
        super.beforeMaxTicksJobChange(ctx);
        ctx.unsafeVillagerData().clear(DATA_KEY_WORKSPOT_OVERRIDE);
        ctx.unsafeVillagerData().clear(DATA_KEY_WORKSPOT_OVERRIDE_UNTIL);
    }
}
