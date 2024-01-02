package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.gatherer.ExplorerWork;
import ca.bradj.questown.jobs.gatherer.GathererMappedAxeWork;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.smelter.DSmelterJob;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

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
        b.put(DSmelterJob.ID, DSmelterJob.asWork());
        b.put(BlacksmithWoodenPickaxeJob.ID, BlacksmithWoodenPickaxeJob.asWork());
//        b.put(CrafterBowlWork.ID, new Work(
//                (town, uuid) -> new CrafterBowlWork(uuid, 6), // TODO: Add support for smaller inventories
//                productionJobSnapshot(CrafterBowlWork.ID),
//                (block) -> block instanceof CraftingTableBlock,
//                Questown.ResourceLocation("crafting_room"),
//                ProductionStatus.FACTORY.idle(),
//                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(CrafterBowlWork.RESULT)),
//                CrafterBowlWork.RESULT,
//                s -> getProductionNeeds(
//                        CrafterBowlWork.INGREDIENTS_REQUIRED_AT_STATES,
//                        CrafterBowlWork.TOOLS_REQUIRED_AT_STATES
//                )
//        ));
//        b.put(CrafterStickWork.ID, new Work(
//                (town, uuid) -> new CrafterStickWork(uuid, 6), // TODO: Add support for smaller inventories
//                productionJobSnapshot(CrafterStickWork.ID),
//                (block) -> block instanceof CraftingTableBlock,
//                Questown.ResourceLocation("crafting_room"),
//                ProductionStatus.FACTORY.idle(),
//                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(CrafterStickWork.RESULT)),
//                CrafterStickWork.RESULT,
//                s -> getProductionNeeds(
//                        CrafterStickWork.INGREDIENTS_REQUIRED_AT_STATES,
//                        CrafterStickWork.TOOLS_REQUIRED_AT_STATES
//                )
//        ));
//        b.put(CrafterPaperWork.ID, new Work(
//                (town, uuid) -> new CrafterPaperWork(uuid, 6), // TODO: Add support for smaller inventories
//                productionJobSnapshot(CrafterPaperWork.ID),
//                (block) -> block instanceof CraftingTableBlock,
//                Questown.ResourceLocation("crafting_room"),
//                ProductionStatus.FACTORY.idle(),
//                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(CrafterPaperWork.RESULT)),
//                CrafterPaperWork.RESULT,
//                s -> getProductionNeeds(
//                        CrafterPaperWork.INGREDIENTS_REQUIRED_AT_STATES,
//                        CrafterPaperWork.TOOLS_REQUIRED_AT_STATES
//                )
//        ));
//        b.put(CrafterPlanksWork.ID, new Work(
//                (town, uuid) -> new CrafterPlanksWork(uuid, 6), // TODO: Add support for smaller inventories
//                productionJobSnapshot(CrafterPlanksWork.ID),
//                (block) -> block instanceof CraftingTableBlock,
//                Questown.ResourceLocation("crafting_room"),
//                ProductionStatus.FACTORY.idle(),
//                t -> ImmutableSet.of(MCTownItem.fromMCItemStack(CrafterPlanksWork.RESULT)),
//                CrafterPlanksWork.RESULT,
//                s -> getProductionNeeds(
//                        CrafterPlanksWork.INGREDIENTS_REQUIRED_AT_STATES,
//                        CrafterPlanksWork.TOOLS_REQUIRED_AT_STATES
//                )
//        ));
//        b.put(GathererUnmappedAxeWork.ID, new Work(
//                (town, uuid) -> new GathererUnmappedAxeWork(uuid, 6),
//                productionJobSnapshot(GathererUnmappedAxeWork.ID),
//                (block) -> block instanceof WelcomeMatBlock,
//                GathererUnmappedAxeWork.JOB_SITE,
//                ProductionStatus.FACTORY.idle(),
//                t -> t.allKnownGatherItemsFn.apply(GathererTools.AXE_LOOT_TABLE_PREFIX),
//                Items.WHEAT_SEEDS.getDefaultInstance(),
//                s -> getProductionNeeds(
//                        GathererUnmappedAxeWork.INGREDIENTS_REQUIRED_AT_STATES,
//                        GathererUnmappedAxeWork.TOOLS_REQUIRED_AT_STATES
//                )
//        ));
        b.put(GathererMappedAxeWork.ID, GathererMappedAxeWork.asWork());
        b.put(ExplorerWork.ID, ExplorerWork.asWork());
        works = b.build();
    }

}
