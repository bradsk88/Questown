package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.*;
import ca.bradj.questown.jobs.declarative.nomc.WorkSeekerJob;
import ca.bradj.questown.jobs.production.AbstractSupplyGetter;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

// TODO: Break ties to MC and unit test - Maybe reuse code from ProductionTimeWarper
public class DeclarativeJob extends
        DeclarativeProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> {

    public static final IProductionStatusFactory<ProductionStatus> STATUS_FACTORY = new IProductionStatusFactory<>() {
        @Override
        public ProductionStatus fromJobBlockState(int s) {
            return ProductionStatus.fromJobBlockStatus(s);
        }

        @Override
        public ProductionStatus waitingForTimedState() {
            return ProductionStatus.FACTORY.waitingForTimedState();
        }

        @Override
        public ProductionStatus droppingLoot() {
            return ProductionStatus.FACTORY.droppingLoot();
        }

        @Override
        public ProductionStatus noSpace() {
            return ProductionStatus.FACTORY.noSpace();
        }

        @Override
        public ProductionStatus goingToJobSite() {
            return ProductionStatus.FACTORY.goingToJobSite();
        }

        @Override
        public ProductionStatus noJobSite() {
            return ProductionStatus.FACTORY.noJobSite();
        }

        @Override
        public ProductionStatus noSupplies() {
            return ProductionStatus.FACTORY.noSupplies();
        }

        @Override
        public ProductionStatus collectingSupplies() {
            return ProductionStatus.FACTORY.collectingSupplies();
        }

        @Override
        public ProductionStatus idle() {
            return ProductionStatus.FACTORY.idle();
        }

        @Override
        public ProductionStatus extractingProduct() {
            return ProductionStatus.FACTORY.extractingProduct();
        }

        @Override
        public ProductionStatus relaxing() {
            return ProductionStatus.FACTORY.relaxing();
        }
    };
    public final ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    public final ImmutableMap<Integer, Ingredient> toolsRequiredAtStates;
    public final ImmutableMap<Integer, Integer> workRequiredAtStates;

    private static final Marker marker = MarkerManager.getMarker("DJob");
    private final RealtimeWorldInteraction world;

    private final JobLogic<MCExtra, Boolean, BlockPos> logic;
    private final WorkLocation location;
    private final @NotNull Integer maxState;
    private final JobID jobId;
    private final ExpirationRules expiration;
    private final long totalDuration;
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
                ownerUUID, inventoryCapacity, buildRecipe(
                        ingredientsRequiredAtStates, toolsRequiredAtStates
                ), marker,
                (capacity, signalSource) -> new ProductionJournal<>(
                        jobId,
                        signalSource,
                        capacity,
                        MCHeldItem::Air,
                        STATUS_FACTORY
                ),
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
        this.world = initWorldInteraction(
                maxState,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                toolsRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
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
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientsQtyRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.expiration = expiration;
        this.totalDuration = timeRequiredAtStates.values().stream().reduce(Integer::sum).orElse(0);
        this.logic = new JobLogic();
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
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            Map<ProductionStatus, Collection<String>> specialRules,
            Function<MCExtra, Claim> claimSpots,
            int interval,
            @Nullable SoundInfo sound
    ) {
        return new RealtimeWorldInteraction(
                journal,
                maxState,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                toolsRequiredAtStates,
                specialRules,
                resultGenerator,
                claimSpots,
                interval,
                sound
        );
    }

    public static RecipeProvider buildRecipe(
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates
    ) {
        return s -> {
            ImmutableList.Builder<JobsClean.TestFn<MCTownItem>> bb = ImmutableList.builder();
            Ingredient ingr = ingredientsRequiredAtStates.get(s);
            if (ingr != null) {
                bb.add(item -> ingr.test(item.toItemStack()));
            }
            // Hold on to tools required for this state and all previous states
            for (int i = 0; i <= s; i++) {
                Ingredient tool = toolsRequiredAtStates.get(i);
                if (tool != null) {
                    bb.add(item -> {
                        ItemStack itemStack = item.toItemStack();
                        return tool.test(itemStack);
                    });
                }
            }
            return bb.build();
        };
    }

    @Override
    protected void tick(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            LivingEntity entity,
            Direction facingPos,
            // Change this to a supplier whose value is cached for one tick
            Supplier<Map<Integer, Collection<MCRoom>>> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        MCExtra extra = new MCExtra(town, work, (VisitorMobEntity) entity);

        RoomRecipeMatch<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(
                town,
                location.baseRoom(),
                entity.blockPosition()
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

        JobTownProvider<MCRoom> jtp = makeTownProvider(town, work, roomsNeedingIngredientsOrTools);

        Supplier<ProductionStatus> computeState = getStateComputer(
                statusFactory,
                jtp,
                elp
        );
        this.signal = Signals.fromDayTime(Util.getDayTime(town.getServerLevel()));
        WorkPosition<BlockPos> workSpot = world.getWorkSpot();
        BlockPos bp = Util.orNull(workSpot, WorkPosition::jobBlock);
        int action = bp == null ? 0 : Util.withNullFallback(work.getJobBlockState(bp), State::processingState, 0);
        logic.tick(
                extra,
                computeState,
                jobId,
                entityCurrentJobSite != null,
                WorkSeekerJob.isSeekingWork(jobId),
                workSpot != null && hasInserted(action),
                expiration,
                maxState,
                this.asLogicWorld(
                        extra, town, work,
                        (VisitorMobEntity) entity, entityCurrentJobSite,
                        roomsNeedingIngredientsOrTools
                ),
                (tuwn, bpp) -> Util.withNullFallback(getWorkStatusHandle(town).getJobBlockState(bpp), State::processingState, 0)
        );
    }

    private @NotNull JobTownProvider<MCRoom> makeTownProvider(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            Supplier<Map<Integer, Collection<MCRoom>>> roomsNeedingIngredientsOrTools
    ) {
        return new JobTownProvider<>() {
            private final Function<BlockPos, State> getJobBlockState = work::getJobBlockState;

            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return roomsWithState(town, getJobBlockState, maxState)
                        .stream()
                        .map(v -> v.room)
                        .toList();
            }

            @Override
            public Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsByState() {
                return roomsNeedingIngredientsOrTools.get();
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return Jobs.isUnfinishedTimeWorkPresent(
                        town.getRoomHandle(), location.baseRoom(),
                        work::getTimeToNextState
                );
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> town.getRoomHandle()
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
                    Ingredient toolsReq = toolsRequiredAtStates.get(s);
                    if (toolsReq != null && !toolsReq.isEmpty()) {
                        return;
                    }
                    b.add(s);
                });
                return b.build();
            }

            @Override
            public boolean hasSupplies() {
                Map<Integer, ? extends Collection<MCRoom>> needs = roomsNeedingIngredientsByState();
                return Jobs.townHasSupplies(town, journal, convertToCleanFns(needs));
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(town);
            }
        };
    }

    private Collection<RoomRecipeMatch<MCRoom>> roomsWithState(
            TownInterface town, Function<BlockPos, State> getJobBlockState, Integer state) {
        return Jobs.roomsWithState(
                town, location.baseRoom(),
                (sl, bp) -> location.isJobBlock().test(sl::getBlockState, bp),
                (sl, bp) -> state.equals(JobBlock.getState(getJobBlockState, bp))
        );
    }

    private boolean hasInserted(Integer action) {
        for (int i = 1; i < action; i++) {
            if (Util.getOrDefault(ingredientQtyRequiredAtStates, i - 1, 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private JobLogic.JLWorld<MCExtra, Boolean, BlockPos> asLogicWorld(
            MCExtra extra,
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            VisitorMobEntity entity,
            RoomRecipeMatch<MCRoom> entityCurrentJobSite,
            Supplier<Map<Integer, Collection<MCRoom>>> roomsNeedingIngredientsOrTools
    ) {
        DeclarativeJob self = this;
        return new JobLogic.JLWorld<>() {
            @Override
            public void changeJob(JobID id) {
                town.getVillagerHandle().changeJobForVisitor(ownerUUID, id, false);
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
                        bp -> location.isJobBlock().test(sl::getBlockState, bp),
                        () -> Direction.getRandom(sl.random)
                );
            }

            @Override
            public void clearWorkSpot(String reason) {
                world.setWorkSpot(new WithReason<>(null, reason));
            }

            @Override
            public boolean tryGrabbingInsertedSupplies() {
                return world.tryGrabbingInsertedSupplies(extra);
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
            public void tryDropLoot() {
                self.tryDropLoot(entity.blockPosition());
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
                extra.town().getVillagerHandle().changeJobForVisitor(ownerUUID, id, false);
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
            Supplier<Map<Integer, Collection<MCRoom>>> roomsNeedingIngredientsOrTools,
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
                (item) -> this.journal.addItem(MCHeldItem.fromTown(item))
        );
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
                Jobs.unMCHeld2(ingredientsRequiredAtStates),
                Jobs.unMCHeld2(toolsRequiredAtStates)
        );
    }

    @Override
    protected @Nullable WorkPosition<BlockPos> findProductionSpot(ServerLevel sl) {
        return logic.workSpot();
    }

    @Override
    protected @NotNull WithReason<@Nullable BlockPos> findJobSite(
            RoomsHolder town,
            Function<BlockPos, State> work,
            Predicate<BlockPos> isValidWalkTarget,
            Predicate<BlockPos> isJobBlock,
            Random rand
    ) {
        // TODO: Use tags to support more tiers of work rooms
        List<RoomRecipeMatch<MCRoom>> rooms = new ArrayList<>(town.getRoomsMatching(location.baseRoom()));

        Map<Integer, Boolean> statusItems = getSupplyItemStatus();

        // TODO: Sort by distance and choose the closest (maybe also coordinate
        //  with other workers who need the same type of job site)
        // For now, we use randomization
        Collections.shuffle(rooms);

        boolean roomFoundButNotBlock = false;

        for (RoomRecipeMatch<MCRoom> match : rooms) {
            for (Map.Entry<BlockPos, Block> blocks : match.containedBlocks.entrySet()
            ) {
                BlockPos blockPos = blocks.getKey();
                @Nullable Integer blockState = JobBlock.getState(work, blockPos);
                if (blockState == null) {
                    continue;
                }
                if (!isJobBlock.test(blockPos)) {
                    roomFoundButNotBlock = true;
                    continue;
                }

                Supplier<BlockPos> is = () -> findInteractionSpot(
                        blockPos,
                        match.room,
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
    public Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, State> work,
            Predicate<BlockPos> canClaim
    ) {
        // TODO: Reduce duplication with MCTownStateWorldInteraction.hasSupplies
        HashMap<Integer, List<MCRoom>> b = new HashMap<>();
        ingredientsRequiredAtStates.forEach((state, ingrs) -> {
            if (ingrs.isEmpty()) {
                b.put(state, new ArrayList<>());
                return;
            }
            Collection<RoomRecipeMatch<MCRoom>> matches = roomsWithState(town, work, state);

            Integer stateQty = ingredientQtyRequiredAtStates.get(state);
            Stream<MCRoom> roomz = matches.stream()
                    .filter(room -> {
                        for (Map.Entry<BlockPos, Block> e : room.getContainedBlocks()
                                .entrySet()) {
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
                    })
                    .map(v -> v.room);
            b.put(state, Lists.newArrayList(roomz.toList()));
        });
        HashMap<Integer, Ingredient> stateTools = new HashMap<>();
        if (toolsRequiredAtStates.values()
                .stream()
                .anyMatch(v -> !v.isEmpty())) {
            for (int i = 0; i < maxState; i++) {
                stateTools.put(i, toolsRequiredAtStates.getOrDefault(i, Ingredient.EMPTY));
            }
        }
        stateTools.forEach((state, ingrs) -> {
            if (!b.containsKey(state)) {
                b.put(state, new ArrayList<>());
            }
            // Hold on to tools that are required at this state and any previous states
            for (int i = 0; i <= state; i++) {
                final Integer ii = i;
                b.get(state).addAll(
                        roomsWithState(town, work, state)
                                .stream()
                                .map(v -> v.room)
                                .toList()
                );
            }
        });
        return ImmutableMap.copyOf(b);
    }

    @Override
    protected BlockPos findNonWorkTarget(
            BlockPos entityBlockPos,
            Vec3 entityPos,
            TownInterface town
    ) {
        return null;
    }

    @Override
    public boolean openScreen(
            ServerPlayer sp,
            VisitorMobEntity e
    ) {
        return Jobs.openInventoryAndStatusScreen(sp, e);
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
}
