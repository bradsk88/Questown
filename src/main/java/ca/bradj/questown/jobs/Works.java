package ca.bradj.questown.jobs;

import ca.bradj.questown.gui.villager.advancements.VillagerAdvancements;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.gatherer.*;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.BlockState;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import static ca.bradj.questown.jobs.WorksBehaviour.NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK;

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
        b.put(BlacksmithWoodenPickaxeJob.DEF.jobId(), BlacksmithWoodenPickaxeJob::asWork);
        b.put(ExplorerWork.ID, ExplorerWork::asWork);
        b.put(GathererMappedAxeWork.ID, GathererMappedAxeWork::asWork);
        b.put(GathererUnmappedAxeWorkHalfDay.ID, GathererUnmappedAxeWorkHalfDay::asWork);
        b.put(GathererUnmappedAxeWorkFullDay.ID, GathererUnmappedAxeWorkFullDay::asWork);
        b.put(GathererUnmappedPickaxeWorkQtrDay.ID, GathererUnmappedPickaxeWorkQtrDay::asWork);
        b.put(GathererUnmappedPickaxeWorkHalfDay.ID, GathererUnmappedPickaxeWorkHalfDay::asWork);
        b.put(GathererUnmappedPickaxeWorkFullDay.ID, GathererUnmappedPickaxeWorkFullDay::asWork);
        b.put(GathererUnmappedShovelWorkQtrDay.ID, GathererUnmappedShovelWorkQtrDay::asWork);
        b.put(GathererUnmappedShovelWorkHalfDay.ID, GathererUnmappedShovelWorkHalfDay::asWork);
        b.put(GathererUnmappedShovelWorkFullDay.ID, GathererUnmappedShovelWorkFullDay::asWork);
        b.put(GathererUnmappedRodWorkQtrDay.ID, GathererUnmappedRodWorkQtrDay::asWork);

        works = b.build();

        works.forEach((id, work) -> {
            VillagerAdvancements.register(id, work.get()
                                                  .parentID());
        });

        initialized = true;
    }

    public static ImmutableSet<Map.Entry<JobID, Supplier<Work>>> entrySet(String rootID) {
        assert initialized;
        ImmutableSet.Builder<Map.Entry<JobID, Supplier<Work>>> b = ImmutableSet.builder();
        b.addAll(works.entrySet());
        b.add(new AbstractMap.SimpleEntry<>(DinerWork.getIdForRoot(rootID), () -> DinerWork.asWork(rootID)));
        return b.build();
    }

    public static Supplier<Work> get(JobID jobID) {
        assert initialized;
        if (DinerWork.isDining(jobID)) {
            return () -> DinerWork.asWork(jobID.rootId());
        }
        if (DinerNoTableWork.isDining(jobID)) {
            return () -> DinerNoTableWork.asWork(jobID.rootId());
        }
        return works.get(jobID);
    }
}
