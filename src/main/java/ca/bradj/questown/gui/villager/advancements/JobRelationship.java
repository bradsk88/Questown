package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import org.apache.commons.lang3.function.TriFunction;

import javax.annotation.Nullable;
import java.util.Collection;

public record JobRelationship(
        @Nullable JobID prerequisite,
        Collection<JobRelationship> jobs
) {
    public <X> void forEach(X parentWidget, TriFunction<JobRelationship, ContextualPosition, X, X> fn) {
        int i = 0;
        int leafs = countLeafNodes(); // TODO:: This is probably quite inefficient
        for (JobRelationship j : jobs) {
            X newWidget = fn.apply(j, new ContextualPosition(i, jobs.size(), leafs), parentWidget);
            j.forEach(newWidget, fn);
            i++;
        }
    }

    public record ContextualPosition(
            int pos,
            int sizeOfLevel,
            int relevantLeafNodes
    ) {
    }

    public int countLeafNodes() {
        if (jobs.isEmpty()) {
            return 1;
        }
        return jobs.stream().map(JobRelationship::countLeafNodes).reduce(Integer::sum).orElse(0);
    }
}
