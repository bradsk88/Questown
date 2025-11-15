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

    @Override
    public void beforeTick(BeforeTickEvent bxEvent) {
        super.beforeTick(bxEvent);
        UnsafeVillagerData villagerData = bxEvent.villagerData();
        ServerLevel serverLevel = bxEvent.level().get();
        @Nullable BlockPos override = getOverride(villagerData, false);
        if (override == null) {
            choosePosAndStoreOnVillager(
                    bxEvent.otherVillagerPositions(),
                    bxEvent.randomWalkableTownPosition(),
                    villagerData::write,
                    serverLevel
            );
            return;
        }
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

    private void choosePosAndStoreOnVillager(
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
                return;
            }
        }
        BlockPos p = randomWalkableTownPosition.get();
        storePosOnVillager(writeUnsafeDataToVillager, p);
    }

    private void storePosOnVillager(
            java.util.function.BiConsumer<String, String> writeUnsafeDataToVillager,
            BlockPos p
    ) {
        if (p == null) {
            QT.logBug("RandomWorkSpot: Chose null work spot position.");
            return;
        }
        String val = Long.toString(p.asLong());
        writeUnsafeDataToVillager.accept(DATA_KEY_WORKSPOT_OVERRIDE, val);
        QT.JOB_LOGGER.debug("RandomWorkSpot: Chose new work spot at " + p + " stored as " + val);
    }

    @Override
    public void beforeFindJobSite(BeforeFindJobSiteEvent event) {
        super.beforeFindJobSite(event);
        BlockPos override = getOverride(event.unsafeVillagerData(), true);
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
