package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobID;
import ca.bradj.questown.jobs.WorkSpot;
import ca.bradj.questown.town.AbstractWorkStatusStore.State;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

class AbstractWorldInteractionTest {

    private static class TestWI extends AbstractWorldInteraction<Void, Position, GathererJournalTest.TestItem, GathererJournalTest.TestItem, Boolean> {

        private final InventoryHandle<GathererJournalTest.TestItem> inventory;
        private boolean extracted;
        private final ImmutableWorkStateContainer<Position, Boolean> workStatuses;

        public TestWI(
                int maxState,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsRequiredAtStates,
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredientsRequiredAtStates,
                ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                Supplier<Collection<GathererJournalTest.TestItem>> journal,
                InventoryHandle<GathererJournalTest.TestItem> inventory,
                ImmutableWorkStateContainer<Position, Boolean> workStatuses,
                Supplier<Claim> claim
        ) {
            super(
                    new JobID("test", "test"),
                    -1, // Not used
                    0,
                    maxState,
                    toolsRequiredAtStates,
                    workRequiredAtStates,
                    ingredientsRequiredAtStates,
                    ingredientQuantityRequiredAtStates,
                    timeRequiredAtStates,
                    (v) -> claim.get(),
                    state -> ImmutableList.of()
            );
            this.workStatuses = workStatuses;
            this.inventory = inventory;
        }

        public static TestWI noMemoryInventory(
                int i,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> toolsNeeded,
                ImmutableMap<Integer, Integer> workRequired,
                ImmutableMap<Integer, Function<GathererJournalTest.TestItem, Boolean>> ingredients,
                Supplier<Collection<GathererJournalTest.TestItem>> inventory,
                Runnable onInventoryChange
        ) {
            ImmutableMap.Builder<Integer, Integer> alwaysOneBuilder = ImmutableMap.builder();
            ingredients.forEach((k, v) -> alwaysOneBuilder.put(k, 1));

            ImmutableMap.Builder<Integer, Integer> alwaysZeroBuilder = ImmutableMap.builder();
            ingredients.forEach((k, v) -> alwaysZeroBuilder.put(k, 0));


            InventoryHandle<GathererJournalTest.TestItem> inventoryHandle = new InventoryHandle<GathererJournalTest.TestItem>() {
                @Override
                public Collection<GathererJournalTest.TestItem> getItems() {
                    return inventory.get();
                }

                @Override
                public void set(
                        int ii,
                        GathererJournalTest.TestItem shrink
                ) {
                    onInventoryChange.run();
                }
            };

            ImmutableWorkStateContainer<Position, Boolean> statuses = testWorkStateContainer();
            return new TestWI(
                    i, toolsNeeded, workRequired, ingredients,
                    alwaysOneBuilder.build(), alwaysZeroBuilder.build(),
                    inventory, inventoryHandle, statuses,
                    () -> new Claim(UUID.randomUUID(), 100)
            );
        }

        @Override
        protected Boolean tryExtractProduct(
                Void unused,
                Position position
        ) {
            extracted = true;
            getWorkStatuses(null).clearState(position);
            return true;
        }

        @Override
        protected Boolean setJobBlockState(
                @NotNull Void inputs,
                Boolean ts,
                Position position,
                State fresh
        ) {
            return null;
        }

        @Override
        protected Boolean withEffectApplied(
                @NotNull Void inputs,
                Boolean ts,
                GathererJournalTest.TestItem newItem
        ) {
            return null;
        }

        @Override
        protected Boolean withKnowledge(
                @NotNull Void inputs,
                Boolean ts,
                GathererJournalTest.TestItem newItem
        ) {
            return null;
        }

        @Override
        protected boolean isInstanze(
                GathererJournalTest.TestItem testItem,
                Class<?> clazz
        ) {
            return false;
        }

        @Override
        protected boolean isMulti(GathererJournalTest.TestItem testItem) {
            return false;
        }

        @Override
        protected Boolean getTown(Void inputs) {
            return null;
        }

        @Override
        protected Iterable<GathererJournalTest.TestItem> getResults(
                Void inputs,
                Collection<GathererJournalTest.TestItem> testItems
        ) {
            return null;
        }

        @Override
        protected boolean isEntityClose(
                Void unused,
                Position position
        ) {
            return true;
        }

        @Override
        protected boolean isReady(Void unused) {
            return true;
        }

        @Override
        public Map<Integer, Integer> ingredientQuantityRequiredAtStates() {
            return null;
        }

        @Override
        protected boolean isWorkResult(
                Void unused,
                GathererJournalTest.TestItem item
        ) {
            return false;
        }

        @Override
        protected int getWorkSpeedOf10(Void unused) {
            return 10;
        }

        @Override
        protected int getAffectedTime(Void unused, Integer nextStepTime) {
            return nextStepTime;
        }

        @Override
        protected Boolean setHeldItem(Void uxtra, Boolean tuwn, int villagerIndex, int itemIndex, GathererJournalTest.TestItem item) {
            inventory.set(itemIndex, item);
            return true;
        }

        @Override
        protected Boolean degradeTool(
                Void unused,
                Boolean tuwn, Function<GathererJournalTest.TestItem, Boolean> heldItemBooleanFunction
        ) {
            return tuwn;
        }

        @Override
        protected boolean canInsertItem(
                Void unused,
                GathererJournalTest.TestItem item,
                Position bp
        ) {
            return true;
        }

        @Override
        protected ImmutableWorkStateContainer<Position, Boolean> getWorkStatuses(Void unused) {
            return workStatuses;
        }

        @Override
        protected Collection<GathererJournalTest.TestItem> getHeldItems(Void unused, int villagerIndex) {
            return inventory.getItems();
        }
    }

    @NotNull
    private static ImmutableWorkStateContainer<Position, Boolean> testWorkStateContainer() {
        HashMap<Position, State> ztate = new HashMap<>();

        ImmutableWorkStateContainer<Position, Boolean> statuses = new ImmutableWorkStateContainer<Position, Boolean>() {
            @Override
            public @Nullable State getJobBlockState(Position bp) {
                return ztate.get(bp);
            }

            @Override
            public ImmutableMap<Position, State> getAll() {
                return ImmutableMap.copyOf(ztate);
            }

            @Override
            public Boolean setJobBlockState(
                    Position bp,
                    State bs
            ) {
                ztate.put(bp, bs);
                return true;
            }

            @Override
            public Boolean setJobBlockStateWithTimer(
                    Position bp,
                    State bs,
                    int ticksToNextState
            ) {
                ztate.put(bp, bs); // Ignoring time
                return true;
            }

            @Override
            public Boolean clearState(Position bp) {
                ztate.remove(bp);
                return true;
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
        return statuses;
    }

    @Test
    void Test_ShouldExtractForCompletelyEmptyWork() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(),
                ImmutableMap.of(),
                ImmutableMap.of(),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("")),
                () -> inserted.set(true)
        );

        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpot(0)); // Try doing work
        wi.tryWorking(null, arbitrarySpot(0)); // Run once more to extract and reset state

        state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertFalse(inserted.get());
        Assertions.assertTrue(wi.extracted); // Extracted
        Assertions.assertNull(state);
    }

    @Test
    void Test_ShouldInsertForWorklessToollessJob() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(), // No work required
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpot(0));

        state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertFalse(wi.extracted);

        Assertions.assertEquals(State.fresh().incrProcessing(), state);
        Assertions.assertTrue(inserted.get());
    }

    @Test
    void Test_ShouldInsertFirst_IfBlockRequiresIngredientsAndWork() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);
        Assertions.assertFalse(wi.extracted);
        Assertions.assertFalse(inserted.get());

        wi.tryWorking(null, arbitrarySpot(0));

        state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.processingState());
        Assertions.assertEquals(100, state.workLeft());
        Assertions.assertEquals(1, state.ingredientCount());
        Assertions.assertFalse(wi.extracted);
        Assertions.assertTrue(inserted.get());
    }

    @Test
    void Test_ShouldInsertThenProcessForToollessJob() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        wi.tryWorking(null, arbitrarySpot(0)); // Insert (see test above)
        wi.tryWorking(null, arbitrarySpot(0)); // Process
        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.processingState());
        Assertions.assertEquals(99, state.workLeft());
        Assertions.assertEquals(1, state.ingredientCount());
        Assertions.assertFalse(wi.extracted);
    }

    @Test
    void Test_ShouldInsertThenProcessThenExtractForToollessJob() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                0, // max state (only one state here)
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 1 // 1 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        wi.tryWorking(null, arbitrarySpot(0)); // Insert (see test above)

        wi.tryWorking(null, arbitrarySpot(0)); // Process (see test above)

        wi.tryWorking(null, arbitrarySpot(0)); // Extract
        @Nullable State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);
        Assertions.assertTrue(wi.extracted);
    }

    @Test
    void Test_ShouldInsertAndNotProcess_ForJobWithTwoStages_OnFirstTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(wi.extracted);

        state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(100, state.workLeft());
        Assertions.assertEquals(0, state.ingredientCount());

        Assertions.assertTrue(inserted.get());
    }

    @Test
    void Test_ShouldMoveToStage2_AfterFirstTry() {
        final AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                3,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );
        WorkSpot<Integer, Position> spot = arbitrarySpot(0);

        Assertions.assertNull(wi.getJobBlockState(null, spot.position()));

        wi.tryWorking(null, spot);

        State state = wi.getJobBlockState(null, spot.position());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(100, state.workLeft()); // Not processed
        Assertions.assertFalse(wi.extracted);
        Assertions.assertTrue(inserted.get());
    }


    @Test
    void Test_ShouldInsertAndProcess_ForJobWithTwoStages_AfterSecondTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                2,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 100 // 100 work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));

        State state = wi.getJobBlockState(null, arbitrarySpot(1).position());
        Assertions.assertNotNull(state);
        Assertions.assertFalse(wi.extracted);
        Assertions.assertEquals(1, state.processingState());
        Assertions.assertEquals(99, state.workLeft()); // Processed
        Assertions.assertTrue(inserted.get()); // Inserted
    }

    @Test
    void Test_ShouldInsertAndProcessAndExtract_ForJobWithThreeStages_AfterThirdTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                2,
                ImmutableMap.of(), // No tools required
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 1, // 1 work required at stage 1
                        2, 0 // No work requred at stage 2
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));
        wi.tryWorking(null, arbitrarySpot(2));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertTrue(wi.extracted); // Extracted
    }

    @Test
    void Test_ShouldDoNothingIfToolIsRequiredButNotHad() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                2,
                ImmutableMap.of(
                        0, (i) -> false // Villager does not have the needed tool
                ),
                ImmutableMap.of(
                        0, 1 // Work required at stage 0
                ),
                ImmutableMap.of(
                        0, (i) -> true // Villager has all the items needed
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state); // Not processed
        Assertions.assertFalse(inserted.get()); // Not inserted
        Assertions.assertFalse(wi.extracted); // Not extracted
    }

    @Test
    void Test_ShouldAdvanceProgress_IfNoIngredientsAreNeeded_AndToolIsHad() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                2,
                ImmutableMap.of(
                        0, (i) -> true // Villager has the needed tool
                ),
                ImmutableMap.of(
                        // No work required
                ),
                ImmutableMap.of(
                        // No items required
                ),
                // This is not actually important because we've overloaded the item check, above
                () -> ImmutableList.of(new GathererJournalTest.TestItem("axe")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));

        Assertions.assertFalse(inserted.get()); // Not inserted
        Assertions.assertFalse(wi.extracted); // Not extracted
        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNotNull(state);
        Assertions.assertEquals(0, state.workLeft());
        Assertions.assertEquals(0, state.ingredientCount());
        Assertions.assertEquals(1, state.processingState());
    }

    @Test
    void Test_ShouldInsertAndProcessAndExtract_WhenToolsRequiredAndPossessed_ForJobWithThreeStages_AfterThirdTry() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = TestWI.noMemoryInventory(
                2,
                ImmutableMap.of(
                        0, (i) -> true, // Villager has the needed tools
                        1, (i) -> true, // Villager has the needed tools
                        2, (i) -> true // Villager has the needed tools
                ),
                ImmutableMap.of(
                        0, 0, // No work required at stage 0
                        1, 1, // 1 work required at stage 1
                        2, 0 // No work required at stage 2
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpot(0));
        wi.tryWorking(null, arbitrarySpot(1));
        wi.tryWorking(null, arbitrarySpot(2));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertNull(state);
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertTrue(wi.extracted); // Extracted
    }

    @Test
    void Test_ShouldNotSetTimerIfToolIsRequired() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        TestWI wi = new TestWI(
                2,
                ImmutableMap.of(
                        1, (i) -> false // Villager does not have the tool
                ),
                ImmutableMap.of(
                        // No work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.value) // Grapes required at stage 0
                ),
                ImmutableMap.of(
                        0, 1
                ),
                ImmutableMap.of(
                        0, 0, // No timer at stage 0
                        1, 100 // Timer applies to stage 1
                ),
                () -> ImmutableList.of(new GathererJournalTest.TestItem("grapes")),
                new InventoryHandle<GathererJournalTest.TestItem>() {
                    @Override
                    public Collection<GathererJournalTest.TestItem> getItems() {
                        return ImmutableList.of(new GathererJournalTest.TestItem("grapes"));
                    }

                    @Override
                    public void set(
                            int ii,
                            GathererJournalTest.TestItem shrink
                    ) {
                        inserted.set(true);
                    }
                },
                testWorkStateContainer(),
                () -> new Claim(UUID.randomUUID(), 100)
        );

        wi.tryWorking(null, arbitrarySpot(0));

        State state = wi.getJobBlockState(null, arbitrarySpot(0).position());
        Assertions.assertFalse(wi.extracted); // Not Extracted
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertNotNull(state);
        Assertions.assertEquals(State.fresh().incrProcessing(), state);
    }

    @NotNull
    private static WorkSpot<Integer, Position> arbitrarySpot(int state) {
        return new WorkSpot<>(new Position(1, 2), state, 0, new Position(0, 1));
    }

}