package ca.bradj.questown.gui;

import com.google.common.collect.ImmutableList;

/** Orders the economics cards, most needed first. While the free food is unclaimed, its card stays in the first row. */
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
