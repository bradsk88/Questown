package ca.bradj.questown.gui;

import ca.bradj.questown.mc.Compat;
import ca.bradj.questown.town.econ.NoMCEconomics;
import net.minecraft.network.chat.Component;

public record ItemEconomicsData(
        String ingredientKey,
        int timesNeeded
) implements NoMCEconomics.Needable {
    public static final int PERIOD_DAYS = 1;

    /**
     * One sentence, so a wrapped tooltip flows it as a paragraph (two hand-broken keys left
     * the count alone on its own line once wrapped; playtest 2026-10-22).
     */
    public static Component neededTooltip(Component itemName, int timesNeeded) {
        if (PERIOD_DAYS == 1) {
            return Compat.translatable("questown.menu.needs_in_past_day", itemName, timesNeeded);
        }
        return Compat.translatable("questown.menu.needs_in_period", itemName, timesNeeded, PERIOD_DAYS);
    }
}
