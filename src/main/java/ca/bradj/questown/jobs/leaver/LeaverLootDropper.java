package ca.bradj.questown.jobs.leaver;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.Jobs;

import java.util.UUID;

public class LeaverLootDropper implements Jobs.LootDropper<MCHeldItem> {
    private final LeaverJob job;

    public LeaverLootDropper(LeaverJob leaverJob) {
        job = leaverJob;
    }

    @Override
    public UUID UUID() {
        return job.ownerUUID;
    }

    @Override
    public boolean hasAnyLootToDrop() {
        return job.journal.hasAnyLootToDrop();
    }

    @Override
    public Iterable<MCHeldItem> getItems() {
        return job.journal.getItems();
    }

    @Override
    public boolean removeItem(MCHeldItem mct) {
        return job.journal.removeItem(mct);
    }
}
