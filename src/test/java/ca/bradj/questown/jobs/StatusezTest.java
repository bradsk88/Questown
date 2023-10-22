package ca.bradj.questown.jobs;

import ca.bradj.questown.Questown;
import ca.bradj.roomrecipes.adapter.RoomRecipeMatch;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

class StatusezTest {

    static class TestStatus implements IStatus<TestStatus> {

        private static final TestStatus DROPPING_LOOT = new TestStatus("dropping_loot");
        private static final TestStatus NO_SPACE = new TestStatus("no_space");
        private static final TestStatus GOING_TO_JOB = new TestStatus("going_to_job");
        private static final TestStatus NO_SUPPLIES = new TestStatus("no_supplies");
        private static final TestStatus COLLECTING_SUPPLIES = new TestStatus("collecting_supplies");
        private static final TestStatus IDLE = new TestStatus("idle");
        private static final TestStatus ITEMLESS_WORK = new TestStatus("itemless_work");
        private static final TestStatus ITEM_WORK = new TestStatus("item_work");
        private static final TestStatus COLLECTING_PRODUCT = new TestStatus("collecting_product");

        private static final IStatusFactory<TestStatus> FACTORY = new IStatusFactory<>() {
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
            public TestStatus collectingFinishedProduct() {
                return COLLECTING_PRODUCT;
            }
        };

        private final String inner;

        private TestStatus(String inner) {
            this.inner = inner;
        }

        @Override
        public IStatusFactory<TestStatus> getFactory() {
            return FACTORY;
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
    RoomRecipeMatch<Room> arbitraryRoom = new RoomRecipeMatch<>(
            new Room(
                    new Position(0, 0),
                    new InclusiveSpace(new Position(0, 0), new Position(1, 1))
            ),
            new ResourceLocation(Questown.MODID, "bakery"),
            ImmutableList.of()
    );

    private record ConstInventory(
            boolean inventoryFull,
            boolean hasItems,
            boolean hasNonSupplyItems,
            Map<TestStatus, Boolean> getSupplyItemStatus
    ) implements EntityInvStateProvider<TestStatus> {
    }

    private record ConstTown(
            boolean hasSupplies,
            boolean hasSpace,
            boolean canUseMoreSupplies
    ) implements TownStateProvider {
    }

    private static class NoOpJob implements JobStatuses.Job<TestStatus> {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return null;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return null;
        }
    }

    private static final JobStatuses.Job<TestStatus> jobWithItemlessWork = new JobStatuses.Job<>() {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return TestStatus.ITEMLESS_WORK;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return TestStatus.ITEM_WORK;
        }
    };

    private static final JobStatuses.Job<TestStatus> jobWithItemWorkOnly = new JobStatuses.Job<>() {
        @Override
        public @Nullable TestStatus tryChoosingItemlessWork() {
            return null;
        }

        @Override
        public @Nullable TestStatus tryUsingSupplies(Map<TestStatus, Boolean> supplyItemStatus) {
            return TestStatus.ITEM_WORK;
        }
    };

    @Test
    void StatusShouldBe_CollectingSupplies_WhenInvEmptyTownFull() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                new ConstInventory(false, false, false, ImmutableMap.of()),
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
                new ConstInventory(false, false, false, ImmutableMap.of()),
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
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, true),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_NoSupplies_WhenInvEmptyTownEmpty() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                new ConstInventory(false, false, false, ImmutableMap.of()),
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
                new ConstInventory(false, true, true, HAS_ALL_SUPPLIES),
                new ConstTown(false, true, true),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_ItemWork_WhenInvFullTownFull() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                new ConstInventory(true, true, false, HAS_ALL_SUPPLIES),
                new ConstTown(true, false, true),
                jobWithItemlessWork,
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.ITEM_WORK, s);
    }

    @Test
    void StatusShouldBe_DroppingLoot_WhenInvHasNonSuppliesOnly() {
        TestStatus s = JobStatuses.usualRoutine(
                TestStatus.IDLE,
                new ConstInventory(true, true, true, ImmutableMap.of()),
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
                new ConstInventory(false, true, false, ImmutableMap.of()),
                new ConstTown(false, true, false),
                new NoOpJob(),
                TestStatus.FACTORY
        );
        Assertions.assertEquals(TestStatus.DROPPING_LOOT, s);
    }
}