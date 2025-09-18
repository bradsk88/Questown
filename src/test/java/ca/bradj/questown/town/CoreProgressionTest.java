package ca.bradj.questown.town;

import ca.bradj.questown.gui.ItemEconomicsData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class CoreProgressionTest {

    private static final CoreProgression<String, String> INSTANCE = new CoreProgression<>(
            ImmutableList.of(),
            "gatherer"::equals,
            l -> l.size() == 1 ? ImmutableList.copyOf(l).get(0) : "random from [" + String.join(",", l) + "]",
            job -> false,
            (job, all) -> ImmutableList.of() // No jobs meet resource needs
    );

    @Test
    public void testOnlyGatherer() {
        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer"),
                j -> "town gate",
                j -> {
                    throw new AssertionError("Should not get here");
                }
        );
        Assertions.assertNull(result);
    }

    @Test
    public void testOnlyOneOtherOptionWhoseRoomHasNotBeenRequestedYet() {
        boolean questAlreadyExists = false;
        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer", "only_one"),
                job -> "arbitrary", // Just needs a value
                room -> questAlreadyExists
        );
        Assertions.assertEquals("only_one", result);
    }

    @Test
    public void testOnlyOneOtherOptionWhoseRoomHasAlreadyBeenRequested() {
        boolean questAlreadyExists = true;
        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer", "only_one"),
                job -> "arbitrary", // Just needs a value
                room -> questAlreadyExists
        );
        // Since there is only one option, we'll go with it. Even though we
        // would prefer something that would warrant a new quest.
        Assertions.assertEquals("only_one", result);
    }

    @Test
    public void testOnlyOneOtherOptionWhoseRoomIsAbsentFromCurrentQuests() {
        boolean questAlreadyExists = false;
        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer", "only_one"),
                job -> "arbitrary", // Just needs a value
                room -> questAlreadyExists
        );
        Assertions.assertEquals("only_one", result);
    }

    @Test
    public void testTwoOtherOptionsWhereOnlyOneRoomIsAlreadyRequested() {

        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "one", "room for one",
                "two", "room for two"
        );

        // Quest already exists for job "two"
        Predicate<String> questAlreadyExists = "room for two"::equals;

        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer", "one", "two"),
                rooms::get,
                questAlreadyExists
        );

        // Prefer the job where a quest DOESN'T already exist
        Assertions.assertEquals("one", result);
    }

    @Test
    public void testTwoOtherOptionsWhereNoRoomsRequestedYet() {

        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "one", "room for one",
                "two", "room for two"
        );

        Predicate<String> questAlreadyExists = room -> false;

        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer", "one", "two"),
                rooms::get,
                questAlreadyExists
        );

        // Since both rooms are equally good, choose one randomly
        Assertions.assertEquals("random from [one,two]", result);
    }

    @Test
    public void testTwoOtherOptionsWhereRoomsRequestedAlreadyForBoth() {
        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "one", "room for one",
                "two", "room for two"
        );

        Predicate<String> questAlreadyExists = room -> true;

        String result = INSTANCE.getFirstJobChange(
                ImmutableList.of("gatherer", "one", "two"),
                rooms::get,
                questAlreadyExists
        );

        // Since both rooms are equally bad, choose one randomly
        Assertions.assertEquals("random from [one,two]", result);
    }

    @Test
    public void testTwoRequestableOtherOptionsWhereFoodIsProducedByOneOnly() {
        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "farmer", "farm",
                "miner", "mine"
        );

        Predicate<String> questAlreadyExists = room -> false;

        Predicate<String> jobMakesFood = "farmer"::equals;
        CoreProgression<String, String> p = INSTANCE.withFoodCheck(jobMakesFood);

        String result = p.getFirstJobChange(
                ImmutableList.of("gatherer", "farmer", "miner"),
                rooms::get,
                questAlreadyExists
        );

        Assertions.assertEquals("farmer", result);
    }

    @Test
    public void testTwoRequestableOtherOptionsWhereFoodIsProducedByBoth() {
        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "farmer", "farm",
                "miner", "mine"
        );

        Predicate<String> questAlreadyExists = room -> false;

        Predicate<String> jobMakesFood = job -> true;
        CoreProgression<String, String> p = INSTANCE.withFoodCheck(jobMakesFood);

        String result = p.getFirstJobChange(
                ImmutableList.of("gatherer", "farmer", "miner"),
                rooms::get,
                questAlreadyExists
        );

        Assertions.assertEquals("random from [farmer,miner]", result);
    }

    @Test
    public void testTwoRequestableOtherOptionsWhereFoodIsProducedByNeither() {
        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "farmer", "farm",
                "miner", "mine"
        );

        Predicate<String> questAlreadyExists = room -> false;

        Predicate<String> jobMakesFood = job -> false;
        CoreProgression<String, String> p = INSTANCE.withFoodCheck(jobMakesFood);

        String result = p.getFirstJobChange(
                ImmutableList.of("gatherer", "farmer", "miner"),
                rooms::get,
                questAlreadyExists
        );

        Assertions.assertEquals("random from [farmer,miner]", result);
    }

    @Test
    public void testTwoRequestableFoodlessOtherOptionsWhereANeededResourceIsProducedByOneOnly() {
        ImmutableMap<String, String> rooms = ImmutableMap.of(
                "gatherer", "gate",
                "farmer", "farm",
                "miner", "mine"
        );

        ImmutableMap<String, ImmutableList<ItemEconomicsData>> timesNeeded = ImmutableMap.of(
                "gatherer", ImmutableList.of(),
                "farmer", ImmutableList.of(),
                "miner", ImmutableList.of(new ItemEconomicsData("iron", 1))
        );

        Predicate<String> questAlreadyExists = room -> false;
        Predicate<String> jobMakesFood = job -> false;
        CoreProgression<String, String> p = INSTANCE
                .withFoodCheck(jobMakesFood)
                .withEconomics((j, all) -> timesNeeded.get(j));

        String result = p.getFirstJobChange(
                ImmutableList.of("gatherer", "farmer", "miner"),
                rooms::get,
                questAlreadyExists
        );

        Assertions.assertEquals("miner", result);

        // TODO: Cases for "both" and "neither"
    }
}