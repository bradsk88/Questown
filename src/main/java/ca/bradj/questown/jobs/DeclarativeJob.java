package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.core.UtilClean;
import ca.bradj.questown.integration.jobs.ItemCheckReplacer;
import ca.bradj.questown.integration.jobs.JobCheckReplacer;
import ca.bradj.questown.integration.jobs.SupplyRoomCheckReplacer;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.AbstractSupplyGetter;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingIngredientsOrTools;
import ca.bradj.questown.logic.IPredicateCollection;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.mc.PredicateCollections;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Claim;
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
import com.google.common.collect.Lists;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.util.Lazy;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Stream;

import static ca.bradj.questown.jobs.DeclarativeJobs.STATUS_FACTORY;

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
    private Signals signal;

    private final AbstractSupplyGetter<ProductionStatus, BlockPos, MCTownItem, MCHeldItem, MCRoom> getter = new AbstractSupplyGetter<>();

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
                ownerUUID, inventoryCapacity, marker,
                DeclarativeJobs.journalInitializer(jobId),
                STATUS_FACTORY,
                specialStatusRules, specialGlobalRules,
                () -> {
                    if (specialGlobalRules.contains(SpecialRules.CLAIM_SPOT)) {
                        return makeClaim(ownerUUID);
                    }
                    return null;
                },
                location
        );
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
                maxState,
                this.checks,
                resultGenerator,
                specialStatusRules,
                extra -> {
                    if (specialGlobalRules.contains(SpecialRules.CLAIM_SPOT)) {
                        return makeClaim(ownerUUID);
                    }
                    return null;
                },
                workInterval,
                sound
        );
        this.maxState = maxState;
        this.location = location;
        this.expiration = expiration;
        this.totalDuration = timeRequiredAtStates.values().stream().reduce(Integer::sum).orElse(0);
        this.logic = new JobLogic<>();
        this.workInterval = workInterval;
        this.recipe = buildRecipe(this);
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

        JobCheckReplacer globalJCR = new JobCheckReplacer(location.isJobBlock());
        SupplyRoomCheckReplacer globalSRCR = new SupplyRoomCheckReplacer();

        DeclarativeJob self = this;

        for (int i = 0; i <= maxState; i++) {
            ProductionStatus ss = ProductionStatus.fromJobBlockStatus(i);
            List<String> stageRules = UtilClean.getOrDefaultCollection(specialRules, ss, ImmutableList.of());
            PreInitHook.run(
                    stageRules,
                    () -> level,
                    ingr.get(i),
                    tool.get(i),
                    globalJCR,
                    globalSRCR
            );
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
                JobCheckReplacer.withItemsAndLevel(globalJCR, self.journal::getItems, level::getBlockState)
        );
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
            int interval,
            @Nullable SoundInfo sound
    ) {
        return new RealtimeWorldInteraction(
                journal,
                maxState,
                checks,
                specialRules,
                resultGenerator,
                claimSpots,
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
        AtomicReference<RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos>> rniot = new AtomicReference<>(
                roomsNeedingIngredientsOrTools(
                        town, work::getJobBlockState, (BlockPos bp) -> work.canClaim(bp, this.claimSupplier)
                )
        );

        VisitorMobEntity vme = (VisitorMobEntity) entity;
        ImmutableList<MCHeldItem> heldItems = vme.getJobJournalSnapshot().items();
        Function<BlockPos, @NotNull State> bsFn = bp -> Util.applyOrDefault(
                bp, p -> town.getWorkStatusHandle(ownerUUID).getJobBlockState(p),
                State.fresh()
        );
        PreTickHook.run(
                specialGlobalRules,
                location,
                heldItems,
                fn -> rniot.set(fn.apply(rniot.get())),
                bsFn
        );
        specialRules.forEach((state, rules) ->
                PreTickHook.run(
                        rules,
                        location,
                        heldItems,
                        fn -> rniot.set(fn.apply(rniot.get())),
                        bsFn
                ));


        this.roomsNeedingIngredientsOrTools = new RoomsNeedingIngredientsOrTools<>(rniot.get().get());

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
            RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        JobTownProvider<MCRoom> jtp = makeTownProviderForTick(extra, work, roomsNeedingIngredientsOrTools);

        RoomRecipeMatch<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(
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
                return entityCurrentJobSite.room;
            }
        };

        Supplier<ProductionStatus> computeState = getStateComputer(statusFactory, jtp, elp);
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
                        extra, work,
                        (VisitorMobEntity) entity, entityCurrentJobSite,
                        roomsNeedingIngredientsOrTools
                ),
                (tuwn, bpp) -> Util.withFallbackForNullInput(
                        getWorkStatusHandle(extra.town()).getJobBlockState(bpp),
                        State::processingState,
                        0
                )
        );
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
            RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools
    ) {
        Lazy<Map<Integer, LZCD.Dependency<Void>>> roomsV2 = Lazy.of(() ->
                DeclarativeJobs.rooms(maxState, roomsNeedingIngredientsOrTools, work)
        );

        return new JobTownProvider<>() {
            private final Function<BlockPos, State> getJobBlockState = work::getJobBlockState;

            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return roomsWithState(extra.town(), getJobBlockState, maxState)
                        .stream()
                        .map(v -> v.room)
                        .toList();
            }

            @Override
            public RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsByState() {
                return roomsNeedingIngredientsOrTools;
            }

            @Override
            public Map<Integer, LZCD.Dependency<Void>> roomsNeedingIngredientsByStateV2() {
                return roomsV2.get();
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return Jobs.isUnfinishedTimeWorkPresent(
                        extra.town().getRoomHandle(), location.baseRoom(),
                        work::getTimeToNextState
                );
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> extra.town().getRoomHandle()
                                   .getRoomsMatching(location.baseRoom())
                                   .stream()
                                   .map(v -> (Supplier<Collection<BlockPos>>) () -> v.getContainedBlocks()
                                                                                     .keySet())
                                   .toList(),
                        getJobBlockState,
                        (bp) -> work.canClaim(bp, () -> makeClaim(ownerUUID))
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
                return roomsNeedingIngredientsOrTools.get().get(state).stream().map(IRoomRecipeMatch::getRoom).toList();
            }

            @Override
            public boolean hasSupplies() {
                RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> needs = roomsNeedingIngredientsByState();
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
                        checks::shouldCheckContainerForSupplies
                );
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(extra.town());
            }
        };
    }

    private Collection<RoomRecipeMatch<MCRoom>> roomsWithState(
            TownInterface town,
            Function<BlockPos, State> getJobBlockState,
            Integer state
    ) {
        Collection<RoomRecipeMatch<MCRoom>> rooms = town.getRoomHandle().getRoomsMatching(location.baseRoom());
        return Jobs.roomsWithState(
                rooms,
                checks::isJobBlock,
                (bp) -> state.equals(JobBlock.getState(getJobBlockState, bp))
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
            RoomRecipeMatch<MCRoom> entityCurrentJobSite,
            RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools
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
                            room.room,
                            bp -> isValidWalkTarget(town, bp),
                            bp -> isJobBlock(bp),
                            () -> Direction.getRandom(sl.random)
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
                        work::getJobBlockState, entityCurrentJobSite.room,
                        bp -> isValidWalkTarget(town, bp),
                        bp -> isJobBlock(bp),
                        () -> Direction.getRandom(sl.random)
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
            public boolean canDropLoot() {
                return logic.isWrappingUp() && !hasAnyLootToDrop();
            }

            @Override
            public AbstractWorldInteraction<MCExtra, BlockPos, ?, ?, Boolean> getHandle() {
                return world;
            }

            @Override
            public boolean tryDropLoot() {
                self.tryDropLoot(entity.blockPosition());
                return self.isDropping();
            }

            @Override
            public void tryGetSupplies() {
                if (logic.isWrappingUp()) {
                    return;
                }
                self.tryGetSupplies(roomsNeedingIngredientsOrTools, entity.blockPosition());
            }

            @Override
            public void seekFallbackWork() {
                JobID id = WorkSeekerJob.getIDForRoot(jobId);
                extra.town().getVillagerHandle().changeJobForVillager(ownerUUID, id, false);
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

    private @NotNull Supplier<ProductionStatus> getStateComputer(
            IProductionStatusFactory<ProductionStatus> statusFactory,
            JobTownProvider<MCRoom> jtp,
            EntityLocStateProvider<MCRoom> elp
    ) {
        return () -> {
            journal.tryUpdateStatus(
                    jtp,
                    elp,
                    defaultEntityInvProvider(),
                    statusFactory,
                    prioritizesExtraction()
            );
            return journal.getStatus();
        };
    }

    public boolean prioritizesExtraction() {
        return specialGlobalRules.contains(SpecialRules.PRIORITIZE_EXTRACTION);
    }

    private void tryGetSupplies(
            RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools,
            BlockPos entityBlockPos
    ) {
        if (suppliesTarget == null) {
            return;
        }
        JobsClean.SuppliesTarget<BlockPos, MCTownItem> st = new JobsClean.SuppliesTarget<BlockPos, MCTownItem>() {
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
            public void removeItem(
                    int i,
                    int quantity
            ) {
                suppliesTarget.getContainer()
                              .removeItem(i, quantity);
            }
        };
        getter.tryGetSupplies(
                journal.getStatus(), journal.getCapacity(),
                roomsNeedingIngredientsOrTools,
                st, recipe::getRecipe, journal.getItems(),
                (item) -> {
                    this.journal.addItem(MCHeldItem.fromTown(item));
                    this.clearJobSite();
                }
        );
    }

    @Override
    protected ImmutableList<PredicateCollection<MCTownItem, ?>> getRecipe(Integer integer) {
        return recipe.getRecipe(integer);
    }

    Map<Integer, Collection<WorkPosition<BlockPos>>> listAllWorkSpots(
            Function<BlockPos, State> town,
            @Nullable MCRoom jobSite,
            Predicate<BlockPos> isValidWalkTarget,
            Predicate<BlockPos> isJobBlock,
            Supplier<Direction> randomDirection
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        Function<BlockPos, BlockPos> is = bp -> findInteractionSpot(bp, jobSite, isValidWalkTarget, randomDirection);

        Map<Integer, List<WorkPosition<BlockPos>>> b = new HashMap<>();
        Consumer<BlockPos> tryAdd = bp -> tryAddSpot(town, bp, b, is, isJobBlock);

        jobSite.getSpaces()
               .stream()
               .flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space)
                                                .stream())
               .forEach(v -> {
                   BlockPos pos = Positions.ToBlock(v, jobSite.yCoord);
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
            Function<BlockPos, BlockPos> is,
            Predicate<BlockPos> isJobBlock
    ) {
        @Nullable Integer blockAction = JobBlock.getState(town, bp);
        if (blockAction != null) {
            if (isJobBlock.test(bp)) {
                Util.addOrInitialize(b, blockAction, new WorkPosition<>(bp, is.apply(bp)));
            }
        }
    }

    private BlockPos findInteractionSpot(
            BlockPos bp,
            Room jobSite,
            Predicate<BlockPos> isValidWalkTarget,
            Supplier<Direction> random
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

        QT.JOB_LOGGER.warn("choosing to approach job block from random side");
        return bp.relative(random.get());
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
        if (backXWall.isPresent() && backXWall.get()
                                              .getZ() > jobSite.doorPos.z) {
            return Direction.NORTH;
        }
        if (backXWall.isPresent()) {
            return Direction.SOUTH;
        }


        Optional<ZWall> backZWall = jobSite.getBackZWall();
        if (backZWall.isPresent() && backZWall.get()
                                              .getX() > jobSite.doorPos.x) {
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
            from = ProductionStatus.from(s);
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
        return journal.getStatus()
                      .name();
    }

    @Override
    protected Map<Integer, Boolean> getSupplyItemStatus() {
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
            RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> blocksSrc,
            Function<BlockPos, State> work,
            Predicate<BlockPos> isValidWalkTarget,
            Predicate<BlockPos> isJobBlock,
            Random rand
    ) {
        // TODO: Use tags to support more tiers of work rooms
        Map<Integer, Boolean> statusItems = getSupplyItemStatus();

        ArrayList<IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?>> rooms = new ArrayList<>(blocksSrc.getMatches());
        // TODO: Sort by distance and choose the closest (maybe also coordinate
        //  with other workers who need the same type of job site)
        // For now, we use randomization
        Collections.shuffle(rooms);

        boolean roomFoundButNotBlock = false;

        for (IRoomRecipeMatch<MCRoom, ResourceLocation, BlockPos, ?> match : rooms) {
            for (Map.Entry<BlockPos, ?> blocks : match.getContainedBlocks().entrySet()
            ) {
                BlockPos blockPos = blocks.getKey();
                @Nullable Integer blockState = JobBlock.getState(work, blockPos);
                if (blockState == null) {
                    blockState = 0;
                }
                if (!isJobBlock.test(blockPos)) {
                    roomFoundButNotBlock = true;
                    continue;
                }

                Supplier<BlockPos> is = () -> findInteractionSpot(
                        blockPos,
                        match.getRoom(),
                        isValidWalkTarget,
                        () -> Direction.getRandom(rand)
                );

                if (maxState.equals(blockState)) {
                    return new WithReason<>(is.get(), "Found extractable product");
                }
                boolean shouldGo = statusItems.getOrDefault(blockState, false);
                if (shouldGo) {
                    return new WithReason<>(is.get(), "Found a spot where a held item can be used");
                }
            }
        }

        if (roomFoundButNotBlock) {
            return new WithReason<>(null, "Job site found, but no usable job blocks");
        }

        return new WithReason<>(null, "No job sites");
    }

    @Override
    public RoomsNeedingIngredientsOrTools<MCRoom, ResourceLocation, BlockPos> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, State> work,
            Predicate<BlockPos> canClaim
    ) {
        // TODO: Reduce duplication with MCTownStateWorldInteraction.hasSupplies
        HashMap<Integer, List<RoomRecipeMatch<MCRoom>>> b = new HashMap<>();
        checks.getAllRequiredIngredients().forEach((state, ingrs) -> {
            if (ingrs.isEmpty()) {
                b.put(state, new ArrayList<>());
                return;
            }
            Collection<RoomRecipeMatch<MCRoom>> matches = roomsWithState(town, work, state);
            Integer stateQty = checks.getQuantityForStep(state, null);
            b.put(state, getRoomsWhereWorkCanBeDone(work, canClaim, matches, stateQty));
        });
        HashMap<Integer, IPredicateCollection<?>> stateTools = new HashMap<>();
        if (checks.getAllRequiredTools().values()
                  .stream()
                  .anyMatch(v -> !v.isEmpty())) {
            for (int i = 0; i < maxState; i++) {
                stateTools.put(
                        i,
                        checks.getAllRequiredTools().getOrDefault(i, PredicateCollection.empty("no tool defined"))
                );
            }
        }
        stateTools.keySet().forEach((state) -> {
            if (!b.containsKey(state)) {
                b.put(state, new ArrayList<>());
            }
            // Hold on to tools that are required at this state and any previous states
            for (int i = 0; i <= state; i++) {
                Collection<RoomRecipeMatch<MCRoom>> list = roomsWithState(town, work, i);
                b.get(state).addAll(list);
            }
        });
        return new RoomsNeedingIngredientsOrTools<>(ImmutableMap.copyOf(b));
    }

    private static @NotNull ArrayList<RoomRecipeMatch<MCRoom>> getRoomsWhereWorkCanBeDone(
            Function<BlockPos, State> work,
            Predicate<BlockPos> canClaim,
            Collection<RoomRecipeMatch<MCRoom>> matches,
            Integer stateQty
    ) {
        Stream<RoomRecipeMatch<MCRoom>> roomz = matches
                .stream()
                .filter(room -> {
                    for (Map.Entry<BlockPos, ?> e : room.getContainedBlocks().entrySet()) {
                        State jobBlockState = work.apply(e.getKey());
                        if (jobBlockState == null) {
                            continue;
                        }
                        if (!canClaim.test(e.getKey())) {
                            continue;
                        }
                        if (jobBlockState.ingredientCount() < stateQty) {
                            return true;
                        }
                    }
                    return false;
                });
        ArrayList<RoomRecipeMatch<MCRoom>> value = Lists.newArrayList(roomz.toList());
        return value;
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
    public Function<Void, Void> addJobCompletionListener(Runnable listener) {
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

    public int getMaxState() {
        return maxState;
    }

    public WorkLocation location() {
        return location;
    }

    public DeclarativeJobChecks<MCExtra, MCHeldItem, MCTownItem, RoomRecipeMatch<MCRoom>, BlockPos> getChecks() {
        return checks;
    }
}
