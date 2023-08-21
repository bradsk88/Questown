package ca.bradj.questown.jobs;

import ca.bradj.questown.town.TownInventory;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;

public class GathererJournalTest {

    public static class TestItem implements GathererJournal.Item<TestItem>, HeldItem<TestItem, TestItem> {
        protected final String value;

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
        public TestItem shrink() {
            return new TestItem("");
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
            return "TestItem{" + "value='" + value + '\'' + '}';
        }

        @Override
        public boolean isLocked() {
            return false;
        }

        @Override
        public TestItem get() {
            return this;
        }

        @Override
        public TestItem locked() {
            throw new IllegalCallerException("Locking is not expected here");
        }

        @Override
        public TestItem unlocked() {
            throw new IllegalCallerException("Locking is not expected here");
        }
    }

    static class TestInventory extends TownInventory<List<TestItem>, TestItem> {

        public TestInventory() {
            super(new TestTaker());
        }
    }

    static class TestTaker implements TownInventory.ItemTaker<List<TestItem>, TestItem> {

        @Override
        public @Nullable TestItem takeItem(
                List<TestItem> items,
                TestItem item
        ) {
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
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        Assertions.assertEquals(GathererJournal.Status.IDLE, gatherer.getStatus());
    }

    @Test
    void testMorningSignalWithNoSpaceInInventory() {
        TestSignals sigs = new TestSignals();
        Statuses.TownStateProvider noSpace = new Statuses.TownStateProvider() {
            @Override
            public boolean IsStorageAvailable() {
                return false;
            }

            @Override
            public boolean hasGate() {
                return true;
            }
        };
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                noSpace,
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        for (int i = 0; i < gatherer.getCapacity(); i++) {
            gatherer.addItem(new TestItem("seeds"));
        }

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "no space"
        Assertions.assertEquals(GathererJournal.Status.NO_SPACE, gatherer.getStatus());
    }

    @Test
    void testMorningSignalWithNoBreadAvailable() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Do not add bread to "inv"

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "hungry"
        Assertions.assertEquals(GathererJournal.Status.NO_FOOD, gatherer.getStatus());
    }

    @Test
    void testMorningSignal_WithFoodAndLootAvailable_ShouldReturnLoot() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Add food and loot
        gatherer.addItem(new TestItem("bread"));
        gatherer.addItem(new TestItem("seeds"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, gatherer.getStatus());
    }

    @Test
    void testMorningSignalWithBreadAvailable() {
        TestInventory inv = new TestInventory();
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        ArrayList<TestItem> chest = new ArrayList<>();
        inv.addContainer(chest);

        // Make bread available
        gatherer.addItem(new TestItem("bread"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "gathering"
        Assertions.assertEquals(GathererJournal.Status.GATHERING, gatherer.getStatus());
    }

    @Test
    void testEveningSignalRemovesFoodAddsRandomLoot() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                }, 6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        gatherer.addItem(new TestItem("bread"));

        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Trigger afternoon to set status to GATHERING_HUNGRY (to mimic typical scenario)
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, gatherer.getStatus());

        // Trigger one evening signal for food
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(() -> ImmutableList.of(
                new TestItem("seeds"),
                new TestItem("seeds"),
                new TestItem("stick"),
                new TestItem("stick"),
                new TestItem("apple"),
                new TestItem("egg"),
                new TestItem("diamond")
        ));

        // Trigger second evening signal for loot (signals happen in game every tick)
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(() -> ImmutableList.of(
                new TestItem("seeds"),
                new TestItem("seeds"),
                new TestItem("stick"),
                new TestItem("stick"),
                new TestItem("apple"),
                new TestItem("egg"),
                new TestItem("diamond")
        ));

        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, gatherer.getStatus());
        Collection<TestItem> items = gatherer.getItems();
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("seeds"),
                new TestItem("seeds"),
                new TestItem("stick"),
                new TestItem("stick"),
                new TestItem("apple"),
                new TestItem("egg")
        ), items);
    }

    @Test
    void testAfternoonSignalRemovesFoodEvenWhenLootIsEmpty() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        gatherer.addItem(new TestItem("bread"));

        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, gatherer.getStatus());

        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RETURNING, gatherer.getStatus());

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        Collection<TestItem> items = gatherer.getItems();

        Assertions.assertTrue(items.stream().noneMatch(TestItem::isFood));
    }

    @Test
    void testAfternoonSignalStaysIdleIfIdle() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.IDLE, gatherer.getStatus());
    }

    @Test
    void testAfternoonSignalStaysIdleIfGathering() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.IDLE, gatherer.getStatus());
    }

    @Test
    void testAfternoonSignalRemovesOnlyOneFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        gatherer.addItem(new TestItem("bread"));
        gatherer.addItem(new TestItem("bread"));

        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Trigger afternoon to set status to GATHERING_HUNGRY
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, gatherer.getStatus());

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        Collection<TestItem> items = gatherer.getItems();
        Assertions.assertIterableEquals(ImmutableList.of(
                new TestItem("bread"),
                new TestItem(""),
                new TestItem(""),
                new TestItem(""),
                new TestItem(""),
                new TestItem("")
        ), items);

    }

    @Test
    void testAfternoonSignalTransitionsFromNoFoodToStayedHome() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // With no food, trigger morning signal
        // Gatherer will begin looking for food
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);

        // Trigger afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.STAYING, gatherer.getStatus());
    }

    @Test
    void testAfternoonSignalTransitionsFromGatheringToGatheringHungry_IfHasFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        gatherer.addItem(new TestItem("bread"));

        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING, gatherer.getStatus());

        // Trigger FIRST afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, gatherer.getStatus());

        // Trigger SECOND afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RETURNING, gatherer.getStatus());
    }

    @Test
    @Disabled("Edge case. Should pass but not super important")
    void testAfternoonSignalTransitionsFromGatheringToNoFood_IfDoesNotHaveFood() {
        // This shouldn't happen, but let's handle it anyway
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Initially has food
        gatherer.addItem(new TestItem("bread"));

        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING, gatherer.getStatus());

        // Remove food somehow (should be impossible, but hey)
        gatherer.removeItem(new TestItem("bread"));

        // Trigger FIRST afternoon signal (goes to IDLE due to legacy change detection)
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.IDLE, gatherer.getStatus());

        // Trigger SECOND afternoon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.NO_FOOD, gatherer.getStatus());
    }

    @Test
    void testEveningSignalSetsReturnedSuccessfulStatus() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        TestItem gold = new TestItem("gold");
        gatherer.addItem(gold);

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "returned successful"
        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, gatherer.getStatus());
    }

    @Test
    void testEveningSignalSetsNoSpaceStatus_WhenNoSpaceInTown() {
        TestSignals sigs = new TestSignals();
        Statuses.TownStateProvider noSpaceInTown = new Statuses.TownStateProvider() {
            @Override
            public boolean IsStorageAvailable() {
                return false;
            }

            @Override
            public boolean hasGate() {
                return true;
            }
        };
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                noSpaceInTown,
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        TestItem gold = new TestItem("gold");
        gatherer.addItem(gold);

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "returned successful"
        Assertions.assertEquals(GathererJournal.Status.NO_SPACE, gatherer.getStatus());

        // Make sure we don't flip back and forth between statuses

        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "returned successful"
        Assertions.assertEquals(GathererJournal.Status.NO_SPACE, gatherer.getStatus());
    }

    @Test
    void testEveningSignalMovesFromSuccessToRelaxingStatusWhenNoItems_AndTownHasNoSpace() {
        TestSignals sigs = new TestSignals();
        Statuses.TownStateProvider noSpace = new Statuses.TownStateProvider() {
            @Override
            public boolean IsStorageAvailable() {
                return false;
            }

            @Override
            public boolean hasGate() {
                return true;
            }
        };
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                noSpace,
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        TestItem gold = new TestItem("gold");
        gatherer.addItem(gold);

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.NO_SPACE, gatherer.getStatus());

        gatherer.removeItem(gold);
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.RELAXING, gatherer.getStatus());

        // Confirm stays in that status
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        Assertions.assertEquals(GathererJournal.Status.RELAXING, gatherer.getStatus());
    }

    @Test
    void testEveningSignalMovesFromSuccessToRelaxingStatusWhenNoItems_AndTownHasSpace() {
        TestSignals sigs = new TestSignals();
        Statuses.TownStateProvider hasSpace = new Statuses.TownStateProvider() {
            @Override
            public boolean IsStorageAvailable() {
                return true;
            }

            @Override
            public boolean hasGate() {
                return true;
            }
        };
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                hasSpace,
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.RETURNING);

        TestItem gold = new TestItem("gold");
        gatherer.addItem(gold);

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);

        // Assert that the status is set to "returned successful"
        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, gatherer.getStatus());

        gatherer.removeItem(gold);
        // Status goes to idle for legacy change detection
        Assertions.assertEquals(GathererJournal.Status.IDLE, gatherer.getStatus());

        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RELAXING, gatherer.getStatus());

        // Confirm stays in that status
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RELAXING, gatherer.getStatus());
    }

    @Test
    void testSkipFromMorningToEveningNoFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // No food

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.NO_FOOD, gatherer.getStatus());

        // No noon signal

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.STAYING, gatherer.getStatus());
    }

    @Test
    void testSkipFromMorningToEveningWithFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return false;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Has one food
        gatherer.addItem(new TestItem("bread"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING, gatherer.getStatus());

        // No noon signal

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, gatherer.getStatus());

        // Trigger another evening signal (happens every tick in game)
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RETURNING_AT_NIGHT, gatherer.getStatus());

        // Trigger another evening signal (happens every tick in game)
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        // Must have loot or else gatherer will go to "relaxing" instead of "returned success"
        gatherer.tick(() -> ImmutableList.of(
                new TestItem("gold")
        ));
        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, gatherer.getStatus());

        Assertions.assertFalse(gatherer.hasAnyFood());
    }

    @Test
    void testSkipFromEveningToNoonShouldSetStatusToNoFood() {
        TestSignals sigs = new TestSignals();
        GathererJournal<TestItem, TestItem> gatherer = new GathererJournal<TestItem, TestItem>(
                sigs,
                () -> new TestItem(""),
                t -> t,
                new Statuses.TownStateProvider() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                },
                6
        );
        gatherer.initializeStatus(GathererJournal.Status.IDLE);

        // Add food
        gatherer.addItem(new TestItem("bread"));

        // Trigger morning signal
        sigs.currentSignal = GathererJournal.Signals.MORNING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING, gatherer.getStatus());

        // Start returning at noon
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, gatherer.getStatus());

        // Trigger evening signal
        sigs.currentSignal = GathererJournal.Signals.EVENING;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RETURNING_AT_NIGHT, gatherer.getStatus());

        // Trigger noon signal
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.RETURNING, gatherer.getStatus());

        // Trigger noon signal again
        sigs.currentSignal = GathererJournal.Signals.NOON;
        gatherer.tick(ImmutableList::of);
        Assertions.assertEquals(GathererJournal.Status.STAYING, gatherer.getStatus());
    }
}
