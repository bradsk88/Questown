package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.mobs.visitor.GathererJob;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class JobsRegistry {

    public static Job<MCHeldItem, ? extends Snapshot> getInitializedJob(
            ServerLevel level,
            String jobName,
            @Nullable Snapshot journal,
            UUID ownerUUID
    ) {
        switch (jobName) {
            case "farmer":
                FarmerJob fj = new FarmerJob(level, ownerUUID, 6);
                if (journal != null) {
                    fj.initialize((FarmerJournal.Snapshot<MCHeldItem>) journal);
                }
                return fj;
            case "baker":
                BakerJob bj = new BakerJob(level, ownerUUID, 6);
                if (journal != null) {
                    bj.initialize((BakerJournal.Snapshot<MCHeldItem>) journal);
                }
                return bj;
            case GathererJournal.Snapshot.NAME: {
                GathererJob gj = new GathererJob(level, 6, ownerUUID); // TODO: Capacity
                if (journal != null) {
                    gj.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
                }
                return gj;
            }
            default: {
                Questown.LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
                GathererJob dj = new GathererJob(level, 6, ownerUUID); // TODO: Capacity
                if (journal != null) {
                    dj.initialize((GathererJournal.Snapshot<MCHeldItem>) journal);
                }
                return dj;
            }
        }
    }
}
