package ca.bradj.questown.jobs;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class NoMCNeedsTest {

    @Test
    public void testTwoSimpleIngredients() {
        ImmutableMap<Integer, Integer> qtys = ImmutableMap.of(
                0, 1, //
                1, 1
        );
        Assertions.assertEquals(0, NoMCNeeds.getActualIndex(1, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(2, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(3, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(4, qtys));
    }

    @Test
    public void testTwoIngredientsFirstWithQuantity() {
        ImmutableMap<Integer, Integer> qtys = ImmutableMap.of(
                0, 2, //
                1, 1
        );
        Assertions.assertEquals(0, NoMCNeeds.getActualIndex(1, qtys));
        Assertions.assertEquals(0, NoMCNeeds.getActualIndex(2, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(3, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(4, qtys));
    }

    @Test
    public void testTwoIngredientsSecondWithQuantity() {
        ImmutableMap<Integer, Integer> qtys = ImmutableMap.of(
                0, 1, //
                1, 2
        );
        Assertions.assertEquals(0, NoMCNeeds.getActualIndex(1, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(2, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(3, qtys));
        Assertions.assertEquals(1, NoMCNeeds.getActualIndex(4, qtys));
    }

}