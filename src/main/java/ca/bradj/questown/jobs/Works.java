package ca.bradj.questown.jobs;

import ca.bradj.questown.core.Config;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.gatherer.*;
import com.google.common.base.Suppliers;
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

    /** Whether the job data has loaded. */
    public static boolean isInitialized() {
        return initialized;
    }

    public static void staticInitialize(
            Map<JobID, Work> dataPackJobs
    ) {
        ImmutableMap.Builder<JobID, Supplier<Work>> b = ImmutableMap.builder();
        dataPackJobs.forEach((k, v) -> b.put(k, () -> v));
        // TODO: The explorer job is not in this line yet. For its implementation see #403.
        b.put(GathererMappedAxeWork.ID, builtOnce(GathererMappedAxeWork::asWork));
        b.put(GathererUnmappedShovelWorkQtrDay.ID, builtOnce(GathererUnmappedShovelWorkQtrDay::asWork));
        b.put(GathererUnmappedShovelWorkHalfDay.ID, builtOnce(GathererUnmappedShovelWorkHalfDay::asWork));
        b.put(GathererUnmappedShovelWorkFullDay.ID, builtOnce(GathererUnmappedShovelWorkFullDay::asWork));

        works = b.build();
        initialized = true;
        ServerJobsRegistry.forgetWhichBlocksJobsCareAbout();
    }

    /** Builds a job's Work once per data load and shares it. A Work never changes, and it is looked up for every block on every flag tick. */
    private static Supplier<Work> builtOnce(Supplier<Work> factory) {
        return Suppliers.memoize(factory::get)::get;
    }

    public static ImmutableSet<Map.Entry<JobID, Supplier<Work>>> regularJobs() {
        assert initialized;
        ImmutableSet.Builder<Map.Entry<JobID, Supplier<Work>>> b = ImmutableSet.builder();
        b.addAll(works.entrySet());
        return b.build();
    }

    public static ImmutableSet<Map.Entry<JobID, Supplier<Work>>> entrySet(String rootForDining) {
        assert initialized;
        ImmutableSet.Builder<Map.Entry<JobID, Supplier<Work>>> b = ImmutableSet.builder();
        b.addAll(works.entrySet());
        if (Config.HUNGER_ENABLED.get()) {
            b.add(new AbstractMap.SimpleEntry<>(DinerWork.getIdForRoot(rootForDining), () -> DinerWork.asWork(rootForDining)));
        }
        return b.build();
    }

    /**
     * @deprecated Doesn't handle special jobs well. Try using ServerJobsRegistry instead.
     */
    @Deprecated(forRemoval = true)
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
