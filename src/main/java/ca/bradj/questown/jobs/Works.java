package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.gatherer.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

// This file attempts to resemble a collection of JSON files - which is the ultimate vision of Questown.
// Having a JSON file approach would allow other mods to easily integrate with Questown.
public class Works {

    private static ImmutableMap<JobID, Supplier<Work>> works;
    private static boolean initialized;

    public static Collection<Supplier<Work>> values() {
        return works.values();
    }

    public static ImmutableSet<JobID> ids() {
        return works.keySet();
    }

    public static void staticInitialize(
            Map<JobID, Work> dataPackJobs
    ) {
        ImmutableMap.Builder<JobID, Supplier<Work>> b = ImmutableMap.builder();
        dataPackJobs.forEach((k, v) -> b.put(k, () -> v));
        b.put(ExplorerWork.ID, ExplorerWork::asWork);
        b.put(GathererMappedAxeWork.ID, GathererMappedAxeWork::asWork);
        b.put(GathererUnmappedPickaxeWorkQtrDay.ID, GathererUnmappedPickaxeWorkQtrDay::asWork);
        b.put(GathererUnmappedPickaxeWorkHalfDay.ID, GathererUnmappedPickaxeWorkHalfDay::asWork);
        b.put(GathererUnmappedPickaxeWorkFullDay.ID, GathererUnmappedPickaxeWorkFullDay::asWork);
        b.put(GathererUnmappedShovelWorkQtrDay.ID, GathererUnmappedShovelWorkQtrDay::asWork);
        b.put(GathererUnmappedShovelWorkHalfDay.ID, GathererUnmappedShovelWorkHalfDay::asWork);
        b.put(GathererUnmappedShovelWorkFullDay.ID, GathererUnmappedShovelWorkFullDay::asWork);

        works = b.build();
        initialized = true;
    }

    public static ImmutableSet<Map.Entry<JobID, Supplier<Work>>> regularJobs() {
        assert initialized;
        ImmutableSet.Builder<Map.Entry<JobID, Supplier<Work>>> b = ImmutableSet.builder();
        b.addAll(works.entrySet());
        return b.build();
    }

    public static ImmutableSet<Map.Entry<JobID, Supplier<Work>>> entrySet(String rootID) {
        assert initialized;
        ImmutableSet.Builder<Map.Entry<JobID, Supplier<Work>>> b = ImmutableSet.builder();
        b.addAll(works.entrySet());
        b.add(new AbstractMap.SimpleEntry<>(DinerWork.getIdForRoot(rootID), () -> DinerWork.asWork(rootID)));
        return b.build();
    }

    public static Supplier<Work> get(JobID jobID) {
        if (!initialized) {
            throw new IllegalStateException("Works not initialized");
        }
        if (DinerWork.isDining(jobID)) {
            return () -> DinerWork.asWork(jobID.rootId());
        }
        if (DinerNoTableWork.isDining(jobID)) {
            return () -> DinerNoTableWork.asWork(jobID.rootId());
        }
        return works.get(jobID);
    }
}
