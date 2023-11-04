package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

public record SimpleSnapshot<STATUS extends IStatus<STATUS>, H extends HeldItem<H, ?> & Item<H>>(
        @NotNull String jobName, @NotNull STATUS status, @NotNull ImmutableList<H> items
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