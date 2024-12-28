package ca.bradj.questown.gui;

public record ItemEconomicsData(
        String ingredientKey,
        int timesNeeded
) {
    public static final int PERIOD_DAYS = 1;
}
