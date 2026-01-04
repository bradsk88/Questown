package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class JobRelationship implements Iterable<JobRelationship> {

    public final @Nullable JobID parentId;
    private final JobID id;
    private final HashSet<JobRelationship> jobs;

    JobRelationship(
            @Nullable JobID parentId,
            @Nullable JobID id,
            Collection<JobRelationship> jobs
    ) {
        this.parentId = parentId;
        this.id = id;
        this.jobs = new HashSet<>(jobs);
    }

    public <X> void forEach(
            X parentWidget,
            TriFunction<JobRelationship, ContextualPosition, X, X> fn,
            Predicate<JobRelationship> include
    ) {
        int i = 0;
        int leafs = countLeafNodes(include); // TODO[Performance]: This is probably quite inefficient
        List<JobRelationship> jobz = jobs.stream().filter(include).toList();
        for (JobRelationship j : jobz) {
            X newWidget = fn.apply(j, new ContextualPosition(i, jobz.size(), leafs), parentWidget);
            j.forEach(newWidget, fn, include);
            i++;
        }
    }

    public JobID id() {
        return id;
    }

    public void addChildLeaf(JobID _id) {
        if (jobs.stream().anyMatch(v -> _id.equals(v.id))) {
            return;
        }
        jobs.add(new JobRelationship(this.id, _id, ImmutableList.of()));
    }

    @NotNull
    @Override
    public Iterator<JobRelationship> iterator() {
        return jobs.iterator();
    }

    public JobRelationship branch(String s) {
        for (JobRelationship job : jobs) {
            if (job.id != null && job.id.rootId().equals(s)) {
                return job;
            }
        }
        return this;
    }

    public JobRelationship filtered(Predicate<JobID> check) {
        List<JobRelationship> jobz = jobs.stream().filter(v -> check.test(v.id))
                                         .map(v -> v.filtered(check))
                                         .toList();
        return new JobRelationship(parentId, id, jobz);
    }

    public record ContextualPosition(int pos, int sizeOfLevel, int relevantLeafNodes) {
    }

    public int countLeafNodes(Predicate<JobRelationship> include) {
        List<JobRelationship> jobz = jobs.stream().filter(include).toList();
        if (jobz.isEmpty()) {
            return 1;
        }
        return jobz.stream().map(z -> z.countLeafNodes(include)).reduce(Integer::sum).orElse(0);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobRelationship that = (JobRelationship) o;
        return Objects.equals(id, that.id) && Objects.equals(jobs, that.jobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, jobs);
    }
}
