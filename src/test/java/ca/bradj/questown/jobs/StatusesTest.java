package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class StatusesTest {

    @Test
    public void test_should_change_to_returning_if_currently_returned_success_and_signal_is_noon() {
        GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                GathererJournal.Status.RETURNED_SUCCESS,
                GathererJournal.Signals.NOON,
                new InventoryStateProvider<GathererJournal.Item<?>>() {
                    @Override
                    public boolean hasAnyLoot() {
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
                    public boolean HasGate() {
                        return true;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNING, newStatus);
    }
    @Test
    public void test_should_stay_dropping_if_currently_dropping_and_signal_is_evening() {
        GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                GathererJournal.Status.DROPPING_LOOT,
                GathererJournal.Signals.EVENING,
                new InventoryStateProvider<GathererJournal.Item<?>>() {
                    @Override
                    public boolean hasAnyLoot() {
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
                    public boolean HasGate() {
                        return true;
                    }
                }
        );
        Assertions.assertNull(newStatus);
    }

    @Test
    public void test_should_stay_dropping_if_currently_dropping_and_inventory_full_and_signal_is_morning() {
        GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                GathererJournal.Status.DROPPING_LOOT,
                GathererJournal.Signals.MORNING,
                new InventoryStateProvider<GathererJournal.Item<?>>() {
                    @Override
                    public boolean hasAnyLoot() {
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
                    public boolean HasGate() {
                        return true;
                    }
                }
        );
        Assertions.assertNull(newStatus);
    }

    @Test
    public void test_should_stay_in_nospace_if_currently_nospace_and_inventory_full_and_no_space_and_signal_is_morning() {
        GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                GathererJournal.Status.NO_SPACE,
                GathererJournal.Signals.MORNING,
                new InventoryStateProvider<GathererJournal.Item<?>>() {
                    @Override
                    public boolean hasAnyLoot() {
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
                    public boolean HasGate() {
                        return true;
                    }
                }
        );
        Assertions.assertNull(newStatus);
    }

    @Test
    public void test_return_nogate_when_no_gate_and_signal_is_morning() {
        GathererJournal.Status newStatus = Statuses.getNewStatusFromSignal(
                GathererJournal.Status.IDLE,
                GathererJournal.Signals.MORNING,
                new InventoryStateProvider<GathererJournal.Item<?>>() {
                    @Override
                    public boolean hasAnyLoot() {
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
                    public boolean HasGate() {
                        return false;
                    }
                }
        );
        Assertions.assertEquals(GathererJournal.Status.NO_GATE, newStatus);
    }

}