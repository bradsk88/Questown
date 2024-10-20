package ca.bradj.questown.jobs;

import ca.bradj.questown.gui.villager.advancements.VillagerAdvancements;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlWork;
import ca.bradj.questown.jobs.crafter.CrafterPaperWork;
import ca.bradj.questown.jobs.crafter.CrafterPlanksWork;
import ca.bradj.questown.jobs.crafter.CrafterStickWork;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.gatherer.*;
import ca.bradj.questown.jobs.smelter.SmelterJob;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;
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

    public static void staticInitialize() {
        ImmutableMap.Builder<JobID, Supplier<Work>> b = ImmutableMap.builder();
        // TODO: Replace with production job
        b.put(FarmerJob.ID, () -> new Work(
                null,
                Items.WHEAT.getDefaultInstance(),
                (town, uuid) -> new FarmerJob(uuid, 6),
                (jobId, status, items) -> new FarmerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
                SpecialQuests.FARM,
                GathererJournal.Status.IDLE,
                t -> ImmutableSet.of(
                        MCTownItem.fromMCItemStack(Items.WHEAT.getDefaultInstance()),
                        MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())
                ),
                Items.WHEAT.getDefaultInstance(),
                s -> ImmutableList.of(Ingredient.of(Items.WHEAT_SEEDS.getDefaultInstance())),
                FarmerJob.SimpleWarper::new
        ));
        b.put(BakerBreadWork.ID, BakerBreadWork::asWork);
        b.put(SmelterJob.ID, SmelterJob::asWork);
        b.put(BlacksmithWoodenPickaxeJob.ID, BlacksmithWoodenPickaxeJob::asWork);
        b.put(CrafterBowlWork.ID, CrafterBowlWork::asWork);
        b.put(CrafterStickWork.ID, CrafterStickWork::asWork);
        b.put(CrafterPaperWork.ID, CrafterPaperWork::asWork);
        b.put(CrafterPlanksWork.ID, CrafterPlanksWork::asWork);
        b.put(ExplorerWork.ID, ExplorerWork::asWork);
        b.put(GathererMappedAxeWork.ID, GathererMappedAxeWork::asWork);
        b.put(GathererUnmappedAxeWorkQtrDay.ID, GathererUnmappedAxeWorkQtrDay::asWork);
        b.put(GathererUnmappedAxeWorkHalfDay.ID, GathererUnmappedAxeWorkHalfDay::asWork);
        b.put(GathererUnmappedAxeWorkFullDay.ID, GathererUnmappedAxeWorkFullDay::asWork);
        b.put(GathererUnmappedPickaxeWorkQtrDay.ID, GathererUnmappedPickaxeWorkQtrDay::asWork);
        b.put(GathererUnmappedPickaxeWorkHalfDay.ID, GathererUnmappedPickaxeWorkHalfDay::asWork);
        b.put(GathererUnmappedPickaxeWorkFullDay.ID, GathererUnmappedPickaxeWorkFullDay::asWork);
        b.put(GathererUnmappedNoToolWorkQtrDay.ID, GathererUnmappedNoToolWorkQtrDay::asWork);
        b.put(GathererUnmappedNoToolWorkHalfDay.ID, GathererUnmappedNoToolWorkHalfDay::asWork);
        b.put(GathererUnmappedNoToolWorkFullDay.ID, GathererUnmappedNoToolWorkFullDay::asWork);
        b.put(GathererUnmappedShovelWorkQtrDay.ID, GathererUnmappedShovelWorkQtrDay::asWork);
        b.put(GathererUnmappedShovelWorkHalfDay.ID, GathererUnmappedShovelWorkHalfDay::asWork);
        b.put(GathererUnmappedShovelWorkFullDay.ID, GathererUnmappedShovelWorkFullDay::asWork);
        b.put(GathererUnmappedRodWorkQtrDay.ID, GathererUnmappedRodWorkQtrDay::asWork);
        b.put(GathererUnmappedRodWorkHalfDay.ID, GathererUnmappedRodWorkHalfDay::asWork);
        b.put(GathererUnmappedRodWorkFullDay.ID, GathererUnmappedRodWorkFullDay::asWork);
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
