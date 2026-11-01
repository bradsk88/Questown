package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EconomicsCardOrderTest {

    private static final String FOOD = "#questown:villager_food";
    private static final ItemEconomicsData SWORDS = new ItemEconomicsData("#questown:swords", 9);
    private static final ItemEconomicsData FOOD_NEED = new ItemEconomicsData(FOOD, 3);
    private static final ItemEconomicsData COAL = new ItemEconomicsData("minecraft:coal", 1);

    @Test
    void theFreeFoodCardStaysInRowOneWhileTheClaimIsOpen() {
        assertEquals(ImmutableList.of(FOOD_NEED, SWORDS, COAL),
                EconomicsCardOrder.order(ImmutableList.of(SWORDS, FOOD_NEED, COAL), true, FOOD));
    }

    @Test
    void afterTheClaimTheRankingIsUntouched() {
        assertEquals(ImmutableList.of(SWORDS, FOOD_NEED, COAL),
                EconomicsCardOrder.order(ImmutableList.of(SWORDS, FOOD_NEED, COAL), false, FOOD));
    }

    @Test
    void noFoodNeedNoChange() {
        assertEquals(ImmutableList.of(SWORDS, COAL),
                EconomicsCardOrder.order(ImmutableList.of(SWORDS, COAL), true, FOOD));
    }
}
