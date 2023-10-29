package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        public boolean isUnset() {
            return false;
        }

        @Override
        public boolean isAllowedToTakeBreaks() {
            return false;
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

    record ConstInventory(
            boolean inventoryFull,
            boolean hasNonSupplyItems,
            Map<TestStatus, Boolean> getSupplyItemStatus
    ) implements EntityInvStateProvider<TestStatus> {
    }

    record ConstTown(
            boolean hasSupplies,
            boolean hasSpace,
            boolean canUseMoreSupplies
    ) implements TownStateProvider {
    }

    static class NoOpJob implements JobStatuses.Job<TestStatus, TestStatus> {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return null;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return null;
        }
    }

    static class FailJob implements JobStatuses.Job<TestStatus, TestStatus> {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            throw new AssertionError("Itemless work is not allowed when using FailJob");
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            throw new AssertionError("Itemless work is not allowed when using FailJob");
        }
    }

    private static final JobStatuses.Job<TestStatus, TestStatus> jobWithItemlessWork = new JobStatuses.Job<>() {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return TestStatus.ITEMLESS_WORK;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return TestStatus.ITEM_WORK;
        }
    };

    private static final JobStatuses.Job<TestStatus, TestStatus> jobWithItemWorkOnly = new JobStatuses.Job<>() {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return null;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            if (supplyItemStatus.getOrDefault(TestStatus.ITEM_WORK, false)) {
                return TestStatus.ITEM_WORK;
            }
            if (supplyItemStatus.getOrDefault(TestStatus.ITEM_WORK_2, false)) {
                return TestStatus.ITEM_WORK_2;
            }
            return null;
        }
    };

    @Test
    void StatusShouldBe_CollectingSupplies_WhenInvEmptyTownFull() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of()),
                new ConstTown(true, true, true),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.COLLECTING_SUPPLIES, s);
    }

    @Test
    void StatusShouldDo_ItemlessWork_WhenInvEmpty() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of()),
                new ConstTown(true, true, true),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEMLESS_WORK, s);
    }

    @Test
    void StatusShouldBe_ItemWork_WhenInvFullOfSuppliesTownEmpty() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, true),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_NoSupplies_WhenInvEmptyTownEmpty() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of()),
                new ConstTown(false, true, true),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.NO_SUPPLIES, s);
    }

    @Test
    void StatusShouldDo_ItemWork_WhenInvHasSupplies_AndTownEmpty() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, true, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, true),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldDo_ItemWork2_WhenInvHasWork2Supplies_AndTownEmpty() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, true, ImmutableMap.of(
                        TestStatus.ITEM_WORK, false,
                        TestStatus.ITEM_WORK_2, true
                )),
                new ConstTown(false, true, true),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK_2, s);
    }

    @Test
    void StatusShouldBe_ItemWork_WhenInvFullTownFull() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldPrefer_ItemlessWork_OverItemWork_WhenInvFullTownFull() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEMLESS_WORK, s);
    }

    @Test
    void StatusShouldPrefer_ItemWork_OverItemlessWork_WhenInvFullTownFull_AndPrioritizeIsSetToFalse() {
        boolean prioritizeCollection = false;
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                prioritizeCollection,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldPrefer_ItemWork_OverItemlessWork_IfItemlessWorkIsInAnotherLocation_WhenInvFullTownFull() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true),
                new JobStatuses.Job<>() {
                    @Override
                    public @Nullable TestStatus tryChoosingItemlessWork() {
                        return TestStatus.GOING_TO_JOB;
                    }

                    @Override
                    public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
                        return TestStatus.ITEM_WORK;
                    }
                },
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_DroppingLoot_WhenInvHasNonSuppliesOnly() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(true, true, ImmutableMap.of()),
                new ConstTown(true, true, true),
                jobWithItemWorkOnly,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.DROPPING_LOOT, s);
    }

    @Test
    void StatusShouldBe_DroppingLoot_WhenInvHasSomeSupplies_AndCannotDoWork() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, true // Some supplies
                )),
                new ConstTown(false, true, false),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.DROPPING_LOOT, s);
    }

    @Test
    void StatusShouldBe_Idle_WhenInvHasNoItems_AndTownHasNoSupplies_AndCannotDoWork() {
        boolean canDoWork = false;
        boolean hasSupplies = false;
        boolean suppliesInInventory = false;

        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                true,
                new ConstInventory(false, false, ImmutableMap.of(
                        TestStatus.ITEM_WORK, suppliesInInventory,
                        TestStatus.ITEM_WORK_2, suppliesInInventory
                )),
                new ConstTown(hasSupplies, true, canDoWork),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.IDLE, s);
    }
}