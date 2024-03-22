package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.core.Config;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.MCExtra;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.declarative.RealtimeWorldInteraction;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.production.AbstractSupplyGetter;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    private final ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<Integer, Ingredient> toolsRequiredAtStates;
    private final ImmutableMap<Integer, Integer> workRequiredAtStates;

    private static final ImmutableList<MCTownItem> allowedToPickUp = ImmutableList.of(
            MCTownItem.fromMCItemStack(Items.COAL.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.IRON_ORE.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.RAW_IRON.getDefaultInstance())
    );

    private static final Marker marker = MarkerManager.getMarker("DJob");
    private final RealtimeWorldInteraction world;
    private final ResourceLocation workRoomId;
    private final @NotNull Integer maxState;
    private final JobID jobId;
    private final ExpirationRules expiration;
    private Signals signal;
    private @Nullable WorkSpot<Integer, BlockPos> workSpot;

    private final AbstractSupplyGetter<ProductionStatus, BlockPos, MCTownItem, MCHeldItem, MCRoom> getter = new AbstractSupplyGetter<>();
    private boolean wrappingUp;
    private int noSuppliesTicks;
    private int ticksSinceStart;
    private boolean grabbingInsertedSupplies;
    private boolean grabbedInsertedSupplies;

    public DeclarativeJob(
            UUID ownerUUID,
            int inventoryCapacity,
            @NotNull JobID jobId,
            ResourceLocation workRoomId,
            int maxState,
            int workInterval,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            ImmutableMap<ProductionStatus, String> specialStatusRules,
            ImmutableList<String> specialGlobalRules,
            ExpirationRules expiration,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            @Nullable ResourceLocation sound
    ) {
        super(
                ownerUUID, inventoryCapacity, allowedToPickUp, buildRecipe(
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
                }
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
        this.workRoomId = workRoomId;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientsQtyRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
        this.workRequiredAtStates = workRequiredAtStates;
        this.expiration = expiration;
    }

    @NotNull
    private static Claim makeClaim(UUID ownerUUID) {
        return new Claim(ownerUUID, Config.BLOCK_CLAIMS_TICK_LIMIT.get());
    }

    @Override
    public JobID getId() {
        return jobId;
    }

    @Override
    public boolean shouldStandStill() {
        return this.workSpot != null;
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
            Function<MCExtra, Claim> claimSpots,
            int interval,
            @Nullable ResourceLocation sound
    ) {
        return new RealtimeWorldInteraction(
                journal,
                maxState,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                toolsRequiredAtStates,
                resultGenerator,
                claimSpots,
                interval,
                sound
        );
    }

    private static RecipeProvider buildRecipe(
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
            Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        VisitorMobEntity vmEntity = (VisitorMobEntity) entity;
        if (this.grabbingInsertedSupplies) {
            if (world.tryGrabbingInsertedSupplies(town, work, vmEntity, workSpot)) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            if (!hasAnyLootToDrop()) {
                town.getVillagerHandle()
                    .changeJobForVisitor(ownerUUID, WorkSeekerJob.getIDForRoot(jobId), false);
                return;
            }
            return;
        }

        this.ticksSinceStart++;
        if (workSpot == null && this.ticksSinceStart > this.expiration.maxTicks()) {
            JobID apply = expiration.maxTicksFallbackFn()
                                    .apply(jobId);
            QT.JOB_LOGGER.debug("Reached max ticks for {}. Falling back to {}.", jobId, apply);
            town.changeJobForVisitor(ownerUUID, apply);
            return;
        }
        this.workSpot = null;
        this.signal = Signals.fromGameTime(town.getServerLevel()
                                               .getDayTime());
        JobTownProvider<MCRoom> jtp = new JobTownProvider<>() {
            private final Function<BlockPos, AbstractWorkStatusStore.State> getJobBlockState = work::getJobBlockState;

            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return Jobs.roomsWithState(
                                   town, workRoomId,
                                   (sl, bp) -> maxState.equals(JobBlock.getState(getJobBlockState, bp))
                           )
                           .stream()
                           .map(v -> v.room)
                           .toList();
            }

            @Override
            public Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsByState() {
                return roomsNeedingIngredientsOrTools;
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return Jobs.isUnfinishedTimeWorkPresent(
                        town.getRoomHandle(), workRoomId,
                        work::getTimeToNextState
                );
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> town.getRoomHandle()
                                  .getRoomsMatching(workRoomId)
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
        BlockPos entityBlockPos = entity.blockPosition();
        RoomRecipeMatch<MCRoom> entityCurrentJobSite = Jobs.getEntityCurrentJobSite(town, workRoomId, entityBlockPos);
        EntityLocStateProvider<MCRoom> elp = new EntityLocStateProvider<>() {
            @Override
            public @Nullable MCRoom getEntityCurrentJobSite() {
                if (entityCurrentJobSite == null) {
                    return null;
                }
                return entityCurrentJobSite.room;
            }
        };
        boolean prioritizeExtraction = this.specialGlobalRules.contains(SpecialRules.PRIORITIZE_EXTRACTION);
        this.journal.tick(jtp, elp, super.defaultEntityInvProvider(), statusFactory, prioritizeExtraction);

        if (ProductionStatus.NO_SUPPLIES.equals(this.journal.getStatus())) {
            noSuppliesTicks++;
        } else {
            noSuppliesTicks = 0;
        }

        if (noSuppliesTicks > expiration.maxTicksWithoutSupplies()) {
            seekFallbackWork(town);
            return;
        }

        if (wrappingUp && !hasAnyLootToDrop()) {
            town.getVillagerHandle()
                .changeJobForVisitor(ownerUUID, WorkSeekerJob.getIDForRoot(jobId), false);
            return;
        }

        if (entityCurrentJobSite != null) {
            tryWorking(town, work, vmEntity, entityCurrentJobSite);
        }
        tryDropLoot(entityBlockPos);
        if (!wrappingUp) {
            tryGetSupplies(roomsNeedingIngredientsOrTools, entityBlockPos);
        }
    }

    private void seekFallbackWork(TownInterface town) {
        town.changeJobForVisitor(
                ownerUUID, expiration.noSuppliesFallbackFn()
                                     .apply(jobId));
    }

    private void tryGetSupplies(
            Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools,
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

    private void tryWorking(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            VisitorMobEntity entity,
            @NotNull RoomRecipeMatch<MCRoom> entityCurrentJobSite
    ) {
        ServerLevel sl = town.getServerLevel();
        Map<Integer, Collection<WorkSpot<Integer, BlockPos>>> workSpots = listAllWorkSpots(
                work::getJobBlockState, entityCurrentJobSite.room,
                sl::isEmptyBlock,
                () -> Direction.getRandom(sl.random)
        );

        ProductionStatus status = getStatus();
        if (status == null || status.isUnset() || !status.isWorkingOnProduction()) {
            return;
        }
        Collection<WorkSpot<Integer, BlockPos>> allSpots = workSpots.get(maxState);

        if (status.isExtractingProduct()) {
            allSpots = workSpots.get(maxState);
        }

        if (allSpots == null) {
            Collection<WorkSpot<Integer, BlockPos>> workSpot1 = workSpots.get(status.getProductionState());
            if (workSpot1 == null) {
                QT.JOB_LOGGER.error(
                        "Worker somehow has different status than all existing work spots. This is probably a bug.");
                return;
            }
            allSpots = workSpot1;
        }

        if (allSpots.isEmpty()) {
            return;
        }

        // TODO: Pass in the previous workspot and keep working it, if it's sill workable
        WorkOutput<Boolean, WorkSpot<Integer, BlockPos>> worked = this.world.tryWorking(
                town, work, entity, allSpots
        );
        this.workSpot = worked.spot();
        if (worked.town() != null && worked.town()) {
            boolean hasWork = !WorkSeekerJob.isSeekingWork(jobId);
            boolean finishedWork = worked.spot()
                                         .action()
                                         .equals(maxState); // TODO: Check all workspots before seeking workRequired
            if (hasWork && finishedWork) {
                if (!wrappingUp) {
                    town.getKnowledgeHandle()
                        .registerFoundLoots(journal.getItems()); // TODO: Is this okay for every job to do?
                }
                wrappingUp = true;
            }
        }
    }

    Map<Integer, Collection<WorkSpot<Integer, BlockPos>>> listAllWorkSpots(
            Function<BlockPos, AbstractWorkStatusStore.State> town,
            @Nullable MCRoom jobSite,
            Predicate<BlockPos> isEmpty,
            Supplier<Direction> randomDirection
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        Function<BlockPos, BlockPos> is = bp -> findInteractionSpot(bp, jobSite, isEmpty, randomDirection);

        Map<Integer, List<WorkSpot<Integer, BlockPos>>> b = new HashMap<>();
        jobSite.getSpaces()
               .stream()
               .flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space)
                                                .stream())
               .forEach(v -> {
                   tryAddSpot(town, Positions.ToBlock(v, jobSite.yCoord), b, is);
                   // TODO: Depend on job and/or villager?
                   //  E.g. a farmer probably needs to consider yCoord and yCoord MINUS 1 (dirt)
                   //  E.g. Maybe villagers can only use blocks on the ground until they unlock a perk?
                   tryAddSpot(town, Positions.ToBlock(v, jobSite.yCoord + 1), b, is);
               });
        return ImmutableMap.copyOf(b);
    }

    private static void tryAddSpot(
            Function<BlockPos, AbstractWorkStatusStore.State> town,
            BlockPos bp,
            Map<Integer, List<WorkSpot<Integer, BlockPos>>> b,
            Function<BlockPos, BlockPos> is
    ) {
        @Nullable Integer blockAction = JobBlock.getState(town, bp);
        List<WorkSpot<Integer, BlockPos>> curSpots = b.get(blockAction);
        if (blockAction != null) {
            if (curSpots == null) {
                curSpots = new ArrayList<>();
            }
            curSpots.add(new WorkSpot<>(bp, blockAction, 0, is.apply(bp)));
            b.put(blockAction, curSpots);
        }
    }

    private BlockPos findInteractionSpot(
            BlockPos bp,
            Room jobSite,
            Predicate<BlockPos> isEmpty,
            Supplier<Direction> random
    ) {
        @Nullable BlockPos spot;

        if (specialGlobalRules.contains(SpecialRules.PREFER_INTERACTION_BELOW)) {
            spot = doFindInteractionSpot(bp.below(), jobSite, isEmpty);
            if (spot != null) {
                return spot;
            }
        }

        spot = doFindInteractionSpot(bp, jobSite, isEmpty);
        if (spot != null) {
            return spot;
        }

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
    protected BlockPos findProductionSpot(ServerLevel sl) {
        if (workSpot != null) {
            return workSpot.interactionSpot();
        }
        return null;
    }

    @Override
    protected BlockPos findJobSite(
            RoomsHolder town,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> isEmpty,
            Random rand
    ) {
        // TODO: Use tags to support more tiers of work rooms
        List<RoomRecipeMatch<MCRoom>> rooms = new ArrayList<>(town.getRoomsMatching(workRoomId));

        Map<Integer, Boolean> statusItems = getSupplyItemStatus();

        // TODO: Sort by distance and choose the closest (maybe also coordinate
        //  with other workers who need the same type of job site)
        // For now, we use randomization
        Collections.shuffle(rooms);

        for (RoomRecipeMatch<MCRoom> match : rooms) {
            for (Map.Entry<BlockPos, Block> blocks : match.getContainedBlocks()
                                                          .entrySet()) {
                BlockPos blockPos = blocks.getKey();
                @Nullable Integer blockState = JobBlock.getState(work, blockPos);
                if (blockState == null) {
                    continue;
                }
                if (maxState.equals(blockState)) {
                    return blockPos;
                }
                boolean shouldGo = statusItems.getOrDefault(blockState, false);
                if (shouldGo) {
                    return findInteractionSpot(
                            blockPos, match.room, isEmpty,
                            () -> Direction.getRandom(rand)
                    );
                }
            }
        }

        return null;
    }

    @Override
    protected Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> canClaim
    ) {
        // TODO: Reduce duplication with MCTownStateWorldInteraction.hasSupplies
        HashMap<Integer, List<MCRoom>> b = new HashMap<>();
        ingredientsRequiredAtStates.forEach((state, ingrs) -> {
            if (ingrs.isEmpty()) {
                b.put(state, new ArrayList<>());
                return;
            }
            Collection<RoomRecipeMatch<MCRoom>> matches = Jobs.roomsWithState(
                    town, workRoomId,
                    (sl, bp) -> state.equals(JobBlock.getState(work, bp))
            );
            List<MCRoom> roomz = matches.stream()
                                        .filter(room -> {
                                            for (Map.Entry<BlockPos, Block> e : room.getContainedBlocks()
                                                                                    .entrySet()) {
                                                AbstractWorkStatusStore.State jobBlockState = work.apply(e.getKey());
                                                if (jobBlockState == null) {
                                                    continue;
                                                }
                                                if (!canClaim.test(e.getKey())) {
                                                    continue;
                                                }
                                                if (jobBlockState.ingredientCount() < ingredientQtyRequiredAtStates.get(
                                                        state)) {
                                                    return true;
                                                }
                                            }
                                            return false;
                                        })
                                        .map(v -> v.room)
                                        .toList();
            b.put(state, Lists.newArrayList(roomz));
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
                b.get(state)
                 .addAll((Jobs.roomsWithState(
                                      town, workRoomId,
                                      (sl, bp) -> ii.equals(JobBlock.getState(work, bp))
                              )
                              .stream()
                              .map(v -> v.room)
                              .toList()));
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
}
