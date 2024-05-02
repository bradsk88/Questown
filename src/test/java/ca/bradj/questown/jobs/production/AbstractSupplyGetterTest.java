package ca.bradj.questown.jobs.production;

import ca.bradj.questown.jobs.AmountHeld;
import ca.bradj.questown.jobs.GathererJournalTest;
import ca.bradj.questown.jobs.JobsClean;
import ca.bradj.questown.jobs.StatusesProductionRoutineTest;
import ca.bradj.questown.mc.Util;
import ca.bradj.roomrecipes.core.Room;
import ca.bradj.roomrecipes.core.space.InclusiveSpace;
import ca.bradj.roomrecipes.core.space.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

class AbstractSupplyGetterTest {

    public static final Room ARBITRARY_ROOM = new Room(
            new Position(1, 2),
            new InclusiveSpace(new Position(3, 4), new Position(5, 6))
    );

    private static class TestSupplyGetter extends AbstractSupplyGetter<StatusesProductionRoutineTest.PTestStatus, Position, GathererJournalTest.TestItem, GathererJournalTest.TestItem> {

    }

    @Test
    void tryGetSupplies_ShouldGrabExactlyOneItem_OnFirstAttempt() {
        // Grabbing more is a level-up ability. We can implement support once leveling up exists.
        TestSupplyGetter g = new TestSupplyGetter();
        ImmutableMap<StatusesProductionRoutineTest.PTestStatus, Collection<Room>> roomsNeedingIngOrTool = ImmutableMap.of(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.fromJobBlockState(0),
                ImmutableList.of(ARBITRARY_ROOM)
        );
        Supplier<GathererJournalTest.TestItem> neededItem = () -> new GathererJournalTest.TestItem("Map");
        final Map<Integer, Integer> removedFromSlots = new HashMap<>();

        final Map<StatusesProductionRoutineTest.PTestStatus, JobsClean.TestFn<GathererJournalTest.TestItem>> recipe = ImmutableMap.of(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.fromJobBlockState(0), (state, item) -> neededItem.get().equals(item)
        );

        JobsClean.SuppliesTarget<Position, GathererJournalTest.TestItem> suppliesTarget = new JobsClean.SuppliesTarget<>() {
            @Override
            public boolean isCloseTo() {
                return true;
            }

            @Override
            public String toShortString() {
                return "chest";
            }

            @Override
            public List<GathererJournalTest.TestItem> getItems() {
                return ImmutableList.of(neededItem.get(), neededItem.get());
            }

            @Override
            public void removeItem(
                    int i,
                    int quantity
            ) {
                removedFromSlots.compute(i, (k, v) -> v == null ? quantity : v + quantity);
            }
        };

        ArrayList<GathererJournalTest.TestItem> taken = new ArrayList<>();

        g.tryGetSupplies(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.collectingSupplies(),
                6, // Standard inventory size
                roomsNeedingIngOrTool,
                suppliesTarget,
                (state) -> ImmutableList.of(Util.getOrDefault(recipe, state, (ztate, item) -> false)::test),
                () -> ImmutableList.of(
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem("") // Air
                ),
                taken::add
        );

        Assertions.assertIterableEquals(ImmutableList.of(neededItem.get()), taken);
    }

    @Test
    void tryGetSupplies_ShouldGrabExactlyOneItem_OnFirstAttempt_IfRecipeCallsForTwoItems() {
        // Grabbing more is a level-up ability. We can implement support once leveling up exists.
        TestSupplyGetter g = new TestSupplyGetter();
        ImmutableMap<StatusesProductionRoutineTest.PTestStatus, Collection<Room>> roomsNeedingIngOrTool = ImmutableMap.of(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.fromJobBlockState(0),
                ImmutableList.of(ARBITRARY_ROOM)
        );
        Supplier<GathererJournalTest.TestItem> neededItem = () -> new GathererJournalTest.TestItem("Map");
        final Map<Integer, Integer> removedFromSlots = new HashMap<>();

        final Map<StatusesProductionRoutineTest.PTestStatus, Collection<BiPredicate<AmountHeld, GathererJournalTest.TestItem>>> recipe = ImmutableMap.of(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.fromJobBlockState(0), ImmutableList.of(
                        (s, item) -> neededItem.get().equals(item),
                        (s, item) -> neededItem.get().equals(item)
                )
        );

        JobsClean.SuppliesTarget<Position, GathererJournalTest.TestItem> suppliesTarget = new JobsClean.SuppliesTarget<>() {
            @Override
            public boolean isCloseTo() {
                return true;
            }

            @Override
            public String toShortString() {
                return "chest";
            }

            @Override
            public List<GathererJournalTest.TestItem> getItems() {
                return ImmutableList.of(neededItem.get(), neededItem.get());
            }

            @Override
            public void removeItem(
                    int i,
                    int quantity
            ) {
                removedFromSlots.compute(i, (k, v) -> v == null ? quantity : v + quantity);
            }
        };

        ArrayList<GathererJournalTest.TestItem> taken = new ArrayList<>();

        g.tryGetSupplies(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.collectingSupplies(),
                6, // Standard inventory size
                roomsNeedingIngOrTool,
                suppliesTarget,
                (state) -> recipe.getOrDefault(state, ImmutableList.of((s, o) -> false)),
                () -> ImmutableList.of(
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem("") // Air
                ),
                taken::add
        );

        Assertions.assertIterableEquals(ImmutableList.of(neededItem.get()), taken);
    }

    @Test
    void tryGetSupplies_ShouldNotGrabMore_OnSecondAttempt_IfRecipeCallsForOneItem() {
        // Grabbing more is a level-up ability. We can implement support once leveling up exists.
        TestSupplyGetter g = new TestSupplyGetter();
        ImmutableMap<StatusesProductionRoutineTest.PTestStatus, Collection<Room>> roomsNeedingIngOrTool = ImmutableMap.of(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.fromJobBlockState(0),
                ImmutableList.of(ARBITRARY_ROOM)
        );
        Supplier<GathererJournalTest.TestItem> neededItem = () -> new GathererJournalTest.TestItem("Map");
        final Map<Integer, Integer> removedFromSlots = new HashMap<>();

        final Map<StatusesProductionRoutineTest.PTestStatus, Collection<BiPredicate<AmountHeld, GathererJournalTest.TestItem>>> recipe = ImmutableMap.of(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.fromJobBlockState(0), ImmutableList.of(
                        (s, item) -> neededItem.get().equals(item)
                )
        );

        JobsClean.SuppliesTarget<Position, GathererJournalTest.TestItem> suppliesTarget = new JobsClean.SuppliesTarget<>() {
            @Override
            public boolean isCloseTo() {
                return true;
            }

            @Override
            public String toShortString() {
                return "chest";
            }

            @Override
            public List<GathererJournalTest.TestItem> getItems() {
                return ImmutableList.of(neededItem.get(), neededItem.get());
            }

            @Override
            public void removeItem(
                    int i,
                    int quantity
            ) {
                removedFromSlots.compute(i, (k, v) -> v == null ? quantity : v + quantity);
            }
        };

        ArrayList<GathererJournalTest.TestItem> taken = new ArrayList<>();

        g.tryGetSupplies(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.collectingSupplies(),
                6, // Standard inventory size
                roomsNeedingIngOrTool,
                suppliesTarget,
                (state) -> recipe.get(state),
                () -> ImmutableList.of(
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem("") // Air
                ),
                taken::add
        );

        g.tryGetSupplies(
                StatusesProductionRoutineTest.PTestStatus.FACTORY.collectingSupplies(),
                6, // Standard inventory size
                roomsNeedingIngOrTool,
                suppliesTarget,
                recipe::get,
                () -> ImmutableList.of(
                        neededItem.get(), // Pretend the inventory was updated
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem(""), // Air
                        new GathererJournalTest.TestItem("") // Air
                ),
                taken::add
        );

        Assertions.assertIterableEquals(ImmutableList.of(neededItem.get()), taken);
    }
}