package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.declarative.WithReason;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

class JobStatusesTest {

    public static class TestStatus implements IStatus<TestStatus> {

        static final TestStatus DROPPING_LOOT = new TestStatus("dropping_loot");

        static final TestStatus NO_SPACE = new TestStatus("no_space");
        static final TestStatus GOING_TO_JOB = new TestStatus("going_to_job");
        static final TestStatus NO_SUPPLIES = new TestStatus("no_supplies");
        static final TestStatus COLLECTING_SUPPLIES = new TestStatus("collecting_supplies");
        static final TestStatus IDLE = new TestStatus("idle");
        static final TestStatus ITEMLESS_WORK = new TestStatus("itemless_work");
        static final TestStatus ITEM_WORK = new TestStatus("item_work");
        static final TestStatus ITEM_WORK_2 = new TestStatus("item_work_2");
        static final TestStatus COLLECTING_PRODUCT = new TestStatus("collecting_product");
        static final TestStatus RELAXING = new TestStatus("relaxing");
        static final TestStatus WAITING_FOR_TIMED_STATE = new TestStatus("waiting");
        static final TestStatus NO_JOBSITE = new TestStatus("no_site");
        static final IStatusFactory<TestStatus> FACTORY = new IStatusFactory<>() {
            @Override
            public TestStatus droppingLoot() {
                return DROPPING_LOOT;
            }

            @Override
            public TestStatus noSpace() {
                return NO_SPACE;
            }

            @Override
            public TestStatus goingToJobSite() {
                return GOING_TO_JOB;
            }

            @Override
            public TestStatus noSupplies() {
                return NO_SUPPLIES;
            }

            @Override
            public TestStatus collectingSupplies() {
                return COLLECTING_SUPPLIES;
            }

            @Override
            public TestStatus idle() {
                return IDLE;
            }

            @Override
            public TestStatus extractingProduct() {
                return COLLECTING_PRODUCT;
            }

            @Override
            public TestStatus relaxing() {
                return RELAXING;
            }

            @Override
            public TestStatus waitingForTimedState() {
                return WAITING_FOR_TIMED_STATE;
            }

            @Override
            public TestStatus noJobSite() {
                return NO_JOBSITE;
            }
        };

        private final String inner;

        protected TestStatus(String inner) {
            this.inner = inner;
        }

        @Override
        public IStatusFactory<TestStatus> getFactory() {
            return FACTORY;
        }

        @Override
        public boolean isGoingToJobsite() {
            return this == GOING_TO_JOB;
        }

        @Override
        public boolean isDroppingLoot() {
            return this == DROPPING_LOOT;
        }

        @Override
        public boolean isCollectingSupplies() {
            return this == COLLECTING_SUPPLIES;
        }

        @Override
        public String name() {
            return inner;
        }

        @Override
        public String nameV2() {
            return name();
        }

        @Override
        public boolean isUnset() {
            return false;
        }

        @Override
        public boolean isAllowedToTakeBreaks() {
            return false;
        }

        @Override
        public @Nullable String getCategoryId() {
            return "test";
        }

        @Override
        public boolean isBusy() {
            return !isAllowedToTakeBreaks();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestStatus that = (TestStatus) o;
            return Objects.equals(inner, that.inner);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inner);
        }

        @Override
        public String toString() {
            return inner;
        }

    }

    public static final ImmutableMap<TestStatus, Boolean> HAS_ALL_SUPPLIES = ImmutableMap.of(
            TestStatus.GOING_TO_JOB, true,
            TestStatus.ITEM_WORK, true
    );

    /**
     * @deprecated Use ConstInventoryV2 for a self-describing interface
     */
    record ConstInventory(
            boolean inventoryFull,
            boolean hasNonSupplyItems,
            Map<TestStatus, Boolean> getSupplyItemStatus
    ) implements EntityInvStateProvider<TestStatus> {

        @Override
        public WithReason<Boolean> hasNonSupplyItems(boolean allowCaching) {
            return new WithReason<>(hasNonSupplyItems, "mock");
        }
    }

    record ConstInventoryV2(
            boolean inventoryFull,
            boolean hasNonSupplyItems,
            Collection<InvBuildingBlock> supplyItemStatus
    ) implements EntityInvStateProvider<TestStatus> {

        @Override
        public WithReason<Boolean> hasNonSupplyItems(boolean allowCaching) {
            return new WithReason<>(hasNonSupplyItems,"mock");
        }

        @Override
        public Map<TestStatus, Boolean> getSupplyItemStatus() {
            ImmutableMap.Builder<TestStatus, Boolean> b = ImmutableMap.builder();
            supplyItemStatus.forEach(v -> b.put(v.status, v.hasInInventory));
            return b.build();
        }
    }

    private static InvBuildingBlock status(TestStatus s) {
        return new InvBuildingBlock(s);
    }

    private static class InvBuildingBlock {
        private final TestStatus status;
        private boolean hasInInventory;

        private InvBuildingBlock(TestStatus status) {
            this.status = status;
        }

        public InvBuildingBlock withSuppliesInInventory(boolean in) {
            this.hasInInventory = in;
            return this;
        }
    }

    record ConstTown(
            boolean hasSupplies,
            boolean hasSpace,
            boolean canUseMoreSupplies,
            boolean isTimerActive
    ) implements TownStateProvider {
        @Override
        public boolean hasSupplies(int i) {
            return hasSupplies;
        }

        @Override
        public boolean canUseMoreSupplies(int i) {
            return canUseMoreSupplies;
        }

        @Override
        public boolean isCachingAllowed() {
            return false;
        }
    }

    static class NoOpJob implements LegacyJob<TestStatus, TestStatus> {

        @Override
        public @Nullable StatusSupplier<TestStatus> tryChoosingItemlessWork() {
            return new StatusSupplier<>(TestStatus.ITEMLESS_WORK, () -> null);
        }

        @Override
        public @Nullable StatusSupplier<TestStatus> tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return new StatusSupplier<>(TestStatus.ITEM_WORK, () -> null);
        }

    }

    static class FailJob implements LegacyJob<TestStatus, TestStatus> {

        @Override
        public @Nullable StatusSupplier<TestStatus> tryChoosingItemlessWork() {
            throw new AssertionError("Itemless work is not allowed when using FailJob");
        }

        @Override
        public @Nullable StatusSupplier<TestStatus> tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            throw new AssertionError("Itemless work is not allowed when using FailJob");
        }

    }

    private static final LegacyJob<TestStatus, TestStatus> jobWithItemlessWork = new LegacyJob<>() {
        @Override
        public @Nullable StatusSupplier<TestStatus> tryChoosingItemlessWork() {
            return new StatusSupplier<>(TestStatus.ITEMLESS_WORK, () -> new WithReason<>(TestStatus.ITEMLESS_WORK, "mocked"));
        }

        @Override
        public @Nullable StatusSupplier<TestStatus> tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return new StatusSupplier<>(TestStatus.ITEM_WORK, () -> new WithReason<>(TestStatus.ITEM_WORK, "mocked"));
        }
    };

    private static final LegacyJob<TestStatus, TestStatus> jobWithItemWorkOnly = new LegacyJob<>() {
        @Override
        public @Nullable StatusSupplier<TestStatus> tryChoosingItemlessWork() {
            return new StatusSupplier<>(TestStatus.ITEMLESS_WORK, () -> null);
        }

        @Override
        public @Nullable StatusSupplier<TestStatus> tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            if (supplyItemStatus.getOrDefault(TestStatus.ITEM_WORK, false)) {
                return new StatusSupplier<>(TestStatus.ITEM_WORK, () -> new WithReason<>(TestStatus.ITEM_WORK, "mocked"));
            }
            if (supplyItemStatus.getOrDefault(TestStatus.ITEM_WORK_2, false)) {
                return new StatusSupplier<>(TestStatus.ITEM_WORK_2, () -> new WithReason<>(TestStatus.ITEM_WORK_2, "mocked"));
            }
            return new StatusSupplier<>(TestStatus.ITEM_WORK, () -> null);
        }
    };

    @Test
    void StatusShouldBe_CollectingSupplies_WhenInvEmptyTownFull() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of()),
                new ConstTown(true, true, true, false),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.COLLECTING_SUPPLIES, s);
    }

    private void assertStatusEquals(
            TestStatus expected,
            WithReason<TestStatus> actual
    ) {
        if (expected != null) {
            Assertions.assertNotNull(actual);
        }
        Assertions.assertEquals(expected, actual.value(), actual.reason());
    }

    @Test
    void StatusShouldDo_ItemlessWork_WhenInvEmpty() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of()),
                new ConstTown(true, true, true, false),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEMLESS_WORK, s);
    }

    @Test
    void StatusShouldBe_ItemWork_WhenInvFullOfSuppliesTownEmpty() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, true, false),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_NoSupplies_WhenInvEmptyTownEmpty() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of()),
                new ConstTown(false, true, true, false),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.NO_SUPPLIES, s);
    }

    @Test
    void StatusShouldDo_ItemWork_WhenInvHasSupplies_AndTownEmpty() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, true, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, true, false),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldDo_ItemWork2_WhenInvHasWork2Supplies_AndTownEmpty() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, true, ImmutableMap.of(
                        TestStatus.ITEM_WORK, false,
                        TestStatus.ITEM_WORK_2, true
                )),
                new ConstTown(false, true, true, false),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEM_WORK_2, s);
    }

    @Test
    void StatusShouldBe_ItemWork_WhenInvFullTownFull() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true, false),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldPrefer_ItemWork_OverItemlessWork_WhenInvFullTownFull_AndPrioritizeIsSetToFalse() {
        boolean prioritizeCollection = false;
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                prioritizeCollection,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true, false),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldPrefer_ItemWork_OverItemlessWork_IfItemlessWorkIsInAnotherLocation_WhenInvFullTownFull() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true, false),
                new LegacyJob<>() {
                    @Override
                    public @Nullable StatusSupplier<TestStatus> tryChoosingItemlessWork() {
                        return new StatusSupplier<>(TestStatus.ITEM_WORK, () -> new WithReason<>(TestStatus.GOING_TO_JOB, "mocked"));
                    }

                    @Override
                    public @Nullable StatusSupplier<TestStatus> tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
                        return new StatusSupplier<>(TestStatus.ITEM_WORK, () -> new WithReason<>(TestStatus.ITEM_WORK, "mocked"));
                    }
                },
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_DroppingLoot_WhenInvHasNonSuppliesOnly() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, true, ImmutableMap.of()),
                new ConstTown(true, true, true, false),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.DROPPING_LOOT, s);
    }

    @Test
    void StatusShouldBe_DroppingLoot_WhenInvHasSomeSupplies_AndCannotDoWork() {
        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, true // Some supplies
                )),
                new ConstTown(false, true, false, false),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.DROPPING_LOOT, s);
    }

    @Test
    void StatusShouldBe_NoJobSite_WhenInvHasNoItems_AndTownHasNoSupplies_AndCannotDoWork() {
        boolean canDoWork = false;
        boolean hasSupplies = false;
        boolean suppliesInInventory = false;

        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, suppliesInInventory,
                        TestStatus.ITEM_WORK_2, suppliesInInventory
                )),
                new ConstTown(hasSupplies, true, canDoWork, false),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.NO_JOBSITE, s);
    }

    @Test
    void StatusShouldBe_NoSpace_WhenInvHasSomeItemsButNotFull_AndTownHasNoSpace_AndCannotDoWork() {
        boolean canDoWork = false;
        boolean hasSupplies = false;
        boolean suppliesInInventory = false;

        boolean hasNonSupplyItems = true;
        boolean townHasSpace = false;

        WithReason<TestStatus> s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, hasNonSupplyItems, ImmutableMap.of(
                        TestStatus.ITEM_WORK, suppliesInInventory,
                        TestStatus.ITEM_WORK_2, suppliesInInventory
                )),
                new ConstTown(hasSupplies, townHasSpace, canDoWork, false),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.NO_SPACE, s);
    }

    @Test
    void StatusShouldBe_Collecting_WhenInvHasToolsButNotIngredients_AndTownIsEmpty() {
        WithReason<TestStatus> s = JobStatuses.usualRoutineV2(
                TestStatus.IDLE,
                true,
                new ConstInventoryV2(false, false, ImmutableList.of()),
                new ConstTown(false, true, true, false),
                new NoOpV2(),
                TestStatus.FACTORY
        );
        assertStatusEquals(TestStatus.COLLECTING_PRODUCT, s);
    }
}