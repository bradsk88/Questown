package ca.bradj.questown.jobs.crafter;

import ca.bradj.questown.jobs.declarative.TaskFinderJob;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CrafterSeekingJob extends TaskFinderJob {

    public CrafterSeekingJob(
            UUID ownerUUID,
            int inventoryCapacity,
            JobChanger jobChanger
    ) {
        super(ownerUUID, inventoryCapacity, jobChanger);
    }

    @Override
    protected @Nullable String chooseBestJob(ImmutableList<String> crafter) {
        if (crafter.contains("crafter")) {
            return "crafter";
        }
        return null;
    }
}
