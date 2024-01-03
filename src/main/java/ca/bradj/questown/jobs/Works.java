package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlWork;
import ca.bradj.questown.jobs.crafter.CrafterPaperWork;
import ca.bradj.questown.jobs.crafter.CrafterPlanksWork;
import ca.bradj.questown.jobs.crafter.CrafterStickWork;
import ca.bradj.questown.jobs.gatherer.ExplorerWork;
import ca.bradj.questown.jobs.gatherer.GathererMappedAxeWork;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedAxeWork;
import ca.bradj.questown.jobs.smelter.SmelterJob;
import com.google.common.collect.ImmutableMap;

// This file attempts to resemble a collection of JSON files - which is the ultimate vision of Questown.
// Having a JSON file approach would allow other mods to easily integrate with Questown.
public class Works {

    static final ImmutableMap<JobID, Work> works;

    static {
        ImmutableMap.Builder<JobID, Work> b = ImmutableMap.builder();
        // TODO: Replace these with production jobs
//        b.put(GathererJob.ID, new Work(
//                (town, uuid) -> new GathererJob(town, 6, uuid),
//                GATHERER_SNAPSHOT_FUNC,
//                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
//                NOT_REQUIRED_BECAUSE_BLOCKLESS_JOB,
//                GathererJournal.Status.IDLE,
//                t -> t.allKnownGatherItemsFn().apply(GathererTools.NO_TOOL_TABLE_PREFIX),
//                NOT_REQUIRED_BECAUSE_NO_JOB_QUEST,
//                // TODO: Review this - probably should be different by status
//                (s) -> ImmutableList.of(Ingredient.of(TagsInit.Items.VILLAGER_FOOD))
//        ));
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
        b.put(BakerBreadWork.ID, BakerBreadWork.asWork());
        b.put(SmelterJob.ID, SmelterJob.asWork());
        b.put(BlacksmithWoodenPickaxeJob.ID, BlacksmithWoodenPickaxeJob.asWork());
        b.put(CrafterBowlWork.ID, CrafterBowlWork.asWork());
        b.put(CrafterStickWork.ID, CrafterStickWork.asWork());
        b.put(CrafterPaperWork.ID, CrafterPaperWork.asWork());
        b.put(CrafterPlanksWork.ID, CrafterPlanksWork.asWork());
        b.put(GathererUnmappedAxeWork.ID, GathererUnmappedAxeWork.asWork());
        b.put(GathererMappedAxeWork.ID, GathererMappedAxeWork.asWork());
        b.put(ExplorerWork.ID, ExplorerWork.asWork());
        works = b.build();
    }

}
