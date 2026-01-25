package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.integration.jobs.UnsafeVillagerData;
import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.jobs.production.RoomsNeedingVillagerInput;
import ca.bradj.questown.logic.PredicateCollection;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStatusHandle;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.*;

/**
 * Test implementation of DeclarativeJobTicker.Dependencies for integration tests.
 */
public class TestTickerDependencies implements
        DeclarativeJobTicker.Dependencies<Position, String, GathererJournalTest.TestItem, GathererJournalTest.TestItem, Room, TestRoomMatch, Void, String> {

    private final JobDefinition definition;
    private final TestWorkStatusHandle workStatusHandle;
    private final TestProductionJournal journal;
    private final TestWorldInteraction worldInteraction;
    private final ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory;
    private final TestRoomMatch jobSite;
    private RoomsNeedingVillagerInput<Room, String, Position> cachedRoomsNeedingInput;
    // Entity position inside the room (walls are at x=0, z=0; inside starts at x=1, z=1)
    private Position entityPosition = new Position(2, 2);
    // When true, getJobSites() returns empty to simulate the bug where getMatches()
    // doesn't find farm rooms (they're in activeFarms, not activeRecipes)
    private boolean simulateFarmRoomNotFoundBug = false;

    public TestTickerDependencies(
            JobDefinition definition,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
            TestWorkStatusHandle workStatusHandle,
            TestWorldInteraction worldInteraction
    ) {
        this(definition, inventory, workStatusHandle, worldInteraction, definition.jobId().rootId());
    }

    public TestTickerDependencies(
            JobDefinition definition,
            ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory,
            TestWorkStatusHandle workStatusHandle,
            TestWorldInteraction worldInteraction,
            String roomRecipeId
    ) {
        this.definition = definition;
        this.inventory = inventory;
        this.workStatusHandle = workStatusHandle;
        this.worldInteraction = worldInteraction;

        // Create a room with the specified recipe ID
        this.jobSite = TestRoomMatch.defaultRoom(roomRecipeId);

        // Initialize the journal
        this.journal = new TestProductionJournal(
                definition.jobId(),
                inventory.getItems().size()
        );
        journal.initializeStatus(ProductionStatus.FACTORY.idle());
    }

    /**
     * Enable simulation of the bug where getJobSites() returns empty for farm rooms.
     * This happens because TownRoomsHandle.getMatches() doesn't have special handling
     * for farms like getRoomsMatching() does.
     */
    public void setSimulateFarmRoomNotFoundBug(boolean simulate) {
        this.simulateFarmRoomNotFoundBug = simulate;
    }

    // ========== Dependencies2 methods ==========

    @Override
    public ImmutableList<TestRoomMatch> getRoomsWithCompletedProduct() {
        // Check if any workspot is at max state
        State state = workStatusHandle.getJobBlockState(IntegrationTestWorld.DEFAULT_WORKSPOT_POS);
        if (state != null && state.processingState() >= definition.maxState()) {
            return ImmutableList.of(jobSite);
        }
        return ImmutableList.of();
    }

    @Override
    public boolean isJobBlock(Position pos) {
        return pos.equals(IntegrationTestWorld.DEFAULT_WORKSPOT_POS);
    }

    @Override
    public ImmutableList<TestRoomMatch> getJobSites() {
        if (simulateFarmRoomNotFoundBug) {
            // Simulate the bug: getMatches() doesn't find farm rooms because
            // they're in activeFarms, not activeRecipes
            return ImmutableList.of();
        }
        return ImmutableList.of(jobSite);
    }

    @Override
    public ImmutableList<TestRoomMatch> getRoomsForSupplyCheck() {
        return ImmutableList.of(jobSite);
    }

    @Override
    public Predicate<TestRoomMatch> isJobSitePredicate() {
        String baseRoom = definition.jobId().rootId();
        return m -> m.getRecipeIDs().contains(baseRoom);
    }

    @Override
    public ContainersClean.Block<ContainerTarget<?, GathererJournalTest.TestItem>> toBlock(Room room, Position pos) {
        return new ContainersClean.Block<>() {
            @Override
            public boolean isAir() {
                return false;
            }

            @Override
            public boolean isJobBlock() {
                return pos.equals(IntegrationTestWorld.DEFAULT_WORKSPOT_POS);
            }

            @Override
            public @Nullable ContainerTarget<?, GathererJournalTest.TestItem> asContainer() {
                return null;
            }

            @Override
            public @Nullable ContainerTarget<?, GathererJournalTest.TestItem> asChest() {
                return null;
            }
        };
    }

    @Override
    public boolean canClaim(Position pos) {
        return true;
    }

    @Override
    public PredicateCollection<GathererJournalTest.TestItem, GathererJournalTest.TestItem> item(Integer state) {
        return worldInteraction.getChecks().getIngredientsForStep(state);
    }

    @Override
    public PredicateCollection<GathererJournalTest.TestItem, GathererJournalTest.TestItem> tools(Integer state) {
        return worldInteraction.getChecks().getToolsForStep(state);
    }

    @Override
    public String stringify(Position pos) {
        return pos.toString();
    }

    @Override
    public GathererJournalTest.TestItem convert(GathererJournalTest.TestItem item) {
        return item;
    }

    @Override
    public boolean hasSpace() {
        return true;
    }

    @Override
    public WorkPosition<Position> getWorkSpot() {
        return IntegrationTestWorld.DEFAULT_WORKSPOT;
    }

    @Override
    public Signals.DayTime getDayTime() {
        return new Signals.DayTime(0);
    }

    // ========== Dependencies3 methods ==========

    @Override
    public boolean isFarm(String recipe) {
        return false;
    }

    @Override
    public @Nullable ProductionStatus getComputeStatusOverrideForSpecialJobs() {
        return null;
    }

    @Override
    public ProductionJournal<?, ?> getJournal() {
        return journal;
    }

    // ========== Dependencies (main) methods ==========

    @Override
    public AbstractWorkStatusStore<Position, GathererJournalTest.TestItem, Room, ?> getWorkStatusHandle() {
        return workStatusHandle;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <X> RoomsNeedingVillagerInput<Room, X, Position> computeRoomsNeedingInput(
            WorkStatusHandle<Position, GathererJournalTest.TestItem> work
    ) {
        // Compute which rooms need villager input based on current state
        Map<Integer, Collection<RoomsNeedingVillagerInput.NVIRoom<Room, String, Position>>> roomsMap = new HashMap<>();

        State state = work.getJobBlockState(IntegrationTestWorld.DEFAULT_WORKSPOT_POS);
        int currentState = state == null ? 0 : state.processingState();

        // If not at max state, room needs input at current state
        if (currentState < definition.maxState()) {
            RoomsNeedingVillagerInput.NVIRoom<Room, String, Position> nviRoom =
                    new RoomsNeedingVillagerInput.NVIRoom<>(jobSite, false);
            roomsMap.put(currentState, ImmutableList.of(nviRoom));
        }

        return (RoomsNeedingVillagerInput<Room, X, Position>) (Object) new RoomsNeedingVillagerInput<>(roomsMap);
    }

    @Override
    public DeclarativeJobTicker.EntityHandle<Position, GathererJournalTest.TestItem> getEntity() {
        return new DeclarativeJobTicker.EntityHandle<>() {
            @Override
            public Position getBlockPosition() {
                return entityPosition;
            }

            @Override
            public ImmutableList<GathererJournalTest.TestItem> getHeldItems() {
                return ImmutableList.copyOf(inventory.getItems());
            }
        };
    }

    @Override
    public Supplier<ImmutableList<Position>> getOtherVillagerPositions() {
        return ImmutableList::of;
    }

    @Override
    public Supplier<Position> getRandomWanderTarget(Position avoiding) {
        return () -> new Position(0, 0);
    }

    @Override
    public UnsafeVillagerData getVillagerData() {
        return new TestVillagerData();
    }

    @Override
    public <X> void runPreTickHook(
            Collection<String> rules,
            String location,
            ImmutableList<GathererJournalTest.TestItem> heldItems,
            Consumer<Function<RoomsNeedingVillagerInput<Room, X, Position>, RoomsNeedingVillagerInput<Room, X, Position>>> roomsReplacer,
            Function<Position, State> blockStateFunction,
            boolean firstTick,
            Position entityPosition,
            Supplier<ImmutableList<Position>> otherVillagerPositions,
            Supplier<Position> randomWalkTarget,
            UnsafeVillagerData villagerData
    ) {
        // No-op for tests - special rules not tested yet
    }

    @Override
    public void cacheRoomsNeedingInput(RoomsNeedingVillagerInput<Room, ?, Position> rniot) {
        this.cachedRoomsNeedingInput = unsafeCast(rniot);
    }

    @SuppressWarnings("unchecked")
    private <X> RoomsNeedingVillagerInput<Room, X, Position> unsafeCast(RoomsNeedingVillagerInput<Room, ?, Position> rniot) {
        return (RoomsNeedingVillagerInput<Room, X, Position>) rniot;
    }

    @Override
    public Position toPosition(Position blockPosition) {
        return blockPosition;
    }

    @Override
    public boolean isSimilarYCoord(Position blockPosition, Room room) {
        return true; // Always true for 2D tests
    }

    @Override
    public JobID getJobId() {
        return definition.jobId();
    }

    @Override
    public ExpirationRules getExpiration() {
        return new ExpirationRules(
                () -> Long.MAX_VALUE,
                () -> Long.MAX_VALUE,
                jobID -> jobID,
                () -> Long.MAX_VALUE,
                jobID -> jobID
        );
    }

    @Override
    public int getWorkInterval() {
        return 1;
    }

    @Override
    public int getWorkedRecentlyTicks() {
        return 100;
    }

    @Override
    public UUID getOwnerUUID() {
        return UUID.randomUUID();
    }

    @Override
    public boolean isInventoryEmpty() {
        return inventory.getItems().stream().allMatch(GathererJournalTest.TestItem::isEmpty);
    }

    @Override
    public @Nullable Integer getWorkForStep(int step) {
        return definition.workRequiredAtStates().get(step);
    }

    @Override
    public ImmutableList<String> getSpecialGlobalRules() {
        return ImmutableList.of();
    }

    @Override
    public boolean isLogicWrappingUp() {
        return false;
    }

    @Override
    public AbstractWorldInteraction<?, Position, ?, ?, ?> getWorldInteraction() {
        return worldInteraction;
    }

    @Override
    public @Nullable ContainerTarget<?, ?> getSuccessTarget() {
        return null;
    }

    @Override
    public @Nullable EntityCurrentJobSite<Room> getEntityCurrentJobSite(RoomsNeedingVillagerInput<Room, ?, Position> rniot) {
        if (simulateFarmRoomNotFoundBug) {
            // Simulate the bug: can't find the job site
            return null;
        }
        return new EntityCurrentJobSite<>(jobSite.room, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <X> RoomsNeedingVillagerInput<Room, X, Position> getCachedRoomsNeedingInput() {
        return (RoomsNeedingVillagerInput<Room, X, Position>) cachedRoomsNeedingInput;
    }

    @Override
    public ImmutableList<? extends Predicate<?>> getRecipe(Integer state) {
        PredicateCollection<GathererJournalTest.TestItem, GathererJournalTest.TestItem> ingredients = item(state);
        if (ingredients != null && !ingredients.isEmpty()) {
            return ImmutableList.of(ingredients);
        }
        return ImmutableList.of();
    }

    public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
        // Compute supply item status based on what items are in inventory
        // Must check BOTH ingredients AND tools (like the real implementation does)
        ImmutableMap.Builder<Integer, SupplyItemStatus> builder = ImmutableMap.builder();
        for (int state = 0; state < definition.maxState(); state++) {
            String requiredIngredient = definition.ingredientsRequiredAtStates().get(state);
            String requiredTool = definition.toolsRequiredAtStates().get(state);

            if (requiredIngredient != null) {
                boolean hasItem = inventory.getItems().stream()
                        .anyMatch(item -> item.value.equals(requiredIngredient));
                builder.put(state, hasItem ? SupplyItemStatus.HAS_ITEM : SupplyItemStatus.NEEDS_ITEM);
            } else if (requiredTool != null) {
                // Tool check - villager must have the tool
                boolean hasTool = inventory.getItems().stream()
                        .anyMatch(item -> item.value.equals(requiredTool));
                builder.put(state, hasTool ? SupplyItemStatus.HAS_ITEM : SupplyItemStatus.NEEDS_ITEM);
            } else {
                builder.put(state, SupplyItemStatus.NOT_REQUIRED);
            }
        }
        return builder.build();
    }

    @Override
    public boolean prioritizesExtraction() {
        return true;
    }

    @Override
    public boolean hasInserted(int action) {
        return worldInteraction.timesInserted(null) > 0;
    }

    @Override
    public Void getExtra() {
        return null;
    }

    @Override
    public Supplier<ImmutableList<GathererJournalTest.TestItem>> getJournalItemsSupplier() {
        return () -> ImmutableList.copyOf(inventory.getItems());
    }

    private Map<Integer, Predicate<GathererJournalTest.TestItem>> buildPredicateMap(Map<Integer, String> tagMap) {
        Map<Integer, Predicate<GathererJournalTest.TestItem>> result = new HashMap<>();
        tagMap.forEach((state, tag) -> result.put(state, item -> item.value.equals(tag)));
        return result;
    }

    @Override
    public SupplyChecks<GathererJournalTest.TestItem> asChecks() {
        return new SupplyChecks<>() {
            @Override
            public Map<Integer, ? extends Predicate<GathererJournalTest.TestItem>> getIngredientsForStep() {
                return buildPredicateMap(definition.ingredientsRequiredAtStates());
            }

            @Override
            public Boolean isIngredientRequiredAtStep(Integer state) {
                return definition.ingredientsRequiredAtStates().containsKey(state);
            }

            @Override
            public Map<Integer, ? extends Predicate<GathererJournalTest.TestItem>> getToolsForStep() {
                return buildPredicateMap(definition.toolsRequiredAtStates());
            }

            @Override
            public Boolean isToolRequiredAtStep(Integer state) {
                return definition.toolsRequiredAtStates().containsKey(state);
            }

            @Override
            public Map<Integer, Integer> getWorkRequiredAtStep() {
                return definition.workRequiredAtStates();
            }
        };
    }

    @Override
    public int getMaxState() {
        return definition.maxState();
    }

    @Override
    public <RECIPE> DeclarativeLogicWorld.WorldDeps<Position, GathererJournalTest.TestItem, Room> createWorldDependencies(
            RoomsNeedingVillagerInput<Room, RECIPE, Position> rniot,
            @Nullable EntityCurrentJobSite<Room> entityCurrentJobSite
    ) {
        TestTickerDependencies self = this;
        return new DeclarativeLogicWorld.WorldDeps<>() {
            @Override
            public Position getEntityBlockPosition() {
                return entityPosition;
            }

            @Override
            public Object getEntityVUID() {
                return "test-villager";
            }

            @Override
            public UUID getOwnerUUID() {
                return self.getOwnerUUID();
            }

            @Override
            public JobID getJobId() {
                return definition.jobId();
            }

            @Override
            public ImmutableList<String> getSpecialGlobalRules() {
                return ImmutableList.of();
            }

            @Override
            public void changeJobForVillager(UUID ownerUUID, JobID id, boolean flag) {
                // No-op for tests
            }

            @Override
            public void changeJobForVisitorFromBoard(UUID ownerUUID, JobID currentJobId) {
                // No-op for tests
            }

            @Override
            public Object getVillagerData(Object vuid) {
                return new TestVillagerData();
            }

            @Override
            public void runPreMaxTicksJobChangeHook(ImmutableList<String> rules, Object villagerData) {
                // No-op for tests
            }

            @Override
            public Collection<Room> getRoomsMatchingClinic() {
                return ImmutableList.of();
            }

            @Override
            public void registerFoundLoots(ImmutableList<GathererJournalTest.TestItem> items) {
                // No-op for tests
            }

            @Override
            public boolean hasServerLevel() {
                return true;
            }

            @Override
            public Position getRandomHorizontalFrom(Position bp) {
                return new Position(bp.x + 1, bp.z);
            }

            @Override
            public void logDebug(String message) {
                // No-op for tests
            }

            @Override
            public State getJobBlockState(Position pos) {
                return workStatusHandle.getJobBlockState(pos);
            }

            @Override
            public void setJobBlockState(Position pos, State state) {
                workStatusHandle.setJobBlockState(pos, state);
            }

            @Override
            public void clearWorkState(Position pos) {
                workStatusHandle.clearState(pos);
            }

            @Override
            public WorkPosition<Position> getWorldWorkSpot() {
                return IntegrationTestWorld.DEFAULT_WORKSPOT;
            }

            @Override
            public boolean tryGrabbingInsertedSupplies() {
                return worldInteraction.tryGrabbingInsertedSupplies(null);
            }

            @Override
            public void clearInsertedSupplies() {
                worldInteraction.clearInsertedSupplies(null);
            }

            @Override
            public void registerUnmetNeeds(Position workspot, int timesInserted) {
                // No-op for tests
            }

            @Override
            public void registerUnmetRooms() {
                // No-op for tests
            }

            @Override
            public int timesInserted() {
                return worldInteraction.timesInserted(null);
            }

            @Override
            public AbstractWorldInteraction<?, Position, ?, ?, ?> getWorldHandle() {
                return worldInteraction;
            }

            @Override
            public Map<Integer, Collection<WorkPosition<Position>>> listAllWorkSpots(
                    Function<Position, State> getBlockState,
                    @Nullable EntityCurrentJobSite<Room> jobSite,
                    Predicate<Position> isValidWalkTarget,
                    Predicate<Position> isJobBlock,
                    Function<Position, Position> getRandomAdjacent
            ) {
                Map<Integer, Collection<WorkPosition<Position>>> spots = new HashMap<>();
                State state = getBlockState.apply(IntegrationTestWorld.DEFAULT_WORKSPOT_POS);
                int currentState = state == null ? 0 : state.processingState();
                spots.put(currentState, ImmutableList.of(IntegrationTestWorld.DEFAULT_WORKSPOT));
                return spots;
            }

            @Override
            public boolean tryDropLoot(long tick, Position entityBlockPos) {
                // Move items from inventory to "dropped" state
                return true;
            }

            @Override
            public void tryGetSupplies(
                    RoomsNeedingVillagerInput<Room, ?, Position> roomsNeedingInput,
                    Position entityBlockPos,
                    long currentTick
            ) {
                // No-op for tests - supplies handled differently
            }

            @Override
            public void setLookTarget(Position position) {
                // No-op for tests
            }

            @Override
            public boolean shouldInitializeWorkState(Position bp) {
                return isJobBlock(bp);
            }

            @Override
            public boolean isValidWalkTarget(Position bp) {
                return true;
            }

            @Override
            public boolean isJobBlock(Position bp) {
                return bp.equals(IntegrationTestWorld.DEFAULT_WORKSPOT_POS);
            }

            @Override
            public boolean isLogicWrappingUp() {
                return false;
            }

            @Override
            public ImmutableList<GathererJournalTest.TestItem> getJournalItems() {
                return journal.getItems();
            }

            @Override
            public @Nullable EntityCurrentJobSite<Room> getEntityCurrentJobSite() {
                return entityCurrentJobSite;
            }

            @Override
            public RoomsNeedingVillagerInput<Room, ?, Position> getRoomsNeedingInput() {
                return rniot;
            }

            @Override
            public @Nullable Position getSuccessTargetPOS() {
                return null;
            }

            @Override
            public void runPostDropHook(
                    Position successTargetPos,
                    ImmutableList<GathererJournalTest.TestItem> itemsBeforeDrop,
                    ImmutableList<GathererJournalTest.TestItem> itemsAfterDrop,
                    Consumer<Position> clearState
            ) {
                // No-op for tests
            }

            @Override
            public long getCurrentTick() {
                return 0;
            }
        };
    }

    // ========== Test helpers ==========

    public void setEntityPosition(Position pos) {
        this.entityPosition = pos;
    }

    public void syncJournalWithInventory() {
        journal.setItems(inventory.getItems());
    }

    private static class TestVillagerData implements UnsafeVillagerData {
        private final Map<String, String> data = new HashMap<>();

        @Override
        public String get(String key) {
            return data.get(key);
        }

        @Override
        public void write(String key, String value) {
            data.put(key, value);
        }

        @Override
        public void clear(String key) {
            data.remove(key);
        }
    }
}
