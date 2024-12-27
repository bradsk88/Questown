package ca.bradj.questown.gui;

import net.minecraft.world.item.crafting.Ingredient;

public record ItemEconomicsData(
        Ingredient item,
        int timesNeeded
) {
    public static final int PERIOD_DAYS = 1;
}
