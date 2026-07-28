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

    public static void staticInitialize(
            Map<JobID, Work> dataPackJobs
    ) {
        ImmutableMap.Builder<JobID, Supplier<Work>> b = ImmutableMap.builder();
        dataPackJobs.forEach((k, v) -> b.put(k, () -> v));
        b.put(ExplorerWork.ID, builtOnce(ExplorerWork::asWork));
        b.put(GathererMappedAxeWork.ID, builtOnce(GathererMappedAxeWork::asWork));
        b.put(GathererUnmappedShovelWorkQtrDay.ID, builtOnce(GathererUnmappedShovelWorkQtrDay::asWork));
        b.put(GathererUnmappedShovelWorkHalfDay.ID, builtOnce(GathererUnmappedShovelWorkHalfDay::asWork));
        b.put(GathererUnmappedShovelWorkFullDay.ID, builtOnce(GathererUnmappedShovelWorkFullDay::asWork));

        works = b.build();
        initialized = true;
        ServerJobsRegistry.forgetWhichBlocksJobsCareAbout();
    }

    /**
     * Datapack jobs are built once at load and handed out as shared instances. The hardcoded jobs
     * below were method references, so every {@code values().get()} rebuilt a whole Work — and
     * {@link ServerJobsRegistry#shouldInitializeWithState} walks all of them per block position,
     * per work-status store, on every flag tick. A Work is an immutable description of a job, so
     * it is built once per datapack load like the rest.
     */
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
