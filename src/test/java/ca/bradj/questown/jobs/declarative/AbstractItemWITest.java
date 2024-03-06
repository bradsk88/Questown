package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

class AbstractItemWITest {

    public static final Position arbitraryPosition = new Position(1, 2);

    private record StateWithTimer(
            AbstractWorkStatusStore.State state,
            int timer
    ) {
    }

    private record TestTownState(boolean updatedHeldItems, boolean updatedJobState) {
        public static TestTownState updateJobState() {
            return new TestTownState(false, true);
        }

        public static TestTownState updateVillagerItems() {
            return new TestTownState(true, false);
        }
    }

    ;

    private static class TestItemWI extends AbstractItemWI<Position, Void, GathererJournalTest.TestItem, TestTownState> {
        private final Map<Position, StateWithTimer> map = new HashMap<>();

        private final ImmutableWorkStateContainer<Position, TestTownState> statuses = new ImmutableWorkStateContainer<Position, TestTownState>() {
            @Override
            public AbstractWorkStatusStore.@Nullable State getJobBlockState(Position bp) {
                StateWithTimer stateWithTimer = map.get(bp);
                return stateWithTimer == null ? null : stateWithTimer.state();
            }

            @Override
            public ImmutableMap<Position, AbstractWorkStatusStore.State> getAll() {
                ImmutableMap.Builder<Position, AbstractWorkStatusStore.State> b = ImmutableMap.builder();
                map.forEach((k, v) -> b.put(k, v.state));
                return b.build();
            }

            @Override
            public TestTownState setJobBlockState(
                    Position bp,
                    AbstractWorkStatusStore.State bs
            ) {
                map.put(bp, new StateWithTimer(bs, 0));
                return null;
            }

            @Override
            public TestTownState setJobBlockStateWithTimer(
                    Position bp,
                    AbstractWorkStatusStore.State bs,
                    int ticksToNextState
            ) {
                map.put(bp, new StateWithTimer(bs, ticksToNextState));
                return TestTownState.updateJobState();
            }

            @Override
            public TestTownState clearState(Position bp) {
                map.remove(bp);
                return null;
            }

            @Override
            public boolean claimSpot(
                    Position bp,
                    Claim claim
            ) {
                return true;
            }

            @Override
            public void clearClaim(Position position) {

            }

            @Override
            public boolean canClaim(
                    Position position,
                    Supplier<Claim> makeClaim
            ) {
                return true;
            }
        };
        private final InventoryHandle<GathererJournalTest.TestItem> inventory;

        public TestItemWI(
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
                ImmutableMap<Integer, Integer> ingredientQtyRequiredAtStates,
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                InventoryHandle<GathererJournalTest.TestItem> inventory
        ) {
            super(
                    -1,
                    ingredientsRequiredAtStates,
                    ingredientQtyRequiredAtStates,
                    workRequiredAtStates,
                    (x, i) -> timeRequiredAtStates.get(i),
                    (v) -> new Claim(UUID.randomUUID(), 100)
            );
            this.inventory = inventory;
        }

        @Override
        protected TestTownState setHeldItem(Void uxtra, TestTownState tuwn, int villagerIndex, int itemIndex, GathererJournalTest.TestItem item) {
            inventory.set(itemIndex, item);
            return TestTownState.updateVillagerItems();
        }

        @Override
        protected Collection<GathererJournalTest.TestItem> getHeldItems(Void unused, int villagerIndex) {
            return inventory.getItems();
        }

        @Override
        protected ImmutableWorkStateContainer<Position, TestTownState> getWorkStatuses(Void unused) {
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
    void tryInsertIngredients_shouldReturnNoStateUpdates_IfInventoryEmpty() {
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
        Object update = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(update);
    }

    @Test
    void tryInsertIngredients_shouldReturnNoStateUpdate_IfItemRequirementsAreEmpty() {
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
        Object res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnNoStateUpdate_IfItemQuantityRequirementsAreEmpty() {
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
        Object res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnNoStateUpdate_IfWorkRequirementsAreEmpty() {
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
        Object res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnNoStateUpdate_IfAllRequirementsAreEmpty() {
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
        Object res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnNoStateUpdate_IfInventoryFullOfAir() {
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
        Object res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(res);
    }

    @Test
    void tryInsertIngredients_shouldReturnNoStateUpdate_IfInventoryHasItems_ButTheyAreNotWanted() {
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
        Object res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));

        Assertions.assertFalse(inventory.inventoryUpdated);
        Assertions.assertNull(res);
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
        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));
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
        TestTownState res = wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));
        Assertions.assertNotNull(res);
        Assertions.assertTrue(res.updatedHeldItems);
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

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));
        AbstractWorkStatusStore.State state = wi.statuses.getJobBlockState(arbitraryPosition);
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

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));
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

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));
        AbstractWorkStatusStore.State state = wi.statuses.getJobBlockState(arbitraryPosition);
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

        wi.tryInsertIngredients(null, new WorkSpot<>(arbitraryPosition, 0, 0, new Position(0, 1)));
        AbstractWorkStatusStore.State state = wi.statuses.getJobBlockState(arbitraryPosition);
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.workLeft());
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(1, wi.timeLeft(arbitraryPosition));
    }
}