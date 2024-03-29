package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlWork;
import ca.bradj.questown.jobs.crafter.CrafterPaperWork;
import ca.bradj.questown.jobs.crafter.CrafterPlanksWork;
import ca.bradj.questown.jobs.crafter.CrafterStickWork;
import ca.bradj.questown.jobs.gatherer.*;
import ca.bradj.questown.jobs.smelter.SmelterJob;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;

// This file attempts to resemble a collection of JSON files - which is the ultimate vision of Questown.
// Having a JSON file approach would allow other mods to easily integrate with Questown.
public class Works {

    private static final ImmutableMap<JobID, Supplier<Work>> works;

    public static Collection<Supplier<Work>> values() {
        return works.values();
    }

    public static ImmutableSet<JobID> ids() {
        return works.keySet();
    }

    static {
        ImmutableMap.Builder<JobID, Supplier<Work>> b = ImmutableMap.builder();
        // TODO: Replace with production job
//        b.put(FarmerJob.ID, new Work(
//                (town, uuid) -> new FarmerJob(uuid, 6),
//                (jobId, status, items) -> new FarmerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
//                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
//                SpecialQuests.FARM,
//                GathererJournal.Status.IDLE,
//                t -> ImmutableSet.of(
//                        MCTownItem.fromMCItemStack(Items.WHEAT.getDefaultInstance()),
//                        MCTownItem.fromMCItemStack(Items.WHEAT_SEEDS.getDefaultInstance())
//                ),
//                Items.WHEAT.getDefaultInstance(),
//                // TODO: Review this - probably should be different by status
//                (s) -> ImmutableList.of(Ingredient.of(Items.WHEAT_SEEDS))
//        ));
        b.put(BakerBreadWork.ID, BakerBreadWork::asWork);
        b.put(SmelterJob.ID, SmelterJob::asWork);
        b.put(BlacksmithWoodenPickaxeJob.ID, BlacksmithWoodenPickaxeJob::asWork);
        b.put(CrafterBowlWork.ID, CrafterBowlWork::asWork);
        b.put(CrafterStickWork.ID, CrafterStickWork::asWork);
        b.put(CrafterPaperWork.ID, CrafterPaperWork::asWork);
        b.put(CrafterPlanksWork.ID, CrafterPlanksWork::asWork);
        b.put(ExplorerWork.ID, ExplorerWork::asWork);
        b.put(GathererMappedAxeWork.ID, GathererMappedAxeWork::asWork);
        b.put(GathererUnmappedAxeWork.ID, GathererUnmappedAxeWork::asWork);
        b.put(GathererUnmappedPickaxeWork.ID, GathererUnmappedPickaxeWork::asWork);
        b.put(GathererUnmappedNoToolWork.ID, GathererUnmappedNoToolWork::asWork);
        b.put(GathererUnmappedShovelWork.ID, GathererUnmappedShovelWork::asWork);
        works = b.build();
    }

    public static ImmutableSet<Map.Entry<JobID, Supplier<Work>>> entrySet() {
        return works.entrySet();
    }

    public static Supplier<Work> get(JobID jobID) {
        return works.get(jobID);
    }
}
