package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public record SimpleSnapshot<STATUS extends IStatus<STATUS>, H extends HeldItem<H, ?> & Item<H>>(
        @NotNull JobID jobId, @NotNull STATUS status, @NotNull ImmutableList<H> items
) implements ImmutableSnapshot<H, SimpleSnapshot<STATUS, H>> {
    @Override
    public String statusStringValue() {
        return this.status().name();
    }

    @Override
    public String jobStringValue() {
        // TODO: Proper serializer
        return jobId.rootId() + "/" + jobId.jobId();
    }

    @Override
    public SimpleSnapshot<STATUS, H> withSetItem(int itemIndex, H item) {
        ArrayList<H> a = new ArrayList<>(items);
        a.set(itemIndex, item);
        return new SimpleSnapshot<>(jobId, status, ImmutableList.copyOf(a));
    }

    @Override
    public SimpleSnapshot<STATUS, H> withItems(ImmutableList<H> items) {
        return new SimpleSnapshot<>(jobId, status, ImmutableList.copyOf(items));
    }

    @Override
    public String toString() {
        return "Journal.Snapshot[" + jobStringValue() + " ]{" +
                "status=" + status +
                ", items=" + items +
                '}';
    }
}