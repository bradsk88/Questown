package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.BlacksmithsTableBlock;
import ca.bradj.questown.blocks.OreProcessingBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlWork;
import ca.bradj.questown.jobs.crafter.CrafterPaperWork;
import ca.bradj.questown.jobs.crafter.CrafterPlanksWork;
import ca.bradj.questown.jobs.crafter.CrafterStickWork;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.GathererTools;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.smelter.DSmelterJob;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JobsRegistry {

    private static final SnapshotFunc GATHERER_SNAPSHOT_FUNC = (id, status, items) ->
            new GathererJournal.Snapshot<>(GathererJournal.Status.from(status), items);

    public static boolean isJobBlock(Block b) {
        if (Ingredient.of(TagsInit.Items.JOB_BOARD_INPUTS).test(b.asItem().getDefaultInstance())) {
            return true;
        }
        return works.values().stream().anyMatch(v -> v.blockCheckFunc.apply(b));
    }

    public static Set<JobID> getAllJobs() {
        return works.keySet().stream()
                .filter(v -> !v.equals(GathererJob.ID))
                .filter(v -> !WorkSeekerJob.isSeekingWork(v))
                .collect(Collectors.toSet());
    }

    public static ResourceLocation getRoomForJobRootId(
            Random rand,
            String rootId
    ) {
        List<Map.Entry<JobID, Work>> x = works.entrySet()
                .stream()
                .filter(v -> v.getKey().rootId().equals(rootId))
                .toList();
        return x.get(rand.nextInt(x.size())).getValue().baseRoom;
    }

    public static ImmutableList<JobID> getPreferredWorkIds(JobID jobId) {
        Jerb f = jobs.get(jobId.rootId());
        if (f == null) {
            QT.JOB_LOGGER.error("Unrecognized job {}", jobId);
            return ImmutableList.of();
        }
        return f.preferredWork;
    }

    public static ImmutableList<JobID> getDefaultWork(JobID jobID) {
        Jerb fn = jobs.get(jobID.rootId());
        if (fn == null) {
            QT.JOB_LOGGER.error("Returning no work for unrecognized job ID: {}", jobID);
            return ImmutableList.of();
        }
        return fn.defaultWork;
    }

    public static String getStringValue(JobID jobID) {
        return String.format("%s/%s", jobID.rootId(), jobID.jobId());
    }

    public static JobID parseStringValue(String jobID) {
        String[] parts = jobID.split("/");
        if (parts.length == 2) {
            return new JobID(parts[0], parts[1]);
        }
        if (parts.length == 1) {
            return switch (parts[0]) {
                case "gatherer" -> GathererJob.ID;
                case "baker" -> BakerJob.ID;
                case "smelter" -> DSmelterJob.ID;
                default -> throw new IllegalArgumentException("Unknown single-part job ID: " + parts[0]);
            };
        }
        throw new IllegalArgumentException("Unexpected job ID format: " + jobID);
    }

    public record TownData(
            Function<GathererTools.LootTablePrefix, ImmutableSet<ItemStack>> allKnownGatherItemsFn
    ) {
    }

    public static boolean canSatisfy(
            TownData town,
            JobID p,
            Ingredient requestedResult
    ) {
        Work w = works.get(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return false;
        }
        for (ItemStack r : w.results.apply(town)) {
            if (requestedResult.test(r)) {
                return true;
            }
        }
        return false;
    }

    public static ItemStack getDefaultWorkForNewWorker(JobID v) {
        Work w = works.get(v);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", v);
            return ItemStack.EMPTY;
        }
        return w.initialRequest;
    }

    public static ImmutableList<Ingredient> getAllOutputs(TownData t) {
        return ImmutableList.copyOf(
                works.values().stream()
                        .map(v -> v.results.apply(t))
                        .flatMap(Collection::stream)
                        .map(Ingredient::of)
                        .toList()
        );
    }

    private interface JobFunc extends BiFunction<TownInterface, UUID, Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>>> {

    }

    private interface SnapshotFunc extends TriFunction<JobID, String, ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>> {

    }

    private interface BlockCheckFunc extends Function<Block, Boolean> {

    }

    private record Jerb(
            ImmutableList<JobID> preferredWork,
            ImmutableList<JobID> defaultWork
    ) {

    }

    private record Work(
            JobFunc jobFunc,
            SnapshotFunc snapshotFunc,
            BlockCheckFunc blockCheckFunc,
            ResourceLocation baseRoom,
            IStatus<?> initialStatus,
            Function<TownData, ImmutableSet<ItemStack>> results,
            ItemStack initialRequest
    ) {
    }

    private static final ResourceLocation NOT_REQUIRED_BECAUSE_BLOCKLESS_JOB = null;
    private static final BlockCheckFunc NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK = (block) -> false;
    private static final ItemStack NOT_REQUIRED_BECAUSE_NO_JOB_QUEST = ItemStack.EMPTY;

    public static final ImmutableList<JobID> CRAFTER_PREFS = ImmutableList.of(
            CrafterPlanksWork.ID,
            CrafterBowlWork.ID,
            CrafterStickWork.ID,
            CrafterPaperWork.ID
    );

    public static final ImmutableList<JobID> CRAFTER_DEFAULT_WORK = ImmutableList.of(
            CrafterPlanksWork.ID
    );

    private static final ImmutableMap<String, Jerb> jobs = ImmutableMap.of(
            FarmerJob.ID.rootId(), new Jerb(
                    ImmutableList.of(FarmerJob.ID),
                    ImmutableList.of(FarmerJob.ID)
            ),
            BakerJob.ID.rootId(), new Jerb(
                    ImmutableList.of(BakerJob.ID),
                    ImmutableList.of(BakerJob.ID)
            ),
            DSmelterJob.ID.rootId(), new Jerb(
                    ImmutableList.of(DSmelterJob.ID),
                    ImmutableList.of(DSmelterJob.ID)
            ),
            GathererJob.ID.rootId(), new Jerb(
                    ImmutableList.of(
                            //ExplorerJob.ID, // TODO[ASAP]: Bring back
                            GathererJob.ID
                    ),
                    ImmutableList.of(GathererJob.ID)
            ),
            BlacksmithWoodenPickaxeJob.ID.rootId(), new Jerb(
                    ImmutableList.of(BlacksmithWoodenPickaxeJob.ID),
                    ImmutableList.of(BlacksmithWoodenPickaxeJob.ID)
            ),
            CrafterStickWork.ID.rootId(), new Jerb(
                    CRAFTER_PREFS,
                    CRAFTER_DEFAULT_WORK
            )
    );

    private static final ImmutableMap<JobID, Work> works;


    static {
        ImmutableMap.Builder<JobID, Work> b = ImmutableMap.builder();
        b.put(GathererJob.ID, new Work(
                (town, uuid) -> new GathererJob(town, 6, uuid),
                GATHERER_SNAPSHOT_FUNC,
                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
                NOT_REQUIRED_BECAUSE_BLOCKLESS_JOB,
                GathererJournal.Status.IDLE,
                // TODO: Load all possible results via loot tables and nearby biomes
                t -> t.allKnownGatherItemsFn().apply(GathererTools.NO_TOOL_TABLE_PREFIX),
                NOT_REQUIRED_BECAUSE_NO_JOB_QUEST
        ));
        // TODO[ASAP]: Bring back for the "outside" update
//        b.put(ExplorerJob.ID, new Work(
//                (town, uuid) -> new ExplorerJob(town, 6, uuid),
//                (id, status, items) -> new GathererJournal.Snapshot<>(id, GathererJournal.Status.from(status), items),
//                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
//                NOT_REQUIRED_BECAUSE_BLOCKLESS_JOB,
//                GathererJournal.Status.IDLE,
//                t -> ImmutableSet.of(ItemsInit.GATHERER_MAP.get().getDefaultInstance()),
//                ItemsInit.GATHERER_MAP.get().getDefaultInstance()
//        ));
        b.put(FarmerJob.ID, new Work(
                (town, uuid) -> new FarmerJob(uuid, 6),
                (jobId, status, items) -> new FarmerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
                SpecialQuests.FARM,
                GathererJournal.Status.IDLE,
                t -> ImmutableSet.of(Items.WHEAT.getDefaultInstance(), Items.WHEAT_SEEDS.getDefaultInstance()),
                Items.WHEAT.getDefaultInstance()
        ));
        b.put(BakerJob.ID, new Work(
                (town, uuid) -> new BakerJob(uuid, 6),
                (jobId, status, items) -> new BakerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                NOT_REQUIRED_BECUASE_HAS_NO_JOB_BLOCK,
                Questown.ResourceLocation("bakery"),
                GathererJournal.Status.IDLE,
                t -> ImmutableSet.of(Items.BREAD.getDefaultInstance()),
                Items.BREAD.getDefaultInstance()
        ));
        b.put(DSmelterJob.ID, new Work(
                (town, uuid) -> new DSmelterJob(uuid, 6),
                productionJobSnapshot(DSmelterJob.ID),
                (block) -> block instanceof OreProcessingBlock,
                Questown.ResourceLocation("smeltery"),
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(DSmelterJob.RESULT),
                DSmelterJob.RESULT
        ));
        b.put(BlacksmithWoodenPickaxeJob.ID, new Work(
                (town, uuid) -> new BlacksmithWoodenPickaxeJob(uuid, 6),
                // TODO: Add support for smaller inventories
                productionJobSnapshot(BlacksmithWoodenPickaxeJob.ID),
                (block) -> block instanceof BlacksmithsTableBlock,
                Questown.ResourceLocation("smithy"),
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(BlacksmithWoodenPickaxeJob.RESULT),
                BlacksmithWoodenPickaxeJob.RESULT
        ));
        b.put(CrafterBowlWork.ID, new Work(
                (town, uuid) -> new CrafterBowlWork(uuid, 6), // TODO: Add support for smaller inventories
                productionJobSnapshot(CrafterBowlWork.ID),
                (block) -> block instanceof CraftingTableBlock,
                Questown.ResourceLocation("crafting_room"),
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(CrafterBowlWork.RESULT),
                CrafterBowlWork.RESULT
        ));
        b.put(CrafterStickWork.ID, new Work(
                (town, uuid) -> new CrafterStickWork(uuid, 6), // TODO: Add support for smaller inventories
                productionJobSnapshot(CrafterStickWork.ID),
                (block) -> block instanceof CraftingTableBlock,
                Questown.ResourceLocation("crafting_room"),
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(CrafterStickWork.RESULT),
                CrafterStickWork.RESULT
        ));
        b.put(CrafterPaperWork.ID, new Work(
                (town, uuid) -> new CrafterPaperWork(uuid, 6), // TODO: Add support for smaller inventories
                productionJobSnapshot(CrafterPaperWork.ID),
                (block) -> block instanceof CraftingTableBlock,
                Questown.ResourceLocation("crafting_room"),
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(CrafterPaperWork.RESULT),
                CrafterPaperWork.RESULT
        ));
        b.put(CrafterPlanksWork.ID, new Work(
                (town, uuid) -> new CrafterPlanksWork(uuid, 6), // TODO: Add support for smaller inventories
                productionJobSnapshot(CrafterPlanksWork.ID),
                (block) -> block instanceof CraftingTableBlock,
                Questown.ResourceLocation("crafting_room"),
                ProductionStatus.FACTORY.idle(),
                t -> ImmutableSet.of(CrafterPlanksWork.RESULT),
                CrafterPlanksWork.RESULT
        ));
        // TODO[ASAP]: Bring back
//        b.put(GathererMappedAxeWork.ID, new Work(
//                (town, uuid) -> new GathererMappedAxeWork(uuid, 6),
//                productionJobSnapshot(GathererMappedAxeWork.ID),
//                (block) -> block instanceof WelcomeMatBlock,
//                SpecialQuests.TOWN_GATE, // TODO[ASAP]: Confirm this works
//                ProductionStatus.FACTORY.idle(),
//                t -> t.allKnownGatherItemsFn.apply(GathererTools.AXE_LOOT_TABLE_PREFIX),
//                NOT_REQUIRED_BECAUSE_NO_JOB_QUEST
//        ));
        works = b.build();
    }

    @NotNull
    private static SnapshotFunc productionJobSnapshot(JobID id) {
        return (jobId, status, items) -> new SimpleSnapshot<>(
                id,
                ProductionStatus.from(status),
                items
        );
    }

    public static Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            JobID jobName,
            @NotNull Snapshot<MCHeldItem> journal,
            UUID ownerUUID
    ) {
        return getInitializedJob(town, jobName, journal, null, ownerUUID);
    }

    public static Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            JobID jobName,
            ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        return getInitializedJob(town, jobName, null, heldItems, ownerUUID);
    }

    private static Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            JobID jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        Job<MCHeldItem, ? extends Snapshot<MCHeldItem>, ? extends IStatus<?>> j;
        Work fn = works.get(jobName);
        if (WorkSeekerJob.isSeekingWork(jobName)) {
            j = new WorkSeekerJob(ownerUUID, 6, jobName.rootId());
            journal = newWorkSeekerJournal(jobName, journal, heldItems);
        } else if (fn == null) {
            QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
            j = new GathererJob(town, 6, ownerUUID);
        } else {
            j = fn.jobFunc.apply(town, ownerUUID);
            journal = newJournal(jobName, journal, heldItems, fn);
        }
        if (journal != null) {
            j.initialize(journal);
        }
        return j;
    }

    @Nullable
    private static Snapshot<MCHeldItem> newJournal(
            JobID jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems,
            Work fn
    ) {
        if (journal == null && heldItems != null) {
            journal = fn.snapshotFunc.apply(jobName, fn.initialStatus.name(), heldItems);
        } else if (heldItems == null) {
            QT.JOB_LOGGER.error("Null items and journal. We probably just lost items.");
        }
        return journal;
    }

    @Nullable
    private static Snapshot<MCHeldItem> newWorkSeekerJournal(
            JobID jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems
    ) {
        if (journal == null && heldItems != null) {
            journal = new SimpleSnapshot<>(jobName, ProductionStatus.FACTORY.idle(), heldItems);
        } else if (heldItems == null) {
            QT.JOB_LOGGER.error("Null items and journal. We probably just lost items.");
        }
        return journal;
    }

    public static Snapshot<MCHeldItem> getNewJournal(
            JobID job,
            String status,
            ImmutableList<MCHeldItem> heldItems
    ) {
        if (WorkSeekerJob.isSeekingWork(job)) {
            return new SimpleSnapshot<>(job, ProductionStatus.from(status), heldItems);
        }

        Work f = works.get(job);
        if (f == null) {
            QT.JOB_LOGGER.error("No journal snapshot factory for {}. Falling back to Simple/Gatherer", job);
            return GATHERER_SNAPSHOT_FUNC.apply(job, status, heldItems);
        }
        return f.snapshotFunc.apply(job, status, heldItems);
    }
}
