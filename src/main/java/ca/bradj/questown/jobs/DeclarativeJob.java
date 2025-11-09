package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
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
import ca.bradj.questown.jobs.declarative.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.fetcher.FetcherHack;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.AbstractSupplyGetter;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.mc.PredicateCollections;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.TownContainers;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.special.SpecialQuests;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.adapter.IRoomRecipeMatch;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.rooms.XWall;
import ca.bradj.roomrecipes.rooms.ZWall;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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

import static ca.bradj.questown.jobs.DeclarativeJobs.STATUS_FACTORY;
import static ca.bradj.questown.mc.Util.info;

// TODO: Break ties to MC and unit test - Maybe reuse code from ProductionTimeWarper
public class DeclarativeJob extends
        DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> {

    private final DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> checks;

    private static final Marker marker = MarkerManager.getMarker("DJob");
    private final RealtimeWorldInteraction world;

    private final JobLogic<MCExtra, Boolean, BlockPos> logic;
    private final WorkLocation location;
    private final @NotNull Integer maxState;
    private final JobID jobId;
    private final ExpirationRules expiration;
    private final long totalDuration;
    private final int workInterval;
    private final RecipeProvider recipe;
    public final ImmutableMap<Integer, Ingredient> initialIngredients;
    public final ImmutableMap<Integer, Ingredient> initialTools;
    private final ImmutableMap<Integer, Integer> initialWork;
    private Signals signal;

    private @Nullable Long lastSupplyTick = null;
    private @Nullable Long secondLastSupplyTick = null;

    private final AbstractSupplyGetter<ProductionStatus, BlockPos, MCTownItem, MCHeldItem, MCRoom> getter = new AbstractSupplyGetter<>();
    private boolean isFirstTick = true;

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
        this.logic = new JobLogic<>();
        this.workInterval = workInterval;
        this.recipe = buildRecipe(this);
    }

    private static @Nullable String getUnmetNeed(
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> qtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            NeedsRegistrations.Need need
    ) {
        Integer ii = need.timesInserted();
        if (ii != null) {
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
            PreInitHook.run(stageRules, () -> level, ingr.get(i), tool.get(i), globalJCR, globalSRCR);
        }

        PreInitHook.run(
                specialGlobalRules,
                () -> level,
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
                                level,
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
        return location.isJobBlock().test(ctx.withBlockAlreadyUsed(logic.hasWorkedRecently() || hasInserted(0))
                                             .withJobAlreadyStarted(true));
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
    public boolean shouldStandStill() {
        return this.logic.hasWorkedRecently();
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
        WorkStatusHandle<BlockPos, MCHeldItem> work = getWorkStatusHandle(town);
        AtomicReference<RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos>> rniot = new AtomicReference<>(
                roomsNeedingIngredientsOrTools(
                        town,
                        work::getJobBlockState,
                        (BlockPos bp) -> work.canClaim(bp, this.claimSupplier)
                ));

        VisitorMobEntity vme = (VisitorMobEntity) entity;
        ImmutableList<MCHeldItem> heldItems = vme.getJobJournalSnapshot().items();
        Function<BlockPos, @NotNull State> bsFn = bp -> Util.applyOrDefault(
                bp,
                p -> town.getWorkStatusHandle(ownerUUID).getJobBlockState(p),
                State.fresh()
        );

        boolean firstTick = this.isFirstTick;
        if (this.isFirstTick) {
            this.isFirstTick = false;
        }

        Supplier<ImmutableList<BlockPos>> otherVillagerPositions = () -> town.getVillagerHandle().entities().stream()
                                                                             .filter(v -> !ownerUUID.equals(v.getUUID()))
                                                                             .map(
                                                                                     Entity::getOnPos)
                                                                             .collect(ImmutableList.toImmutableList());
        Supplier<BlockPos> randomWalkableTownPosition = () -> town.getRandomWanderTarget(entity.getOnPos());
        UnsafeVillagerData villagerData = town.getVillagerHandle().getUnprotectedDataHandle(vme.getVUID());

        PreTickHook.run(
                specialGlobalRules,
                town::getServerLevel,
                location,
                heldItems,
                fn -> rniot.set(fn.apply(rniot.get())),
                bsFn,
                firstTick,
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        );
        specialRules.forEach((state, rules) -> PreTickHook.run(
                rules,
                town::getServerLevel,
                location,
                heldItems,
                fn -> rniot.set(fn.apply(rniot.get())),
                bsFn,
                firstTick,
                otherVillagerPositions,
                randomWalkableTownPosition,
                villagerData
        ));

        this.roomsNeedingIngredientsOrTools = new RoomsNeedingVillagerInput<>(rniot.get().get());

        MCExtra extra = new MCExtra(town, work, (VisitorMobEntity) entity);
        this.tick(extra, work, entity, facingPos, this.roomsNeedingIngredientsOrTools, statusFactory);
    }

    @Override
    protected void tick(
            MCExtra extra,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            LivingEntity entity,
            Direction facingPos,
            // Change this to a supplier whose value is cached for one tick
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        JobTownProvider<MCRoom> jtp = makeTownProviderForTick(extra, work, roomsNeedingIngredientsOrTools);

        EntityCurrentJobSite<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(
                entity.blockPosition(),
                roomsNeedingIngredientsOrTools,
                jtp.roomsWithCompletedProduct()
        );

        EntityLocStateProvider<MCRoom> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable MCRoom getEntityCurrentJobSite() {
                if (entityCurrentJobSite == null) {
                    return null;
                }
                return entityCurrentJobSite.room();
            }
        };

        Supplier<ProductionStatus> computeState = getStateComputer(extra.town(), statusFactory, jtp, elp);
        this.signal = Signals.fromDayTime(Util.getDayTime(extra.town().getServerLevel()));
        WorkPosition<BlockPos> workSpot = world.getWorkSpot();
        BlockPos bp = Util.orNull(workSpot, WorkPosition::jobBlock);
        int action = bp == null ? 0 : Util.withFallbackForNullInput(
                work.getJobBlockState(bp),
                State::processingState,
                0
        );
        logic.tick(
                extra,
                computeState,
                jobId,
                entityCurrentJobSite != null,
                WorkSeekerJob.isSeekingWork(jobId),
                workSpot != null && hasInserted(action),
                !inventory.isEmpty(),
                expiration,
                new JobLogic.JobDetails(maxState, checks.getWorkForStep(0), workInterval),
                this.asLogicWorld(
                        extra,
                        work,
                        (VisitorMobEntity) entity,
                        entityCurrentJobSite,
                        roomsNeedingIngredientsOrTools
                ),
                (tuwn, bpp) -> Util.withFallbackForNullInput(
                        getWorkStatusHandle(extra.town()).getJobBlockState(bpp),
                        State::processingState,
                        0
                ),
                Config.WORKED_RECENTLY_TICKS.get().intValue()
        );
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
        return roomsNeedingIngredientsOrTools.cleanFns(checks::getIngredientsForStep, checks::getToolsForStep);
    }

    private @NotNull JobTownProvider<MCRoom> makeTownProviderForTick(
            MCExtra extra,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools
    ) {
        WorkLocation.BlockInfo info = info(extra.town().getServerLevel());
        Supplier<Map<Integer, DeclarativeJobs.RoomsWithWorkableStatefulBlocks>> roomsV2 = () -> DeclarativeJobs.rooms(
                maxState,
                roomsNeedingIngredientsOrTools,
                work,
                (BlockPos bp) -> isJobBlock(new JobBlockTestContext(
                        extra.town().getServerLevel(),
                        info,
                        bp,
                        journal::getItems,
                        () -> TownContainers.getUniqueItems(extra.town()),
                        false, // Gets overridden
                        false // Gets overridden

                ))
        );

        return new JobTownProvider<>() {
            private final Function<BlockPos, State> getJobBlockState = work::getJobBlockState;

            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return roomsWithState(extra.town(), getJobBlockState, maxState).stream().map(v -> v.room).toList();
            }

            @Override
            public RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsByState() {
                return roomsNeedingIngredientsOrTools;
            }

            @Override
            public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
                return roomsV2.get();
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return Jobs.isUnfinishedTimeWorkPresent(
                        extra.town().getRoomHandle(),
                        location.baseRoom(),
                        work::getTimeToNextState
                );
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> extra.town().getRoomHandle().getRoomsMatching(location.baseRoom()).stream()
                                   .map(v -> (Supplier<Collection<BlockPos>>) () -> v.getContainedBlocks().keySet()
                                                                                     .stream()
                                                                                     .filter(z -> shouldInit(z, extra))
                                                                                     .toList())
                                   .toList(), getJobBlockState, (bp) -> work.canClaim(bp, () -> makeClaim(ownerUUID))
                );
                ImmutableList.Builder<Integer> b = ImmutableList.builder();
                statesWithUnfinishedWork.forEach(s -> {
                    IPredicateCollection<MCTownItem> toolsReq = checks.getToolsForStep(s);
                    if (toolsReq != null && !toolsReq.isEmpty()) {
                        return;
                    }
                    b.add(s);
                });
                return b.build();
            }

            @Override
            public Collection<MCRoom> roomsAtState(Integer state) {
                return roomsNeedingIngredientsOrTools.get().get(state).stream()
                                                     .map(RoomsNeedingVillagerInput.NVIRoom::room)
                                                     .map(IRoomRecipeMatch::getRoom).toList();
            }

            @Override
            public boolean hasSupplies() {
                RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> needs = roomsNeedingIngredientsByState();
                ImmutableList<PredicateCollection<MCTownItem, ?>> neededItems = needs.cleanFns(
                        checks::getIngredientsForStep,
                        checks::getToolsForStep
                );
                return Jobs.townHasSupplies(extra.town(), journal, neededItems);
            }

            @Override
            public LZCD.Dependency<Void> hasSuppliesV2() {
                return DeclarativeJobs.supplies(
                        extra.town().getServerLevel(),
                        roomsV2,
                        extra.town(),
                        checks.getAllRequiredIngredients(),
                        checks.getAllRequiredTools(),
                        checks::shouldCheckContainerForSupplies,
                        bp -> isJobBlock(bp),
                        js -> location.baseRoom().equals(js)
                );
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(extra.town());
            }
        };
    }

    private boolean shouldInit(
            BlockPos z,
            MCExtra extra
    ) {
        WorkLocation.BlockInfo info = info(extra.town().getServerLevel());
        return location.shouldInitializeWorkState().test(info, z);
    }

    private Collection<RoomRecipeMatch<MCRoom>> roomsWithState(
            TownInterface town,
            Function<BlockPos, State> getJobBlockState,
            Integer state
    ) {
        return roomsWithState(town, getJobBlockState, state, s -> true);
    }


    private Collection<RoomRecipeMatch<MCRoom>> roomsWithState(
            TownInterface town,
            Function<BlockPos, State> getJobBlockState,
            Integer state,
            Predicate<State> extraCheck
    ) {
        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle().getRoomsMatching(location.baseRoom());
        return Jobs.roomsWithState(
                rooms, p -> location.shouldInitializeWorkState().test(info(town.getServerLevel()), p), (bp) -> {
                    State jbs = getJobBlockState.apply(bp);
                    if (jbs == null) {
                        return false;
                    }
                    return state.equals(jbs.processingState()) && extraCheck.test(jbs);
                }
        );
    }

    @Override
    protected boolean isJobBlock(BlockPos bp) {
        return checks.isJobBlock(bp);
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

    private JobLogic.JLWorld<MCExtra, Boolean, BlockPos> asLogicWorld(
            MCExtra extra,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            VisitorMobEntity entity,
            EntityCurrentJobSite<MCRoom> entityCurrentJobSite,
            RoomsNeedingVillagerInput<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools
    ) {
        DeclarativeJob self = this;
        TownInterface town = extra.town();
        return new JobLogic.JLWorld<>() {
            @Override
            public void changeJob(JobID id) {
                town.getVillagerHandle().changeJobForVillager(ownerUUID, id, false);
            }

            @Override
            public void changeToNextJob() {
                town.getVillagerHandle().changeToNextJobForVillager(ownerUUID, getId());
            }

            @Override
            public boolean setWorkLeftAtFreshState(int workRequiredAtFirstState) {
                ServerLevel sl = town.getServerLevel();
                boolean didIt = false;
                for (RoomRecipeMatch<MCRoom> room : town.getRoomHandle().getRoomsMatching(SpecialQuests.CLINIC)) {
                    Map<Integer, Collection<WorkPosition<BlockPos>>> spots = DeclarativeJob.this.listAllWorkSpots(
                            getWorkStatusHandle(town)::getJobBlockState,
                            new EntityCurrentJobSite<>(room.room, false),
                            bp -> isValidWalkTarget(town, bp),
                            bp -> location.shouldInitializeWorkState().test(info(sl), bp),
                            bp -> bp.relative(Compat.getRandomHorizontal(sl))
                    );
                    for (WorkPosition<BlockPos> p : UtilClean.getOrDefault(spots, 0, ImmutableList.of())) {
                        work.setJobBlockState(p.jobBlock(), State.fresh().setWorkLeft(workRequiredAtFirstState));
                        didIt = true;
                    }
                }
                return didIt;
            }

            @Override
            public WorkPosition<BlockPos> getWorkSpot() {
                return world.getWorkSpot();
            }

            @Override
            public Map<Integer, Collection<WorkPosition<BlockPos>>> listAllWorkSpots() {
                ServerLevel sl = town.getServerLevel();
                if (sl == null) {
                    return ImmutableMap.of();
                }
                return self.listAllWorkSpots(
                        work::getJobBlockState,
                        entityCurrentJobSite,
                        bp -> isValidWalkTarget(town, bp),
                        bp -> isJobBlock(bp),
                        bp -> bp.relative(Compat.getRandomHorizontal(sl))
                );
            }

            @Override
            public boolean tryGrabbingInsertedSupplies() {
                return world.tryGrabbingInsertedSupplies(extra);
            }

            @Override
            public void clearInsertedSupplies() {
                world.clearInsertedSupplies(extra);
            }

            @Override
            public void registerUnmetNeeds(
                    ProductionStatus status,
                    @Nullable BlockPos workspot,
                    int timesInserted
            ) {
                world.registerUnmetNeeds(extra, workspot, timesInserted);
            }

            @Override
            public void registerUnmetRooms() {
                world.registerUnmetRooms(extra);
            }

            @Override
            public int timesInserted() {
                return world.timesInserted(extra);
            }

            @Override
            public AbstractWorldInteraction<MCExtra, BlockPos, ?, ?, Boolean> getHandle() {
                return world;
            }

            @Override
            public boolean tryDropLoot() {
                ImmutableList<MCHeldItem> itemsBeforeDrop = journal.getItems();
                boolean result = self.tryDropLoot(Util.getTick(town.getServerLevel()), entity.blockPosition());
                ImmutableList<MCHeldItem> itemsAfterDrop = journal.getItems();
                if (result) {
                    PostDropHook.run(
                            town,
                            specialGlobalRules,
                            town.getServerLevel(),
                            successTarget.getBlockPos(),
                            itemsBeforeDrop,
                            itemsAfterDrop,
                            work::clearState
                    );
                }
                return result;
            }

            @Override
            public void tryGetSupplies() {
                if (logic.isWrappingUp()) {
                    return;
                }
                self.tryGetSupplies(
                        extra.town(),
                        roomsNeedingIngredientsOrTools,
                        entity.blockPosition(),
                        Util.getTick(town.getServerLevel())
                );
            }

            @Override
            public void setLookTarget(BlockPos position) {
                self.setLookTarget(position);
            }

            @Override
            public void registerHeldItemsAsFoundLoot() {
                town.getKnowledgeHandle().registerFoundLoots(journal.getItems());
            }
        };
    }

    @Override
    public String toString() {
        return "DeclarativeJob{" + "jobId=" + jobId.toNiceString() + '}';
    }

    protected @NotNull Supplier<ProductionStatus> getStateComputer(
            TownInterface town,
            IProductionStatusFactory<ProductionStatus> statusFactory,
            JobTownProvider<MCRoom> jtp,
            EntityLocStateProvider<MCRoom> elp
    ) {
        return () -> {
            if (FetcherHack.isFetcher(jobId)) {
                ProductionStatus s = FetcherHack.computeStatus(town, journal.getItems());
                if (s != null) {
                    journal.changeStatus(s);
                    return s;
                }
            }
            journal.tryUpdateStatus(jtp, elp, defaultEntityInvProvider(), statusFactory, prioritizesExtraction());
            return journal.getStatus();
        };
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
        JobsClean.SuppliesTarget<BlockPos, MCTownItem> st = new JobsClean.SuppliesTarget<>() {
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

        jobSite.room().getSpaces().stream().flatMap(space -> InclusiveSpaces.getPositions(space, InclusiveSpaces.PositionType.INTERIOR_ONLY).stream())
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

        QT.JOB_LOGGER.trace("choosing to approach job block from random side");
        return getRandomAdjacent.apply(bp);
    }

    @Nullable
    private BlockPos doFindInteractionSpot(
            BlockPos bp,
            Room jobSite,
            Predicate<BlockPos> isEmpty
    ) {
        Direction d = getDoorDirectionFromCenter(jobSite);
        if (isEmpty.test(bp.relative(d))) {
            return bp.relative(d);
        }
        for (Direction dd : Direction.Plane.HORIZONTAL) {
            if (isEmpty.test(bp.relative(dd))) {
                return bp.relative(dd);
            }
        }
        if (InclusiveSpaces.calculateArea(jobSite.getSpaces()) == 9) {
            // 1x1 room (plus walls)
            return Positions.ToBlock(jobSite.getDoorPos(), bp.getY());
        }
        return null;
    }

    private Direction getDoorDirectionFromCenter(Room jobSite) {
        Optional<XWall> backXWall = jobSite.getBackXWall();
        if (backXWall.isPresent() && backXWall.get().getZ() > jobSite.doorPos.z) {
            return Direction.NORTH;
        }
        if (backXWall.isPresent()) {
            return Direction.SOUTH;
        }


        Optional<ZWall> backZWall = jobSite.getBackZWall();
        if (backZWall.isPresent() && backZWall.get().getX() > jobSite.doorPos.x) {
            return Direction.WEST;
        }
        if (backZWall.isPresent()) {
            return Direction.EAST;
        }
        return Direction.NORTH;
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
    public Signals getSignal() {
        if (specialGlobalRules.contains(SpecialRules.WORK_IN_EVENING)) {
            return Signals.NOON;
        }
        return signal;
    }

    @Override
    public String getStatusToSyncToClient() {
        return journal.getStatus().name();
    }

    @Override
    protected Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
        return JobsClean.getSupplyItemStatuses(
                journal::getItems,
                checks.getAllRequiredIngredients(),
                s -> !UtilClean.getOrDefault(
                        checks.getAllRequiredIngredients(),
                        s,
                        PredicateCollection.empty("no ingredient defined")
                ).isEmpty(),
                Jobs.unTown(checks.getAllRequiredTools()),
                s -> {
                    PredicateCollection<MCTownItem, MCTownItem> toool = UtilClean.getOrDefault(
                            checks.getAllRequiredTools(),
                            s,
                            PredicateCollection.empty("no tool defined")
                    );
                    return !toool.isEmpty();
                },
                checks.getAllRequiredWork(),
                maxState
        );
    }

    @Override
    protected @Nullable WorkPosition<BlockPos> findProductionSpot(ServerLevel sl) {
        return logic.workSpot();
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
        ca.bradj.questown.jobs.declarative.PreFindJobSiteHook.run(
                getGlobalSpecialRules(),
                data,
                override::set
        );
        if (override.get() != null && override.get().value() != null) {
            return override.get();
        }

        Map<Integer, SupplyItemStatus> statusItems = getSupplyItemStatus();
        return JobsClean.findJobSite(
                maxState,
                prioritizesExtraction(),
                statusItems,
                roomsWithState(town, work, maxState).stream().map(v -> v.room).toList(),
                (MCRoom room) -> Positions.ToBlock(room.getDoorPos(), room.yCoord),
                blocksSrc,
                work,
                isJobBlock,
                (block, room) -> findInteractionSpot(block, room, isValidWalkTarget, getRandomAdjacent)
        );
    }

    @Override
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

    public DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> getChecks() {
        return checks;
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
}
