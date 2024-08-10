package ca.bradj.questown.town;

import ca.bradj.questown.jobs.requests.WorkRequest;
import net.minecraft.world.item.Item;

public interface WorkHandle {
    void requestWork(Item requested);

    void requestWork(WorkRequest r);

    void removeWorkRequest(WorkRequest requested);

    boolean hasAtLeastOneBoard();
}
