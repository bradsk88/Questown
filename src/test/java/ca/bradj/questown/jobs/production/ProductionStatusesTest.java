package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.*;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProductionStatuses, particularly evening status detection.
 */
class ProductionStatusesTest {

    private static final IProductionStatusFactory<ProductionStatus> FACTORY =
            DeclarativeJobs.STATUS_FACTORY;

    private static final Room TEST_ROOM = new Room(
            new Position(0, 0),
            InclusiveSpace.from(0, 0).to(5, 5)
    );

    // --- Test Fixtures ---

    private record TestInventory(
            boolean inventoryFull,
            boolean hasNonSupplyItems
    ) implements EntityInvStateProvider<Integer> {
        @Override
        public Map<Integer, SupplyItemStatus> getSupplyItemStatus() {
            return Map.of();
        }
    }

    private record TestTown(
            boolean hasSupplies,
            boolean hasSpace,
            Collection<Room> roomsWithCompletedProduct,
            Map<Integer, Boolean> workableBlocksAtState
    ) implements JobTownProvider<Room> {

        @Override
        public RoomsNeedingVillagerInput<Room, ?, ?> roomsNeedingIngredientsByState() {
            return new RoomsNeedingVillagerInput<>(ImmutableMap.of());
        }

        @Override
        public Map<Integer, ? extends LZCD.Dependency<Void>> roomsWithWorkableStatefulBlocks() {
            ImmutableMap.Builder<Integer, LZCD.Dependency<Void>> b = ImmutableMap.builder();
            workableBlocksAtState.forEach((state, hasBlocks) ->
                b.put(state, new ConstantDep("test", hasBlocks))
            );
            return b.build();
        }

        @Override
        public LZCD.Dependency<Void> hasSuppliesV2() {
            return new ConstantDep("test", hasSupplies);
        }

        @Override
        public boolean isUnfinishedTimeWorkPresent() {
            return false;
        }

        @Override
        public Collection<Integer> getStatesWithUnfinishedItemlessWork() {
            return List.of();
        }

        @Override
        public Collection<Room> roomsAtState(Integer state) {
            return List.of();
        }
    }

    // --- Evening Status Tests ---

    @Test
    void getEveningStatus_withItems_shouldReturnDroppingLoot() {
        TestInventory inventory = new TestInventory(false, true);
        TestTown town = new TestTown(false, true, List.of(), Map.of());

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        assertEquals(ProductionStatus.DROPPING_LOOT, result);
    }

    @Test
    void getEveningStatus_workInProgress_shouldContinueWorking() {
        TestInventory inventory = new TestInventory(false, false);
        // Work at state 2 is in progress
        TestTown town = new TestTown(false, true, List.of(), Map.of(0, false, 1, false, 2, true));

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        assertEquals(ProductionStatus.fromJobBlockStatus(2), result);
    }

    @Test
    void getEveningStatus_completedProduct_shouldReturnExtractingProduct() {
        TestInventory inventory = new TestInventory(false, false);
        // No work in progress, but there's a completed product
        TestTown town = new TestTown(false, true, List.of(TEST_ROOM), Map.of());

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        assertEquals(ProductionStatus.EXTRACTING_PRODUCT, result);
    }

    @Test
    void getEveningStatus_noWorkNoProduct_shouldReturnRelaxing() {
        TestInventory inventory = new TestInventory(false, false);
        TestTown town = new TestTown(false, true, List.of(), Map.of());

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        assertEquals(ProductionStatus.RELAXING, result);
    }

    @Test
    void getEveningStatus_workAtState0_shouldNotContinue() {
        // Work at state 0 should NOT trigger continue working (only state > 0)
        TestInventory inventory = new TestInventory(false, false);
        TestTown town = new TestTown(false, true, List.of(), Map.of(0, true));

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        // Should relax, not continue working at state 0
        assertEquals(ProductionStatus.RELAXING, result);
    }

    @Test
    void getEveningStatus_itemsTakePriorityOverExtraction() {
        // If villager has items AND there's completed product, drop items first
        TestInventory inventory = new TestInventory(false, true);
        TestTown town = new TestTown(false, true, List.of(TEST_ROOM), Map.of());

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        assertEquals(ProductionStatus.DROPPING_LOOT, result);
    }

    @Test
    void getEveningStatus_workInProgressTakesPriorityOverExtraction() {
        // If there's work in progress AND completed product, continue working first
        TestInventory inventory = new TestInventory(false, false);
        TestTown town = new TestTown(false, true, List.of(TEST_ROOM), Map.of(1, true));

        ProductionStatus result = ProductionStatuses.getEveningStatus(
                ProductionStatus.IDLE, inventory, town, FACTORY
        );

        assertEquals(ProductionStatus.fromJobBlockStatus(1), result);
    }
}
