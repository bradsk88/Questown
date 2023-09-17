package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.GathererJob;

import java.util.UUID;

public class JobsRegistry {

    public static Job<MCHeldItem, ? extends Snapshot> getInitializedJob(
            Snapshot journal, UUID ownerUUID
    ) {
        String jobName = journal.jobStringValue();
        switch (jobName) {
            case "farmer":
                FarmerJob fj = new FarmerJob(null, UUID.randomUUID(), 6);
                fj.initialize((FarmerJournal.Snapshot<MCHeldItem>) journal);
                return fj;
            case "baker":
                BakerJob bj = new BakerJob(null, UUID.randomUUID(), 6);
                bj.initialize((BakerJournal.Snapshot<MCHeldItem>) journal);
                return bj;
            case GathererJournal.Snapshot.NAME: {
                GathererJob gj = new GathererJob(null, 6, ownerUUID); // TODO: Capacity
                gj.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
                return gj;
            }
            default: {
                Questown.LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
                GathererJob dj = new GathererJob(null, 6, ownerUUID); // TODO: Capacity
                dj.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
                return dj;
            }
        }
    }
}
