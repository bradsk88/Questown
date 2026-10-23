package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.commands.DebugLogArgument;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.Pair;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.core.VillagerUUID;
import ca.bradj.questown.gui.Ingredients;
import ca.bradj.questown.integration.jobs.ItemCheckReplacer;
import ca.bradj.questown.integration.jobs.JobCheckReplacer;
import ca.bradj.questown.integration.jobs.SupplyRoomCheckReplacer;
import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.integration.minecraft.MCContainer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.fetcher.FetcherHack;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.AbstractSupplyGetter;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.PredicateCollections;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.entity.TownFlagBlockEntity;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.questown.world.MinecraftWorldAccess;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.questown.jobs.InteractionSpots;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Ingredient;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import static ca.bradj.questown.jobs.declarative.DeclarativeJobs.STATUS_FACTORY;
import static ca.bradj.questown.mc.Util.info;

// TODO: Break ties to MC and unit test - Maybe reuse code from ProductionTimeWarper
public class DeclarativeJob extends
        DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> {

    private final DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks;

    private static final Marker marker = MarkerManager.getMarker("DJob");
    private final RealtimeWorldInteraction world;

    private final WorkLocation location;
    private final @NotNull Integer maxState;
    private final JobID jobId;

    // take-driver count: feeds the [PROBE:supplies] TOOK line (psweep verdict oracle; keep)
    private static final java.util.concurrent.atomic.AtomicLong SUPPLY_TOOK_CNT = new java.util.concurrent.atomic.AtomicLong();
    private final ExpirationRules expiration;
    private boolean finishedProductNeedsStorage = true;
    private final long totalDuration;
    private final int workInterval;
    private final RecipeProvider recipe;
    public final ImmutableMap<Integer, Ingredient> initialIngredients;
    public final ImmutableMap<Integer, Ingredient> initialTools;
    private final ImmutableMap<Integer, Integer> initialWork;

    private @Nullable Long lastSupplyTick = null;
    private @Nullable Long secondLastSupplyTick = null;

    private final AbstractSupplyGetter<ProductionStatus, BlockPos, MCTownItem, MCHeldItem, MCRoom> getter = new AbstractSupplyGetter<>();
    private boolean isFirstTick = true;
    @SuppressWarnings("removal")
    private TownInterface.DebugLogger logger = QT.JOB_LOGGER::debug;
    private final DeclarativeJobTicker<BlockPos, MCHeldItem, MCRoom, RoomRecipeMatch<MCRoom>, MCExtra, Void, WorkLocation> ticker;

    public DeclarativeJob(
            UUID ownerUUID,
            int inventoryCapacity,
            @NotNull JobID jobId,
            WorkLocation location,
            int maxState,
            int workInterval,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<ProductionStatus, Collection<String>> specialStatusRules,
            ImmutableList<String> specialGlobalRules,
            ExpirationRules expiration,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            @Nullable SoundInfo sound
    ) {
        super(
                ownerUUID,
                inventoryCapacity,
                marker,
                DeclarativeJobs.journalInitializer(jobId),
                STATUS_FACTORY,
                specialStatusRules,
                specialGlobalRules,
                () -> {
                    if (specialGlobalRules.contains(SpecialRules.CLAIM_SPOT)) {
                        return makeClaim(ownerUUID);
                    }
                    return null;
                },
                location
        );
        this.initialIngredients = ingredientsRequiredAtStates;
        this.initialTools = toolsRequiredAtStates;
        this.initialWork = workRequiredAtStates;
        this.jobId = jobId;
        this.checks = new DeclarativeJobChecks<>(
                Jobs.unMCHeld3(ingredientsRequiredAtStates),
                ingredientsQtyRequiredAtStates,
                Jobs.unMC5(toolsRequiredAtStates),
                workRequiredAtStates,
                timeRequiredAtStates,
                (r) -> true,
                (p) -> false
        );

        this.world = initWorldInteraction(
                maxState, this.checks, resultGenerator, specialStatusRules, extra -> {
                    if (specialGlobalRules.contains(SpecialRules.CLAIM_SPOT)) {
                        return makeClaim(ownerUUID);
                    }
                    return null;
                },
                (x, i) -> getUnmetNeed(
                        ingredientsRequiredAtStates,
                        ingredientsQtyRequiredAtStates,
                        toolsRequiredAtStates,
                        i
                ),
                () -> location.baseRoom().toString(), workInterval, sound
        );
        this.maxState = maxState;
        this.location = location;
        this.expiration = expiration;
        this.totalDuration = timeRequiredAtStates.values().stream().reduce(Integer::sum).orElse(0);
        this.workInterval = workInterval;
        this.recipe = buildRecipe(this);
        ticker = new DeclarativeJobTicker<>(specialGlobalRules, specialStatusRules, location, maxState);
    }

    private static @Nullable String getUnmetNeed(
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> qtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            NeedsRegistrations.Need need
    ) {
        Integer ii = need.timesInserted();
        if (ii != null) {
            if (qtyRequiredAtStates.isEmpty()) {
                return null;
            }
            int actual = NoMCNeeds.getActualIndex(ii, qtyRequiredAtStates);
            return Util.orNull(ingredientsRequiredAtStates.get(actual + 1), Ingredients::toString);
        }
        return Util.orNull(toolsRequiredAtStates.get(need.toolIndex()), Ingredients::toString);
    }

    @Override
    public void initialize(
            ServerLevel level,
            Snapshot<MCHeldItem> journal
    ) {
        super.initialize(level, journal);

        Map<Integer, ItemCheckReplacer<MCHeldItem>> ingr = new HashMap<>();
        Map<Integer, ItemCheckReplacer<MCTownItem>> tool = new HashMap<>();

        Map<Integer, PredicateCollection<MCHeldItem, MCHeldItem>> jobIngrs = checks.getAllRequiredIngredients();
        Map<Integer, PredicateCollection<MCTownItem, MCTownItem>> jobTools = checks.getAllRequiredTools();
        PredicateCollection noCheck = PredicateCollection.empty("no requirements");
        for (int i = 0; i < maxState; i++) {
            ingr.put(i, new ItemCheckReplacer<>(UtilClean.getOrDefault(jobIngrs, i, noCheck)));
            tool.put(i, new ItemCheckReplacer<>(UtilClean.getOrDefault(jobTools, i, noCheck)));
        }

        JobCheckReplacer globalJCR = new JobCheckReplacer(this::isJobBlock);
        SupplyRoomCheckReplacer globalSRCR = new SupplyRoomCheckReplacer();

        DeclarativeJob self = this;

        for (int i = 0; i <= maxState; i++) {
            ProductionStatus ss = ProductionStatus.fromJobBlockStatus(i);
            List<String> stageRules = UtilClean.getOrDefaultCollection(specialRules, ss, ImmutableList.of());
            PreInitHook.run(stageRules, () -> new MinecraftWorldAccess(level), ingr.get(i), tool.get(i), globalJCR, globalSRCR);
        }

        PreInitHook.run(
                specialGlobalRules,
                () -> new MinecraftWorldAccess(level),
                ItemCheckReplacer.doNotReplace(),
                ItemCheckReplacer.doNotReplace(),
                globalJCR,
                globalSRCR
        );
        this.checks.initialize(
                ItemCheckReplacer.withItems(ingr, self.journal::getItems),
                checks.getAllRequiredQuantity(),
                ItemCheckReplacer.withItems(tool, self.journal::getItems),
                checks.getAllRequiredWork(),
                checks.getAllRequiredTime(),
                SupplyRoomCheckReplacer.withItems(globalSRCR, self.journal::getItems),
                JobCheckReplacer.withContext(
                        globalJCR, new JobBlockTestContext(
                                new MinecraftWorldAccess(level),
                                info(level),
                                BlockPos.ZERO,
                                self.journal::getItems,
                                ImmutableList::of,
                                false,
                                false
                        )
                )
        );
    }

    private boolean isJobBlock(
            JobBlockTestContext ctx
    ) {
        boolean r = location.isJobBlock().test(ctx.withBlockAlreadyUsed(ticker.hasWorkedRecently() || hasInserted(0))
                                             .withJobAlreadyStarted(true));
        return r;
    }

    @NotNull
    public static Claim makeClaim(UUID ownerUUID) {
        return new Claim(ownerUUID, Config.BLOCK_CLAIMS_TICK_LIMIT.get());
    }

    @Override
    public JobID getId() {
        return jobId;
    }

    @Override
    public boolean shouldStandStill(
            TownInterface town,
            BlockPos position
    ) {
        // TODO: Remove this hack and use a special rule instead
        if (DowntimeWork.matches(jobId) && isCloseToJobSite(position) && !hasTargetOverrideChanged(town)) {
            return true;
        }
        return this.ticker.hasWorkedRecently();
    }

    @NotNull
    protected RealtimeWorldInteraction initWorldInteraction(
            int maxState,
            DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Map<ProductionStatus, Collection<String>> specialRules,
            Function<MCExtra, Claim> claimSpots,
            BiFunction<MCExtra, NeedsRegistrations.Need, String> getUnmetNeed,
            Supplier<String> location,
            int interval,
            @Nullable SoundInfo sound
    ) {
        return new RealtimeWorldInteraction(
                t -> t.town().getTownFlagBasePos(),
                journal,
                maxState,
                checks,
                specialRules,
                resultGenerator,
                claimSpots,
                getUnmetNeed,
                location,
                interval,
                sound
        );
    }

    public static RecipeProvider buildRecipe(DeclarativeJob self) {
        return s -> {
            ImmutableList.Builder<PredicateCollection<MCTownItem, ?>> bb = ImmutableList.builder();
            PredicateCollection<MCHeldItem, ?> ingr = self.checks.getIngredientsForStep(s);
            if (ingr != null) {
                bb.add(PredicateCollections.townify(ingr));
            }
            // Hold on to tools required for this state and all previous states
            for (int i = 0; i <= s; i++) {
                PredicateCollection<MCTownItem, ?> tool = self.checks.getToolsForStep(i);
                if (tool != null) {
                    bb.add(tool);
                }
            }
            return bb.build();
        };
    }


    @Override
    public void tick(
            TownInterface town,
            LivingEntity entity,
            Direction facingPos
    ) {
        DeclarativeJob self = this;
        VisitorMobEntity vme = (VisitorMobEntity) entity;
//        DeclarativeJobTickerDependencies deps = new DeclarativeJobTickerDependencies(this, town,vme);
        DeclarativeJobTicker.Dependencies<BlockPos, ResourceLocation, MCHeldItem, MCTownItem, MCRoom, RoomRecipeMatch<MCRoom>, MCExtra, WorkLocation> deps
                = new DeclarativeJobTickerDependencies(this, (TownFlagBlockEntity) town, vme);
        this.ticker.tick(deps, (p, a) -> town.getDebugLogger(QT.JOB_LOGGER, DebugLogArgument.JOB_LOGIC).log(p, a));
    }

    /**
     * The timed leaver trip this job is currently on, when its gate-mat
     * timer is still counting down: the absolute server tick at which the
     * timer expires and the trip's declared total duration. Null when the
     * job has no live timed trip (no work spot, no state, no timer, or the
     * state declares no time). Feeds the villagers-menu leaver-return
     * progress bar.
     */
    public @Nullable LeaverReturn getLeaverReturn(TownInterface town) {
        WorkPosition<BlockPos> spot = ticker.workSpot();
        if (spot == null || spot.jobBlock() == null) {
            return null;
        }
        BlockPos bp = spot.jobBlock();
        AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> work =
                ((TownFlagBlockEntity) town).getRealWorkStatusHandle(
                        specialGlobalRules.contains(SpecialRules.SHARED_WORK_STATUS) ? null : ownerUUID
                );
        Integer remaining = work.getTimeToNextState(bp);
        if (remaining == null || remaining <= 0) {
            return null;
        }
        State state = work.getJobBlockState(bp);
        if (state == null) {
            return null;
        }
        int total = checks.getTimeForStep(null, state.processingState(), 0);
        if (total <= 0) {
            return null;
        }
        ServerLevel level = town.getServerLevel();
        if (level == null) {
            return null;
        }
        return new LeaverReturn(level.getGameTime() + remaining, Math.max(total, remaining));
    }

    /**
     * Eyes-level debug snapshot of the job's current work-site progress: the
     * processing state and how many work/time ticks remain before the next
     * stage. This backs the "progress / [work/time] left" indicator and is how
     * an observer tells a leaver who is genuinely mid-trip (timer still
     * counting down) from one whose trip was cut short. Returns null when the
     * job has no current work spot (e.g. it is not yet assigned a job block).
     */
    public @Nullable String getWorkProgressDebug(TownInterface town) {
        WorkPosition<BlockPos> spot = ticker.workSpot();
        if (spot == null || spot.jobBlock() == null) {
            return null;
        }
        BlockPos bp = spot.jobBlock();
        AbstractWorkStatusStore<BlockPos, MCHeldItem, MCRoom, ServerLevel> work =
                ((TownFlagBlockEntity) town).getRealWorkStatusHandle(
                        specialGlobalRules.contains(SpecialRules.SHARED_WORK_STATUS) ? null : ownerUUID
                );
        State state = work.getJobBlockState(bp);
        Integer timeToNext = work.getTimeToNextState(bp);
        StringBuilder sb = new StringBuilder();
        sb.append("job block ").append(bp.getX()).append(',').append(bp.getY()).append(',').append(bp.getZ());
        if (state == null) {
            sb.append(" (no state yet)");
        } else {
            sb.append(" state=").append(state.processingState());
            if (state.hasWorkLeft()) {
                sb.append(" work-left=").append(state.workLeft());
            }
        }
        if (timeToNext != null && timeToNext > 0) {
            sb.append(" time-left=").append(timeToNext);
        }
        return sb.toString();
    }


    @Override
    protected boolean grabbedSuppliesRecently(Predicate<Long> isTickRecent) {
        return isTickRecent.test(lastSupplyTick) && isTickRecent.test(secondLastSupplyTick);
    }

    @Override
    protected boolean shouldCheckContainerForSupplies(RoomRecipeMatch<MCRoom> mcRoom) {
        return checks.shouldCheckContainerForSupplies(mcRoom);
    }

    @Override
    protected Collection<? extends Predicate<MCTownItem>> cleanRooms() {
        Collection<? extends Predicate<MCTownItem>> r = roomsNeedingIngredientsOrTools.cleanFns(
                checks::getIngredientsForStep,
                checks::getToolsForStep,
                MCHeldItem::fromTown
        );
        return r;
    }

//    private @NotNull JobTownProvider<MCRoom> makeTownProviderForTick(
//            MCExtra extra,
//            WorkStatusHandle<BlockPos, MCHeldItem> work,
//            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools
//    ) {
//        TownInterface town = extra.town();
//        WorkLocation.BlockInfo info = info(town.getServerLevel());
//        Supplier<? extends Map<Integer, ? extends RoomsWithWorkableStatefulBlocks<?>>> roomsV2 = () -> DeclarativeJobs.rooms(
//                maxState,
//                roomsNeedingIngredientsOrTools,
//                work,
//                (BlockPos bp) -> isJobBlock(new JobBlockTestContext(
//                        town.getServerLevel(),
//                        info,
//                        bp,
//                        journal::getItems,
//                        () -> TownContainers.getUniqueItems(town),
//                        false, // Gets overridden
//                        false // Gets overridden
//
//                ))
//        );
//
//        return new JobTownProvider<>() {
//            private final Function<BlockPos, State> getJobBlockState = work::getJobBlockState;
//
//            @Override
//            public Collection<MCRoom> roomsWithCompletedProduct() {
//                return roomsWithState.get(
//                        roomsMatching(town),
//                        isCorrectBlock(town),
//                        getJobBlockState
//                ).stream().map(v -> v.room).toList();
//            }
//
//            @Override
//            public RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsByState() {
//                return roomsNeedingIngredientsOrTools;
//            }
//
//            @Override
//            public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
//                return roomsV2.get();
//            }
//
//            @Override
//            public boolean isUnfinishedTimeWorkPresent() {
//                return Jobs.isUnfinishedTimeWorkPresent(
//                        town.getRoomHandle(),
//                        location.baseRoom(),
//                        work::getTimeToNextState
//                );
//            }
//
//            @Override
//            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
//                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
//                        () -> town.getRoomHandle().getRoomsMatching(location.baseRoom()).stream()
//                                  .map(v -> (Supplier<Collection<BlockPos>>) () -> v.getContainedBlocks().keySet()
//                                                                                     .stream()
//                                                                                     .filter(z -> shouldInit(z, extra))
//                                                                                     .toList())
//                                  .toList(), getJobBlockState, (bp) -> work.canClaim(bp, () -> makeClaim(ownerUUID))
//                );
//                ImmutableList.Builder<Integer> b = ImmutableList.builder();
//                statesWithUnfinishedWork.forEach(s -> {
//                    IPredicateCollection<MCTownItem> toolsReq = checks.getToolsForStep(s);
//                    if (toolsReq != null && !toolsReq.isEmpty()) {
//                        return;
//                    }
//                    b.add(s);
//                });
//                return b.build();
//            }
//
//            @Override
//            public Collection<MCRoom> roomsAtState(Integer state) {
//                return roomsNeedingIngredientsOrTools.get().get(state).stream()
//                                                     .map(RoomsNeedingVillagerInput.NVIRoom::room)
//                                                     .map(IRoomRecipeMatch::getRoom).toList();
//            }
//
//            @Override
//            public LZCD.Dependency<Void> hasSuppliesV2() {
//                return DeclarativeJobs.supplies(
//                        town.getServerLevel(),
//                        roomsV2,
//                        town,
//                        checks.getAllRequiredIngredients(),
//                        checks.getAllRequiredTools(),
//                        checks::shouldCheckContainerForSupplies,
//                        bp -> isJobBlock(bp),
//                        js -> location.baseRoom().equals(js)
//                );
//            }
//
//            @Override
//            public boolean hasSpace() {
//                return Jobs.townHasSpace(town);
//            }
//        };
//    }

    static @NotNull BiPredicate<WorkLocation, BlockPos> isCorrectBlock(TownInterface town) {
        return (l, p) -> l.shouldInitializeWorkState().test(info(town.getServerLevel()), p);
    }

    Function<WorkLocation, ImmutableList<RoomRecipeMatch<MCRoom>>> roomsMatching(
            TownInterface town
    ) {
        return loc -> ImmutableList.copyOf(town.getRoomHandle().getRoomsMatching(loc.baseRoom()));
    }

    private boolean shouldInit(
            BlockPos z,
            MCExtra extra
    ) {
        WorkLocation.BlockInfo info = info(extra.town().getServerLevel());
        return location.shouldInitializeWorkState().test(info, z);
    }

    @Override
    protected boolean isJobBlock(BlockPos bp) {
        return checks.isJobBlock(bp);
    }

    /**
     * Checks if any rooms have job blocks at the specified state.
     * Used by TownPossibleWork to determine if work is available.
     */
    public WithReason<Boolean> hasRoomsAtState(
            TownFlagBlockEntity town,
            int state
    ) {
        WorkStatusHandle<BlockPos, MCHeldItem> ws = town.getWorkStatusHandle(null);
        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle()
                .getRoomsMatching(location.baseRoom());
        if (rooms.isEmpty()) {
            return WithReason.always(false, "No rooms match base room " + location.baseRoom());
        }
        ImmutableList<RoomRecipeMatch<MCRoom>> roomsWithState = DeclarativeJobs.roomsWithState(
                ImmutableList.copyOf(rooms),
                this::isJobBlock,
                bp -> Integer.valueOf(state).equals(JobBlock.getState(ws::getJobBlockState, bp))
        );
        if (roomsWithState.isEmpty()) {
            return WithReason.always(false, rooms.size() + " rooms match " + location.baseRoom() + " but none have job blocks at state " + state);
        }
        return WithReason.always(true, roomsWithState.size() + " rooms have job blocks at state " + state);
    }

    /**
     * {@link #hasRoomsAtState} for the warp's job list: job blocks are tested without their slot
     * precondition ({@code blockAlreadyUsed=true} skips it in {@code ResourceJobLoader.isJobBlockV2}),
     * because the warp simulates the slot contents step by step. A furnace with an empty output
     * slot is still a cook/extract site for a warp that will fill it.
     */
    public WithReason<Boolean> hasRoomsAtStateIgnoringSlot(
            TownFlagBlockEntity town,
            int state
    ) {
        ServerLevel level = town.getServerLevel();
        if (level == null) {
            return WithReason.always(false, "No server level");
        }
        WorkStatusHandle<BlockPos, MCHeldItem> ws = town.getWorkStatusHandle(null);
        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle()
                .getRoomsMatching(location.baseRoom());
        if (rooms.isEmpty()) {
            return WithReason.always(false, "No rooms match base room " + location.baseRoom());
        }
        JobBlockTestContext ctx = new JobBlockTestContext(
                new MinecraftWorldAccess(level), info(level), BlockPos.ZERO, journal::getItems, ImmutableList::of, true, true
        );
        ImmutableList<RoomRecipeMatch<MCRoom>> roomsWithState = DeclarativeJobs.roomsWithState(
                ImmutableList.copyOf(rooms),
                bp -> location.isJobBlock().test(ctx.withPos(bp)),
                bp -> Integer.valueOf(state).equals(JobBlock.getState(ws::getJobBlockState, bp))
        );
        if (roomsWithState.isEmpty()) {
            return WithReason.always(false, rooms.size() + " rooms match " + location.baseRoom() + " but none have job blocks at state " + state + " (slot ignored)");
        }
        return WithReason.always(true, roomsWithState.size() + " rooms have job blocks at state " + state + " (slot ignored)");
    }

    private boolean hasInserted(Integer action) {
        for (int i = 1; i < action; i++) {
            //noinspection DataFlowIssue
            if (checks.getQuantityForStep(i - 1, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "DeclarativeJob{" + "jobId=" + jobId.toNiceString() + '}';
    }

    public boolean prioritizesExtraction() {
        return specialGlobalRules.contains(SpecialRules.PRIORITIZE_EXTRACTION);
    }

    private void tryGetSupplies(
            TownInterface town,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
            BlockPos entityBlockPos,
            Long currentTick
    ) {
        if (suppliesTarget == null) {
            return;
        }
        SupplyItems.SuppliesTarget<BlockPos, MCTownItem> st = new SupplyItems.SuppliesTarget<>() {
            @Override
            public boolean isCloseTo() {
                return Jobs.isCloseTo(entityBlockPos, suppliesTarget.getBlockPos());
            }

            @Override
            public String toShortString() {
                return suppliesTarget.toShortString();
            }

            @Override
            public List<MCTownItem> getItems() {
                return suppliesTarget.getItems();
            }

            @Override
            public void removeItem(int i) {
                suppliesTarget.getContainer().removeItem(i);
            }
        };
        Function<List<MCTownItem>, List<Pair<Integer, MCTownItem>>> adjustOrder = UtilClean::enumerate;
        if (specialGlobalRules.contains(SpecialRules.GLOBAL_TAKE_RANDOM_INGREDIENT)) {
            adjustOrder = list -> {
                ImmutableList<Pair<Integer, MCTownItem>> shuffled = ImmutableList.copyOf(UtilClean.enumerate(list));
                shuffled = Compat.shuffle(shuffled, town.getServerLevel());
                return shuffled;
            };
        }
        if (getter.tryGetSupplies(
                journal.getStatus(),
                journal.getCapacity(),
                roomsNeedingIngredientsOrTools,
                st,
                recipe::getRecipe,
                journal.getItems(),
                (item) -> {
                    SUPPLY_TOOK_CNT.incrementAndGet();
                    QT.JOB_LOGGER.warn("[PROBE:supplies] job={} TOOK item={} (totalTakes={})", jobId, item, SUPPLY_TOOK_CNT.get());
                    this.journal.addItem(MCHeldItem.fromTown(item));
                    this.clearJobSite();
                },
                adjustOrder
        )) {
            this.secondLastSupplyTick = this.lastSupplyTick;
            this.lastSupplyTick = currentTick;
        }
        ;
    }

    @Override
    protected ImmutableList<PredicateCollection<MCTownItem, ?>> getRecipe(Integer integer) {
        return recipe.getRecipe(integer);
    }

    Map<Integer, Collection<WorkPosition<BlockPos>>> listAllWorkSpots(
            Function<BlockPos, State> town,
            @Nullable EntityCurrentJobSite<MCRoom> jobSite,
            Predicate<BlockPos> isValidWalkTarget,
            Predicate<BlockPos> isJobBlock,
            Function<BlockPos, BlockPos> getRandomAdjacent
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        Function<BlockPos, BlockPos> is = bp -> {
            bp = jobSite.isFarm() ? bp.above() : bp;
            return findInteractionSpot(bp, jobSite.room(), isValidWalkTarget, getRandomAdjacent);
        };

        Map<Integer, List<WorkPosition<BlockPos>>> b = new HashMap<>();
        Consumer<BlockPos> tryAdd = bp -> tryAddSpot(town, bp, b, is, isJobBlock);

        jobSite.room().getSpaces().stream()
               .flatMap(space -> InclusiveSpaces.getPositions(space, InclusiveSpaces.PositionType.INTERIOR_ONLY)
                                                .stream())
               .forEach(v -> {
                   BlockPos pos = Positions.ToBlock(v, jobSite.room().yCoord);
                   tryAdd.accept(pos);
                   tryAdd.accept(pos.above());
               });
        if (b.isEmpty()) {
            // TODO: We need an "isJobBlock" AND "isJobBlockReady" predicate
            // QT.JOB_LOGGER.warn("No work spots found in job site. This is probably an issue with the job's JSON definition.");
        }
        return ImmutableMap.copyOf(b);
    }

    private static void tryAddSpot(
            Function<BlockPos, State> town,
            BlockPos bp,
            Map<Integer, List<WorkPosition<BlockPos>>> b,
            Function<BlockPos, BlockPos> i9nSpot,
            Predicate<BlockPos> isJobBlock
    ) {
        @Nullable Integer blockAction = JobBlock.getState(town, bp);
        if (blockAction != null) {
            if (isJobBlock.test(bp)) {
                WorkPosition<BlockPos> v = new WorkPosition<>(bp, i9nSpot.apply(bp));
                UtilClean.addAllOrInitializeList(b, blockAction, ImmutableList.of(v));
            }
        }
    }

    private BlockPos findInteractionSpot(
            BlockPos bp,
            Room jobSite,
            Predicate<BlockPos> isValidWalkTarget,
            Function<BlockPos, BlockPos> getRandomAdjacent
    ) {
        @Nullable BlockPos spot;

        if (specialGlobalRules.contains(SpecialRules.PREFER_INTERACTION_BELOW)) {
            spot = doFindInteractionSpot(bp.below(), jobSite, isValidWalkTarget);
            if (spot != null) {
                return spot;
            }
        }
        if (specialGlobalRules.contains(SpecialRules.PREFER_INTERACTION_STAND_ON_TOP)) {
            return bp.above();
        }

        spot = doFindInteractionSpot(bp, jobSite, isValidWalkTarget);
        if (spot != null) {
            return spot;
        }
        return getRandomAdjacent.apply(bp);
    }

    @Nullable
    private BlockPos doFindInteractionSpot(
            BlockPos bp,
            Room jobSite,
            Predicate<BlockPos> isEmpty
    ) {
        return InteractionSpots.find(bp, jobSite, isEmpty);
    }

    public static Direction getDoorDirectionFromCenter(Room jobSite) {
        return InteractionSpots.doorDirectionFromCenter(jobSite);
    }

    @Override
    public void initializeStatusFromEntityData(@Nullable String s) {
        ProductionStatus from;
        try {
            from = ProductionStatus.fromNumber(s);
        } catch (NumberFormatException nfe) {
            QT.JOB_LOGGER.error("Ignoring exception: {}", nfe.getMessage());
            from = ProductionStatus.FACTORY.idle();
        }
        if (from.isUnset()) {
            from = ProductionStatus.FACTORY.idle();
        }
        this.journal.initializeStatus(from);
    }

    @Override
    public Signals getSignal(Signals.DayTime dayTime) {
        if (specialGlobalRules.contains(SpecialRules.WORK_IN_EVENING)) {
            return Signals.NOON;
        }
        return Signals.fromDayTime(dayTime);
    }

    @Override
    public String getStatusToSyncToClient() {
        return journal.getStatus().name();
    }

    @Override
    protected @Nullable WorkPosition<BlockPos> findProductionSpot(ServerLevel sl) {
        return ticker.workSpot();
    }

    @Override
    protected @NotNull WithReason<@Nullable BlockPos> findJobSite(
            TownInterface town,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> blocksSrc,
            Function<BlockPos, State> work,
            Predicate<BlockPos> isValidWalkTarget,
            Predicate<BlockPos> isJobBlock,
            Function<BlockPos, BlockPos> getRandomAdjacent
    ) {
        // Call the new pre-hook before any logic
        AtomicReference<WithReason<BlockPos>> override = new AtomicReference<>(null);
        UnsafeVillagerData data = town.getVillagerHandle().getUnprotectedDataHandle(VillagerUUID.from(ownerUUID));
        PreFindJobSiteHook.run(
                getGlobalSpecialRules(),
                data,
                override::set
        );
        if (override.get() != null && override.get().value() != null) {
            return override.get();
        }

        Map<Integer, SupplyItemStatus> statusItems = DeclarativeJobTicker.getSupplyItemStatuses(getSupplyDeps());
        // Find rooms with completed product (at max state)
        ImmutableList<RoomRecipeMatch<MCRoom>> roomsAtMaxState = DeclarativeJobs.roomsWithState(
                roomsMatching(town).apply(location),
                p -> isCorrectBlock(town).test(location, p),
                bp -> {
                    State jbs = work.apply(bp);
                    return jbs != null && maxState.equals(jbs.processingState());
                }
        );
        return DeclarativeJobs.findJobSite(
                maxState,
                prioritizesExtraction(),
                statusItems,
                roomsAtMaxState.stream().map(v -> v.room).toList(),
                (MCRoom room) -> Positions.ToBlock(room.getDoorPos(), room.yCoord),
                blocksSrc,
                work,
                isJobBlock,
                (block, room) -> findInteractionSpot(block, room, isValidWalkTarget, getRandomAdjacent)
        );
    }

    private Dependencies4<MCHeldItem> getSupplyDeps() {
        return new Dependencies4<>() {
            @Override
            public Supplier<ImmutableList<MCHeldItem>> getJournalItemsSupplier() {
                return journal::getItems;
            }

            @Override
            public SupplyChecks<MCHeldItem> asChecks() {
                return DeclarativeJobs.toSupplyChecks(checks);
            }

            @Override
            public int getMaxState() {
                return maxState;
            }
        };
    }

    public RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, State> work,
            Predicate<BlockPos> canClaim
    ) {
        Collection<RoomRecipeMatch<MCRoom>> x = town.getRoomHandle().getRoomsMatching(location.baseRoom());
        Function<RoomRecipeMatch<MCRoom>, Collection<BlockPos>> gcb = m -> m.getContainedBlocks().keySet().stream()
                                                                            .toList();
        WorkLocation.BlockInfo info = info(town.getServerLevel());
        return RoomsStatusLogic.compute(
                x,
                work,
                canClaim,
                p -> location.shouldInitializeWorkState().test(info, p),
                checks,
                gcb,
                maxState
        );
    }

    @Override
    public JobName getJobName() {
        return new JobName("jobs." + jobId);
    }

    @Override
    public Function<Void, Void> addItemInsertionListener(BiConsumer<BlockPos, MCHeldItem> listener) {
        final TriConsumer<MCExtra, BlockPos, MCHeldItem> l = (extra, bp, item) -> listener.accept(bp, item);
        this.world.addItemInsertionListener(l);
        return (nul) -> {
            this.world.removeItemInsertionListener(l);
            return null;
        };
    }

    @Override
    public Function<Void, Void> addJobCompletionListener(Consumer<JobID> listener) {
        this.world.addJobCompletionListener(listener);
        return (nul) -> {
            this.world.removeJobCompletionListener(listener);
            return null;
        };
    }

    @Override
    public long getTotalDuration() {
        return totalDuration;
    }

    @Override
    public int getWarpTicksPerCycle() {
        // Count ingredient collection ticks: sum of all ingredient quantities across states
        int ingredientTicks = 0;
        for (int state = 0; state <= maxState; state++) {
            Integer qty = checks.getQuantityForStep(state, null);
            if (qty != null && qty > 0) {
                ingredientTicks += qty;
            }
        }

        // Count work ticks: sum of all work required across states
        int workTicks = initialWork.values().stream().reduce(0, Integer::sum);

        // Overhead: 1 for extraction, 1 for dropping loot
        int overhead = 2;

        // Total ticks needed per cycle
        return ingredientTicks + workTicks + overhead;
    }

    @Override
    public Collection<String> getGlobalSpecialRules() {
        return specialGlobalRules;
    }

    @Override
    public int getExperienceEarned() {
        if (specialGlobalRules.contains(SpecialRules.NO_EXPERIENCE_GAINED)) {
            return 0;
        }
        int workPart = initialWork.values().stream().reduce(0, Integer::sum) * workInterval;
        long timePart = workPart + Math.max((totalDuration / 10), 1);
        QT.JOB_LOGGER.debug(
                "{} gained experience: {} from work, {} from time",
                UtilClean.truncateMiddle(ownerUUID),
                workPart,
                timePart
        );
        return (int) timePart;
    }

    public int getMaxState() {
        return maxState;
    }

    public WorkLocation location() {
        return location;
    }

    public ExpirationRules getExpiration() {
        return expiration;
    }

    public int getWorkInterval() {
        return workInterval;
    }

    public RealtimeWorldInteraction getWorld() {
        return world;
    }

    public DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> getChecks() {
        return checks;
    }

    // Package-accessible delegates for DeclarativeJobTickerDependencies

    /**
     * False for a job whose finished product is consumed, not stored (a diner's meal); set by
     * {@code WorksBehaviour} from {@code WorkDescription.productConsumed}. Full storage never stops such a job.
     */
    public DeclarativeJob withFinishedProductNeedsStorage(boolean needsStorage) {
        this.finishedProductNeedsStorage = needsStorage;
        return this;
    }

    boolean finishedProductNeedsStorage() {
        return finishedProductNeedsStorage;
    }

    ProductionJournal<MCTownItem, MCHeldItem> getJournal() {
        return journal;
    }

    // getRecipe is already defined as protected - no need for duplicate

    boolean tryDropLootForDeps(long tick, BlockPos entityBlockPos) {
        return super.tryDropLoot(tick, entityBlockPos);
    }

    void tryGetSuppliesForDeps(
            TownInterface town,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingInput,
            BlockPos entityBlockPos,
            long currentTick
    ) {
        Long before = this.lastSupplyTick; // nullable until the first take
        this.tryGetSupplies(town, roomsNeedingInput, entityBlockPos, currentTick);
        collectingStall.observe(
                journal.getStatus().isCollectingSupplies(),
                !java.util.Objects.equals(this.lastSupplyTick, before),
                currentTick,
                () -> describeCollectingStall(town, roomsNeedingInput),
                msg -> QT.JOB_LOGGER.warn("[STALL:collecting] job={} villager={} {}", jobId, ownerUUID, msg)
        );
    }

    private final CollectingStallTrace collectingStall = new CollectingStallTrace();

    /** What the job sees while stuck collecting: its own and the town's non-zero spots, and the input it is told about. */
    private String describeCollectingStall(
            TownInterface town,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingInput
    ) {
        StringBuilder b = new StringBuilder();
        b.append("status=").append(journal.getStatus())
         .append(" held=").append(journal.getItems().stream().filter(i -> !i.isEmpty()).map(MCHeldItem::getShortName).toList())
         .append(" suppliesTarget=").append(suppliesTarget == null ? "none" : suppliesTarget.toShortString(false))
         .append(" statesNeedingInput=").append(roomsNeedingInput.getNonEmptyStates());
        appendSpots(b, "own", town.getWorkStatusHandle(ownerUUID));
        appendSpots(b, "town", town.getWorkStatusHandle(null));
        return b.toString();
    }

    private void appendSpots(StringBuilder b, String label, Object handle) {
        if (!(handle instanceof ca.bradj.questown.town.AbstractWorkStatusStore<?, ?, ?, ?> store)) {
            b.append(" ").append(label).append("=?");
            return;
        }
        @SuppressWarnings("unchecked")
        ca.bradj.questown.town.AbstractWorkStatusStore<BlockPos, ?, ?, ?> s = (ca.bradj.questown.town.AbstractWorkStatusStore<BlockPos, ?, ?, ?>) store;
        b.append(" ").append(label).append("=[");
        s.getAll().forEach((pos, st) -> {
            if (st == null || st.processingState() == 0) {
                return;
            }
            b.append(pos.toShortString()).append(":").append(st.processingState())
             .append(" timer=").append(s.getTimeToNextState(pos))
             .append(" claimable=").append(s.canClaim(pos, () -> makeClaim(ownerUUID))).append("; ");
        });
        b.append("]");
    }

    boolean isValidWalkTargetForDeps(TownInterface town, BlockPos bp) {
        return super.isValidWalkTarget(town, bp);
    }

    void setLookTargetForDeps(BlockPos position) {
        super.setLookTarget(position);
    }

    // isJobBlock is already defined as protected - no need for duplicate

    @Nullable ContainerTarget<MCContainer, MCTownItem> getSuccessTargetForDeps() {
        return successTarget;
    }

    RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> getRoomsNeedingInputForDeps() {
        return roomsNeedingIngredientsOrTools;
    }

    boolean hasInsertedForDeps(int action) {
        return hasInserted(action);
    }

    @Override
    protected void setupForGetSupplies(
            TownInterface town,
            BlockPos pos,
            Long currentTick
    ) {
        if (FetcherHack.isFetcher(jobId)) {
            if (inventory.isEmpty()) {
                suppliesTarget = FetcherHack.getTarget(town);
                return;
            }
        }
        super.setupForGetSupplies(town, pos, currentTick);
    }

    @Override
    protected @Nullable ContainerTarget<MCContainer, MCTownItem> getDropTargetForLoot(
            BlockPos entityBlockPos,
            TownInterface town
    ) {
        ContainerTarget<MCContainer, MCTownItem> defaultTarget = super.getDropTargetForLoot(entityBlockPos, town);
        if (!FetcherHack.isFetcher(jobId)) {
            return defaultTarget;
        }
        return FetcherHack.getDropTargetForLoot(town, journal.getItems(), defaultTarget);
    }

    @Override
    public Iterable<MCHeldItem> getItemsForDrop() {
        if (!FetcherHack.isFetcher(jobId)) {
            return super.getItemsForDrop();
        }
        return FetcherHack.getItemsForDrop(super.getItemsForDrop(), successTarget);
    }

    public String getIngredient(@Nullable Integer integer) {
        return Util.orNull(initialIngredients.get(integer), Ingredients::toString);
    }

    public String getTool(@Nullable Integer integer) {
        return Util.orNull(initialTools.get(integer), Ingredients::toString);
    }

    @Override
    public void log(
            String s,
            Object... args
    ) {
        logger.log(s, args);
    }

    @Nullable UUID getOwnerUUID() {
        return ownerUUID;
    }

    Supplier<Claim> getClaimSupplier() {
        return claimSupplier;
    }

    public void cacheRoomsNeedingIngredientsOrTools(RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> rniot2) {
        this.roomsNeedingIngredientsOrTools = rniot2;
    }

    /**
     * Override this method to provide a custom status computation for special jobs.
     * Return null to use the default status computation logic.
     *
     * @param town The town interface
     * @param statusFactory The status factory for creating statuses
     * @return A ProductionStatus to override the default, or null for default behavior
     */
    protected @Nullable ProductionStatus getComputeStatusOverrideForSpecialJobs(
            TownInterface town,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        if (FetcherHack.isFetcher(jobId)) {
            // The fetcher's bespoke status (acquire request → fetch ingredient → deliver) is not
            // expressible in the generic pipeline; drive it here so the supply getter engages.
            return FetcherHack.computeStatusOverride(town, journal.getItems());
        }
        return null; // Default: use standard status computation
    }
}
