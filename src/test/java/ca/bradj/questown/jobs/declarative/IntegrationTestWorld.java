package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.jobs.blacksmith.MapBackedWSC;
import ca.bradj.questown.jobs.production.ProductionStatus;
import ca.bradj.questown.town.workstatus.State;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Test world implementation for integration tests.
 * Simulates a simplified town with supply containers and job execution.
 */
public class IntegrationTestWorld implements JobLogic.JLWorld<Void, Boolean, Position> {

    public static final Position DEFAULT_WORKSPOT_POS = new Position(0, 0);
    public static final WorkPosition<Position> DEFAULT_WORKSPOT = new WorkPosition<>(
            DEFAULT_WORKSPOT_POS,
            DEFAULT_WORKSPOT_POS
    );

    private final JobDefinition definition;
    private final TestWorldInteraction worldInteraction;
    private final ValidatedInventoryHandle<GathererJournalTest.TestItem> inventory;
    private final MapBackedWSC workStates;

    // State tracking
    private Map<Integer, Collection<WorkPosition<Position>>> allWorkSpots = new HashMap<>();
    private boolean hasDroppedLoot = false;
    private boolean hasChangedJob = false;
    private JobID changedToJob = null;
    private List<GathererJournalTest.TestItem> droppedLoot = new ArrayList<>();
    private boolean insertedSupplies = false;
    private boolean productExtracted = false;
    private int previousState = 0;

    // Supply container simulation
    private final Map<String, Integer> availableSupplies = new HashMap<>();

    public IntegrationTestWorld(JobDefinition definition) {
        this(definition, 6); // Default inventory size of 6
    }

    public IntegrationTestWorld(JobDefinition definition, int inventorySize) {
        this.definition = definition;
        this.workStates = new MapBackedWSC();
        this.inventory = TestInventory.sized(inventorySize);
        this.worldInteraction = TestWorldInteraction.forDefinition(
                definition,
                inventory,
                workStates,
                () -> null
        );

        // Set up default workspot for the first state
        initializeWorkSpots();
    }

    private void initializeWorkSpots() {
        // Initialize workspots for each state
        for (int state = 0; state <= definition.maxState(); state++) {
            allWorkSpots.put(state, ImmutableList.of(DEFAULT_WORKSPOT));
        }
    }

    // ---- Supply Management ----

    /**
     * Add supplies to the simulated container (chest).
     */
    public void addSupply(String itemName, int quantity) {
        availableSupplies.merge(itemName, quantity, Integer::sum);
    }

    /**
     * Give an item directly to the villager's inventory.
     */
    public void giveItem(String itemName) {
        giveItem(itemName, 1);
    }

    /**
     * Give multiple items directly to the villager's inventory.
     */
    public void giveItem(String itemName, int quantity) {
        for (int i = 0; i < quantity; i++) {
            int slot = findEmptySlot();
            if (slot >= 0) {
                inventory.set(slot, new GathererJournalTest.TestItem(itemName));
            }
        }
    }

    private int findEmptySlot() {
        int slot = 0;
        for (GathererJournalTest.TestItem item : inventory.getItems()) {
            if (item.isEmpty()) {
                return slot;
            }
            slot++;
        }
        return -1;
    }

    // ---- State Accessors ----

    public State getJobBlockState() {
        return workStates.getJobBlockState(DEFAULT_WORKSPOT_POS);
    }

    public int getProcessingState() {
        State state = getJobBlockState();
        return state == null ? 0 : state.processingState();
    }

    public boolean hasDroppedLoot() {
        return hasDroppedLoot;
    }

    public boolean hasChangedJob() {
        return hasChangedJob;
    }

    public @Nullable JobID getChangedToJob() {
        return changedToJob;
    }

    public List<GathererJournalTest.TestItem> getDroppedLoot() {
        return droppedLoot;
    }

    public Collection<GathererJournalTest.TestItem> getInventoryItems() {
        return inventory.getItems();
    }

    public List<GathererJournalTest.TestItem> getNonEmptyInventoryItems() {
        return inventory.getItems().stream()
                .filter(item -> !item.isEmpty())
                .toList();
    }

    public boolean wasProductExtracted() {
        // Track extraction based on state reset from max to 0
        int currentState = getProcessingState();
        if (previousState >= definition.maxState() && currentState == 0) {
            productExtracted = true;
        }
        previousState = currentState;
        return productExtracted;
    }

    // ---- JLWorld Implementation ----

    @Override
    public AbstractWorldInteraction<Void, Position, ?, ?, Boolean> getHandle() {
        return worldInteraction;
    }

    @Override
    public void changeJob(JobID id) {
        hasChangedJob = true;
        changedToJob = id;
    }

    @Override
    public void changeToNextJob() {
        hasChangedJob = true;
    }

    @Override
    public WorkPosition<Position> getWorkSpot() {
        return DEFAULT_WORKSPOT;
    }

    @Override
    public Map<Integer, Collection<WorkPosition<Position>>> listAllWorkSpots() {
        return allWorkSpots;
    }

    @Override
    public boolean setWorkLeftAtFreshState(int work) {
        State cur = workStates.getJobBlockState(DEFAULT_WORKSPOT_POS);
        if (cur == null) {
            cur = State.fresh();
        }
        workStates.setJobBlockState(DEFAULT_WORKSPOT_POS, cur.setWorkLeft(work));
        return true;
    }

    @Override
    public void clearInsertedSupplies() {
        insertedSupplies = false;
    }

    @Override
    public int timesInserted() {
        return insertedSupplies ? 1 : 0;
    }

    @Override
    public boolean tryGrabbingInsertedSupplies() {
        return false; // Simplified - not simulating supply grabbing
    }

    @Override
    public boolean tryDropLoot() {
        // Collect non-empty items as dropped loot
        for (GathererJournalTest.TestItem item : inventory.getItems()) {
            if (!item.isEmpty()) {
                droppedLoot.add(item);
            }
        }
        hasDroppedLoot = !droppedLoot.isEmpty();
        return hasDroppedLoot;
    }

    @Override
    public void tryGetSupplies() {
        // Try to get supplies from the container
        for (Map.Entry<String, Integer> supply : new HashMap<>(availableSupplies).entrySet()) {
            if (supply.getValue() > 0) {
                int slot = findEmptySlot();
                if (slot >= 0) {
                    inventory.set(slot, new GathererJournalTest.TestItem(supply.getKey()));
                    availableSupplies.put(supply.getKey(), supply.getValue() - 1);
                }
            }
        }
    }

    @Override
    public void setLookTarget(Position position) {
        // No-op for tests
    }

    @Override
    public void registerHeldItemsAsFoundLoot() {
        // No-op for tests
    }

    @Override
    public void registerUnmetNeeds(ProductionStatus status, @Nullable Position workspot, int timesInserted) {
        // No-op for tests
    }

    @Override
    public void registerUnmetRooms() {
        // No-op for tests
    }

    // ---- Test Helpers ----

    /**
     * Reset state tracking for a new test run.
     */
    public void reset() {
        hasDroppedLoot = false;
        hasChangedJob = false;
        changedToJob = null;
        droppedLoot.clear();
    }

    /**
     * Set a specific work spot configuration for a state.
     */
    public void setWorkSpotForState(int state, WorkPosition<Position> workSpot) {
        allWorkSpots.put(state, ImmutableList.of(workSpot));
    }

    /**
     * Mark that supplies have been inserted (for expiration tracking).
     */
    public void markInserted() {
        insertedSupplies = true;
    }
}
