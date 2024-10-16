package ca.bradj.questown.gui.villager.advancements;

import ca.bradj.questown.jobs.JobID;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class VillagerAdvancements {

    // This gets populated via register
    private static final JobRelationship all = new JobRelationship(
            null,
            ImmutableList.of()
    );
    private static List<Supplier<JobID>> unregistered = new ArrayList<>();

    public static JobRelationship all() {
        return all;
    }

    public static void register(JobID id, JobID parentID) {
        // TODO: audit performance
        Supplier<JobID> lambda = () -> addToParentOrReturn(id, parentID, all);
        JobID notRegistered = lambda.get();
        if (notRegistered != null) {
            unregistered.add(lambda);
            return;
        }
        if (unregistered.isEmpty()) {
            return;
        }
        ImmutableList.Builder<Supplier<JobID>> b = ImmutableList.builder();
        for (Supplier<JobID> jobIDSupplier : unregistered) {
            JobID stillUnregged = jobIDSupplier.get();
            if (stillUnregged != null) {
                b.add(jobIDSupplier);
            }
        }
        unregistered.clear();
        unregistered.addAll(b.build());
    }

    private static JobID addToParentOrReturn(JobID id, JobID parentID, JobRelationship rels) {
        if (rels.prerequisite() == null && parentID == null) {
            rels.addChildLeaf(id);
            return null;
        }
        if (rels.prerequisite() != null && rels.prerequisite().equals(parentID)) {
            rels.addChildLeaf(id);
            return null;
        }
        for (JobRelationship rel : rels) {
            JobID v = addToParentOrReturn(id, parentID, rel);
            if (v == null) {
                return null;
            }
        }
        return id;
    }
}
