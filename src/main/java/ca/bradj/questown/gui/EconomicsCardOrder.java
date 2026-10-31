package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;

/**
 * The economics tab lists needs most-needed first. While the one-time free-food claim is open, the
 * food card is the day-1 lifeline and stays in row 1: a new need ranking above it pushed it to row
 * 2 and the player's click landed on the wrong card (2026-10-22 r3 #28).
 */
public final class EconomicsCardOrder {

    private EconomicsCardOrder() {
    }

    public static ImmutableList<ItemEconomicsData> order(
            ImmutableList<ItemEconomicsData> ranked,
            boolean pinFood,
            String foodKey
    ) {
        if (!pinFood) {
            return ranked;
        }
        ImmutableList.Builder<ItemEconomicsData> b = ImmutableList.builder();
        ranked.stream().filter(d -> foodKey.equals(d.ingredientKey())).forEach(b::add);
        ranked.stream().filter(d -> !foodKey.equals(d.ingredientKey())).forEach(b::add);
        return b.build();
    }
}
