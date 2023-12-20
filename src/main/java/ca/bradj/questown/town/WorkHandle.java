package ca.bradj.questown.town;

import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.Item;

public interface WorkHandle {
    ImmutableList<WorkRequest> getRequestedResults();

    void requestWork(Item requested);

    void requestWork(WorkRequest r);

    void removeWorkRequest(WorkRequest requested);
}
