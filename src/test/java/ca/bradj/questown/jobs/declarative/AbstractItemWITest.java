package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.WorkStatusStore;
import ca.bradj.questown.town.interfaces.WorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

class AbstractItemWITest {

    public static final Position arbitraryPosition = new Position(1, 2);

    private record StateWithTimer(
            WorkStatusStore.State state,
            int timer
    ) {
    }

    private static class TestItemWI extends AbstractItemWI<Position, Void, GathererJournalTest.TestItem> {

        private final WorkStateContainer<Position> statuses = new WorkStateContainer<>() {

            private final Map<Position, StateWithTimer> map = new HashMap<>();

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
    }

    private static class TestInvHandle implements InventoryHandle<GathererJournalTest.TestItem> {
        boolean inventoryUpdated = false;
        private final Collection<GathererJournalTest.TestItem> items;

        public TestInvHandle(Collection<GathererJournalTest.TestItem> items) {
            this.items = items;
        }

        @Override
        public Collection<GathererJournalTest.TestItem> getItems() {
            return items;
        }

        @Override
        public void setChanged() {
            inventoryUpdated = true;
        }
    }

    @Test
    void tryInsertIngredients_shouldReturnFalse_IfInventoryEmpty() {
        TestInvHandle inventory = new TestInvHandle(
                ImmutableList.of() // Empty inventory
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
                ImmutableList.of(
                        new GathererJournalTest.TestItem("dirt")
                )
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
                ImmutableList.of(
                        new GathererJournalTest.TestItem("dirt")
                )
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
                ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )
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
                ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )
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
        Assertions.assertEquals(1, state.processingState());
    }

    @Test
    void tryInsertIngredients_shouldUpdateInventoryWithShrunkenItem_IfInventoryHasItems_AndTheyAreWanted() {
        TestInvHandle inventory = new TestInvHandle(
                ImmutableList.of(
                        new GathererJournalTest.TestItem("gold")
                )
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
}