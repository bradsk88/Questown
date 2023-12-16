package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;

class AbstractItemWITest {

    public static final Position arbitraryPosition = new Position(1, 2);

    private record StateWithTimer(
            WorkStatusStore.State state,
            int timer
    ) {
    }

    private static class TestItemWI extends AbstractItemWI<Position, Void, GathererJournalTest.TestItem> {
        private final Map<Position, StateWithTimer> map = new HashMap<>();

        private final WorkStateContainer<Position> statuses = new WorkStateContainer<Position>() {
            @Override
            public WorkStatusStore.@Nullable State getJobBlockState(Position bp) {
                StateWithTimer stateWithTimer = map.get(bp);
                return stateWithTimer == null ? null : stateWithTimer.state();
            }

            @Override
            public void setJobBlockState(
                    Position bp,
                    WorkStatusStore.State bs
            ) {
                map.put(bp, new StateWithTimer(bs, 0));
            }

            @Override
            public void setJobBlockStateWithTimer(
                    Position bp,
                    WorkStatusStore.State bs,
                    int ticksToNextState
            ) {
                map.put(bp, new StateWithTimer(bs, ticksToNextState));
            }
        };

        public TestItemWI(
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
                ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                InventoryHandle<GathererJournalTest.TestItem> inventory
        ) {
            super(
                    ingredientsRequiredAtStates,
                    ingredientQtyRequiredAtStates,
                    workRequiredAtStates,
                    timeRequiredAtStates,
                    inventory
            );
        }

        @Override
        protected WorkStateContainer<Position> getWorkStatuses(Void unused) {
            return statuses;
        }

        @Override
        protected boolean canInsertItem(
                Void unused,
                GathererJournalTest.TestItem item,
                Position bp
        ) {
            // TODO: Any other logic needed here?
            return true;
        }

        public int timeLeft(Position bp) {
            return map.get(bp).timer;
        }
    }

    private static class TestInvHandle implements InventoryHandle<GathererJournalTest.TestItem> {
        boolean inventoryUpdated = false;
        private final List<GathererJournalTest.TestItem> items;

        public TestInvHandle(ArrayList<GathererJournalTest.TestItem> items) {
            this.items = items;
        }

        @Override
        public Collection<GathererJournalTest.TestItem> getItems() {
            return items;
        }

        @Override
        public void set(
                int ii,
                GathererJournalTest.TestItem shrink
        ) {
            items.set(ii, shrink);
            inventoryUpdated = true;
        }
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfInventoryEmpty() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>() // Empty inventory
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items accepted as input
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfItemRequirementsAreEmpty() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )) // Empty inventory
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfItemQuantityRequirementsAreEmpty() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )) // Empty inventory
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfWorkRequirementsAreEmpty() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )) // Empty inventory
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfAllRequirementsAreEmpty() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )) // Empty inventory
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfInventoryFullOfAir() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(new GathererJournalTest.TestItem(""))) // Full of air
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items accepted as input
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfInventoryHasItems_ButTheyAreNotWanted() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("dirt")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> false // All items in inventory are not wanted
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertFalse(res);
    }

    @Test
    void tryInsertIngredients_shouldLeaveStateUnchanged_IfInventoryHasItems_ButTheyAreNotWanted() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("dirt")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> false // All items in inventory are not wanted
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );

        Assertions.assertNull(wi.statuses.getJobBlockState(arbitraryPosition));
        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));
        Assertions.assertNull(wi.statuses.getJobBlockState(arbitraryPosition));
    }

    @Test
    void tryInsertIngredients_shouldReturnTrue_IfInventoryHasItems_AndTheyAreWanted() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items in inventory are wanted
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );
        boolean res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));
        Assertions.assertTrue(res);
    }

    @Test
    void tryInsertIngredients_shouldMoveToNextState_IfInventoryHasItems_AndTheyAreWanted_AndOnlyQtyOneIsWanted() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items in inventory are wanted
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));
        WorkStatusStore.State state = wi.statuses.getJobBlockState(arbitraryPosition);
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.workLeft());
        Assertions.assertEquals(0, wi.timeLeft(arbitraryPosition));
        Assertions.assertEquals(1, state.processingState());
    }

    @Test
    void tryInsertIngredients_shouldUpdateInventoryWithShrunkenItem_IfInventoryHasItems_AndTheyAreWanted() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items in inventory are wanted
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 0 // No work required
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));
        Assertions.assertTrue(inventory.inventoryUpdated);
        Assertions.assertEquals(
                ImmutableList.of(new GathererJournalTest.TestItem("")), // Shrunk, became empty
                ImmutableList.copyOf(inventory.getItems())
        );
    }

    @Test
    void tryInsertIngredients_shouldUpdateStateToIncludeWork() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items in inventory were wanted at current stage (0)
                        // Next stage (1) has no item requirements
                ),
                ImmutableMap.of(
                        0, 1, // Want up to 1 item
                        1, 0 // Next stage (1) has no item requirements
                ),
                ImmutableMap.of(
                        0, 0, // No work required at current stage (0)
                        1, 1 // Work of 1 required at next stage (1)
                ),
                ImmutableMap.of(
                        0, 0, // No time required
                        1, 0 // No time required
                ),
                inventory
        );

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));
        WorkStatusStore.State state = wi.statuses.getJobBlockState(arbitraryPosition);
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, wi.timeLeft(arbitraryPosition));
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(1, state.workLeft());
    }

    @Test
    void tryInsertIngredients_shouldUpdateStateToIncludeTime() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items in inventory were wanted at current stage (0)
                        // Next stage (1) has no item requirements
                ),
                ImmutableMap.of(
                        0, 1, // Want up to 1 item
                        1, 0 // Next stage (1) has no item requirements
                ),
                ImmutableMap.of(
                        0, 0, // No work required at current stage (0)
                        1, 0 // Work of 1 required at next stage (1)
                ),
                ImmutableMap.of(
                        0, 0, // No time required
                        1, 1 // Time of 1 required at next state (1)
                ),
                inventory
        );

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0));
        WorkStatusStore.State state = wi.statuses.getJobBlockState(arbitraryPosition);
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.workLeft());
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(1, wi.timeLeft(arbitraryPosition));
    }

    @Test
    void tryInsertIngredients_shouldThrowIfWorkStillRequired() {
        TestInvHandle inventory = new TestInvHandle(
                new ArrayList<>(ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                ))
        );
        TestItemWI wi = new TestItemWI(
                ImmutableMap.of(
                        0, (item) -> true // All items in inventory were wanted at current stage (0)
                ),
                ImmutableMap.of(
                        0, 1 // Want up to 1 item
                ),
                ImmutableMap.of(
                        0, 1 // Work of 1 required at current stage (0)
                ),
                ImmutableMap.of(
                        0, 0 // No time required
                ),
                inventory
        );

        Assertions.assertThrows(
                IllegalStateException.class,
                () -> wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0))
        );
    }
}