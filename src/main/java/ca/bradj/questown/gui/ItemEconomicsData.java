package ca.bradj.questown.gui;

import ca.bradj.questown.town.NoMCEconomics;

public record ItemEconomicsData(
        String ingredientKey,
        int timesNeeded
) implements NoMCEconomics.Needable {
    public static final int PERIOD_DAYS = 1;
}
