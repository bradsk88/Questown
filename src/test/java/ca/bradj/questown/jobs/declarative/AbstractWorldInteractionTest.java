package ca.bradj.questown.jobs.declarative;

import ca.bradj.questown.jobs.*;
import ca.bradj.questown.logic.TownContainerChecksTest.TestItem;
import ca.bradj.questown.town.AbstractWorkStatusStore.State;
import ca.bradj.questown.town.Claim;
import ca.bradj.questown.town.interfaces.ImmutableWorkStateContainer;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

class AbstractWorldInteractionTest {

    private record TestHeldItem(
            String name,
            int quantity
    ) implements HeldItem<TestHeldItem, TestItem> {
        public TestHeldItem(
                String name,
                int quantity
        ) {
            this.quantity = quantity;
            this.name = name;
        }

        @Override
        public boolean isLocked() {
            return false;
        }

        @Override
        public TestItem get() {
            return new TestItem(name, quantity);
        }

        @Override
        public TestHeldItem locked() {
            return new TestHeldItem(name, quantity);
        }

        @Override
        public TestHeldItem unlocked() {
            return new TestHeldItem(name, quantity);
        }

        @Override
        public @Nullable String acquiredViaLootTablePrefix() {
            return null;
        }

        @Override
        public @Nullable String foundInBiome() {
            return null;
        }

        @Override
        public String toShortString() {
            return name;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public boolean isFood() {
            return false;
        }

        @Override
        public TestHeldItem shrink() {
            return new TestHeldItem(name, quantity - 1);
        }

        @Override
        public String getShortName() {
            return name;
        }

        @Override
        public TestHeldItem unit() {
            return new TestHeldItem(name, 1);
        }
    }

    private static class TestWI extends AbstractWorldInteraction<Void, Position, TestItem, TestHeldItem, Boolean> {

        private final InventoryHandle<TestItem> inventory;
        private final ImmutableMap<Integer, Integer> qty;
        private boolean extracted;
        private final ImmutableWorkStateContainer<Position, Boolean> workStatuses;
        private TestItem lastInserted;
        private List<TestItem> chest = new ArrayList<>();

        public TestWI(
                int maxState,
                ImmutableMap<Integer, Function<TestItem, Boolean>> toolsRequiredAtStates,
                ImmutableMap<Integer, Integer> workRequiredAtStates,
                ImmutableMap<Integer, Function<TestHeldItem, Boolean>> ingredientsRequiredAtStates,
                ImmutableMap<Integer, Integer> ingredientQuantityRequiredAtStates,
                ImmutableMap<Integer, Integer> timeRequiredAtStates,
                InventoryHandle<TestItem> inventory,
                ImmutableWorkStateContainer<Position, Boolean> workStatuses,
                Supplier<Claim> claim,
                Collection<String> specialRules
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
                    state -> specialRules
            );
            this.workStatuses = workStatuses;
            this.inventory = inventory;
            this.qty = ingredientQuantityRequiredAtStates;
        }

        public static TestWI noMemoryInventory(
                int i,
                ImmutableMap<Integer, Function<TestItem, Boolean>> toolsNeeded,
                ImmutableMap<Integer, Integer> workRequired,
                ImmutableMap<Integer, Function<TestHeldItem, Boolean>> ingredients,
                Supplier<Collection<TestItem>> inventory,
                Runnable onInventoryChange
        ) {
            ImmutableMap.Builder<Integer, Integer> alwaysOneBuilder = ImmutableMap.builder();
            ingredients.forEach((k, v) -> alwaysOneBuilder.put(k, 1));

            ImmutableMap.Builder<Integer, Integer> alwaysZeroBuilder = ImmutableMap.builder();
            ingredients.forEach((k, v) -> alwaysZeroBuilder.put(k, 0));


            InventoryHandle<TestItem> inventoryHandle = new InventoryHandle<TestItem>() {
                @Override
                public Collection<TestItem> getItems() {
                    return inventory.get();
                }

                @Override
                public void set(
                        int ii,
                        TestItem shrink
                ) {
                    onInventoryChange.run();
                }
            };

            ImmutableWorkStateContainer<Position, Boolean> statuses = testWorkStateContainer();
            return new TestWI(
                    i, toolsNeeded, workRequired, ingredients,
                    alwaysOneBuilder.build(), alwaysZeroBuilder.build(),
                    inventoryHandle, statuses,
                    () -> new Claim(UUID.randomUUID(), 100),
                    ImmutableList.of()
            );
        }

        public void setLastInserted(TestItem i) {
            this.lastInserted = i;
        }

        @Override
        protected Boolean tryExtractProduct(
                Void unused,
                Position position
        ) {
            super.tryExtractProduct(unused, position);
            extracted = true;
            getWorkStatuses(null).clearState(position);
            return true;
        }

        @Override
        protected Boolean addToNearbyChest(
                @NotNull Void inputs,
                Boolean updatedTown,
                Position chestPosition,
                TestItem stackWithQuantity
        ) {
            chest.add(stackWithQuantity);
            return true;
        }

        @Override
        protected TestItem createStackWithQuantity(
                TestItem item,
                int qy
        ) {
            return new TestItem(item.name(), qy);
        }

        @Override
        protected @Nullable TestItem getLastInsertedIngredients(
                Void inputs,
                int villagerIndex,
                Position insertionPos
        ) {
            return lastInserted;
        }

        @Override
        protected Boolean setJobBlockState(
                @NotNull Void inputs,
                Boolean town,
                Position position,
                State fresh
        ) {
            return null;
        }

        @Override
        protected Boolean withEffectApplied(
                @NotNull Void inputs,
                Boolean ts,
                TestHeldItem newItem
        ) {
            return null;
        }

        @Override
        protected Boolean withKnowledge(
                @NotNull Void inputs,
                Boolean ts,
                TestHeldItem newItem
        ) {
            return null;
        }

        @Override
        protected boolean isInstanze(
                TestItem testItem,
                Class<?> clazz
        ) {
            return false;
        }

        @Override
        protected boolean isMulti(TestItem testItem) {
            return false;
        }

        @Override
        protected Boolean getTown(Void inputs) {
            return null;
        }

        @Override
        protected Iterable<TestHeldItem> getResults(
                Void inputs,
                Collection<TestHeldItem> testItems
        ) {
            return ImmutableList.of();
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
            return qty;
        }

        @Override
        protected TestHeldItem getHeldItemProxyFor(TestItem key) {
            return new TestHeldItem(key.name(), key.quantity());
        }

        @Override
        protected ImmutableMap<TestItem, Integer> getItemsInTownWithoutCustomNBT(Void unused) {
            return ImmutableMap.of();
        }

        @Override
        protected Collection<? extends Function<Predicate<TestHeldItem>, Predicate<TestHeldItem>>> getItemInsertionCheckModifiers(
                Void unused,
                Collection<String> activeSpecialRules,
                Predicate<TestHeldItem> originalCheck,
                QuantityRequired qtyRequired,
                int villagerIndex
        ) {
            return ImmutableList.of(); // Item insertion tests should go in WI suite
        }

        @Override
        protected WorksBehaviour.@NotNull TownData getTownData(Void inputs) {
            return new WorksBehaviour.TownData((p) -> ImmutableSet.of());
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
        protected Boolean setHeldItem(Void uxtra, Boolean tuwn, int villagerIndex, int itemIndex, TestHeldItem item) {
            inventory.set(itemIndex, item.get());
            return true;
        }

        @Override
        protected Boolean degradeTool(
                Void unused,
                Boolean tuwn, Function<TestItem, Boolean> heldItemBooleanFunction
        ) {
            return tuwn;
        }

        @Override
        protected boolean canInsertItem(
                Void unused,
                TestHeldItem item,
                Position bp
        ) {
            return true;
        }

        @Override
        protected ImmutableWorkStateContainer<Position, Boolean> getWorkStatuses(Void unused) {
            return workStatuses;
        }

        @Override
        protected Collection<TestHeldItem> getHeldItems(Void unused, int villagerIndex) {
            return inventory.getItems().stream().map(v -> new TestHeldItem(v.name(), v.quantity())).toList();
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
                () -> ImmutableList.of(new TestItem("", 0)),
                () -> inserted.set(true)
        );

        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpotWithState(0)); // Try doing work
        wi.tryWorking(null, arbitrarySpotWithState(0)); // Run once more to extract and reset state

        state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpotWithState(0));

        state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
        Assertions.assertNull(state);
        Assertions.assertFalse(wi.extracted);
        Assertions.assertFalse(inserted.get());

        wi.tryWorking(null, arbitrarySpotWithState(0));

        state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );
        wi.tryWorking(null, arbitrarySpotWithState(0)); // Insert (see test above)
        wi.tryWorking(null, arbitrarySpotWithState(0)); // Process
        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );
        wi.tryWorking(null, arbitrarySpotWithState(0)); // Insert (see test above)

        wi.tryWorking(null, arbitrarySpotWithState(0)); // Process (see test above)

        wi.tryWorking(null, arbitrarySpotWithState(0)); // Extract
        @Nullable State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );
        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
        Assertions.assertNull(state);

        wi.tryWorking(null, arbitrarySpotWithState(0));

        Assertions.assertFalse(wi.extracted);

        state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );
        WorkSpot<Integer, Position> spot = arbitrarySpotWithState(0);

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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpotWithState(0));
        wi.tryWorking(null, arbitrarySpotWithState(1));

        State state = wi.getJobBlockState(null, arbitrarySpotWithState(1).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpotWithState(0));
        wi.tryWorking(null, arbitrarySpotWithState(1));
        wi.tryWorking(null, arbitrarySpotWithState(2));

        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                () -> ImmutableList.of(new TestItem("", 0)),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpotWithState(0));

        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                () -> ImmutableList.of(new TestItem("axe", 1)),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpotWithState(0));

        Assertions.assertFalse(inserted.get()); // Not inserted
        Assertions.assertFalse(wi.extracted); // Not extracted
        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                () -> ImmutableList.of(new TestItem("grapes", 1)),
                () -> inserted.set(true)
        );

        wi.tryWorking(null, arbitrarySpotWithState(0));
        wi.tryWorking(null, arbitrarySpotWithState(1));
        wi.tryWorking(null, arbitrarySpotWithState(2));

        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
        Assertions.assertNull(state);
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertTrue(wi.extracted); // Extracted
    }

    @NotNull
    private static WorkSpot<Integer, Position> arbitrarySpotWithState(int state) {
        return new WorkSpot<>(new Position(1, 2), state, 0, new Position(0, 1));
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
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                ImmutableMap.of(
                        0, 1
                ),
                ImmutableMap.of(
                        0, 0, // No timer at stage 0
                        1, 100 // Timer applies to stage 1
                ),
                new InventoryHandle<TestItem>() {
                    @Override
                    public Collection<TestItem> getItems() {
                        return ImmutableList.of(new TestItem("grapes", 1));
                    }

                    @Override
                    public void set(
                            int ii,
                            TestItem shrink
                    ) {
                        inserted.set(true);
                    }
                },
                testWorkStateContainer(),
                () -> new Claim(UUID.randomUUID(), 100),
                ImmutableList.of()
        );

        wi.tryWorking(null, arbitrarySpotWithState(0));

        State state = wi.getJobBlockState(null, arbitrarySpotWithState(0).position());
        Assertions.assertFalse(wi.extracted); // Not Extracted
        Assertions.assertTrue(inserted.get()); // Inserted
        Assertions.assertNotNull(state);
        Assertions.assertEquals(State.fresh().incrProcessing(), state);
    }

    @Disabled("This is really a test of the rule's logic. Maybe test the rule itself.")
    @Test
    void Test_ShouldInsertStack_IfSpecialRuleIsActive() {
        AtomicBoolean inserted = new AtomicBoolean(false);
        ImmutableWorkStateContainer<Position, Boolean> workStates = testWorkStateContainer();
        TestWI wi = new TestWI(
                1,
                ImmutableMap.of(
                        0, (i) -> true // Villager has the required tools
                ),
                ImmutableMap.of(
                        // No work required
                ),
                ImmutableMap.of(
                        0, (i) -> "grapes".equals(i.name()) // Grapes required at stage 0
                ),
                ImmutableMap.of(
                        0, 2 // Two grapes required
                ),
                ImmutableMap.of(
                        0, 0 // No timers
                ),
                new InventoryHandle<TestItem>() {
                    @Override
                    public Collection<TestItem> getItems() {
                        return ImmutableList.of();
                    }

                    @Override
                    public void set(
                            int ii,
                            TestItem shrink
                    ) {
                        throw new UnsupportedOperationException();
                    }
                },
                workStates,
                () -> new Claim(UUID.randomUUID(), 100),
                ImmutableList.of(SpecialRules.DROP_LOOT_AS_STACK)
        );

        workStates.setJobBlockState(arbitrarySpotWithState(1).position(), State.freshAtState(1));
        wi.setLastInserted(new TestItem("wood", 1));

        wi.tryWorking(null, arbitrarySpotWithState(1));

        Assertions.assertEquals(1, wi.chest.size());
        Assertions.assertEquals(2, wi.chest.get(0).quantity());
        Assertions.assertEquals("wood", wi.chest.get(0).name());
    }

}