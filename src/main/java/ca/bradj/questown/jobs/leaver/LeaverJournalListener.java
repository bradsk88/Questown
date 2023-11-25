package ca.bradj.questown.jobs.leaver;

import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.Jobs;
import ca.bradj.questown.jobs.JournalItemsListener;
import com.google.common.collect.ImmutableList;

public class LeaverJournalListener implements JournalItemsListener<MCHeldItem> {
    private final LeaverJob job;

    public LeaverJournalListener(LeaverJob leaverJob) {
        job = leaverJob;
    }


    @Override
    public void itemsChanged(ImmutableList<MCHeldItem> items) {
        Jobs.handleItemChanges(job.inventory, items);
    }

}
