package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;

public class JobRelationship implements Iterable<JobRelationship> {

    private final JobID prerequisite;
    private final HashSet<JobRelationship> jobs;

    JobRelationship(
            @Nullable JobID prerequisite,
            Collection<JobRelationship> jobs
    ) {
        this.prerequisite = prerequisite;
        this.jobs = new HashSet<>(jobs);
    }

    public <X> void forEach(
            X parentWidget,
            TriFunction<JobRelationship, ContextualPosition, X, X> fn
    ) {
        int i = 0;
        int leafs = countLeafNodes(); // TODO:: This is probably quite inefficient
        for (JobRelationship j : jobs) {
            X newWidget = fn.apply(j, new ContextualPosition(i, jobs.size(), leafs), parentWidget);
            j.forEach(newWidget, fn);
            i++;
        }
    }

    public JobID prerequisite() {
        return prerequisite;
    }

    public void addChildLeaf(JobID id) {
        if (jobs.stream().anyMatch(v -> id.equals(v.prerequisite))) {
            return;
        }
        jobs.add(new JobRelationship(id, ImmutableList.of()));
    }

    @NotNull
    @Override
    public Iterator<JobRelationship> iterator() {
        return jobs.iterator();
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobRelationship that = (JobRelationship) o;
        return Objects.equals(prerequisite, that.prerequisite) && Objects.equals(jobs, that.jobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(prerequisite, jobs);
    }
}
