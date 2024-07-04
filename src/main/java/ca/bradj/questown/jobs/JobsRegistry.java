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
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.gatherer.*;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.jobs.smelter.SmelterJob;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.NoOpWarper;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.isSeekingWork;

public class JobsRegistry {

    private static final WorksBehaviour.SnapshotFunc GATHERER_SNAPSHOT_FUNC = (id, status, items) ->
            new GathererJournal.Snapshot<>(GathererJournal.Status.from(status), items);

    public static boolean isJobBlock(Block b) {
        if (Ingredient.of(TagsInit.Items.JOB_BOARD_INPUTS).test(b.asItem().getDefaultInstance())) {
            return true;
        }
        boolean isWorkMatch = Works.values().stream().anyMatch(v -> v.get().isJobBlock().test(b));
        // TODO: This might not be needed anymore
        if (Ingredient.of(ItemsInit.PLATE_BLOCK.get()).test(b.asItem().getDefaultInstance())) {
            return true;
        }
        // TODO: This might not be needed anymore
        if (Ingredient.of(ItemsInit.TOWN_FLAG_BLOCK.get()).test(b.asItem().getDefaultInstance())) {
            return true;
        }

        return isWorkMatch;
    }

    public static Set<JobID> getAllJobs() {
        return Works.ids().stream()
                .filter(v -> !isSeekingWork(v))
                .collect(Collectors.toSet());
    }

    public static ResourceLocation getRoomForJobRootId(
            ServerLevel rand,
            String rootId
    ) {
        List<Map.Entry<JobID, Supplier<Work>>> x = Works.entrySet(rootId)
                .stream()
                .filter(v -> v.getKey().rootId().equals(rootId))
                .toList();
        return x.get(Compat.nextInt(rand, x.size())).getValue().get().baseRoom();
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
                case "gatherer" -> GathererUnmappedNoToolWorkQtrDay.ID;
                case "baker" -> BakerBreadWork.ID;
                case "smelter" -> SmelterJob.ID;
                default -> throw new IllegalArgumentException("Unknown single-part job ID: " + parts[0]);
            };
        }
        throw new IllegalArgumentException("Unexpected job ID format: " + jobID);
    }

    public static State getDefaultJobBlockState(Block b) {
        if (b instanceof JobBoardBlock) {
            return State.freshAtState(ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.MAX_STATE);
        }
        return State.fresh();
    }

    public static Warper<ServerLevel, MCTownState> getWarper(
            int villagerIndex,
            JobID jobID
    ) {
        if (isSeekingWork(jobID)) {
            return NoOpWarper.INSTANCE;
        }
        Supplier<Work> w = Works.get(jobID);
        assert w != null;
        return w.get().warper().apply(new WorksBehaviour.WarpInput(villagerIndex));
    }

    public static boolean canSatisfy(
            WorksBehaviour.TownData town,
            JobID p,
            Ingredient requestedResult
    ) {
        if (isSeekingWork(p)) {
            return false;
        }

        Supplier<Work> w = Works.get(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return false;
        }
        for (MCTownItem r : w.get().results().apply(town)) {
            if (requestedResult.test(r.toItemStack())) {
                return true;
            }
        }
        return false;
    }

    public static Function<IStatus<?>, Collection<Ingredient>> getWantedResourcesProvider(
            JobID p
    ) {
        if (isSeekingWork(p)) {
            return (s) -> ImmutableList.of(Ingredient.of(ItemsInit.JOB_BOARD_BLOCK.get().asItem()));
        }

        Supplier<Work> w = Works.get(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return (s) -> ImmutableList.of();
        }
        return w.get().needs();
    }

    public static ItemStack getDefaultWorkForNewWorker(JobID v) {
        if (isSeekingWork(v)) {
            return ItemStack.EMPTY;
        }
        Supplier<Work> w = Works.get(v);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", v);
            return ItemStack.EMPTY;
        }
        return w.get().initialRequest();
    }

    public static ImmutableSet<Ingredient> getAllOutputs(WorksBehaviour.TownData t) {
        List<Ingredient> list = Works.values().stream()
                .map(v -> v.get().results().apply(t))
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

    public static boolean isDining(JobID jobID) {
        return DinerWork.isDining(jobID) || DinerNoTableWork.isDining(jobID);
    }

    public static boolean canFit(
            TownInterface town,
            UUID villagerID,
            JobID p,
            Signals.DayTime currentTick
    ) {
        Work w = Works.get(p).get();
        long jobDuration = w.jobFunc()
                              .apply(town, villagerID)
                              .getTotalDuration();
        long finalTick = currentTick.dayTime() + jobDuration;
        Signals nextSegment = Signals.fromDayTime(new Signals.DayTime(finalTick));
        Signals currentSegment = Signals.fromDayTime(currentTick);
        if (nextSegment.compareTo(currentSegment) < 0) {
            return false;
        }
        return ImmutableList.of(
                Signals.MORNING,
                Signals.NOON
        ).contains(nextSegment);
    }

    private record Jerb(
            ImmutableList<JobID> preferredWork,
            ImmutableList<JobID> defaultWork
    ) {

    }

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
            GathererUnmappedNoToolWorkQtrDay.ID.rootId(), new Jerb(
                    ImmutableList.of(
                            ExplorerWork.ID,
                            GathererUnmappedNoToolWorkQtrDay.ID,
                            GathererUnmappedPickaxeWorkQtrDay.ID,
                            GathererUnmappedShovelWorkQtrDay.ID,
                            GathererUnmappedAxeWorkQtrDay.ID,
                            GathererMappedAxeWork.ID
                    ),
                    ImmutableList.of(GathererUnmappedNoToolWorkQtrDay.ID)
            ),
            BlacksmithWoodenPickaxeJob.DEF.jobId().rootId(), new Jerb(
                    ImmutableList.of(BlacksmithWoodenPickaxeJob.DEF.jobId()),
                    ImmutableList.of(BlacksmithWoodenPickaxeJob.DEF.jobId())
            )
            // FIXME: Handle jobs from files
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
        Supplier<Work> fn = Works.get(jobName);
        if (isSeekingWork(jobName)) {
            j = new WorkSeekerJob(ownerUUID, 6, jobName.rootId());
            journal = newWorkSeekerJournal(jobName, journal, heldItems);
        } else if (DinerWork.isDining(jobName)) {
            Work dw = DinerWork.asWork(jobName.rootId());
            j = dw.jobFunc().apply(town, ownerUUID);
            journal = newJournal(jobName, journal, heldItems, dw);
        } else if (DinerNoTableWork.isDining(jobName)) {
            Work dw = DinerNoTableWork.asWork(jobName.rootId());
            j = dw.jobFunc().apply(town, ownerUUID);
            journal = newJournal(jobName, journal, heldItems, dw);
        } else if (fn == null) {
            QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
            j = Works.get(GathererUnmappedNoToolWorkQtrDay.ID).get().jobFunc().apply(town, ownerUUID);
        } else {
            Work work = fn.get();
            j = work.jobFunc().apply(town, ownerUUID);
            journal = newJournal(jobName, journal, heldItems, work);
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
        if (isSeekingWork(job)) {
            return new SimpleSnapshot<>(job, ProductionStatus.from(status), heldItems);
        }

        Supplier<Work> f = Works.get(job);
        if (f == null) {
            QT.JOB_LOGGER.error("No journal snapshot factory for {}. Falling back to Simple/Gatherer", job);
            f = Works.get(GathererUnmappedNoToolWorkQtrDay.ID);
        }
        return f.get().snapshotFunc().apply(job, status, heldItems);
    }
}
