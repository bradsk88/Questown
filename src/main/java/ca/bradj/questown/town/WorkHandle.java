package ca.bradj.questown.town;

import ca.bradj.questown.jobs.requests.BoardRequests;
import ca.bradj.questown.jobs.requests.WorkRequest;
import com.google.common.collect.ImmutableList;
import net.minecraft.world.item.Item;

public interface WorkHandle {
    void requestWork(Item requested);

    void requestWork(WorkRequest r);

    void removeWorkRequest(WorkRequest requested);

    boolean hasAtLeastOneBoard();

    ImmutableList<WorkRequest> getRequestedResults();

    /** The requests plus the ones paused because storage already holds enough. */
    BoardRequests getBoardRequests();
}
