package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.GathererJob;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

public class JobsRegistry {

    public static Job<MCHeldItem, ? extends Snapshot> getInitializedJob(
            ServerLevel level,
            Snapshot journal,
            UUID ownerUUID
    ) {
        String jobName = journal.jobStringValue();
        switch (jobName) {
            case "farmer":
                FarmerJob fj = new FarmerJob(level, UUID.randomUUID(), 6);
                fj.initialize((FarmerJournal.Snapshot<MCHeldItem>) journal);
                return fj;
            case "baker":
                BakerJob bj = new BakerJob(level, UUID.randomUUID(), 6);
                bj.initialize((BakerJournal.Snapshot<MCHeldItem>) journal);
                return bj;
            case GathererJournal.Snapshot.NAME: {
                GathererJob gj = new GathererJob(level, 6, ownerUUID); // TODO: Capacity
                gj.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
                return gj;
            }
            default: {
                Questown.LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
                GathererJob dj = new GathererJob(level, 6, ownerUUID); // TODO: Capacity
                dj.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
                return dj;
            }
        }
    }
}
