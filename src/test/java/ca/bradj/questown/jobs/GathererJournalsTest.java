package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

class GathererJournalsTest {

    // TODO: Write tests

    private final GathererJournals.LootGiver<GathererJournalTest.TestItem> defaultLootGiver =
            () -> ImmutableList.of(
                    new GathererJournalTest.TestItem("gold"),
                    new GathererJournalTest.TestItem("gold"),
                    new GathererJournalTest.TestItem("gold"),
                    new GathererJournalTest.TestItem("gold"),
                    new GathererJournalTest.TestItem("gold"),
                    new GathererJournalTest.TestItem("gold")
            );

    @Test
    public void test_OneDay_WithEmptyContainers_ShouldStaySame() {
        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Statuses.NO_FOOD,
                false,
                ImmutableList.of()
        );
        GathererJournals.FoodRemover<GathererJournalTest.TestItem> emptyContainers = () -> null;
        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial, 24000, emptyContainers, defaultLootGiver
        );
        Assertions.assertEquals(result.status(), initial.status());
        Assertions.assertEquals(result.ate(), initial.ate());
        Assertions.assertEquals(result.items(), initial.items());
    }

    @Test
    public void test_OneDay_WithBreadInContainers_ShouldRemoveBreadAndAddLootToTown() {

        ArrayList<GathererJournalTest.TestItem> containers = new ArrayList<>();
        containers.add(new GathererJournalTest.TestItem("bread"));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Statuses.NO_FOOD,
                false,
                ImmutableList.of()
        );
        GathererJournals.FoodRemover<GathererJournalTest.TestItem> breadTaker = () -> {
            GathererJournalTest.TestItem bread = new GathererJournalTest.TestItem("bread");
            if (containers.remove(bread)) {
                return bread;
            }
            return null;
        };

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                        new GathererJournalTest.TestItem("flint"),
                        new GathererJournalTest.TestItem("wood"),
                        new GathererJournalTest.TestItem("stone"),
                        new GathererJournalTest.TestItem("iron"),
                        new GathererJournalTest.TestItem("gold"),
                        new GathererJournalTest.TestItem("diamond")
                );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial, 24000, breadTaker, specificLoot
        );
        Assertions.assertEquals(result.status(), initial.status());
        Assertions.assertTrue(result.ate());
        Assertions.assertTrue(result.items().isEmpty());
        Assertions.assertEquals(specificLoot.giveLoot(), containers);
    }

    @Test
    public void test_One6000_WithBreadInContainers_ShouldRemoveBreadAndAddLootToGatherer() {

        ArrayList<GathererJournalTest.TestItem> containers = new ArrayList<>();
        containers.add(new GathererJournalTest.TestItem("bread"));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Statuses.NO_FOOD,
                false,
                ImmutableList.of()
        );
        GathererJournals.FoodRemover<GathererJournalTest.TestItem> breadTaker = () -> {
            GathererJournalTest.TestItem bread = new GathererJournalTest.TestItem("bread");
            if (containers.remove(bread)) {
                return bread;
            }
            return null;
        };

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial, 6000, breadTaker, specificLoot
        );
        Assertions.assertEquals(GathererJournal.Statuses.GATHERING, initial.status());
        Assertions.assertTrue(result.ate());
        Assertions.assertEquals(specificLoot.giveLoot(), result.items());
        Assertions.assertTrue(containers.isEmpty());
    }

}