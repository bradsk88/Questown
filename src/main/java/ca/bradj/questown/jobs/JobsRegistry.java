package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.DinerNoTableWork;
import ca.bradj.questown.jobs.declarative.DinerWork;
import ca.bradj.questown.jobs.declarative.ResterWork;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.declarative.meta.DinerRawFoodWork;
import ca.bradj.questown.jobs.gatherer.GathererUnmappedNoToolWorkQtrDay;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.requests.WorkRequest;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.town.NoOpWarper;
import ca.bradj.questown.town.Warper;
import ca.bradj.questown.town.workstatus.State;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vazkii.patchouli.api.TriPredicate;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.isSeekingWork;

public class JobsRegistry {

    private record SpecialJob(
            Predicate<JobID> idTest,
            BiFunction<JobID, UUID, Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> jobFn,
            TriFunction<JobID, @Nullable Snapshot<MCHeldItem>, @Nullable ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>> journalFn,
            TriPredicate<JobID, Supplier<BlockState>, BlockPos> jobBlockTest,
            BiFunction<JobID, IStatus<?>, ImmutableList<Ingredient>> needs
    ) {

        static SpecialJob fromWork(
                Predicate<JobID> idTest,
                Function<JobID, Work> work
        ) {
            HashMap<JobID, Work> cache = new HashMap<>();
            Function<JobID, Work> cached = id -> {
                Work w = cache.get(id);
                if (w != null) {
                    return w;
                }
                Work newOne = work.apply(id);
                cache.put(id, newOne);
                return newOne;
            };
            return new SpecialJob(
                    idTest,
                    (id, owner) -> cached.apply(id).jobFunc().apply(owner),
                    (id, snap, held) -> newJournal(id, snap, held, cached.apply(id)),
                    (id, bs, bp) -> cached.apply(id).isJobBlock().test(ignored -> bs.get(), bp),
                    (id, s) -> ImmutableList.copyOf(cached.apply(id).needs().apply(s))
            );
        }
    }

    private static ImmutableList<SpecialJob> specialJobs;

    private static void initSpecialJobs() {
        ImmutableList.Builder<SpecialJob> b = ImmutableList.builder();

        b.add(new SpecialJob(
                ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob::isSeekingWork,
                (j, owner) -> new WorkSeekerJob(owner, 6, j.rootId()),
                JobsRegistry::newWorkSeekerJournal,
                (id, bsSrc, bp) -> {
                    Block block = bsSrc.get().getBlock();
                    return Ingredient.of(TagsInit.Items.JOB_BOARD_INPUTS).test(block.asItem().getDefaultInstance());
                },
                (id, s) -> ImmutableList.of()
        ));

        b.add(SpecialJob.fromWork(DinerWork::isDining, id -> DinerWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(DinerNoTableWork::isDining, id -> DinerNoTableWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(DinerRawFoodWork::isDining, id -> DinerRawFoodWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(ResterWork::isResting, id -> ResterWork.asWork(id.rootId())));

        specialJobs = b.build();
    }

    public static boolean isJobBlock(
            Function<BlockPos, BlockState> sl,
            BlockPos bp
    ) {
        if (sl.apply(bp).isAir()) {
            return false;
        }
        BlockState bs = sl.apply(bp);
        Block b = bs.getBlock();
        JobID a = new JobID("temporary", "temporary"); // TODO: Add a way to get the jobBlockTest without an ID
        for (SpecialJob sj : specialJobs) {
            if (sj.jobBlockTest.test(a, () -> bs, bp)) {
                return true;
            }
        }
        boolean isWorkMatch = Works.values().stream().anyMatch(v -> v.get().isJobBlock().test(sl, bp));
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
        return NoOpWarper.INSTANCE;

        // TODO: Bring back warpers
//        if (isSeekingWork(jobID)) {
//            return NoOpWarper.INSTANCE;
//        }
//        Supplier<Work> w = Works.get(jobID);
//        assert w != null;
//        return w.get().warper().apply(new WorksBehaviour.WarpInput(villagerIndex));
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
        return requestedResult.test(w.get().initialRequest());
    }

    public static Function<IStatus<?>, ImmutableList<Ingredient>> getWantedResourcesProvider(
            JobID p
    ) {
        if (isSeekingWork(p)) {
            return (s) -> ImmutableList.of(Ingredient.of(ItemsInit.JOB_BOARD_BLOCK.get()));
        }
        if (ResterWork.isResting(p)) {
            return (s) -> ImmutableList.of(Ingredient.of(ItemsInit.HOSPITAL_BED.get()));
        }

        Optional<SpecialJob> sj = specialJobs.stream().filter(v -> v.idTest.test(p)).findFirst();
        if (sj.isPresent()) {
            return s -> sj.get().needs().apply(p, s);
        }

        Supplier<Work> w = Works.get(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return (s) -> ImmutableList.of();
        }
        return s -> ImmutableList.copyOf(w.get().needs().apply(s));
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
                                     .map(v -> {
                                         ImmutableSet.Builder<ItemStack> b = ImmutableSet.builder();
                                         v.get().results().apply(t).forEach(z -> b.add(z.toItemStack()));
                                         b.add(v.get().initialRequest());
                                         return b.build();
                                     })
                                     .flatMap(Collection::stream)
                                     .map(Ingredient::of)
                                     .map(Ingredients::asWorkRequest)
                                     .collect(Collectors.toSet())
                                     .stream()
                                     .map(WorkRequest::asIngredient)
                                     .toList();
        return ImmutableSet.copyOf(list);
    }

    public static boolean isDining(JobID jobID) {
        return DinerWork.isDining(jobID) || DinerNoTableWork.isDining(jobID) || DinerRawFoodWork.isDining(jobID);
    }

    public static boolean canFit(
            UUID villagerID,
            JobID p,
            Signals.DayTime currentTick
    ) {
        Work w = Works.get(p).get();
        long jobDuration = w.jobFunc()
                            .apply(villagerID)
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

    public static void staticInitialize(ImmutableMap<JobID, Work> js) {
        initJobs(js);
        initSpecialJobs();
    }

    private static void initJobs(ImmutableMap<JobID, Work> js) {
        ImmutableMap.Builder<String, Jerb> b = ImmutableMap.builder();

        HashMap<String, ArrayList<Work>> ps = new HashMap<>();
        js.forEach((id, job) -> {
            ArrayList<Work> rL = Util.getOrDefault(ps, id.rootId(), new ArrayList<>());
            rL.add(job);
            rL.sort(Comparator.comparingInt(Work::priority));
            ps.put(id.rootId(), rL);
        });

        ps.forEach((rootId, w) -> {
            b.put(rootId, new Jerb(
                    w.stream().map(Work::id).toList(),
                    ImmutableList.of()
            ));
        });
        jobs = b.build();
    }

    private record Jerb(
            ImmutableList<JobID> preferredWork,
            ImmutableList<JobID> defaultWork
    ) {
        public Jerb(
                List<JobID> preferredWork,
                List<JobID> defaultWork
        ) {
            this(
                    ImmutableList.copyOf(preferredWork),
                    ImmutableList.copyOf(defaultWork)
            );
        }
    }

    private static ImmutableMap<String, Jerb> jobs;

    public static Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> getInitializedJob(
            JobID jobName,
            @NotNull Snapshot<MCHeldItem> journal,
            UUID ownerUUID
    ) {
        return getInitializedJob(jobName, journal, null, ownerUUID);
    }

    public static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            JobID jobName,
            ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        return getInitializedJob(jobName, null, heldItems, ownerUUID);
    }

    private static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            JobID jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> j = null;
        for (SpecialJob sj : specialJobs) {
            if (sj.idTest.test(jobName)) {
                j = sj.jobFn.apply(jobName, ownerUUID);
                journal = sj.journalFn.apply(jobName, journal, heldItems);
                break;
            }
        }
        if (j == null) {
            Supplier<Work> fn = Works.get(jobName);
            if (fn == null) {
                QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
                j = Works.get(GathererUnmappedNoToolWorkQtrDay.ID).get().jobFunc().apply(ownerUUID);
            } else {
                Work work = fn.get();
                j = work.jobFunc().apply(ownerUUID);
                journal = newJournal(jobName, journal, heldItems, work);
            }
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
