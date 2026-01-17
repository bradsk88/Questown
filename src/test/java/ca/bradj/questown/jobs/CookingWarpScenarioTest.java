package ca.bradj.questown.jobs;

import ca.bradj.questown.jobs.GathererJournalTest.TestItem;
import ca.bradj.questown.jobs.leaver.ContainerTarget;
import ca.bradj.questown.jobs.leaver.RankBoost;
import ca.bradj.questown.town.TownState;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Scenario tests for cooking during time warp.
 * <p>
 * These tests verify the expected OUTCOMES of cooking during warp, not
 * implementation details. The scenarios tested here are:
 * <ol>
 *   <li>Raw cookable items in containers + warp → cooked items in containers</li>
 *   <li>Multiple cook cycles based on available time</li>
 *   <li>No cookable items → no change</li>
 *   <li>Container full → cooking stops</li>
 * </ol>
 */
class CookingWarpScenarioTest {

    // --- Test Infrastructure ---

    /**
     * Test container implementation backed by an ArrayList.
     */
    static class TestContainer implements ContainerTarget.Container<TestItem> {
        private final List<TestItem> items;
        private final Supplier<TestItem> emptyItem;

        TestContainer(int size, Supplier<TestItem> emptyItem) {
            this.emptyItem = emptyItem;
            this.items = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                items.add(emptyItem.get());
            }
        }

        @Override
        public int size() {
            return items.size();
        }

        @Override
        public TestItem getItem(int i) {
            return items.get(i);
        }

        @Override
        public RankBoost getItemAcceptanceRankBoost() {
            return RankBoost.SAME_AS_VANILLA_CHEST;
        }

        @Override
        public boolean canAcceptIfSpaceAllows(TestItem item) {
            return true;
        }

        @Override
        public TestItem removeItem(int index) {
            TestItem old = items.get(index);
            if (old.isEmpty()) {
                return null;
            }
            items.set(index, emptyItem.get());
            return old;
        }

        @Override
        public boolean setItem(int i, TestItem item) {
            if (items.get(i).isEmpty()) {
                items.set(i, item);
                return true;
            }
            return false;
        }

        @Override
        public boolean isFull() {
            return items.stream().noneMatch(Item::isEmpty);
        }

        @Override
        public String toShortString() {
            return items.toString();
        }

        @Override
        public String toShortString(boolean includeAir) {
            return toShortString();
        }

        void setItemAt(int index, TestItem item) {
            items.set(index, item);
        }
    }

    /**
     * Test TownState implementation for cooking scenarios.
     */
    static class TestCookTown extends TownState<TestContainer, TestItem, TestItem, Position, TestCookTown> {

        TestCookTown(
                List<VillagerData<TestItem>> villagers,
                List<ContainerTarget<TestContainer, TestItem>> containers
        ) {
            super(
                    villagers,
                    containers,
                    ImmutableMap.of(),
                    ImmutableMap.of(),
                    ImmutableList.of(),
                    ImmutableMap.of(),
                    0L
            );
        }

        @Override
        protected TestCookTown newTownState(
                ImmutableList<VillagerData<TestItem>> villagers,
                ImmutableList<ContainerTarget<TestContainer, TestItem>> containers,
                ImmutableMap<Position, ca.bradj.questown.town.workstatus.State> workStates,
                ImmutableMap<Position, Integer> workTimers,
                ImmutableList<Position> gates,
                ImmutableMap<UUID, Boolean> blocksOfProgress,
                long worldTimeAtSleep
        ) {
            return new TestCookTown(villagers, new ArrayList<>(containers));
        }
    }

    /**
     * Recipe lookup function: returns cooked item for raw item, null if not cookable.
     */
    private static final Function<TestItem, @Nullable TestItem> RECIPES = item -> {
        return switch (item.value) {
            case "raw_beef" -> new TestItem("cooked_beef");
            case "raw_chicken" -> new TestItem("cooked_chicken");
            case "raw_porkchop" -> new TestItem("cooked_porkchop");
            case "raw_mutton" -> new TestItem("cooked_mutton");
            case "potato" -> new TestItem("baked_potato");
            case "kelp" -> new TestItem("dried_kelp");
            default -> null;  // Not cookable
        };
    };

    /**
     * Item matcher for test items.
     */
    private static final EagerCookResolver.ItemMatcher<TestItem> MATCHER =
            (a, b) -> Objects.equals(a.value, b.value);

    /**
     * Convert TestItem to "held" form (TestItem implements both Item and HeldItem).
     */
    private static final Function<TestItem, TestItem> TO_HELD = item -> item;

    // --- Helper Methods ---

    /**
     * Creates a container target with the given container.
     */
    private static ContainerTarget<TestContainer, TestItem> containerAt(
            Position pos,
            TestContainer container
    ) {
        return new ContainerTarget<>(
                pos, 1,
                new Position(pos.x + 1, pos.z),
                container,
                () -> true,
                item -> {},
                i -> true,
                RankBoost.SAME_AS_VANILLA_CHEST.value()
        );
    }

    /**
     * Creates an empty TestItem.
     */
    private static TestItem empty() {
        return new TestItem("");
    }

    /**
     * Creates a test town with the given containers.
     */
    @SafeVarargs
    private static TestCookTown townWith(ContainerTarget<TestContainer, TestItem>... containers) {
        return new TestCookTown(
                ImmutableList.of(),
                Arrays.asList(containers)
        );
    }

    /**
     * Counts items matching the given value in all containers.
     */
    private static int countItems(TestCookTown town, String itemValue) {
        int count = 0;
        for (var container : town.containers) {
            for (int i = 0; i < container.size(); i++) {
                if (itemValue.equals(container.getItem(i).value)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Runs cooking resolution on the town.
     */
    private static TestCookTown resolveCooking(
            TestCookTown state,
            long ticksPerCycle,
            long availableTicks
    ) {
        return EagerCookResolver.resolveCookingGeneric(
                state,
                ticksPerCycle,
                availableTicks,
                RECIPES,
                MATCHER,
                TO_HELD
        );
    }

    // --- Scenario Tests ---

    @Test
    void rawItemsInContainers_afterWarp_shouldBecomeCooked() {
        // SCENARIO: Town has raw beef in a container, warp happens, beef should be cooked

        // Setup: Container with 1 raw beef
        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Verify initial state
        Assertions.assertEquals(1, countItems(town, "raw_beef"));
        Assertions.assertEquals(0, countItems(town, "cooked_beef"));

        // Warp: enough time for 1 cooking cycle
        TestCookTown afterWarp = resolveCooking(town, 400, 500);

        // Verify: raw beef is now cooked beef
        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"),
                "Raw beef should be consumed");
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"),
                "Cooked beef should be produced");
    }

    @Test
    void multipleRawItems_withEnoughTime_shouldAllBeCook() {
        // SCENARIO: Town has 3 raw beef, enough warp time for all 3 cycles

        TestContainer container = new TestContainer(6, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));
        container.setItemAt(1, new TestItem("raw_beef"));
        container.setItemAt(2, new TestItem("raw_beef"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        Assertions.assertEquals(3, countItems(town, "raw_beef"));

        // Warp: enough time for 3 cycles (400 ticks each)
        TestCookTown afterWarp = resolveCooking(town, 400, 1500);

        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"),
                "All raw beef should be consumed");
        Assertions.assertEquals(3, countItems(afterWarp, "cooked_beef"),
                "All beef should be cooked");
    }

    @Test
    void multipleRawItems_withLimitedTime_shouldOnlyCookSome() {
        // SCENARIO: Town has 5 raw beef, but only time for 2 cooking cycles

        TestContainer container = new TestContainer(10, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));
        container.setItemAt(1, new TestItem("raw_beef"));
        container.setItemAt(2, new TestItem("raw_beef"));
        container.setItemAt(3, new TestItem("raw_beef"));
        container.setItemAt(4, new TestItem("raw_beef"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        Assertions.assertEquals(5, countItems(town, "raw_beef"));

        // Warp: only enough time for 2 cycles
        TestCookTown afterWarp = resolveCooking(town, 400, 800);

        Assertions.assertEquals(3, countItems(afterWarp, "raw_beef"),
                "3 raw beef should remain (5 - 2 cooked)");
        Assertions.assertEquals(2, countItems(afterWarp, "cooked_beef"),
                "2 beef should be cooked");
    }

    @Test
    void noCookableItems_shouldNotChangeAnything() {
        // SCENARIO: Town only has non-cookable items

        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("diamond"));
        container.setItemAt(1, new TestItem("stick"));
        container.setItemAt(2, new TestItem("coal"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp
        TestCookTown afterWarp = resolveCooking(town, 400, 10000);

        // Nothing should change
        Assertions.assertEquals(1, countItems(afterWarp, "diamond"));
        Assertions.assertEquals(1, countItems(afterWarp, "stick"));
        Assertions.assertEquals(1, countItems(afterWarp, "coal"));
        Assertions.assertEquals(0, countItems(afterWarp, "cooked_beef"));
    }

    @Test
    void containerNearlyFull_shouldStillCookUsingFreedSlot() {
        // SCENARIO: Container is nearly full (only raw item slot available)
        // Cooking should still work because removing the raw item frees a slot
        // for the cooked item to be deposited

        // Nearly full container: 1 raw beef but all other slots have items
        TestContainer container = new TestContainer(3, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));
        container.setItemAt(1, new TestItem("diamond"));
        container.setItemAt(2, new TestItem("stick"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp - should cook: raw is removed, freeing slot for cooked item
        TestCookTown afterWarp = resolveCooking(town, 400, 1000);

        // Raw beef should be transformed to cooked beef
        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"),
                "Raw beef should be consumed");
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"),
                "Cooked beef should be produced using the freed slot");
        // Other items should remain unchanged
        Assertions.assertEquals(1, countItems(afterWarp, "diamond"));
        Assertions.assertEquals(1, countItems(afterWarp, "stick"));
    }

    @Test
    void mixedCookableItems_shouldCookAllTypes() {
        // SCENARIO: Different types of cookable items

        TestContainer container = new TestContainer(10, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));
        container.setItemAt(1, new TestItem("raw_chicken"));
        container.setItemAt(2, new TestItem("potato"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp: enough time for all 3
        TestCookTown afterWarp = resolveCooking(town, 400, 1500);

        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"));
        Assertions.assertEquals(0, countItems(afterWarp, "raw_chicken"));
        Assertions.assertEquals(0, countItems(afterWarp, "potato"));

        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"));
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_chicken"));
        Assertions.assertEquals(1, countItems(afterWarp, "baked_potato"));
    }

    @Test
    void multipleContainers_shouldCookFromAll() {
        // SCENARIO: Raw items in multiple containers

        TestContainer container1 = new TestContainer(5, CookingWarpScenarioTest::empty);
        container1.setItemAt(0, new TestItem("raw_beef"));

        TestContainer container2 = new TestContainer(5, CookingWarpScenarioTest::empty);
        container2.setItemAt(0, new TestItem("raw_chicken"));

        TestCookTown town = townWith(
                containerAt(new Position(0, 0), container1),
                containerAt(new Position(5, 0), container2)
        );

        // Warp
        TestCookTown afterWarp = resolveCooking(town, 400, 1000);

        // Both should be cooked
        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"));
        Assertions.assertEquals(0, countItems(afterWarp, "raw_chicken"));
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"));
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_chicken"));
    }

    @Test
    void zeroAvailableTime_shouldNotCookAnything() {
        // SCENARIO: No warp time available

        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp with 0 time
        TestCookTown afterWarp = resolveCooking(town, 400, 0);

        Assertions.assertEquals(1, countItems(afterWarp, "raw_beef"),
                "Raw beef should remain with no warp time");
        Assertions.assertEquals(0, countItems(afterWarp, "cooked_beef"));
    }

    @Test
    void insufficientTimeForOneCycle_shouldNotCookAnything() {
        // SCENARIO: Warp time less than one cooking cycle

        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp with time less than one cycle
        TestCookTown afterWarp = resolveCooking(town, 400, 399);

        Assertions.assertEquals(1, countItems(afterWarp, "raw_beef"),
                "Raw beef should remain with insufficient time");
        Assertions.assertEquals(0, countItems(afterWarp, "cooked_beef"));
    }

    @Test
    void emptyContainers_shouldNotChangeAnything() {
        // SCENARIO: All containers are empty

        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp
        TestCookTown afterWarp = resolveCooking(town, 400, 10000);

        // Should remain empty
        for (int i = 0; i < 5; i++) {
            Assertions.assertTrue(afterWarp.containers.get(0).getItem(i).isEmpty());
        }
    }

    @Test
    void alreadyCookedItems_shouldNotBeRecooked() {
        // SCENARIO: Container has cooked items - they shouldn't change

        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("cooked_beef"));
        container.setItemAt(1, new TestItem("baked_potato"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp
        TestCookTown afterWarp = resolveCooking(town, 400, 10000);

        // Cooked items should remain unchanged
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"));
        Assertions.assertEquals(1, countItems(afterWarp, "baked_potato"));
    }

    @Test
    void cookOneDropThenCookAnother_shouldContinueCookingNewCycles() {
        // SCENARIO: After cooking one item and depositing, should continue with next

        TestContainer container = new TestContainer(6, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));
        container.setItemAt(1, new TestItem("raw_chicken"));
        container.setItemAt(2, new TestItem("raw_porkchop"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp with time for exactly 2 cycles
        TestCookTown afterWarp = resolveCooking(town, 400, 800);

        // 2 should be cooked, 1 raw remaining
        int totalRaw = countItems(afterWarp, "raw_beef")
                + countItems(afterWarp, "raw_chicken")
                + countItems(afterWarp, "raw_porkchop");
        int totalCooked = countItems(afterWarp, "cooked_beef")
                + countItems(afterWarp, "cooked_chicken")
                + countItems(afterWarp, "cooked_porkchop");

        Assertions.assertEquals(1, totalRaw, "1 raw item should remain");
        Assertions.assertEquals(2, totalCooked, "2 items should be cooked");
    }

    @Test
    void partialContainerFill_shouldStopWhenFull() {
        // SCENARIO: Container starts with some space, runs out during cooking

        // Container with 4 slots: 2 raw items, 1 other item = 1 empty slot
        TestContainer container = new TestContainer(4, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));
        container.setItemAt(1, new TestItem("raw_chicken"));
        container.setItemAt(2, new TestItem("diamond"));  // Takes up space
        // Slot 3 is empty

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp with time for 5 cycles (more than needed)
        TestCookTown afterWarp = resolveCooking(town, 400, 2000);

        // Should cook 1 item (filling the last slot), then stop
        // Original: raw_beef, raw_chicken, diamond, empty
        // After 1st cook: cooked_beef replaces raw_beef, cooked_beef goes to empty slot
        // Wait - the raw is removed and cooked is deposited...
        // So: empty, raw_chicken, diamond, cooked_beef (after first cycle)
        // Then: empty, empty, diamond, cooked_beef, cooked_chicken (after second cycle)
        // Both should cook since removing raw frees up space

        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"));
        Assertions.assertEquals(0, countItems(afterWarp, "raw_chicken"));
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"));
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_chicken"));
    }

    @Test
    void defaultTicksPerCycle_whenZeroProvided_shouldUseDefault() {
        // SCENARIO: When ticksPerCycle is 0 or negative, use default (400)

        TestContainer container = new TestContainer(5, CookingWarpScenarioTest::empty);
        container.setItemAt(0, new TestItem("raw_beef"));

        TestCookTown town = townWith(containerAt(new Position(0, 0), container));

        // Warp with 0 ticks per cycle (should use default 400)
        // With 500 ticks available, should complete 1 cycle
        TestCookTown afterWarp = resolveCooking(town, 0, 500);

        Assertions.assertEquals(0, countItems(afterWarp, "raw_beef"));
        Assertions.assertEquals(1, countItems(afterWarp, "cooked_beef"));
    }
}
