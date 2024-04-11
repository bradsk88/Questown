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
import ca.bradj.questown.jobs.production.ControlledCache;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.logic.Emptyable;
import ca.bradj.questown.logic.TownContainerChecks;
import ca.bradj.questown.logic.TownNeeds;
import ca.bradj.questown.mc.MCRoomWithBlocks;
import ca.bradj.questown.mc.Util;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ContainerRoomFinder;
import ca.bradj.questown.town.interfaces.RoomsHolder;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
import ca.bradj.roomrecipes.serialization.MCRoom;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

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
        public ProductionStatus noJobSite() {
            return ProductionStatus.FACTORY.noJobSite();
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
    private final ImmutableMap<ProductionStatus, Ingredient> ingredientsRequiredAtStates;
    private final ImmutableMap<ProductionStatus, Integer> ingredientQtyRequiredAtStates;
    private final ImmutableMap<ProductionStatus, Ingredient> toolsRequiredAtStates;
    private final ImmutableMap<ProductionStatus, Integer> workRequiredAtStates;

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
    private final long totalDuration;
    private Signals signal;
    private @Nullable WorkSpot<Integer, BlockPos> workSpot;

    private final AbstractSupplyGetter<ProductionStatus, BlockPos, MCTownItem, MCHeldItem, MCRoom> getter = new AbstractSupplyGetter<>();
    private boolean wrappingUp;
    private int noSuppliesTicks;
    private int ticksSinceStart;
    private boolean grabbingInsertedSupplies;
    private boolean grabbedInsertedSupplies;
    private ImmutableMap<ProductionStatus, Predicate<MCHeldItem>> statesWhereSpecialRulesApply = ImmutableMap.of();
    private final ImmutableList<ProductionStatus> statesPriority;

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
            ImmutableList<Integer> statesPriority,
            ImmutableMap<Integer, ? extends Collection<String>> specialStatusRules,
            ImmutableList<String> specialGlobalRules,
            ExpirationRules expiration,
            BiFunction<ServerLevel, Collection<MCHeldItem>, Iterable<MCHeldItem>> resultGenerator,
            @Nullable ResourceLocation sound
    ) {
        super(ownerUUID, inventoryCapacity, allowedToPickUp,
                buildRecipe(ingredientsRequiredAtStates, toolsRequiredAtStates), marker,
                (capacity, signalSource) -> new ProductionJournal<>(jobId, signalSource, capacity, MCHeldItem::Air,
                        STATUS_FACTORY
                ), STATUS_FACTORY, ProductionStatus.mapUnsafe(specialStatusRules), specialGlobalRules, () -> {
                    if (specialGlobalRules.contains(SpecialRules.CLAIM_SPOT)) {
                        return makeClaim(ownerUUID);
                    }
                    return null;
                }
        );
        this.jobId = jobId;
        this.world = initWorldInteraction(maxState, ingredientsRequiredAtStates, ingredientsQtyRequiredAtStates,
                toolsRequiredAtStates, workRequiredAtStates, timeRequiredAtStates, resultGenerator, extra -> {
                    if (specialGlobalRules.contains(SpecialRules.CLAIM_SPOT)) {
                        return makeClaim(ownerUUID);
                    }
                    return null;
                }, workInterval, sound
        );
        this.maxState = maxState;
        this.workRoomId = workRoomId;
        this.ingredientsRequiredAtStates = ProductionStatus.mapUnsafe(ingredientsRequiredAtStates);
        this.ingredientQtyRequiredAtStates = ProductionStatus.mapUnsafe(ingredientsQtyRequiredAtStates);
        this.toolsRequiredAtStates = ProductionStatus.mapUnsafe(toolsRequiredAtStates);
        this.workRequiredAtStates = ProductionStatus.mapUnsafe(workRequiredAtStates);
        this.statesPriority = ProductionStatus.list(statesPriority);
        this.expiration = expiration;
        this.totalDuration = timeRequiredAtStates.values().stream().reduce(Integer::sum).orElse(0);
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
        return new RealtimeWorldInteraction(jobId, asItemsHolder(), maxState, ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates, workRequiredAtStates, timeRequiredAtStates, toolsRequiredAtStates,
                resultGenerator, claimSpots, interval, sound
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
            ControlledCache<Map<ProductionStatus, Collection<MCRoom>>> roomsNeedingIngredientsOrTools,
            IProductionStatusFactory<ProductionStatus> statusFactory
    ) {
        ImmutableMap.Builder<ProductionStatus, Predicate<MCHeldItem>> b = ImmutableMap.builder();
        for (int i = 0; i < maxState; i++) {
            b.put(
                    ProductionStatus.fromJobBlockStatus(i),
                    item -> Works.isWorkResult(town.getTownData(), item.toItem())
            );
        }
        this.statesWhereSpecialRulesApply = b.build();

        VisitorMobEntity vmEntity = (VisitorMobEntity) entity;
        if (this.grabbingInsertedSupplies) {
            if (world.tryGrabbingInsertedSupplies(town, work, vmEntity)) {
                this.grabbingInsertedSupplies = false;
                this.grabbedInsertedSupplies = true;
            }
            return;
        }

        if (this.grabbedInsertedSupplies) {
            seekFallbackWork(town);
            return;
        }

        this.ticksSinceStart++;
        if (workSpot == null && this.ticksSinceStart > this.expiration.maxTicks()) {
            JobID apply = expiration.maxTicksFallbackFn().apply(jobId);
            QT.JOB_LOGGER.debug("Reached max ticks for {}. Falling back to {}.", jobId, apply);
            town.changeJobForVisitor(ownerUUID, apply);
            return;
        }
        this.workSpot = null;
        this.signal = Signals.fromDayTime(Util.getDayTime(town.getServerLevel()));
        JobTownProvider<MCRoom, ProductionStatus> jtp = new JobTownProvider<>() {
            private final Function<BlockPos, AbstractWorkStatusStore.State> getJobBlockState = work::getJobBlockState;

            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return Jobs.roomsWithState(town, workRoomId,
                        (sl, bp) -> maxState.equals(JobBlock.getState(getJobBlockState, bp))
                ).stream().map(v -> v.room).toList();
            }

            @Override
            public Map<ProductionStatus, Collection<MCRoom>> roomsToGetSuppliesForByState() {
                return roomsNeedingIngredientsOrTools.get(town.getDebugHandle().isCacheEnabled());
            }

            @Override
            public boolean isUnfinishedTimeWorkPresent() {
                return Jobs.isUnfinishedTimeWorkPresent(town.getRoomHandle(), workRoomId, work::getTimeToNextState);
            }

            @Override
            public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
                Collection<Integer> statesWithUnfinishedWork = Jobs.getStatesWithUnfinishedWork(
                        () -> town.getRoomHandle().getRoomsMatching(workRoomId).stream()
                                  .map(v -> (Supplier<Collection<BlockPos>>) () -> v.getContainedBlocks().keySet())
                                  .toList(), getJobBlockState, (bp) -> work.canClaim(bp, () -> makeClaim(ownerUUID)));
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
                return TownContainerChecks.<MCTownItem, ProductionStatus>hasSupplies(
                        () -> roomsNeedingIngredientsOrTools.get(town.getDebugHandle().isCacheEnabled()).keySet(),
                        Jobs.unMC2(toolsRequiredAtStates),
                        ingredientQtyRequiredAtStates,
                        Jobs.unMC3(toolsRequiredAtStates),
                        town::getItemMatches,
                        lvl -> specialRules.apply(lvl),
                        item -> Works.isWorkResult(town.getTownData(), item)
                );
            }

            @Override
            public boolean hasSpace() {
                return Jobs.townHasSpace(town);
            }

            @Override
            public boolean isCachingAllowed() {
                return town.getDebugHandle().isCacheEnabled();
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
        this.journal.tick(
                jtp,
                elp,
                super.defaultEntityInvProvider(town.getTownData()),
                statusFactory,
                prioritizeExtraction,
                statesPriority
        );

        if (ProductionStatus.NO_SUPPLIES.equals(computeStatus())) {
            if (noSuppliesTicks == 0) {
                QT.JOB_LOGGER.debug(
                        "No supplies. Villager will give up work in {} ticks",
                        expiration.maxTicksWithoutSupplies()
                );
            }
            noSuppliesTicks++;
        } else {
            noSuppliesTicks = 0;
        }

        if (noSuppliesTicks > expiration.maxTicksWithoutSupplies()) {
            this.grabbingInsertedSupplies = true;
            return;
        }

        if (wrappingUp && !hasAnyLootToDrop()) {
            town.getVillagerHandle().changeJobForVisitor(ownerUUID, WorkSeekerJob.getIDForRoot(jobId), false);
            return;
        }

        if (entityCurrentJobSite != null) {
            tryWorking(town, work, vmEntity, entityCurrentJobSite);
        }
        tryDropLoot(entityBlockPos);
        if (!wrappingUp) {
            boolean cacheEnabled = town.getDebugHandle().isCacheEnabled();
            tryGetSupplies(town, roomsNeedingIngredientsOrTools.get(cacheEnabled), entityBlockPos);
        }
    }

    @Override
    protected ImmutableList<ProductionStatus> sortByPriority(Collection<ProductionStatus> states) {
        ImmutableList.Builder<ProductionStatus> b = ImmutableList.builder();
        statesPriority.forEach(v -> {
            if (states.contains(v)) {
                b.add(v);
            }
        });
        return b.build();
    }

    private void seekFallbackWork(TownInterface town) {
        town.changeJobForVisitor(ownerUUID, expiration.noSuppliesFallbackFn().apply(jobId));
    }

    private void tryGetSupplies(
            TownInterface town,
            Map<ProductionStatus, Collection<MCRoom>> roomsNeedingIngredientsOrTools,
            BlockPos entityBlockPos
    ) {
        if (suppliesTarget == null) {
            return;
        }
        JobsClean.SuppliesTarget<BlockPos, MCTownItem> st = new JobsClean.SuppliesTarget<>() {
            @Override
            public boolean isCloseTo() {
                return Jobs.isCloseTo(
                        entityBlockPos,
                        Positions.ToBlock(suppliesTarget.getPosition(), suppliesTarget.getYPosition())
                );
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
                suppliesTarget.getContainer().removeItem(i, quantity);
            }
        };
        WorkStatusHandle<BlockPos, MCHeldItem> ws = town.getWorkStatusHandle(ownerUUID); // TODO: Use shared status handle when appropriate
        getter.tryGetSupplies(
                computeStatus(),
                asItemsHolder().getCapacity(),
                roomsNeedingIngredientsOrTools,
                st,
                status -> ImmutableList.of(SpecialRuleIngredientAnyValidWorkOutput.apply(
                        specialRules.apply(status),
                        v -> Util.getOrDefault(ingredientsRequiredAtStates, status, Ingredient.EMPTY)
                                 .test(v.toItemStack()),
                        i -> Works.isWorkResult(town.getTownData(), i)
                )),
                asItemsHolder().getItems(),
                (item) -> asItemsHolder().addItem(MCHeldItem.fromTown(item)),
                (targetState, originalCheck) -> SpecialRuleIngredientAnyValidWorkOutput.apply(
                        specialRules.apply(ProductionStatus.fromJobBlockStatus(targetState)),
                        originalCheck,
                        i -> Works.isWorkResult(town.getTownData(), i)
                )
        );
    }

    private void tryWorking(
            TownInterface town,
            WorkStatusHandle<BlockPos, MCHeldItem> work,
            VisitorMobEntity entity,
            @NotNull RoomRecipeMatch<MCRoom> entityCurrentJobSite
    ) {
        ServerLevel sl = town.getServerLevel();
        Map<Integer, Collection<WorkSpot<Integer, BlockPos>>> workSpots = listAllWorkSpots(work::getJobBlockState,
                entityCurrentJobSite.room, sl::isEmptyBlock, sl.random
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
        WorkOutput<Boolean, WorkSpot<Integer, BlockPos>> worked = this.world.tryWorking(town, work, entity, allSpots);
        this.workSpot = worked.spot();
        if (worked.town() != null && worked.town()) {
            this.setLookTarget(worked.spot().position());
            boolean hasWork = !WorkSeekerJob.isSeekingWork(jobId);
            boolean finishedWork = worked.spot().action()
                                         .equals(maxState); // TODO: Check all workspots before seeking workRequired
            if (hasWork && finishedWork) {
                if (!wrappingUp) {
                    town.getKnowledgeHandle()
                        .registerFoundLoots(asItemsHolder().getItems()); // TODO: Is this okay for every job to do?
                }
                wrappingUp = true;
            }
        }
    }

    Map<Integer, Collection<WorkSpot<Integer, BlockPos>>> listAllWorkSpots(
            Function<BlockPos, AbstractWorkStatusStore.State> town,
            @Nullable MCRoom jobSite,
            Predicate<BlockPos> isEmpty,
            Random rand
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        Function<BlockPos, BlockPos> is = bp -> JobSites.findInteractionSpot(
                bp, jobSite, specialGlobalRules, new MCPosKit(rand, isEmpty));

        Map<Integer, List<WorkSpot<Integer, BlockPos>>> b = new HashMap<>();
        jobSite.getSpaces().stream().flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space).stream())
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
        return computeStatus().nameV2();
    }

    @Override
    protected Map<ProductionStatus, Boolean> getSupplyItemStatus() {
        ImmutableMap.Builder<ProductionStatus, Predicate<MCHeldItem>> b = ImmutableMap.builder();
        ImmutableMap<ProductionStatus, Predicate<MCHeldItem>> ingrs = Jobs.unMCHeld2(ingredientsRequiredAtStates);
        statesWhereSpecialRulesApply.forEach((k, v) -> // FIXME: Test this
                b.put(k, item -> v.test(item) || Util.getOrDefault(ingrs, k, (z) -> false).test(item)));

        return JobsClean.getSupplyItemStatuses(
                asItemsHolder()::getItems,
                b.build(),
                Jobs.unMCHeld2(toolsRequiredAtStates)
        );
    }

    @Override
    protected @Nullable WorkSpot<?, BlockPos> findProductionSpot(ServerLevel sl) {
        return workSpot; // FIXME: Null for organizer
    }

    @Override
    protected BlockPos findJobSite(
            RoomsHolder town,
            Supplier<? extends Collection<MCRoomWithBlocks>> roomsWhereSpecialRulesApply,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> isEmpty,
            Random rand
    ) {
        return JobSites.find(
                jobRooms(town, roomsWhereSpecialRulesApply),
                match -> match.containedBlocks.entrySet(),
                match -> match.room,
                work,
                ProductionStatus.unmap(getSupplyItemStatus()),
                maxState,
                specialGlobalRules,
                new MCPosKit(rand, isEmpty)
        );
    }

    private Supplier<Collection<MCRoomWithBlocks>> jobRooms(
            RoomsHolder town,
            Supplier<? extends Collection<MCRoomWithBlocks>> roomsWhereSpecialRulesApply
    ) {
        return () -> {
            Collection<MCRoomWithBlocks> sr = roomsWhereSpecialRulesApply.get();
            if (sr.isEmpty()) {
                return MCRoomWithBlocks.fromMatches(town.getRoomsMatching(workRoomId));
            }
            return sr;
        };
    }

    @Override
    protected Map<ProductionStatus, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
            TownInterface town,
            Function<BlockPos, AbstractWorkStatusStore.State> work,
            Predicate<BlockPos> canClaim
    ) {
        return TownNeeds.getRoomsNeedingIngredientsOrTools(
                asEmptyable(ingredientsRequiredAtStates),
                ingredientQtyRequiredAtStates,
                asEmptyable(toolsRequiredAtStates),
                i -> JobsClean.roomsWithState(
                        town.getRoomHandle().getRoomsMatching(workRoomId),
                        (bp) -> i.equals(JobBlock.getState(work, bp))
                ),
                getRoomsWhereSpecialRulesApply(town.getRoomHandle(), town.getTownData()),
                work,
                canClaim,
                maxState
        );
    }

    private static <K> ImmutableMap<K, Emptyable> asEmptyable(Map<K, Ingredient> ingredientsRequiredAtStates) {
        ImmutableMap.Builder<K, Emptyable> builder = ImmutableMap.builder();
        ingredientsRequiredAtStates.forEach((k, v) -> builder.put(k, v::isEmpty));
        return builder.build();
    }

    @Override
    protected Map<ProductionStatus, ImmutableList<MCRoom>> getRoomsWhereSpecialRulesApply(
            ContainerRoomFinder<MCRoom, MCTownItem> rooms,
            WorksBehaviour.TownData townData
    ) {
        Supplier<List<MCRoom>> resultRooms = () -> rooms.getRoomsWithContainersOfItem(
                i -> Works.isWorkResult(townData, i));
        return JobRequirements.roomsWhereSpecialRulesApply(
                maxState, specialRules, resultRooms
        );
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

    @Override
    public ImmutableList<String> getSpecialRules(int state) {
        return ImmutableList.copyOf(
                specialRules.apply(ProductionStatus.fromJobBlockStatus(state))
        );
    }
}
