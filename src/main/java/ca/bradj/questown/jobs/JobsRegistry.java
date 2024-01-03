package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.blacksmith.BlacksmithWoodenPickaxeJob;
import ca.bradj.questown.jobs.crafter.CrafterBowlWork;
import ca.bradj.questown.jobs.crafter.CrafterPaperWork;
import ca.bradj.questown.jobs.crafter.CrafterPlanksWork;
import ca.bradj.questown.jobs.crafter.CrafterStickWork;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.ExplorerWork;
import ca.bradj.questown.jobs.gatherer.GathererMappedAxeWork;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedAxeWork;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.smelter.SmelterJob;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ca.bradj.questown.jobs.Works.works;

public class JobsRegistry {

    private static final WorksBehaviour.SnapshotFunc GATHERER_SNAPSHOT_FUNC = (id, status, items) ->
            new GathererJournal.Snapshot<>(GathererJournal.Status.from(status), items);

    public static boolean isJobBlock(Block b) {
        if (Ingredient.of(TagsInit.Items.JOB_BOARD_INPUTS).test(b.asItem().getDefaultInstance())) {
            return true;
        }
        return works.values().stream().anyMatch(v -> v.isJobBlock().test(b));
    }

    public static Set<JobID> getAllJobs() {
        return works.keySet().stream()
                .filter(v -> !v.equals(GathererJob.ID))
                .filter(v -> !WorkSeekerJob.isSeekingWork(v))
                .collect(Collectors.toSet());
    }

    public static ResourceLocation getRoomForJobRootId(
            RandomSource rand,
            String rootId
    ) {
        List<Map.Entry<JobID, Work>> x = works.entrySet()
                .stream()
                .filter(v -> v.getKey().rootId().equals(rootId))
                .toList();
        return x.get(rand.nextInt(x.size())).getValue().baseRoom();
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
                case "baker" -> BakerBreadWork.ID;
                case "smelter" -> SmelterJob.ID;
                default -> throw new IllegalArgumentException("Unknown single-part job ID: " + parts[0]);
            };
        }
        throw new IllegalArgumentException("Unexpected job ID format: " + jobID);
    }

    public static AbstractWorkStatusStore.State getDefaultJobBlockState(Block b) {
        if (b instanceof JobBoardBlock) {
            return new AbstractWorkStatusStore.State(WorkSeekerJob.MAX_STATE, 0, 0);
        }
        return new AbstractWorkStatusStore.State(0, 0, 0);
    }

    public static Warper<ServerLevel, MCTownState> getWarper(
            int villagerIndex,
            JobID jobID
    ) {
        Work w = works.get(jobID);
        assert w != null;
        return w.warper().apply(new WorksBehaviour.WarpInput(villagerIndex));
    }

    public static boolean canSatisfy(
            WorksBehaviour.TownData town,
            JobID p,
            Ingredient requestedResult
    ) {
        if (WorkSeekerJob.isSeekingWork(p)) {
            return false;
        }

        Work w = works.get(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return false;
        }
        for (MCTownItem r : w.results().apply(town)) {
            if (requestedResult.test(r.toItemStack())) {
                return true;
            }
        }
        return false;
    }

    public static Function<IStatus<?>, Collection<Ingredient>> getWantedResourcesProvider(
            JobID p
    ) {
        if (WorkSeekerJob.isSeekingWork(p)) {
            return (s) -> ImmutableList.of(Ingredient.of(ItemsInit.JOB_BOARD_BLOCK.get().asItem()));
        }

        Work w = works.get(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return (s) -> ImmutableList.of();
        }
        return w.needs();
    }

    public static ItemStack getDefaultWorkForNewWorker(JobID v) {
        if (WorkSeekerJob.isSeekingWork(v)) {
            return ItemStack.EMPTY;
        }
        Work w = works.get(v);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", v);
            return ItemStack.EMPTY;
        }
        return w.initialRequest();
    }

    public static ImmutableSet<Ingredient> getAllOutputs(WorksBehaviour.TownData t) {
        List<Ingredient> list = works.values().stream()
                .map(v -> v.results().apply(t))
                .flatMap(Collection::stream)
                .map(MCTownItem::toItemStack)
                .map(Ingredient::of)
                .map(Ingredients::asWorkRequest)
                .collect(Collectors.toSet())
                .stream()
                .map(WorkRequest::asIngredient)
                .toList();
        return ImmutableSet.copyOf(list);
    }


    private record Jerb(
            ImmutableList<JobID> preferredWork,
            ImmutableList<JobID> defaultWork
    ) {

    }

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
            BakerBreadWork.ID.rootId(), new Jerb(
                    ImmutableList.of(BakerBreadWork.ID),
                    ImmutableList.of(BakerBreadWork.ID)
            ),
            SmelterJob.ID.rootId(), new Jerb(
                    ImmutableList.of(SmelterJob.ID),
                    ImmutableList.of(SmelterJob.ID)
            ),
            GathererJob.ID.rootId(), new Jerb(
                    ImmutableList.of(
                            ExplorerWork.ID,
                            GathererMappedAxeWork.ID,
                            GathererUnmappedAxeWork.ID,
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

    public static Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            JobID jobName,
            @NotNull Snapshot<MCHeldItem> journal,
            UUID ownerUUID
    ) {
        return getInitializedJob(town, jobName, journal, null, ownerUUID);
    }

    public static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            JobID jobName,
            ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        return getInitializedJob(town, jobName, null, heldItems, ownerUUID);
    }

    private static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            TownInterface town,
            JobID jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> j;
        Work fn = works.get(jobName);
        if (WorkSeekerJob.isSeekingWork(jobName)) {
            j = new WorkSeekerJob(ownerUUID, 6, jobName.rootId());
            journal = newWorkSeekerJournal(jobName, journal, heldItems);
        } else if (fn == null) {
            QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
            j = new GathererJob(town, 6, ownerUUID);
        } else {
            j = fn.jobFunc().apply(town, ownerUUID);
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
            journal = fn.snapshotFunc().apply(jobName, fn.initialStatus().name(), heldItems);
        } else if (journal == null) {
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
        } else if (journal == null) {
            QT.JOB_LOGGER.error("Null items and journal. We probably just lost items.");
        }
        return journal;
    }

    public static ImmutableSnapshot<MCHeldItem, ?> getNewJournal(
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
        return f.snapshotFunc().apply(job, status, heldItems);
    }
}
