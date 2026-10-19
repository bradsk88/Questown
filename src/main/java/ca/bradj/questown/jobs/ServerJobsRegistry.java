package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.Questown;
import ca.bradj.questown.blocks.JobBoardBlock;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
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
import ca.bradj.questown.world.QTWorldAccess;
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
import ca.bradj.questown.loader.TriPredicate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob.isSeekingWork;

public class ServerJobsRegistry {

    private static final Map<JobID, String> proficiencyIdOverrides = new HashMap<>();

    public static boolean isExcludedFromWarp(JobID jobID) {
        Supplier<Work> w = Works.get(jobID);
        if (w == null) return false;
        return w.get().getSpecialGlobalRules().contains(SpecialRules.EXCLUDE_FROM_WARP);
    }

    /**
     * The job's block slot precondition (job JSON {@code block.has_item_in_slot_initially}, e.g.
     * cook/fuel: an empty fuel slot), or null.
     */
    public static @Nullable SlotPrecondition slotPrecondition(JobID jobID) {
        Supplier<Work> w = Works.get(jobID);
        return w == null ? null : w.get().getSlotPrecondition();
    }

    public static boolean canAlwaysStart(
            UUID uuid,
            JobID p
    ) {
        return getWorkSupplier(p).get().jobFunc.apply(uuid).getGlobalSpecialRules()
                                               .contains(SpecialRules.ALWAYS_CONSIDER);
    }

    public static ResourceLocation getTexture(
            JobID job,
            IStatus<?> status
    ) {
        if (isSeekingWork(job)) {
            return StatusArt.getTexture(job, status);
        }
        try {
            Work work = getWork(job, true);
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
            Work work = getWork(job, true);
            if (work == null) {
                throw new IllegalStateException("No work found for job ID: " + job);
            }
            Pair<String, String> stringStringPair = work.applyStatusTextOverride(status);
            if (stringStringPair != null) {
                Component jobName = Compat.translatable("jobs." + job.rootId());
                return Pair.toList(stringStringPair).stream().map(v -> Compat.translatable(v, jobName))
                           .collect(ImmutableList.toImmutableList());
            }
        } catch (Exception e) {
            QT.JOB_LOGGER.error("Failed to apply status tooltip override", e);
        }
        return JobTooltips.buildStandardTooltipKeys(status, job);
    }

    private static @Nullable Work getWork(
            JobID job,
            boolean includeSpecial
            // But not "seeking work"
    ) {
        Optional<SpecialJob> sj = specialJobs.stream().filter(v -> v.idTest.test(job)).findFirst();
        if (sj.isPresent()) {
            // A special job answers for itself. Seeking work has no Work (null): falling through
            // to getWorkSupplier logged "No work found" and answered with some other job of the
            // same root (2026-10-17 playtest: hunter:seeking_work).
            return sj.get().reference().apply(job);
        }
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
        return jobsThatProduce(data, wantedResult, true);
    }

    /**
     * Jobs whose results include the item, as far as the town knows: a loot job's results are
     * the town's known loot for its table ({@code allKnownGatherItemsFn}), so a gatherer counts
     * for wheat seeds only once the town has found seeds that way. Unlike
     * {@link #getAllJobsThatProduce}, a job's declared {@code initial_request} alone does not
     * count. For the player-facing "There are N known jobs which produce this item" (dev,
     * 2026-10-19 playtest: count only jobs where the item is in the town's knowledge store).
     */
    public static ImmutableMap<JobID, ResourceLocation> getJobsKnownToProduce(
            WorksBehaviour.TownData data,
            Ingredient wantedResult
    ) {
        return jobsThatProduce(data, wantedResult, false);
    }

    private static ImmutableMap<JobID, ResourceLocation> jobsThatProduce(
            WorksBehaviour.TownData data,
            Ingredient wantedResult,
            boolean countDeclaredInitialRequest
    ) {
        ImmutableMap.Builder<JobID, ResourceLocation> b = ImmutableMap.builder();
        for (Supplier<Work> value : Works.values()) {
            Work w = value.get();
            if (w.hasNoOutput()) {
                continue;
            }
            ResourceLocation name = Compat.getItemId(w.icon.getItem());
            name = Util.ifNull(name, Questown.ResourceLocationError);
            if (countDeclaredInitialRequest && matches(wantedResult, w.initialRequest.apply(data.serverLevel()))) {
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

    private static boolean matches(
            Ingredient wantedResult,
            @Nullable Ingredient apply
    ) {
        if (apply == null) {
            // A job whose initial_request is null (e.g. organizer/fetch) yields a non-null
            // Function<ServerLevel, Ingredient> that returns null; without this guard, satisfaction
            // checks for any board request would NPE the flag tick. Such a job satisfies no request
            // by its initial request.
            return false;
        }
        for (ItemStack item : apply.getItems()) {
            if (wantedResult.test(item)) {
                return true;
            }
        }
        return false;
    }

    /** One job's declared product: {@code initial_request} as the job data names it; {@code null} = names none. */
    public record ProducerDeclaration(JobID job, @Nullable Ingredient product) {
    }

    /**
     * The static producer capability for a missing item: which roots' job definitions
     * <em>declare</em> it as their product ({@code initial_request}) — independent of
     * what the town has discovered. Quest generation must be able to ask for a producer
     * the town does not yet hold (that is the unlock quest's whole purpose; gannet
     * handoff, decision 3), so the discovery gate ({@code allKnownGatherItemsFn} via
     * the results function) stays on the job engine's execution path only.
     * <p>
     * Scope is the declared product — the {@code biome_loot} result tables would extend
     * this (parked follow-up).
     */
    public static Set<String> staticProducerRootsForItem(
            @Nullable ServerLevel sl,
            Ingredient wanted
    ) {
        List<ProducerDeclaration> declared = new ArrayList<>(Works.regularJobs().size());
        for (Map.Entry<JobID, Supplier<Work>> entry : Works.regularJobs()) {
            Work work = entry.getValue().get();
            if (work.initialRequest == null) {
                continue;
            }
            try {
                declared.add(new ProducerDeclaration(entry.getKey(), work.initialRequest.apply(sl)));
            } catch (RuntimeException re) {
                QT.JOB_LOGGER.error("Failed to resolve the declared product of {}", entry.getKey(), re);
            }
        }
        return staticProducerRootsFor(declared, wanted);
    }

    /**
     * Pure core of {@link #staticProducerRootsForItem}: the declared products are handed
     * in, which keeps this unit-testable although building them from the live {@code Works}
     * registry is Forge-registry-bound (compile + held-run covers that half; the
     * {@code Work.isUnlockedInitially} precedent).
     */
    public static Set<String> staticProducerRootsFor(
            Iterable<ProducerDeclaration> declaredProducts,
            Ingredient wanted
    ) {
        Set<String> roots = new HashSet<>();
        for (ProducerDeclaration declared : declaredProducts) {
            if (matches(wanted, declared.product())) {
                roots.add(declared.job().rootId());
            }
        }
        return roots;
    }

    /**
     * False when the child has no Work (seeking work) or no parent.
     */
    public static boolean isParentOf(
            JobID parent,
            JobID child
    ) {
        Work work = getWork(child, false);
        if (work == null || work.parentID == null) return false;
        JobID parentID = work.parentID;
        return parentID.equals(parent);
    }

    /**
     * The level at which the job becomes available for skill-tab unlocking (JSON
     * {@code available_at_level}, {@link Work#DEFAULT_AVAILABLE_AT_LEVEL} when absent).
     * Pre-startup (job data not loaded) answers the default, so callers degrade to
     * "available" rather than an NPE.
     */
    public static int getAvailableAtLevel(JobID job) {
        if (!Works.isInitialized()) {
            return Work.DEFAULT_AVAILABLE_AT_LEVEL;
        }
        Work work = getWork(job, false);
        return work != null ? work.getAvailableAtLevel() : Work.DEFAULT_AVAILABLE_AT_LEVEL;
    }

    public static ImmutableSet<JobID> getAllRootJobs() {
        if (!Works.isInitialized()) {
            // Job data not loaded (headless unit test, or pre-startup): no candidate roots,
            // not an NPE - callers treat "no candidates" as "re-roll / nothing to pick".
            return ImmutableSet.of();
        }
        ImmutableSet.Builder<JobID> b = ImmutableSet.builder();
        for (JobID j : getAllJobs()) {
            Work work = getWork(j, false);
            if (work != null && work.parentID == null) {
                b.add(j);
            }
        }
        return b.build();
    }

    public static ImmutableSet<MCTownItem> getResults(
            WorksBehaviour.TownData data,
            JobID job
    ) {
        @Nullable Work work = getWork(job, true);
        if (work == null) {
            return ImmutableSet.of();
        }
        return work.results.apply(data);
    }

    private record SpecialJob(
            Function<JobID, @Nullable Work> reference,
            Predicate<JobID> idTest,
            BiFunction<JobID, UUID, Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> jobFn,
            TriFunction<JobID, @Nullable Snapshot<MCHeldItem>, @Nullable ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>> journalFn,
            TriPredicate<JobID, Supplier<BlockState>, JobBlockTestContext> jobBlockTest,
            TriPredicate<JobID, Supplier<BlockState>, Pair<WorkLocation.BlockInfo, BlockPos>> shouldInit,
            BiFunction<JobID, List<MCHeldItem>, ImmutableList<Ingredient>> needs
    ) {

        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getJob(JobID job, VillagerUUID owner) {
            return jobFn.apply(job, VillagerUUID.get(owner));
        }

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
                    cached,
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
                id -> null,
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
        b.add(SpecialJob.fromWork(DowntimeWork::matches, id -> DowntimeWork.asWork(id.rootId())));

        specialJobs = b.build();
    }


    /**
     * Answers "does any job care about this block?" — asked for every position of a room, by every
     * work-status store, on every flag tick. Answering it from scratch walks every registered job
     * (there is one cook job per cookable item), so the answer is cached.
     *
     * <p>The cache key is the block state plus whether the block above is air, because those are
     * the only two things every registered {@code shouldInitializeWorkState} predicate looks at
     * (the block state, and {@code p.above()} for {@code REQUIRE_AIR_ABOVE}). A predicate that
     * inspects any other neighbour would need this key widened.
     */
    public static boolean shouldInitializeWithState(
            WorkLocation.BlockInfo info,
            BlockPos pos
    ) {
        BlockState state = info.state(pos);
        if (state.isAir()) {
            return false;
        }
        JobInterestKey key = new JobInterestKey(state, info.state(pos.above()).isAir());
        Boolean known = jobInterestByBlock.get(key);
        if (known != null) {
            return known;
        }
        boolean interesting = computeShouldInitializeWithState(info, pos, state);
        jobInterestByBlock.put(key, interesting);
        return interesting;
    }

    private record JobInterestKey(BlockState state, boolean airAbove) {
    }

    private static final Map<JobInterestKey, Boolean> jobInterestByBlock = new ConcurrentHashMap<>();

    /** Called when the job registry is (re)built, since the answers depend on the registered jobs. */
    public static void forgetWhichBlocksJobsCareAbout() {
        jobInterestByBlock.clear();
    }

    private static boolean computeShouldInitializeWithState(
            WorkLocation.BlockInfo info,
            BlockPos pos,
            BlockState state
    ) {
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

    /**
     * The proficiency-id declared by the job {@code jobID}, or null if it declares none (flat 1×
     * work speed, no leveling). Resolved from the same job definition on both the realtime and
     * warp paths so effective speed stays symmetric. See ADR-0010.
     */
    public static @Nullable String getProficiencyId(JobID jobID) {
        String overridden = proficiencyIdOverrides.get(jobID);
        if (overridden != null) {
            return overridden;
        }
        Work work = getWork(jobID, true);
        if (work == null) {
            return null;
        }
        return work.getProficiencyId();
    }

    /**
     * Test seam: make {@code jobID} behave as if its definition declared {@code proficiencyId}.
     * Shipped job JSON declares none yet, so this is how an autotest reaches the leveling and
     * multiplier code at all. Deliberately not fed into {@link #getAllDeclaredProficiencyIds()} —
     * spawn-seeding draws random levels, and a test asserting exact levels must set them itself.
     */
    public static void overrideProficiencyIdForTest(
            JobID jobID,
            String proficiencyId
    ) {
        proficiencyIdOverrides.put(jobID, proficiencyId);
    }

    /** Undoes {@link #overrideProficiencyIdForTest}; every scenario that sets one must clear it. */
    public static void clearProficiencyIdOverridesForTest() {
        proficiencyIdOverrides.clear();
    }

    /**
     * The union of proficiency-ids declared by any registered job — the finite pool a townie's
     * proficiency map is drawn from and bounded by. See ADR-0010.
     */
    public static ImmutableSet<String> getAllDeclaredProficiencyIds() {
        ImmutableSet.Builder<String> b = ImmutableSet.builder();
        for (Supplier<Work> value : Works.values()) {
            String id = value.get().getProficiencyId();
            if (id != null) {
                b.add(id);
            }
        }
        return b.build();
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
            JobID jobID,
            BlockPos townFlagPos,
            Collection<BlockPos> roomPositions,
            @Nullable BlockPos assignedWorkBlock,
            @Nullable QTWorldAccess warpWorld
    ) {
        if (isSeekingWork(jobID)) {
            return NoOpWarper.INSTANCE;
        }
        Supplier<Work> w = Works.get(jobID);
        if (w == null) {
            return NoOpWarper.INSTANCE;
        }
        return w.get().warper(
                new WorksBehaviour.WarpInput(villagerIndex, townFlagPos, roomPositions, assignedWorkBlock)
                        .withWarpWorld(warpWorld)
        );
    }

    /**
     * Whether the job's own block occupies {@code pos} — the warp-lane seam for assigning a job
     * a real work block. Uses the job block identity check (block id + properties), not the
     * full slot-gated predicate (which is how the baker's {@code baker/extract} would have
     * stayed unassigned on an empty oven, dropping its slot-rule takes at a virtual position
     * for the whole warp). Content-agnostic by design: the warp is what fills the slots.
     */
    public static boolean jobBlockAt(JobID jobID, WorkLocation.BlockInfo blockInfo, BlockPos pos) {
        Supplier<Work> w = Works.get(jobID);
        if (w == null) {
            return false;
        }
        Work work = w.get();
        if (work == null) {
            return false;
        }
        return work.isJobBlockAt(blockInfo, pos);
    }

    public static boolean jobBlockAt(JobID jobID, ServerLevel level, BlockPos pos) {
        return jobBlockAt(jobID, levelBlockInfo(level), pos);
    }

    /**
     * A pos-aware {@link WorkLocation.BlockInfo} over a live level — reads the block state
     * (and its above-cell, for the air-above check) at the queried position.
     */
    public static WorkLocation.BlockInfo levelBlockInfo(ServerLevel level) {
        return new WorkLocation.BlockInfo() {
            @Override
            public BlockState state(BlockPos p) {
                return level.getBlockState(p);
            }

            @Override
            public @Nullable BlockEntity entity(BlockPos p) {
                return level.getBlockEntity(p);
            }
        };
    }

    /**
     * A {@link WorkLocation.BlockInfo} over a single captured position — the state is
     * returned for ANY queried position (position-agnostic). For the pos-sensitive checks
     * (air-above) use {@link #levelBlockInfo} instead.
     */
    public static WorkLocation.BlockInfo blockInfo(BlockState state, @Nullable BlockEntity entity) {
        return new WorkLocation.BlockInfo() {
            @Override
            public BlockState state(BlockPos p) {
                return state;
            }

            @Override
            public @Nullable BlockEntity entity(BlockPos p) {
                return entity;
            }
        };
    }

    public static boolean canSatisfy(
            WorksBehaviour.TownData town,
            JobID p,
            Ingredient requestedResult
    ) {
        if (isSeekingWork(p)) {
            return false;
        }
        // Meta-states (downtime / rest / diner / BOP, ...) are not production jobs and are
        // not registered in `Works`; looking them up fell into getWorkSupplier's same-root
        // fallback (wrong job + ERROR spam — observed 2026-10-06 via
        // updateWorkersAfterRequestChange passing a townie's downtime job id).
        if (specialJobs.stream().anyMatch(sj -> sj.idTest().test(p))) {
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
        return matches(requestedResult, work.initialRequest.apply(town.serverLevel()));
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

    private static @Nullable Supplier<Work> getWorkSupplier(JobID p) {
        Supplier<Work> workSupplier = Works.get(p);
        if (workSupplier == null) {
            // With the caller's stack: the 2026-10-17 hunter:seeking_work hits came from a path no
            // autotest reproduces.
            QT.JOB_LOGGER.error("No work found for job ID: {}. Falling back to any job in the same root ID.", p, new Throwable("caller"));
            // Prefer a job unlocked with the root: the first-listed one could be e.g.
            // cook/stock_ingredients, which needs a stocked kitchen (2026-10-17 playtest).
            List<Supplier<Work>> sameRoot = Works.entrySet(p.rootId()).stream()
                                                 .filter(v -> v.getKey().sameRoot(p))
                                                 .map(Map.Entry::getValue)
                                                 .toList();
            Optional<Supplier<Work>> pick = Work.preferInitiallyUnlocked(
                    sameRoot,
                    s -> s.get().parentID,
                    s -> s.get().isUnlockedWithRootJob()
            );
            if (pick.isEmpty()) {
                QT.JOB_LOGGER.error("No fallback work found for root ID: {}", p.rootId());
                return null;
            }
            return pick.get();
        }
        return workSupplier;
    }

    public static ItemStack getDefaultWorkForNewWorker(
            ServerLevel sl,
            JobID v
    ) {
        if (isSeekingWork(v)) {
            return ItemStack.EMPTY;
        }
        Supplier<Work> w = getWorkSupplier(v);
        if (w == null) {
            QT.JOB_LOGGER.error("[Default Work Request] No recognized job for ID: {}", v);
            return ItemStack.EMPTY;
        }
        Ingredient apply = w.get().initialRequest.apply(sl);
        if (apply == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack item : apply.getItems()) {
            return item;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Outputs the player can request on the job board / stock-request screen.
     * Only includes the products of jobs that are currently unlocked in town:
     * a job's outputs appear iff {@code jobIsUnlocked} accepts its {@link JobID}.
     * <p>
     * This is the same predicate the work-seeker uses to decide which jobs a
     * villager will actually perform ({@code WorkSeekerJob} gates on
     * {@code VillagerHolder::isUnlocked}), so the request screen only offers
     * products that some villager can fulfil — never a permanently
     * un-fulfillable request for a job no one has unlocked.
     * <p>
     * Gatherer-style {@code results} are additionally narrowed to discovered
     * loot upstream, via {@code t}'s known-gather-results function (the job's
     * fixed {@code initialRequest} item is not loot and is not narrowed).
     */
    public static ImmutableSet<Ingredient> getAllOutputs(
            WorksBehaviour.TownData t,
            Predicate<JobID> jobIsUnlocked
    ) {
        List<Ingredient> list = Works.values().stream()
                                     .map(Supplier::get)
                                     .filter(work -> jobIsUnlocked.test(work.id))
                                     .map(work -> {
                                         ImmutableSet.Builder<ItemStack> b = ImmutableSet.builder();
                                         work.results.apply(t).forEach(z -> b.add(z.toMCItemStack()));
                                         @Nullable Ingredient req = work.initialRequest.apply(t.serverLevel());
                                         if (req != null) {
                                             for (ItemStack item : req.getItems()) {
                                                 b.add(item);
                                             }
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
        return Work.isUnlockedInitially(job.parentID, job.isUnlockedWithRootJob());
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

    public interface JobInitPair {
        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getJob();

        Snapshot<MCHeldItem> newJournal(
                ImmutableList<MCHeldItem> heldItems,
                @Nullable Snapshot<MCHeldItem> seed
        );
    }

    public static JobInitPair getUninitializedJob(
            JobID jobName,
            VillagerUUID ownerVUID
    ) {
        BiFunction<ImmutableList<MCHeldItem>, @Nullable Snapshot<MCHeldItem>, Snapshot<MCHeldItem>> journal = null;
        Supplier<Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> j = null;
        for (SpecialJob sj : specialJobs) {
            if (sj.idTest.test(jobName)) {
                j = () -> sj.getJob(jobName, ownerVUID);
                journal = (items, jrn) -> sj.journalFn.apply(jobName, jrn, items);
                break;
            }
        }
        if (j == null) {
            Supplier<Work> fn = getWorkSupplier(jobName);
            if (fn == null) {
                QT.JOB_LOGGER.error("Unknown job name {}. Falling back to gatherer.", jobName);
                j = () -> getWorkSupplier(GathererUnmappedNoToolWorkQtrDay.ID).get().jobFunc.get(ownerVUID);
            } else {
                Work work = fn.get();
                j = () -> work.jobFunc.get(ownerVUID);
                journal = (items, jrn) -> newJournal(jobName, jrn, items, work);
            }
        }
        BiFunction<ImmutableList<MCHeldItem>, Snapshot<MCHeldItem>, Snapshot<MCHeldItem>> fJournal = journal;
        Supplier<Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>>> fj = j;
        return new JobInitPair() {
            @Override
            public Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getJob() {
                return fj.get();
            }

            @Override
            public Snapshot<MCHeldItem> newJournal(
                    ImmutableList<MCHeldItem> heldItems,
                    @Nullable Snapshot<MCHeldItem> seed
            ) {
                return fJournal.apply(heldItems, seed);
            }
        };
    }

    private static Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> getInitializedJob(
            ServerLevel level,
            JobID jobName,
            @Nullable Snapshot<MCHeldItem> journal,
            @Nullable ImmutableList<MCHeldItem> heldItems,
            UUID ownerUUID
    ) {
        JobInitPair u = getUninitializedJob(jobName, VillagerUUID.from(ownerUUID));
        Job<MCHeldItem, ? extends ImmutableSnapshot<MCHeldItem, ?>, ? extends IStatus<?>> j = u.getJob();
        journal = u.newJournal(heldItems, journal);
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
