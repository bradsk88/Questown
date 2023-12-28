package ca.bradj.questown.jobs;

import ca.bradj.questown.QT;
import ca.bradj.questown.blocks.JobBlock;
import ca.bradj.questown.integration.minecraft.MCHeldItem;
import ca.bradj.questown.integration.minecraft.MCTownItem;
import ca.bradj.questown.jobs.declarative.ProductionJournal;
import ca.bradj.questown.jobs.declarative.WorkSeekerJob;
import ca.bradj.questown.jobs.declarative.WorldInteraction;
import ca.bradj.questown.jobs.production.AbstractSupplyGetter;
import ca.bradj.questown.jobs.production.ProductionJob;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.mobs.visitor.VisitorMobEntity;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.TownInterface;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.roomrecipes.adapter.Positions;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.logic.InclusiveSpaces;
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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

// TODO[ASAP]: Break ties to MC and unit test
public class DeclarativeJob extends ProductionJob<ProductionStatus, SimpleSnapshot<ProductionStatus, MCHeldItem>, ProductionJournal<MCTownItem, MCHeldItem>> {

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

    private static final ImmutableList<MCTownItem> allowedToPickUp = ImmutableList.of(
            MCTownItem.fromMCItemStack(Items.COAL.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.IRON_ORE.getDefaultInstance()),
            MCTownItem.fromMCItemStack(Items.RAW_IRON.getDefaultInstance())
    );

    private static final Marker marker = MarkerManager.getMarker("DJob");
    private final WorldInteraction world;
    private final ResourceLocation workRoomId;
    private final @NotNull Integer maxState;
    private final JobID jobId;
    private Signals signal;
    private WorkSpot<Integer, BlockPos> workSpot;
    private final boolean prioritizeExtraction;

    private final AbstractSupplyGetter<ProductionStatus, BlockPos, MCTownItem, MCHeldItem, MCRoom> getter = new AbstractSupplyGetter<>();
    private boolean wrappingUp;

    public DeclarativeJob(
            UUID ownerUUID,
            int inventoryCapacity,
            @NotNull JobID jobId,
            ResourceLocation workRoomId,
            int maxState,
            boolean prioritizeExtraction,
            int workInterval,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            boolean sharedTimers,
            ImmutableMap<ProductionStatus, String> specialRules,
            BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<MCHeldItem>> workResult,
            boolean nullifyExcessProduct
    ) {
        super(
                ownerUUID, sharedTimers, inventoryCapacity, allowedToPickUp, buildRecipe(
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
                specialRules
        );
        this.jobId = jobId;
        this.prioritizeExtraction = prioritizeExtraction;
        this.world = initWorldInteraction(
                maxState,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                toolsRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                workResult,
                nullifyExcessProduct,
                workInterval
        );
        this.maxState = maxState;
        this.workRoomId = workRoomId;
        this.ingredientsRequiredAtStates = ingredientsRequiredAtStates;
        this.ingredientQtyRequiredAtStates = ingredientsQtyRequiredAtStates;
        this.toolsRequiredAtStates = toolsRequiredAtStates;
    }

    @Override
    public JobID getId() {
        return jobId;
    }

    @NotNull
    protected WorldInteraction initWorldInteraction(
            int maxState,
            ImmutableMap<Integer, Ingredient> ingredientsRequiredAtStates,
            ImmutableMap<Integer, Integer> ingredientsQtyRequiredAtStates,
            ImmutableMap<Integer, Ingredient> toolsRequiredAtStates,
            ImmutableMap<Integer, Integer> workRequiredAtStates,
            ImmutableMap<Integer, Integer> timeRequiredAtStates,
            BiFunction<ServerLevel, ProductionJournal<MCTownItem, MCHeldItem>, Iterable<MCHeldItem>> workResult,
            boolean nullifyExcessProduct,
            int interval
    ) {
        return new WorldInteraction(
                inventory,
                journal,
                maxState,
                ingredientsRequiredAtStates,
                ingredientsQtyRequiredAtStates,
                workRequiredAtStates,
                timeRequiredAtStates,
                toolsRequiredAtStates,
                workResult,
                nullifyExcessProduct,
                interval
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
                final int ii = i;
                Ingredient tool = toolsRequiredAtStates.get(ii);
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
        this.signal = Signals.fromGameTime(town.getServerLevel().getDayTime());
        JobTownProvider<MCRoom> jtp = new JobTownProvider<>() {
            @Override
            public Collection<MCRoom> roomsWithCompletedProduct() {
                return Jobs.roomsWithState(town, workRoomId, (sl, bp) -> maxState.equals(JobBlock.getState(work, bp)))
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
            public @Nullable RoomRecipeMatch<MCRoom> getEntityCurrentJobSite() {

                return entityCurrentJobSite;
            }
        };
        this.journal.tick(jtp, elp, super.defaultEntityInvProvider(), statusFactory, this.prioritizeExtraction);

        if (wrappingUp && !hasAnyLootToDrop()) {
            town.changeJobForVisitor(ownerUUID, WorkSeekerJob.getIDForRoot(jobId));
        }

        if (entityCurrentJobSite != null) {
            tryWorking(town, work, entity, entityCurrentJobSite);
        }
        tryDropLoot(entityBlockPos);
        if (!wrappingUp) {
            tryGetSupplies(roomsNeedingIngredientsOrTools, entityBlockPos);
        }
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
            public void removeItem(int i, int quantity) {
                suppliesTarget.getContainer().removeItem(i, quantity);
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
            LivingEntity entity,
            @NotNull RoomRecipeMatch<MCRoom> entityCurrentJobSite
    ) {
        Map<Integer, WorkSpot<Integer, BlockPos>> workSpots = listAllWorkspots(
                work, entityCurrentJobSite.room
        );

        ProductionStatus status = getStatus();
        if (status == null || status.isUnset() || !status.isWorkingOnProduction()) {
            return;
        }

        this.workSpot = null;

        if (status.isExtractingProduct()) {
            this.workSpot = workSpots.get(maxState);
        }

        if (workSpot == null) {
            WorkSpot<Integer, BlockPos> workSpot1 = workSpots.get(status.getProductionState());
            if (workSpot1 == null) {
                QT.JOB_LOGGER.error("Worker somehow has different status than all existing work spots. This is probably a bug.");
                return;
            }
            this.workSpot = workSpot1;
        }

        this.world.tryWorking(town, work, entity, workSpot);
        boolean hasWork = !WorkSeekerJob.isSeekingWork(jobId);
        boolean finishedWork = workSpot.action.equals(maxState); // TODO: Check all workspots before seeking work
        if (hasWork && finishedWork) {
            if (!wrappingUp) {
                town.getKnowledgeHandle()
                        .registerFoundLoots(journal.getItems()); // TODO: Is this okay for every job to do?
            }
            wrappingUp = true;
        }
    }

    Map<Integer, WorkSpot<Integer, BlockPos>> listAllWorkspots(
            WorkStateContainer<BlockPos> town,
            @Nullable MCRoom jobSite
    ) {
        if (jobSite == null) {
            return ImmutableMap.of();
        }

        Map<Integer, WorkSpot<Integer, BlockPos>> b = new HashMap<>();
        jobSite.getSpaces().stream()
                .flatMap(space -> InclusiveSpaces.getAllEnclosedPositions(space).stream())
                .forEach(v -> {
                    BlockPos bp = Positions.ToBlock(v, jobSite.yCoord);
                    @Nullable Integer blockAction = JobBlock.getState(town, bp);
                    if (blockAction != null && !b.containsKey(blockAction)) {
                        b.put(blockAction, new WorkSpot<>(bp, blockAction, 0));
                    }
                    // TODO: Depend on job and/or villager?
                    //  E.g. a farmer probably needs to consider yCoord and yCoord MINUS 1 (dirt)
                    //  E.g. Maybe villagers can only use blocks on the ground until they unlock a perk?
                    bp = Positions.ToBlock(v, jobSite.yCoord + 1);
                    blockAction = JobBlock.getState(town, bp);
                    if (blockAction != null && !b.containsKey(blockAction)) {
                        b.put(blockAction, new WorkSpot<>(bp, blockAction, 0));
                    }
                });
        return ImmutableMap.copyOf(b);
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
        return signal;
    }

    @Override
    public String getStatusToSyncToClient() {
        return journal.getStatus().name();
    }

    @Override
    protected Map<Integer, Boolean> getSupplyItemStatus() {
        HashMap<Integer, Boolean> b = new HashMap<>();
        BiConsumer<Integer, Ingredient> fn = (state, ingr) -> {
            if (ingr == null) {
                if (!b.containsKey(state)) {
                    b.put(state, false);
                }
                return;
            }

            // The check passes if the worker has ALL the ingredients needed for the state
            boolean has = journal.getItems().stream().anyMatch(z -> ingr.test(z.get().toItemStack()));
            if (!b.getOrDefault(state, false)) {
                b.put(state, has);
            }
        };
        ingredientsRequiredAtStates.forEach(fn);
        toolsRequiredAtStates.forEach(fn);
        return ImmutableMap.copyOf(b);
    }

    @Override
    protected BlockPos findProductionSpot(ServerLevel sl) {
        if (workSpot != null) {
            // TODO: Choose a location based on its horizontal orientation
            return workSpot.position.relative(Direction.getRandom(sl.getRandom()));
        }
        return null;
    }

    @Override
    protected BlockPos findJobSite(TownInterface town, WorkStateContainer<BlockPos> work) {
        @Nullable ServerLevel sl = town.getServerLevel();
        if (sl == null) {
            return null;
        }

        // TODO: Use tags to support more tiers of work rooms
        Collection<RoomRecipeMatch<MCRoom>> bakeries = town.getRoomsMatching(workRoomId);

        Map<Integer, Boolean> statusItems = getSupplyItemStatus();

        // TODO: Sort by distance and choose the closest
        for (RoomRecipeMatch<MCRoom> match : bakeries) {
            for (Map.Entry<BlockPos, Block> blocks : match.getContainedBlocks().entrySet()) {
                @Nullable Integer blockState = JobBlock.getState(work, blocks.getKey());
                if (blockState == null) {
                    continue;
                }
                if (maxState.equals(blockState)) {
                    return blocks.getKey();
                }
                boolean shouldGo = statusItems.getOrDefault(blockState, false);
                if (shouldGo) {
                    return blocks.getKey();
                }
            }
        }

        return null;
    }

    @Override
    protected Map<Integer, Collection<MCRoom>> roomsNeedingIngredientsOrTools(
            TownInterface town, WorkStateContainer<BlockPos> th
    ) {
        HashMap<Integer, List<MCRoom>> b = new HashMap<>();
        ingredientsRequiredAtStates.forEach((state, ingrs) -> {
            if (ingrs.isEmpty()) {
                b.put(state, new ArrayList<>());
                return;
            }
            Collection<RoomRecipeMatch<MCRoom>> matches = Jobs.roomsWithState(
                    town, workRoomId, (sl, bp) -> state.equals(JobBlock.getState(th, bp))
            );
            List<MCRoom> roomz = matches.stream().filter(room -> {
                for (Map.Entry<BlockPos, Block> e : room.getContainedBlocks().entrySet()) {
                    AbstractWorkStatusStore.State jobBlockState = th.getJobBlockState(e.getKey());
                    if (jobBlockState == null) {
                        continue;
                    }
                    if (jobBlockState.ingredientCount() < ingredientQtyRequiredAtStates.get(state)) {
                        return true;
                    }
                }
                return false;
            }).map(v -> v.room).toList();
            b.put(state, Lists.newArrayList(roomz));
            // TODO[ASAP]: Check block state to see if ingredients and quantity are already satisfied
        });
        HashMap<Integer, Ingredient> stateTools = new HashMap<>();
        if (toolsRequiredAtStates.values().stream().anyMatch(v -> !v.isEmpty())) {
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
                b.get(state).addAll((Jobs.roomsWithState(
                        town, workRoomId, (sl, bp) -> ii.equals(JobBlock.getState(th, bp))
                ).stream().map(v -> v.room).toList()));
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
        return Jobs.openInventoryAndStatusScreen(journal.getCapacity(), sp, e, jobId);
    }

    @Override
    public JobName getJobName() {
        return new JobName("jobs." + jobId);
    }
}
