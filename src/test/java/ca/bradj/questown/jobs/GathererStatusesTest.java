package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class GathererStatusesTest {

    @Test
    public void test_should_change_to_returning_if_currently_returned_success_and_signal_is_noon() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.RETURNED_SUCCESS,
                Signals.NOON,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return true;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return true;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return true;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        throw new IllegalStateException("should not get called");
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        throw new IllegalStateException("should not get called");
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNING, newStatus);
    }
    @Test
    public void test_should_stay_dropping_if_currently_dropping_and_signal_is_evening() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.DROPPING_LOOT,
                Signals.EVENING,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return true;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return true;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return true;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return ImmutableList.of(
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem("")
                        );
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertNull(newStatus);
    }

    @Test
    public void test_should_stay_dropping_if_currently_dropping_and_inventory_full_and_signal_is_morning() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.DROPPING_LOOT,
                Signals.MORNING,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return true;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return true;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return true;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return ImmutableList.of(
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem(""),
                                new GathererJournalTest.TestItem("")
                        );
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertNull(newStatus);
    }

    @Test
    public void test_should_stay_in_nospace_if_currently_nospace_and_inventory_full_and_no_space_and_signal_is_morning() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.NO_SPACE,
                Signals.MORNING,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return true;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return true;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return true;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return false;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return itemsToDeposit;
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertNull(newStatus);
    }

    @Test
    public void test_return_nogate_when_no_gate_and_signal_is_morning() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                Signals.MORNING,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return false;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return false;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return false;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return ImmutableList.copyOf(
                                itemsToDeposit.stream().map(v -> new GathererJournalTest.TestItem("")).toList()
                        );
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.NO_GATE, newStatus);
    }

    @Test
    public void test_return_gathering_when_status_is_no_gate_and_signal_is_morning() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.NO_GATE,
                Signals.MORNING,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return false;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return false;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return false;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return ImmutableList.copyOf(
                                itemsToDeposit.stream().map(v -> new GathererJournalTest.TestItem("")).toList()
                        );
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.GATHERING, newStatus);
    }

    @Test
    public void test_change_to_stating_when_no_gate_and_signal_is_noon() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.UNSET,
                Signals.NOON,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return false;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return false;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return true;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return false;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return ImmutableList.copyOf(
                                itemsToDeposit.stream().map(v -> new GathererJournalTest.TestItem("")).toList()
                        );
                    }

                    @Override
                    public boolean hasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.STAYING, newStatus);
    }

    @Test
    public void test_change_to_dropping_when_no_space_status_but_town_has_space_and_signal_is_evening() {
        GathererJournal.Status newStatus = GathererStatuses.getNewStatusFromSignal(
                GathererJournal.Status.NO_SPACE,
                Signals.EVENING,
                new InventoryStateProvider<Item<?>>() {
                    @Override
                    public boolean hasAnyDroppableLoot() {
                        return true;
                    }

                    @Override
                    public boolean inventoryIsFull() {
                        return true;
                    }

                    @Override
                    public boolean inventoryHasFood() {
                        return false;
                    }

                    @Override
                    public boolean hasAnyItems() {
                        return true;
                    }

                    @Override
                    public boolean isValid() {
                        return true;
                    }
                },
                new GathererTimeWarper.Town<GathererJournalTest.TestItem>() {
                    @Override
                    public boolean IsStorageAvailable() {
                        return true;
                    }

                    @Override
                    public ImmutableList depositItems(ImmutableList itemsToDeposit) {
                        return ImmutableList.copyOf(
                                itemsToDeposit.stream().map(v -> new GathererJournalTest.TestItem("")).toList()
                        );
                    }

                    @Override
                    public boolean hasGate() {
                        return true;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, newStatus);
    }

}