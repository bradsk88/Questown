package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;

public record SimpleSnapshot<H extends HeldItem<H, ?> & GathererJournal.Item<H>>(
        String jobName, GathererJournal.Status status, ImmutableList<H> items
) implements ca.bradj.questown.jobs.Snapshot<H> {
    @Override
    public String statusStringValue() {
        return this.status().name();
    }

    @Override
    public String jobStringValue() {
        return jobName;
    }

    @Override
    public String toString() {
        return jobName + "Journal.Snapshot{" +
                "status=" + status +
                ", items=" + items +
                '}';
    }
}