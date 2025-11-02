package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.init.TagsInit;
import ca.bradj.questown.core.init.items.ItemsInit;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.gui.JobTooltips;
import ca.bradj.questown.gui.StatusArt;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.integration.minecraft.MCTownState;
import ca.bradj.questown.jobs.declarative.*;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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

public class ServerJobsRegistry {

    public static boolean canAlwaysStart(
            UUID uuid,
            JobID p
    ) {
        return getWorkSupplier(p).get().jobFunc.apply(uuid).getGlobalSpecialRules().contains(SpecialRules.ALWAYS_CONSIDER);
    }

    public static ResourceLocation getTexture(
            JobID job,
            IStatus<?> status
    ) {
        try {
            Work work = getWork(job);
            if (work != null) {
                ResourceLocation tex = work.applyStatusTextureOverride(status);
                if (tex != null) {
                    return tex;
                }
            }
        } catch (Exception e) {
            QT.JOB_LOGGER.error("Failed to apply status texture override");
        }
        return StatusArt.getTexture(job, status);
    }

    public static @NotNull ImmutableList<Component> getStatusText(
            JobID job,
            IStatus<?> status
    ) {
        if (isSeekingWork(job)) {
            return JobTooltips.buildStandardTooltipKeys(status, job);
        }
        try {
            Work work = getWork(job);
            if (work == null) {
                throw new IllegalStateException("No work found for job ID: " + job);
            }
            Pair<String, String> stringStringPair = work.applyStatusTextOverride(status);
            if (stringStringPair != null) {
                return Pair.toList(stringStringPair).stream().map(Compat::translatable)
                           .collect(ImmutableList.toImmutableList());
            }
        } catch (Exception e) {
            QT.JOB_LOGGER.error("Failed to apply status tooltip override", e);
        }
        return JobTooltips.buildStandardTooltipKeys(status, job);
    }

    private static @Nullable Work getWork(JobID job) {
        Supplier<Work> workSupplier = getWorkSupplier(job);
        if (workSupplier == null) {
            return null;
        }
        Work work = workSupplier.get();
        if (work == null) {
            return null;
        }
        return work;
    }

    public static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitialJobForVillager(
            UUID villagerUUID
    ) {
        JobID initialID = GathererUnmappedNoToolWorkQtrDay.ID;
        Work work = getWorkSupplier(initialID).get();
        return work.jobFunc.apply(villagerUUID);
    }

    public static ImmutableMap<JobID, ResourceLocation> getAllJobsThatProduce(
            WorksBehaviour.TownData data,
            Ingredient wantedResult
    ) {
        ImmutableMap.Builder<JobID, ResourceLocation> b = ImmutableMap.builder();
        for (Supplier<Work> value : Works.values()) {
            Work w = value.get();
            if (w.hasNoOutput()) {
                continue;
            }
            ResourceLocation name = Compat.getItemId(w.icon.getItem());
            if (wantedResult.test(w.initialRequest)) {
                b.put(w.id, Util.ifNull(name, Questown.ResourceLocationError));
                continue;
            }
            ImmutableSet<MCTownItem> wResults = w.results.apply(data);
            for (MCTownItem wr : wResults) {
                if (wantedResult.test(wr.toQTItemStack())) {
                    b.put(w.id, Util.ifNull(name, Questown.ResourceLocationError));
                    break;
                }
            }
        }
        return b.build();
    }

    public static Collection<JobID> getRandomNodesUnder(
            JobID parentID,
            Supplier<Integer> randomInt
    ) {
        return null;
    }

    /**
     * @throws NullPointerException if job does not exist
     */
    public static boolean isParentOf(
            JobID parent,
            JobID child
    ) {
        @SuppressWarnings("DataFlowIssue") JobID parentID = getWork(child).parentID;
        if (parentID == null) return false;
        return parentID.equals(parent);
    }

    public static ImmutableSet<JobID> getAllRootJobs() {
        ImmutableSet.Builder<JobID> b = ImmutableSet.builder();
        for (JobID j : getAllJobs()) {
            if (getWork(j).parentID == null) {
                b.add(j);
            }
        }
        return b.build();
    }

    public static ImmutableSet<MCTownItem> getResults(
            WorksBehaviour.TownData data,
            JobID job
    ) {
        Supplier<Work> work = getWorkSupplier(job);
        Work w = work.get();
        return w.results.apply(data);
    }

    private record SpecialJob(Predicate<JobID> idTest,
                              BiFunction<JobID, UUID, Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> jobFn,
                              TriFunction<JobID, @Nullable Snapshot<MCHeldItem>, @Nullable ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>> journalFn,
                              TriPredicate<JobID, Supplier<BlockState>, JobBlockTestContext> jobBlockTest,
                              TriPredicate<JobID, Supplier<BlockState>, Pair<WorkLocation.BlockInfo, BlockPos>> shouldInit,
                              BiFunction<JobID, List<MCHeldItem>, ImmutableList<Ingredient>> needs) {

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
                    (id, owner) -> cached.apply(id).jobFunc.apply(owner),
                    (id, snap, held) -> newJournal(id, snap, held, cached.apply(id)),
                    (id, bs, bp) -> cached.apply(id).isJobBlock.test(new JobBlockTestContext(
                            null, new WorkLocation.BlockInfo() {
                        @Override
                        public BlockState state(BlockPos bp) {
                            return bs.get();
                        }

                        @Override
                        public @Nullable BlockEntity entity(BlockPos bp) {
                            return null;
                        }
                    }, bp.blockPos(), bp.heldItems(), bp.townUniqueItems(), bp.jobBlockAlreadyUsed(),
                            bp.jobActive()
                    )),
                    (id, bs, bp) -> cached.apply(id).shouldInitializeWorkState.test(
                            new WorkLocation.BlockInfo() {
                                @Override
                                public BlockState state(BlockPos bp) {
                                    return bs.get();
                                }

                                @Override
                                public @Nullable BlockEntity entity(BlockPos bp) {
                                    return null;
                                }
                            }, bp.b()
                    ),
                    (id, items) -> ImmutableList.copyOf(cached.apply(id).needs.apply(items))
            );
        }
    }

    private static ImmutableList<SpecialJob> specialJobs;

    private static void initSpecialJobs() {
        ImmutableList.Builder<SpecialJob> b = ImmutableList.builder();

        b.add(new SpecialJob(
                ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob::isSeekingWork,
                (j, owner) -> new WorkSeekerJob(owner, 6, j.rootId()),
                ServerJobsRegistry::newWorkSeekerJournal,
                (id, bsSrc, bp) -> {
                    Block block = bsSrc.get().getBlock();
                    return Ingredient.of(TagsInit.Items.JOB_BOARD_INPUTS).test(block.asItem().getDefaultInstance());
                },
                (id, bsSrc, bp) -> {
                    Block block = bsSrc.get().getBlock();
                    return Ingredient.of(TagsInit.Items.JOB_BOARD_INPUTS).test(block.asItem().getDefaultInstance());
                },
                (id, items) -> ImmutableList.of()
        ));

        b.add(SpecialJob.fromWork(DinerWork::isDining, id -> DinerWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(DinerNoTableWork::isDining, id -> DinerNoTableWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(DinerRawFoodWork::isDining, id -> DinerRawFoodWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(ResterWork::isResting, id -> ResterWork.asWork(id.rootId())));
        b.add(SpecialJob.fromWork(BOPDepositorWork::matches, id -> BOPDepositorWork.asWork(id.rootId())));

        specialJobs = b.build();
    }


    public static boolean shouldInitializeWithState(
            WorkLocation.BlockInfo info,
            BlockPos pos
    ) {
        BlockState state = info.state(pos);
        if (state.isAir()) {
            return false;
        }
        BlockState bs = state;
        Block b = bs.getBlock();
        JobID a = new JobID("temporary", "temporary"); // TODO: Add a way to get the jobBlockTest without an ID
        for (SpecialJob sj : specialJobs) {
            if (sj.shouldInit.test(a, () -> bs, new Pair<>(info, pos))) {
                return true;
            }
        }
        boolean isWorkMatch = Works
                .values()
                .stream()
                .anyMatch(v -> v.get().shouldInitializeWorkState.test(info, pos));

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

    public static boolean isJobBlock(
            JobBlockTestContext ctx
    ) {
        BlockState state = ctx.blockInfo().state(ctx.blockPos());
        if (state.isAir()) {
            return false;
        }
        BlockState bs = state;
        Block b = bs.getBlock();
        JobID a = new JobID("temporary", "temporary"); // TODO: Add a way to get the jobBlockTest without an ID
        for (SpecialJob sj : specialJobs) {
            if (sj.jobBlockTest.test(a, () -> bs, ctx)) {
                return true;
            }
        }
        boolean isWorkMatch = Works.values().stream().anyMatch(v -> v.get().isJobBlock.test(ctx));
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

    public static ImmutableSet<JobID> getAllJobs() {
        return ImmutableSet.copyOf(Works.ids().stream().filter(v -> !isSeekingWork(v)).collect(Collectors.toSet()));
    }

    public static ResourceLocation getRoomForJobRootId(
            ServerLevel rand,
            String rootId
    ) {
        Work work = getRandomWork(rand, rootId, v -> true);
        return work.baseRoom;
    }

    public static ResourceLocation getRoomForJobId(
            JobID jobId
    ) {
        Supplier<Work> w = getWorkSupplier(jobId);
        return w.get().baseRoom;
    }

    public static Work getRandomWork(
            ServerLevel rand,
            String rootId,
            Predicate<JobID> include
    ) {
        List<Map.Entry<JobID, Supplier<Work>>> x = Works.entrySet(rootId)
                                                        .stream()
                                                        .filter(v -> v.getKey().rootId().equals(rootId))
                                                        .filter(v -> include.test(v.getKey()))
                                                        .toList();
        if (x.isEmpty()) {
            QT.JOB_LOGGER.error("No jobs found for root ID: {}", rootId);
            return getWorkSupplier(GathererUnmappedNoToolWorkQtrDay.ID).get();
        }
        Work work = x.get(Compat.nextRandomInt(rand, x.size())).getValue().get();
        return work;
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

        Supplier<Work> w = getWorkSupplier(p);
        if (w == null) {
            QT.JOB_LOGGER.error("[Satisfaction check] No recognized job for ID: {}", p);
            return false;
        }

        Work work = w.get();
        if (work.initialRequest == null) {
            return true;
        }

        for (MCTownItem r : work.results.apply(town)) {
            if (requestedResult.test(r.toQTItemStack())) {
                return true;
            }
        }
        return requestedResult.test(work.initialRequest);
    }

    public static Function<List<MCHeldItem>, ImmutableList<Ingredient>> getWantedResourcesProvider(
            JobID p
    ) {
        if (isSeekingWork(p)) {
            return (items) -> ImmutableList.of(Ingredient.of(ItemsInit.JOB_BOARD_BLOCK.get()));
        }
        if (ResterWork.isResting(p)) {
            return (items) -> ImmutableList.of(Ingredient.of(ItemsInit.HOSPITAL_BED.get()));
        }

        Optional<SpecialJob> sj = specialJobs.stream().filter(v -> v.idTest.test(p)).findFirst();
        if (sj.isPresent()) {
            return (items) -> sj.get().needs().apply(p, items);
        }

        Supplier<Work> w = getWorkSupplier(p);
        if (w == null) {
            QT.JOB_LOGGER.error("No recognized job for ID: {}", p);
            return (items) -> ImmutableList.of();
        }
        return (items) -> ImmutableList.copyOf(w.get().needs.apply(items));
    }

    private static Supplier<Work> getWorkSupplier(JobID p) {
        //noinspection removal ServerJobsRegistry is the only entity who should use Works
        return Works.get(p);
    }

    public static ItemStack getDefaultWorkForNewWorker(JobID v) {
        if (isSeekingWork(v)) {
            return ItemStack.EMPTY;
        }
        Supplier<Work> w = getWorkSupplier(v);
        if (w == null) {
            QT.JOB_LOGGER.error("[Default Work Request] No recognized job for ID: {}", v);
            return ItemStack.EMPTY;
        }
        return w.get().initialRequest;
    }

    public static ImmutableSet<Ingredient> getAllOutputs(WorksBehaviour.TownData t) {
        List<Ingredient> list = Works.values().stream().map(v -> {
                                         Work work = v.get();
                                         ImmutableSet.Builder<ItemStack> b = ImmutableSet.builder();
                                         work.results.apply(t).forEach(z -> b.add(z.toMCItemStack()));
                                         if (work.initialRequest != null) {
                                             b.add(work.initialRequest);
                                         }
                                         return b.build();
                                     }).flatMap(Collection::stream).map(Ingredient::of).filter(v -> !v.isEmpty()).map(Ingredients::asWorkRequest)
                                     .collect(Collectors.toSet()).stream().map(WorkRequest::asIngredient).toList();
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
        Work w = getWorkSupplier(p).get();
        long jobDuration = w.jobFunc.apply(villagerID).getTotalDuration();
        long finalTick = currentTick.dayTime() + jobDuration;
        Signals nextSegment = Signals.fromDayTime(new Signals.DayTime(finalTick));
        Signals currentSegment = Signals.fromDayTime(currentTick);
        if (nextSegment.compareTo(currentSegment) < 0) {
            return false;
        }
        return ImmutableList.of(Signals.MORNING, Signals.NOON).contains(nextSegment);
    }

    public static void staticInitialize(ImmutableMap<JobID, Work> js) {
        initJobs(js);
        initSpecialJobs();
    }

    private static void initJobs(ImmutableMap<JobID, Work> js) {
        ImmutableMap.Builder<String, Jerb> b = ImmutableMap.builder();

        HashMap<String, ArrayList<Work>> ps = new HashMap<>();
        HashMap<String, List<JobID>> defaults = new HashMap<>();
        js.forEach((id, job) -> {
            ArrayList<Work> rL = Util.getOrDefault(ps, id.rootId(), new ArrayList<>());
            rL.add(job);
            rL.sort(Comparator.comparingInt(w -> w.priority));
            ps.put(id.rootId(), rL);
            if (isUnlockedInitially(job)) {
                UtilClean.addOrInitializeList(defaults, id.rootId(), job.id);
            }
        });

        ps.forEach((rootId, w) -> {
            b.put(rootId, new Jerb(w.stream().map(x -> x.id).toList(), ImmutableList.copyOf(defaults.get(rootId))));
        });
        jobs = b.build();
    }

    private static boolean isUnlockedInitially(Work job) {
        return job.parentID == null;
    }

    private record Jerb(ImmutableList<JobID> preferredWork, ImmutableList<JobID> defaultWork) {
        public Jerb(
                List<JobID> preferredWork,
                List<JobID> defaultWork
        ) {
            this(ImmutableList.copyOf(preferredWork), ImmutableList.copyOf(defaultWork));
        }
    }

    private static ImmutableMap<String, Jerb> jobs;

    public static Job<MCHeldItem, ? extends Snapshot<?>, ? extends IStatus<?>> getInitializedJob(
            ServerLevel level,
            JobID jobName,
            @NotNull Snapshot<MCHeldItem> journal,
            UUID ownerUUID
    ) {
        return getInitializedJob(level, jobName, journal, null, ownerUUID);
    }

    public static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            ServerLevel level,
            JobID jobName,
            ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        return getInitializedJob(level, jobName, null, heldItems, ownerUUID);
    }

    private static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            ServerLevel level,
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
            Supplier<Work> fn = getWorkSupplier(jobName);
            if (fn == null) {
                QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
                j = getWorkSupplier(GathererUnmappedNoToolWorkQtrDay.ID).get().jobFunc.apply(ownerUUID);
            } else {
                Work work = fn.get();
                j = work.jobFunc.apply(ownerUUID);
                journal = newJournal(jobName, journal, heldItems, work);
            }
        }
        if (journal != null) {
            j.initialize(level, journal);
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
        if (specialJobs.stream().anyMatch(j -> j.idTest().test(job))) {
            return new SimpleSnapshot<>(job, ProductionStatus.fromNumber(status), heldItems);
        }

        Supplier<Work> f = getWorkSupplier(job);
        if (f == null) {
            QT.JOB_LOGGER.error("No journal snapshot factory for {}. Falling back to Simple/Gatherer", job);
            f = getWorkSupplier(GathererUnmappedNoToolWorkQtrDay.ID);
        }
        return f.get().snapshotFunc.apply(job, status, heldItems);
    }
}
