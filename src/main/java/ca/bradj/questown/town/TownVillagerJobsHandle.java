package ca.bradj.questown.town;

import ca.bradj.questown.InventoryFullStrategy;
import ca.bradj.questown.QT;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.ServerJobsRegistry;
import ca.bradj.questown.jobs.WorksBehaviour;
import ca.bradj.questown.jobs.declarative.BOPDepositorWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.interfaces.VillagerHolder;
import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

public class TownVillagerJobsHandle implements JobsHandle {

    private final UnsafeTown town = new UnsafeTown(TownVillagerJobsHandle.class);
    private int preferredBuffer;

    private static @Nullable UUID getVisitorUUID(VillagerUUID visitorUUID) {
        return VillagerUUID.get(visitorUUID);
    }

    public void associate(TownFlagBlockEntity townVillagerHandle) {
        town.initialize(townVillagerHandle);
    }

    @Override
    public void change(
            VillagerUUID visitorUUID,
            JobID jobID,
            boolean announce
    ) {

        @NotNull TownFlagBlockEntity t = town.getUnsafe();
        VisitorMobEntity f = t.getVillagerHandle().get(visitorUUID);
        if (f == null) {
            QT.FLAG_LOGGER.error("Could not find entity {} to apply job change: {}", visitorUUID, jobID);
            return;
        }

        doSetJob(visitorUUID, jobID, f);
        t.setChanged();
        if (announce) {
            t.messages.jobChanged(jobID, getVisitorUUID(visitorUUID));
        }

        t.possibleWork.invalidate();
        f.setJobChangePending(false);
    }

    @SuppressWarnings({"deprecation"})
    private void doSetJob(
            VillagerUUID visitorUUID,
            JobID jobName,
            VisitorMobEntity f
    ) {
        f.setJob(ServerJobsRegistry.getInitializedJob(
                town.getServerLevelUnsafe(),
                jobName,
                f.getJobJournalSnapshot().items(),
                getVisitorUUID(visitorUUID)
        ));
    }

    @Override
    public boolean changeFromBoard(
            VillagerUUID ownerUUID,
            JobID currentJob
    ) {
        TownFlagBlockEntity t = town.getUnsafe();
        VillagerHolder vh = t.getVillagerHandle();
        ServerLevel level = town.getServerLevelUnsafe();
        VisitorMobEntity villager = vh.get(ownerUUID);
        if (villager == null) {
            return true;
        }
        float damagePercent = vh.getDamagePercent(ownerUUID);
        if (damagePercent > 0) {
            // The more damaged they are, the more likely they are to rest.
            if (level.getRandom().nextFloat() < damagePercent) {
                change(ownerUUID, ResterWork.getIdForRoot(currentJob.rootId()), false);
                return true;
            }
        }

        UUID uuid = getVisitorUUID(ownerUUID);
        if (vh.hasBlockOfProgress(uuid)) {
            MCHeldItem bop = MCHeldItem.fromTown(ItemsInit.BLOCK_OF_PROGRESS.get());
            villager.tryGiveItem(bop, InventoryFullStrategy.REMOVE_FROM_WORLD);
            JobID depositor = BOPDepositorWork.getIdForRoot(currentJob.rootId());
            change(ownerUUID, depositor, false);
            return true;
        }

        ImmutableList<WorkRequest> requestedResults = t.getWorkHandle().getRequestedResults();
        WorksBehaviour.TownData td = t.getTownData();
        Predicate<JobID> canFit = p -> ServerJobsRegistry.canFit(uuid, p, Util.getDayTime(level));
        Predicate<JobID> canAlwaysStart = p -> ServerJobsRegistry.canAlwaysStart(uuid, p);
        JobID work = TownVillagers.chooseFromList(
                canFit,
                canAlwaysStart,
                requestedResults,
                td,
                t.getPossibleWork().getFor(villager.getJobId())
        );
        if (work != null) {
            change(ownerUUID, work, false);
            return true;
        }

        if (preferredBuffer < 100) {
            preferredBuffer++;
            return false;
        }
        preferredBuffer = 0;

        work = TownVillagers.getPreferredWork(villager.getJobId(), canFit, canAlwaysStart, requestedResults, td);
        if (work != null) {
            change(ownerUUID, work, false);
            return true;
        }

        return false;
    }
}
