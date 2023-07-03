package ca.bradj.questown.jobs;

import ca.bradj.questown.town.TownInventory;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

class GathererJournalTest {

    static class TestItem implements GathererJournal.Item {
        private final String value;

        public TestItem(String value) {
            this.value = value;
        }

        @Override
        public boolean isEmpty() {
            return "".equals(value);
        }

        @Override
        public boolean isFood() {
            return "bread".equals(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TestItem testItem = (TestItem) o;
            return Objects.equals(value, testItem.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }

        @Override
        public String toString() {
            return "TestItem{" +
                    "value='" + value + '\'' +
                    '}';
        }
    }

    static class TestInventory extends TownInventory<List<TestItem>, TestItem> {

        public TestInventory() {
            super(new TestTaker());
        }
    }

    static class TestTaker implements TownInventory.ItemTaker<List<TestItem>, TestItem> {

        @Override
        public @Nullable TestItem takeItem(List<TestItem> items, TestItem item) {
            if (items.contains(item)) {
                items.remove(item);
                return item;
            } else {
                return null;
            }
        }
    }

    static class TestSignals implements GathererJournal.SignalSource {

        GathererJournal.Signals currentSignal = GathererJournal.Signals.UNDEFINED;

        @Override
        public GathererJournal.Signals getSignal() {
            return currentSignal;
        }
    }

    @Test
    void testDefaultStatusBeforeSignals() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        Assertions.assertEquals(GathererJournal.Statuses.IDLE, gatherer.getStatus());
    }

    @Test
    void testMorningSignalWithNoSpaceInInventory() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        for (int i = 0; i < gatherer.getCapacity(); i++) {
            gatherer.addItem(new TestItem("seeds"));
        }

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "no space"
        Assertions.assertEquals(GathererJournal.Statuses.NO_SPACE, gatherer.getStatus());
    }

    @Test
    void testMorningSignalWithNoBreadAvailable() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // Do not add bread to "inv"

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "hungry"
        Assertions.assertEquals(GathererJournal.Statuses.NO_FOOD, gatherer.getStatus());
    }

    @Test
    void testMorningSignal_WithFoodAndLootAvailable_ShouldReturnLoot() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // Add food and loot
        gatherer.addItem(new TestItem("bread"));
        gatherer.addItem(new TestItem("seeds"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Statuses.RETURNED_SUCCESS, gatherer.getStatus());
    }

    @Test
    void testAfternoonSignalSetsStatusToReturning() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "gathering"
        Assertions.assertEquals(GathererJournal.Statuses.RETURNING, gatherer.getStatus());
    }

    @Test
    void testMorningSignalWithBreadAvailable() {
        TestInventory inv = new TestInventory();
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        ArrayList<TestItem> chest = new ArrayList<>();
        inv.addContainer(chest);

        // Make bread available
        gatherer.addItem(new TestItem("bread"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "gathering"
        Assertions.assertEquals(GathererJournal.Statuses.GATHERING, gatherer.getStatus());
    }

    @Test
    void testAfternoonSignalRemovesFoodAddsRandomLoot() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        gatherer.addItem(new TestItem("bread"));

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(() -> ImmutableList.of(
                new TestItem("seeds"),
                new TestItem("seeds"),
                new TestItem("stick"),
                new TestItem("stick"),
                new TestItem("apple"),
                new TestItem("egg"),
                new TestItem("diamond")
        ));

        Collection<TestItem> items = gatherer.getItems();

        Assertions.assertIterableEquals(
                ImmutableList.of(
                        new TestItem("seeds"),
                        new TestItem("seeds"),
                        new TestItem("stick"),
                        new TestItem("stick"),
                        new TestItem("apple"),
                        new TestItem("egg")
                ),
                items
        );
    }

    @Test
    void testAfternoonSignalRemovesFoodEvenWhenLootIsEmpty() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        gatherer.addItem(new TestItem("bread"));

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        Collection<TestItem> items = gatherer.getItems();

        Assertions.assertTrue(items.stream().noneMatch(TestItem::isFood));
    }

    @Test
    void testAfternoonSignalOnlyRemovesOneFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        gatherer.addItem(new TestItem("bread"));
        gatherer.addItem(new TestItem("bread"));

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        Collection<TestItem> items = gatherer.getItems();
        Assertions.assertIterableEquals(
                ImmutableList.of(
                        new TestItem("bread"),
                        new TestItem(""),
                        new TestItem(""),
                        new TestItem(""),
                        new TestItem(""),
                        new TestItem("")
                ),
                items
        );

    }

    @Test
    void testAfternoonSignalTransitionsFromNoFoodToStayedHome() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // With no food, trigger morning signal
        // Gatherer will begin looking for food
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Statuses.STAYING, gatherer.getStatus());
    }

    @Test
    void testEveningSignalSetsReturnedSuccessfulStatus() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "returned successful"
        Assertions.assertEquals(
                GathererJournal.Statuses.RETURNED_SUCCESS, gatherer.getStatus()
        );
    }

    @Test
    void testEveningSignalMovesFromSuccessToIdleStatusWhenNoItems() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        TestItem gold = new TestItem("gold");
        gatherer.addItem(gold);

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "returned successful"
        Assertions.assertEquals(
                GathererJournal.Statuses.RETURNED_SUCCESS, gatherer.getStatus()
        );

        gatherer.removeItem(gold);
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(
                GathererJournal.Statuses.IDLE, gatherer.getStatus()
        );
    }

    @Test
    void testSkipFromMorningToEveningNoFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // No food

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.NO_FOOD, gatherer.getStatus()
        );

        // No noon signal

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.STAYING, gatherer.getStatus()
        );
    }

    @Test
    void testSkipFromMorningToEveningWithFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // Has one food
        gatherer.addItem(new TestItem("bread"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.GATHERING, gatherer.getStatus()
        );

        // No noon signal

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.RETURNED_SUCCESS, gatherer.getStatus()
        );
        Assertions.assertFalse(
                gatherer.hasAnyFood()
        );
    }

    @Test
    void testSkipFromEveningToNoonShouldSetStatusToNoFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestInventory, TestItem> gatherer = new GathererJournal<>(
                sigs, () -> new TestItem("")
        );
        gatherer.initializeStatus(GathererJournal.Statuses.IDLE);

        // Add food
        gatherer.addItem(new TestItem("bread"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.GATHERING, gatherer.getStatus()
        );

        // Start returning at noon
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.RETURNING, gatherer.getStatus()
        );

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertTrue(
                ImmutableList.of(
                        GathererJournal.Statuses.RETURNED_SUCCESS,
                        GathererJournal.Statuses.RETURNED_FAILURE
                ).contains(gatherer.getStatus())
        );

        // Trigger noon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.NO_FOOD, gatherer.getStatus()
        );

        // Trigger noon signal again
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(
                GathererJournal.Statuses.STAYING, gatherer.getStatus()
        );
    }
}
