package ca.bradj.questown.jobs;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class GathererJournalsTest {

    // TODO: Write tests

    private final GathererJournals.LootGiver<GathererJournalTest.TestItem> defaultLootGiver = () -> ImmutableList.of(
            new GathererJournalTest.TestItem(
                    "gold"),
            new GathererJournalTest.TestItem("gold"),
            new GathererJournalTest.TestItem("gold"),
            new GathererJournalTest.TestItem("gold"),
            new GathererJournalTest.TestItem("gold"),
            new GathererJournalTest.TestItem("gold")
    );

    private static class FakeTownWithInfiniteStorage implements
            GathererJournals.Town<GathererJournalTest.TestItem>,
            GathererJournals.FoodRemover<GathererJournalTest.TestItem> {

        final List<GathererJournalTest.TestItem> container = new ArrayList<>();

        @Override
        public ImmutableList<GathererJournalTest.TestItem> depositItems(ImmutableList<GathererJournalTest.TestItem> itemsToDeposit) {
            itemsToDeposit.stream().filter(Predicates.not(GathererJournalTest.TestItem::isEmpty)).forEach(
                    container::add
            );
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
        public boolean IsStorageAvailable() {
            return true;
        }

        @Nullable
        @Override
        public GathererJournalTest.TestItem removeFood() {
            GathererJournalTest.TestItem bread = new GathererJournalTest.TestItem("bread");
            if (container.remove(bread)) {
                return bread;
            }
            return null;
        }
    }

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
                initial,
                24000,
                emptyContainers,
                defaultLootGiver,
                new FakeTownWithInfiniteStorage(), // No items added
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(result.status(), initial.status());
        Assertions.assertEquals(result.items(), initial.items());
    }

    @Test
    public void test_OneDay_WithBreadInContainers_ShouldRemoveBreadAndAddLootToTown() {

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();
        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE,
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                24000,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(
                GathererJournal.Status.NO_FOOD,
                result.status()
        ); // Debatable. Idle (or sleeping?) could also be good
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournalTest.TestItem::isEmpty));
        Assertions.assertEquals(specificLoot.giveLoot(), infiniteStorage.container);
    }

    @Test
    public void test_6000_WithBreadInContainers_ShouldBeEating() {

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE,
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                6000,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
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
        Assertions.assertTrue(infiniteStorage.container.isEmpty());
    }
    @Test
    public void test_10_WithBreadInContainers_AndFullInventory_WithFood_ShouldBeNoSpace() {

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE,
                ImmutableList.of(
                        new GathererJournalTest.TestItem("gold"),
                        new GathererJournalTest.TestItem("bread"),
                        new GathererJournalTest.TestItem("gold"),
                        new GathererJournalTest.TestItem("gold"),
                        new GathererJournalTest.TestItem("gold"),
                        new GathererJournalTest.TestItem("gold")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                6000,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.NO_SPACE, result.status());
        Assertions.assertEquals(ImmutableList.of(
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("gold")
        ), result.items());
    }

    @Test
    public void test_6010_WithBreadInContainers_ShouldBeReturningWithoutLoot() {

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                6010,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNING, result.status());
        Assertions.assertTrue(result.items()
                .stream()
                .allMatch(GathererJournalTest.TestItem::isEmpty)); // Loot is not given until evening
        Assertions.assertTrue(infiniteStorage.container.isEmpty());
    }

    @Test
    public void test_11499_WithBreadInContainers_ShouldBeReturningWithoutLoot() {
        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                11499,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNING, result.status());
        Assertions.assertTrue(result.items()
                .stream()
                .allMatch(GathererJournalTest.TestItem::isEmpty)); // Loot is not given until evening
        Assertions.assertTrue(infiniteStorage.container.isEmpty());
    }

    @Test
    public void test_11500_WithBreadInContainers_ShouldBeReturnedSuccessWithLoot() {
        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                11500,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, result.status());
        Assertions.assertEquals(specificLoot.giveLoot(), result.items());
        Assertions.assertTrue(infiniteStorage.container.isEmpty());
    }

    @Test
    public void test_11501_WithBreadInContainers_ShouldBeDroppingLoot_AndItemsShouldGoToTown() {
        int ticksPassed = 11501;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, result.status());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournal.Item::isEmpty));
        Assertions.assertEquals(specificLoot.giveLoot(), infiniteStorage.container);
    }

    @Test
    public void test_11501_WithTwoBreadInContainers_ShouldBeDroppingLoot_AndItemsShouldBeAddedToTown() {
        int ticksPassed = 11501;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );

        ImmutableList.Builder<GathererJournalTest.TestItem> b = ImmutableList.builder();
        b.add(new GathererJournalTest.TestItem("bread"));
        b.addAll(specificLoot.giveLoot());
        ImmutableList<GathererJournalTest.TestItem> expectedTownLoot = b.build();

        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, result.status());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournal.Item::isEmpty));
        Assertions.assertEquals(expectedTownLoot, infiniteStorage.container);
    }


    @Test
    public void test_11501_WithBreadInLimitedContainers_ShouldBeDroppingLoot_AndSomeItemsShouldBeMovedToTown() {
        int ticksPassed = 11501;

        FakeTownWithInfiniteStorage sizeSixStorage = new FakeTownWithInfiniteStorage() {
            @Override
            public ImmutableList<GathererJournalTest.TestItem> depositItems(ImmutableList<GathererJournalTest.TestItem> itemsToDeposit) {
                ImmutableList.Builder<GathererJournalTest.TestItem> itemsToReturn = ImmutableList.builder();

                for (GathererJournalTest.TestItem i : itemsToDeposit) {
                    if (container.size() < 6) {
                        container.add(i);
                    } else {
                        itemsToReturn.add(i);
                    }
                }

                return itemsToReturn.build();
            }

            @Override
            public boolean IsStorageAvailable() {
                return container.size() < 6;
            }
        };

        sizeSixStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("wheat"),
                new GathererJournalTest.TestItem("seeds")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                sizeSixStorage,
                specificLoot,
                sizeSixStorage,
                () -> new GathererJournalTest.TestItem("")
        );

        ImmutableList<GathererJournalTest.TestItem> expectedTownLoot = ImmutableList.of(
                new GathererJournalTest.TestItem("wheat"),
                new GathererJournalTest.TestItem("seeds"),
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron")
        );

        ImmutableList<GathererJournalTest.TestItem> expectedKeptLoot = ImmutableList.of(
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond"),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem("")
        );

        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, result.status());
        Assertions.assertEquals(expectedKeptLoot, result.items());
        Assertions.assertEquals(expectedTownLoot, sizeSixStorage.container);
    }

    @Test
    public void test_24001_WithOneBreadInContainers_ShouldSetStatusNoFood() {
        int ticksPassed = 24001;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.NO_FOOD, result.status());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournal.Item::isEmpty));
        Assertions.assertEquals(specificLoot.giveLoot(), infiniteStorage.container); // From the first day
    }

    @Test
    public void test_24002_WithOneBreadInContainers_ShouldSetStatusNoFood() {
        int ticksPassed = 24002;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.NO_FOOD, result.status());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournal.Item::isEmpty));
        Assertions.assertEquals(specificLoot.giveLoot(), infiniteStorage.container); // From the first day
    }

    @Test
    public void test_24002_WithTwoBreadInContainers_ShouldSetStatusGathering_AndTakeBothBreads() {
        int ticksPassed = 24002;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.GATHERING, result.status());
        Assertions.assertEquals(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem(""),
                new GathererJournalTest.TestItem("")
        ), result.items());
        Assertions.assertEquals(specificLoot.giveLoot(), infiniteStorage.container); // From the first day
    }

    @Test
    public void test_35500_WithTwoBreadInContainers_ShouldBeReturnedSuccessWithLoot() {
        int ticksPassed = 35500;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );
        Assertions.assertEquals(GathererJournal.Status.RETURNED_SUCCESS, result.status());
        Assertions.assertEquals(specificLoot.giveLoot(), result.items());
        Assertions.assertEquals(specificLoot.giveLoot(), infiniteStorage.container);
    }

    @Test
    public void test_35501_WithTwoBreadInContainers_ShouldBeDroppingLootWithEmptyInventory_AndTwoLootBatchesAddedToTown() {
        int ticksPassed = 35501;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );

        ImmutableList<GathererJournalTest.TestItem> expectedTownLoot = ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond"), // <-- From day one
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond") // <-- From day two
        );

        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, result.status());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournalTest.TestItem::isEmpty));
        Assertions.assertEquals(expectedTownLoot, infiniteStorage.container);
    }

    @Test
    public void test_35501_WithThreeBreadInContainers_ShouldBeDroppingLootWithEmptyInventory_AndTwoLootBatchesAddedToTownBread() {
        int ticksPassed = 35501;

        FakeTownWithInfiniteStorage infiniteStorage = new FakeTownWithInfiniteStorage();

        infiniteStorage.depositItems(ImmutableList.of(
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("bread"),
                new GathererJournalTest.TestItem("bread")
        ));

        GathererJournal.Snapshot<GathererJournalTest.TestItem> initial = new GathererJournal.Snapshot<>(
                GathererJournal.Status.IDLE, // Technically this should also work if the status is NO_FOOD
                ImmutableList.of(
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem(""),
                        new GathererJournalTest.TestItem("")
                )
        );

        GathererJournals.LootGiver<GathererJournalTest.TestItem> specificLoot = () -> ImmutableList.of(
                new GathererJournalTest.TestItem("flint"),
                new GathererJournalTest.TestItem("wood"),
                new GathererJournalTest.TestItem("stone"),
                new GathererJournalTest.TestItem("iron"),
                new GathererJournalTest.TestItem("gold"),
                new GathererJournalTest.TestItem("diamond")
        );

        GathererJournal.Snapshot<GathererJournalTest.TestItem> result = GathererJournals.timeWarp(
                initial,
                ticksPassed,
                infiniteStorage,
                specificLoot,
                infiniteStorage,
                () -> new GathererJournalTest.TestItem("")
        );

        ImmutableList.Builder<GathererJournalTest.TestItem> b = ImmutableList.builder();
        b.add(new GathererJournalTest.TestItem("bread"));
        b.addAll(specificLoot.giveLoot());
        b.addAll(specificLoot.giveLoot());
        ImmutableList<GathererJournalTest.TestItem> expectedTownLoot = b.build();

        Assertions.assertEquals(GathererJournal.Status.DROPPING_LOOT, result.status());
        Assertions.assertTrue(result.items().stream().allMatch(GathererJournalTest.TestItem::isEmpty));
        Assertions.assertEquals(expectedTownLoot, infiniteStorage.container);
    }

}