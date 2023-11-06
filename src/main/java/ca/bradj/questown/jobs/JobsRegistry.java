package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.BlacksmithsTableBlock;
import ca.bradj.questown.blocks.OreProcessingBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlWork;
import ca.bradj.questown.jobs.crafter.CrafterStickWork;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.smelter.DSmelterJob;
import ca.bradj.questown.mobs.visitor.GathererJob;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.special.SpecialQuests;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CraftingTableBlock;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

public class JobsRegistry {

    private static final SnapshotFunc GATHERER_SNAPSHOT_FUNC = productionJobSnapshot(GathererJob.ID);

    public static boolean isJobBlock(Block b) {
        // TODO: Switch to job board block
        if (Ingredient.of(ItemTags.SIGNS).test(b.asItem().getDefaultInstance())) {
            return true;
        }
        return works.values().stream().anyMatch(v -> v.blockCheckFunc.apply(b));
    }

    public static Collection<JobID> getAllJobs() {
        return works.keySet().stream()
                .filter(v -> !v.equals(GathererJob.ID))
                .filter(v -> !WorkSeekerJob.isSeekingWork(v))
                .toList();
    }

    public static ResourceLocation getRoomForJobRootId(Random rand, String rootId) {
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
            IStatus<?> initialStatus
    ) {

    }

    private static final ResourceLocation NOT_REQUIRED_BECAUSE_META_JOB = null;
    private static final BlockCheckFunc NOT_A_DECLARATIVE_JOB = (block) -> false;
    private static final ImmutableList<String> NOT_REQUIRED_BECAUSE_JOB_SEEKER = ImmutableList.of();

    public static final ImmutableList<JobID> CRAFTER_PREFS = ImmutableList.of(
            CrafterBowlWork.ID,
            CrafterStickWork.ID
    );

    public static final ImmutableList<JobID> CRAFTER_DEFAULT_WORK = ImmutableList.of(
            CrafterBowlWork.ID
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
                    ImmutableList.of(GathererJob.ID),
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

    private static final ImmutableMap<JobID, Work> works = ImmutableMap.of(
            FarmerJob.ID, new Work(
                    (town, uuid) -> new FarmerJob(uuid, 6),
                    (jobId, status, items) -> new FarmerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                    NOT_A_DECLARATIVE_JOB,
                    SpecialQuests.FARM,
                    GathererJournal.Status.IDLE
            ),
            BakerJob.ID, new Work(
                    (town, uuid) -> new BakerJob(uuid, 6),
                    (jobId, status, items) -> new BakerJournal.Snapshot<>(GathererJournal.Status.from(status), items),
                    NOT_A_DECLARATIVE_JOB,
                    new ResourceLocation("bakery"),
                    GathererJournal.Status.IDLE
            ),
            DSmelterJob.ID, new Work(
                    (town, uuid) -> new DSmelterJob(uuid, 6),
                    productionJobSnapshot(DSmelterJob.ID),
                    (block) -> block instanceof OreProcessingBlock,
                    new ResourceLocation("smeltery"),
                    ProductionStatus.FACTORY.idle()
            ),
            GathererJob.ID, new Work(
                    (town, uuid) -> new GathererJob(town, 6, uuid),
                    GATHERER_SNAPSHOT_FUNC,
                    NOT_A_DECLARATIVE_JOB,
                    NOT_REQUIRED_BECAUSE_META_JOB,
                    GathererJournal.Status.IDLE
            ),
            BlacksmithWoodenPickaxeJob.ID, new Work(
                    (town, uuid) -> new BlacksmithWoodenPickaxeJob(uuid, 6),
                    // TODO: Add support for smaller inventories
                    productionJobSnapshot(BlacksmithWoodenPickaxeJob.ID),
                    (block) -> block instanceof BlacksmithsTableBlock,
                    new ResourceLocation("smithy"),
                    ProductionStatus.FACTORY.idle()
            ),
            CrafterBowlWork.ID, new Work(
                    (town, uuid) -> new CrafterBowlWork(uuid, 6), // TODO: Add support for smaller inventories
                    productionJobSnapshot(CrafterBowlWork.ID),
                    (block) -> block instanceof CraftingTableBlock,
                    new ResourceLocation("crafting_room"),
                    ProductionStatus.FACTORY.idle()
            ),
            CrafterStickWork.ID, new Work(
                    (town, uuid) -> new CrafterStickWork(uuid, 6), // TODO: Add support for smaller inventories
                    productionJobSnapshot(CrafterStickWork.ID),
                    (block) -> block instanceof CraftingTableBlock,
                    new ResourceLocation("crafting_room"),
                    ProductionStatus.FACTORY.idle()
            )
    );

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
        Work f = works.get(job);
        if (f == null) {
            QT.JOB_LOGGER.error("No journal snapshot factory for {}. Falling back to Simple/Gatherer", job);
            return GATHERER_SNAPSHOT_FUNC.apply(job, status, heldItems);
        }
        return f.snapshotFunc.apply(job, status, heldItems);
    }
}
