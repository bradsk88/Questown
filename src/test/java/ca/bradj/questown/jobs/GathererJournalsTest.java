package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableList;
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

    GathererJournals.Town infiniteStorage = new GathererJournals.Town() {
        @Override
        public boolean IsStorageAvailable() {
            return true;
        }
    };

    @Test
    public void test_OneDay_WithEmptyContainers_ShouldStaySame() {
        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.NO_FOOD,
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.FoodRemover<GathererJournalTest.TestItem> emptyContainers = () -> null;
        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial, 24000, emptyContainers, defaultLootGiver, infiniteStorage, () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(result.status(), initial.status());
        Assertions.assertEquals(result.items(), initial.items());
    }

    @Test
    public void test_OneDay_WithBreadInContainers_ShouldRemoveBreadAndAddLootToTown() {

        ArrayList<GathererJournalTest.TestItem> containers = new ArrayList<>();
        containers.add(new GathererJournalTest.TestItem("bread"));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.NO_FOOD,
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
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
                initial, 24000, breadTaker, specificLoot, infiniteStorage, () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(result.status(), initial.status());
        Assertions.assertTrue(result.items().isEmpty());
        Assertions.assertEquals(specificLoot.giveLoot(), containers);
    }

    @Test
    public void test_One6000_WithBreadInContainers_ShouldBeEating() {

        ArrayList<GathererJournalTest.TestItem> containers = new ArrayList<>();
        containers.add(new GathererJournalTest.TestItem("bread"));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.NO_FOOD,
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
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
                initial, 6000, breadTaker, specificLoot, infiniteStorage, () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.GATHERING_HUNGRY, result.status());
        Assertions.assertEquals(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem("")
        ), result.items());
        Assertions.assertTrue(containers.isEmpty());
    }

    @Test
    public void test_6010_WithBreadInContainers_ShouldBeReturningWithLoot() {

        ArrayList<GathererJournalTest.TestItem> containers = new ArrayList<>();
        containers.add(new GathererJournalTest.TestItem("bread"));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.NO_FOOD,
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
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
                initial, 6010, breadTaker, specificLoot, infiniteStorage, () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNING, result.status());
//        Assertions.assertTrue(result.ate());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournalTest.TestItem::isEmpty)); // Loot is not given until evening
        Assertions.assertTrue(containers.isEmpty());
    }

}